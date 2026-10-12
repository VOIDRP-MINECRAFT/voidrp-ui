package ru.voidrp.ui.input

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener

/**
 * The pointer as the player's own mouse, for a client that runs our companion mod.
 *
 * A vanilla client says nothing about its mouse while it plays, which is why the ordinary
 * pointer is the player's look. The mod (`voidrp_client_info` on Origins) opens an empty,
 * transparent screen while a page is up, so the system cursor is free and the page — on the
 * boss bars behind it — shows exactly as always, and it reports the mouse here. The page is
 * then pointed at with the real cursor: no lag but the round trip for the highlight, real
 * wheel, real drags.
 *
 * Two channels, both plain big-endian bytes so neither side needs the other's codec:
 *
 *  - [SCREEN], to the client: `op:byte arg:byte` — [OPEN], [CLOSE], [CURSOR] with a [Shape].
 *  - [INPUT], from the client, nine bytes: `kind:byte a:short b:short c:int` — see [Input].
 *
 * Whether a client has the mod is whether it listens on [SCREEN].
 */
class ClientPointer(
    private val plugin: Plugin,
    private val receive: (Player, Input) -> Unit,
    /** Whether a page is up for this player now. */
    private val pageOpen: (Player) -> Boolean = { false },
) : PluginMessageListener {

    /** What the client sent, decoded. Positions are canvas units: x from the middle, y from the top. */
    sealed interface Input {
        data class Move(val dx: Int, val y: Int) : Input
        data class Press(val dx: Int, val y: Int, val button: Int, val down: Boolean) : Input
        data class Scroll(val dx: Int, val y: Int, val amount: Int) : Input
        data class Key(val key: Int, val modifiers: Int) : Input
        data class Character(val codePoint: Int) : Input
        /** The client took its screen down by itself — the server never answered an escape. */
        data object Closed : Input
        /** The window's size in pixels, sent when the server is first heard from and on every resize. */
        data class Window(val width: Int, val height: Int) : Input
    }

    enum class Shape(val code: Int) { ARROW(0), HAND(1), TEXT(2) }

    fun register() {
        val messenger = plugin.server.messenger
        messenger.registerOutgoingPluginChannel(plugin, SCREEN)
        messenger.registerIncomingPluginChannel(plugin, INPUT, this)
        // Players already on the server were told the channels at their join, before this
        // one existed, and the mod sends only on a channel it was told of: on a live test a
        // plugin loaded after the join opened the page and never heard the mouse.
        plugin.server.onlinePlayers.forEach { player ->
            runCatching { player.javaClass.getMethod("sendSupportedChannels").invoke(player) }
        }
    }

    fun unregister() {
        val messenger = plugin.server.messenger
        runCatching { messenger.unregisterOutgoingPluginChannel(plugin, SCREEN) }
        runCatching { messenger.unregisterIncomingPluginChannel(plugin, INPUT, this) }
    }

    /** Whether this player's client can take the pointer over. */
    fun available(player: Player): Boolean = runCatching { SCREEN in player.listeningPluginChannels }.getOrDefault(false)

    fun open(player: Player) = send(player, OPEN, 0)

    /**
     * Takes the client's screen down — a tick later, and only if no page took this one's
     * place by then. Going from one page to the next closes a session and opens another, and
     * a close sent in between has the client grab the mouse for a frame and put the cursor
     * back in the middle of the screen. While the plugin is being disabled there is no next
     * tick, so it goes at once.
     */
    fun close(player: Player) {
        val later = runCatching {
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (player.isOnline && !pageOpen(player)) send(player, CLOSE, 0)
            })
        }
        if (later.isFailure) send(player, CLOSE, 0)
    }

    fun cursor(player: Player, shape: Shape) = send(player, CURSOR, shape.code)

    private fun send(player: Player, op: Int, arg: Int) {
        runCatching { player.sendPluginMessage(plugin, SCREEN, byteArrayOf(op.toByte(), arg.toByte())) }
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != INPUT || message.size < SIZE) return
        val data = DataInputStream(ByteArrayInputStream(message))
        val kind = data.readUnsignedByte()
        val a = data.readShort().toInt()
        val b = data.readShort().toInt()
        val c = data.readInt()
        val input = when (kind) {
            1 -> Input.Move(a, b)
            2 -> Input.Press(a, b, c shr 1, (c and 1) == 1)
            3 -> Input.Scroll(a, b, c.coerceIn(-1, 1))
            4 -> Input.Key(c, a)
            5 -> Input.Character(c)
            6 -> Input.Closed
            7 -> Input.Window(a and 0xFFFF, b and 0xFFFF)
            else -> return
        }
        receive(player, input)
    }

    companion object {
        const val SCREEN = "voidrp:ui_screen"
        const val INPUT = "voidrp:ui_input"
        const val CLOSE = 0
        const val OPEN = 1
        const val CURSOR = 2
        private const val SIZE = 9

        // GLFW key codes the pages answer to.
        const val KEY_ESCAPE = 256
        const val KEY_BACKSPACE = 259
        const val KEY_1 = 49
        const val KEY_9 = 57
    }
}
