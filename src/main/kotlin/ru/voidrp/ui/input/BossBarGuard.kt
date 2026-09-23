package ru.voidrp.ui.input

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBossBar
import java.util.Collections
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Keeps the page on the first boss bar, whatever else is on the player's screen.
 *
 * A page travels in a boss bar's title, and bars stack in the order the client first saw
 * them. So a bar another plugin was already showing — a TPS meter, an event timer — puts
 * ours second, and the whole page is drawn a line lower than every y it carries: nineteen
 * units of the world along the top of the screen, and the bottom row off the edge.
 *
 * The server cannot see what is on the client's screen, and it cannot move a bar it does
 * not own. What it can do is watch the packets going out, and that is enough: a bar that
 * is removed and immediately added again returns to the screen at the **bottom** of the
 * stack. So when a page opens, every other plugin's bar is replayed exactly as it was
 * sent, and lands below the page instead of above it.
 *
 * Nothing is hidden and nothing is lost: the bar keeps its id, so its own plugin goes on
 * updating it as if nothing happened. It flickers once.
 *
 * Needs PacketEvents. Without it there is no way to know a foreign bar exists at all, and
 * the plugin says so in the log instead.
 */
class BossBarGuard {

    /** Everything needed to put a bar back exactly as it was. */
    private data class Bar(
        val id: UUID,
        val title: Component,
        val health: Float,
        val colour: BossBar.Color,
        val overlay: BossBar.Overlay,
        val flags: EnumSet<BossBar.Flag>,
    )

    /** Other plugins' bars, per player, in the order the client received them. */
    private val foreign = ConcurrentHashMap<UUID, MutableMap<UUID, Bar>>()

    /** Our own bars, so they are never mistaken for someone else's. */
    private val ours = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    /** How many of our own bars are still on their way out to each player. */
    private val expected = ConcurrentHashMap<UUID, Int>()

    private var listener: PacketListenerAbstract? = null

    val installed: Boolean get() = listener != null

    fun install(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("packetevents")?.isEnabled != true) return false
        return runCatching {
            listener = PacketEvents.getAPI().eventManager.registerListener(
                object : PacketListenerAbstract(PacketListenerPriority.MONITOR) {
                    override fun onPacketSend(event: PacketSendEvent) {
                        if (event.packetType != PacketType.Play.Server.BOSS_BAR) return
                        // Nothing here is worth breaking someone else's packet over.
                        runCatching { watch(event) }
                    }

                    private fun watch(event: PacketSendEvent) {
                        val player = event.user.uuid ?: return
                        val bar = WrapperPlayServerBossBar(event)
                        when (bar.action) {
                            WrapperPlayServerBossBar.Action.ADD -> remember(player, bar)
                            WrapperPlayServerBossBar.Action.REMOVE -> {
                                foreign[player]?.remove(bar.uuid)
                                ours[player]?.remove(bar.uuid)
                            }

                            else -> Unit
                        }
                    }
                },
            ).let { it as? PacketListenerAbstract }
            true
        }.getOrDefault(false)
    }

    /** Takes the listener off again — PacketEvents outlives this plugin. */
    fun uninstall() {
        val registered = listener ?: return
        listener = null
        runCatching { PacketEvents.getAPI().eventManager.unregisterListener(registered) }
        foreign.clear()
        ours.clear()
        expected.clear()
    }

    /**
     * Said by the renderer just before it shows a bar of ours.
     *
     * There is no way to ask Adventure what id it gave a bar, so the next one out is taken
     * to be that one. Both are created on the same thread as this call, one after the
     * other, so in practice nothing gets in between.
     */
    fun expectOwn(player: Player) {
        if (listener == null) return
        expected.merge(player.uniqueId, 1) { a, b -> a + b }
    }

    /**
     * Puts every other plugin's bar back underneath ours.
     *
     * Called once, after a page's own bars exist. A bar that arrives later is already
     * below them and needs nothing.
     */
    fun demoteOthers(player: Player) {
        if (listener == null) return
        val bars = foreign[player.uniqueId]?.values?.toList().orEmpty()
        if (bars.isEmpty()) return
        val api = runCatching { PacketEvents.getAPI().playerManager }.getOrNull() ?: return
        bars.forEach { bar ->
            runCatching {
                api.sendPacket(player, WrapperPlayServerBossBar(bar.id, WrapperPlayServerBossBar.Action.REMOVE))
                api.sendPacket(player, add(bar))
            }
        }
    }

    /** How many bars other plugins are showing this player — for the log. */
    fun othersFor(player: Player): Int = foreign[player.uniqueId]?.size ?: 0

    fun forget(player: UUID) {
        foreign.remove(player)
        ours.remove(player)
        expected.remove(player)
    }

    private fun remember(player: UUID, bar: WrapperPlayServerBossBar) {
        val pending = expected[player] ?: 0
        if (pending > 0) {
            expected[player] = pending - 1
            ours.computeIfAbsent(player) { Collections.newSetFromMap(ConcurrentHashMap()) } += bar.uuid
            return
        }
        if (ours[player]?.contains(bar.uuid) == true) return
        foreign.computeIfAbsent(player) { Collections.synchronizedMap(LinkedHashMap()) }[bar.uuid] = Bar(
            bar.uuid,
            bar.title,
            bar.health,
            bar.color,
            bar.overlay,
            // Not EnumSet.copyOf: it throws on an empty collection, and a bar with no
            // flags — which is most of them — would have thrown on every packet.
            EnumSet.noneOf(BossBar.Flag::class.java).apply { addAll(bar.flags) },
        )
    }

    private fun add(bar: Bar): WrapperPlayServerBossBar =
        WrapperPlayServerBossBar(bar.id, WrapperPlayServerBossBar.Action.ADD).apply {
            title = bar.title
            health = bar.health
            color = bar.colour
            overlay = bar.overlay
            flags = bar.flags
        }
}
