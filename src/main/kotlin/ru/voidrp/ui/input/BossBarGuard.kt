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
 * not own. What it can do is watch the packets going out, and that is enough.
 *
 * **While a page is open, other plugins' bars are held back.** Replaying them below the page
 * kept the page in its place, but a bar below ours is a line further down the screen, and
 * on a live client an event timer's title came out across the middle of the page. A page
 * covers the screen the way the game's own menus cover the HUD, so those bars go while it
 * is open: every packet for them is kept — a new bar, a changed title, a timer ticking down —
 * and when the last page closes they come back exactly as they are by then, not as they
 * were. The owning plugin is never told and goes on updating them as if nothing happened.
 *
 * [demoteOthers] is still here for a server that would rather keep the bars in sight.
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

    /** Players whose screen a page has now: other plugins' bars are held back for them. */
    private val hiding: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    private var listener: PacketListenerAbstract? = null

    val installed: Boolean get() = listener != null

    fun install(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("packetevents")?.isEnabled != true) return false
        return runCatching {
            listener = PacketEvents.getAPI().eventManager.registerListener(
                // Not MONITOR: holding a bar back means cancelling its packet.
                object : PacketListenerAbstract(PacketListenerPriority.NORMAL) {
                    override fun onPacketSend(event: PacketSendEvent) {
                        if (event.packetType != PacketType.Play.Server.BOSS_BAR) return
                        // Nothing here is worth breaking someone else's packet over.
                        runCatching { watch(event) }
                    }

                    private fun watch(event: PacketSendEvent) {
                        val player = event.user.uuid ?: return
                        val bar = WrapperPlayServerBossBar(event)
                        val held = player in hiding
                        when (bar.action) {
                            WrapperPlayServerBossBar.Action.ADD -> {
                                remember(player, bar)
                                if (held && foreign[player]?.containsKey(bar.uuid) == true) event.isCancelled = true
                            }

                            WrapperPlayServerBossBar.Action.REMOVE -> {
                                val wasForeign = foreign[player]?.remove(bar.uuid) != null
                                ours[player]?.remove(bar.uuid)
                                if (held && wasForeign) event.isCancelled = true
                            }

                            // Changes are kept even when nothing is held back, so a bar put
                            // back is the bar as it is now — a replay of how it looked when it
                            // was first sent showed a timer from minutes ago.
                            else -> if (update(player, bar) && held) event.isCancelled = true
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

    /**
     * Takes every other plugin's bar off this player's screen until [showOthers].
     *
     * Sent silently, so these packets do not come back through the watcher above and get
     * held back themselves.
     */
    fun hideOthers(player: Player) {
        if (listener == null) return
        if (!hiding.add(player.uniqueId)) return
        val api = runCatching { PacketEvents.getAPI().playerManager }.getOrNull() ?: return
        foreign[player.uniqueId]?.values?.toList().orEmpty().forEach { bar ->
            runCatching {
                api.sendPacketSilently(player, WrapperPlayServerBossBar(bar.id, WrapperPlayServerBossBar.Action.REMOVE))
            }
        }
    }

    /** Puts them back, as they are by now. */
    fun showOthers(player: Player) {
        if (listener == null) return
        if (!hiding.remove(player.uniqueId)) return
        val api = runCatching { PacketEvents.getAPI().playerManager }.getOrNull() ?: return
        foreign[player.uniqueId]?.values?.toList().orEmpty().forEach { bar ->
            runCatching { api.sendPacketSilently(player, add(bar)) }
        }
    }

    fun isHiding(player: Player): Boolean = player.uniqueId in hiding

    /** How many bars other plugins are showing this player — for the log. */
    fun othersFor(player: Player): Int = foreign[player.uniqueId]?.size ?: 0

    fun forget(player: UUID) {
        hiding.remove(player)
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

    /** Applies a change to a bar we are keeping; true if the bar was someone else's. */
    private fun update(player: UUID, packet: WrapperPlayServerBossBar): Boolean {
        val bars = foreign[player] ?: return false
        val known = bars[packet.uuid] ?: return false
        bars[packet.uuid] = when (packet.action) {
            WrapperPlayServerBossBar.Action.UPDATE_HEALTH -> known.copy(health = packet.health)
            WrapperPlayServerBossBar.Action.UPDATE_TITLE -> known.copy(title = packet.title)
            WrapperPlayServerBossBar.Action.UPDATE_STYLE -> known.copy(colour = packet.color, overlay = packet.overlay)
            WrapperPlayServerBossBar.Action.UPDATE_FLAGS ->
                known.copy(flags = EnumSet.noneOf(BossBar.Flag::class.java).apply { addAll(packet.flags) })
            else -> known
        }
        return true
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
