package ru.voidrp.ui.page

import java.util.UUID
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerAnimationType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.plugin.java.JavaPlugin
import ru.voidrp.ui.render.BossBarRenderer

/**
 * Keeps track of who has a page open, and turns what the player does into what the page
 * hears.
 *
 * There is nothing installed on the client, so every input is an ordinary action the game
 * already sends: a swing of the arm is a left click, a use is a right click, the scroll
 * wheel is a change of held slot, and crouching closes the page. Each of those is swallowed
 * while a page is open, so the player does not mine a block or swap tools by using the
 * interface.
 */
class PageManager(
    private val plugin: JavaPlugin,
    private val renderer: BossBarRenderer,
) : Listener {

    private val sessions = mutableMapOf<UUID, PageSession>()

    /**
     * Canvas units per degree of turn. At 30 the canvas is about sixty degrees across —
     * a comfortable sweep of the head. Configurable, because what feels right depends on
     * the player's own mouse sensitivity.
     */
    var sensitivity: Double = plugin.config.getDouble("input.sensitivity", 30.0)

    fun open(player: Player, page: Page) {
        close(player)
        val session = PageSession(player, page, renderer) { sensitivity }
        sessions[player.uniqueId] = session
        session.open()
    }

    fun close(player: Player) {
        sessions.remove(player.uniqueId)?.close()
    }

    fun isOpen(player: Player): Boolean = sessions.containsKey(player.uniqueId)

    fun closeAll() {
        sessions.values.toList().forEach { it.close() }
        sessions.clear()
    }

    /** Called every tick: the cursor follows the player's aim, so it has to keep up. */
    fun tick() {
        sessions.values.toList().forEach { session ->
            if (session.player.isOnline) session.tick() else close(session.player)
        }
    }

    private fun session(player: Player): PageSession? = sessions[player.uniqueId]

    @EventHandler(priority = EventPriority.LOWEST)
    fun onSwing(event: PlayerAnimationEvent) {
        if (event.animationType != PlayerAnimationType.ARM_SWING) return
        val session = session(event.player) ?: return
        event.isCancelled = true
        session.click(Button.LEFT)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onUse(event: PlayerInteractEvent) {
        val session = session(event.player) ?: return
        event.isCancelled = true
        if (event.action.name.startsWith("RIGHT")) session.click(Button.RIGHT)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onScroll(event: PlayerItemHeldEvent) {
        val session = session(event.player) ?: return
        event.isCancelled = true
        // The hotbar wraps around, so the short way between the two slots is the scroll.
        val raw = event.newSlot - event.previousSlot
        val direction = when {
            raw > 4 -> raw - 9
            raw < -4 -> raw + 9
            else -> raw
        }
        if (direction != 0) session.scroll(if (direction > 0) 1 else -1)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) return
        val player = event.player
        if (isOpen(player)) close(player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBreak(event: BlockBreakEvent) {
        if (isOpen(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onHit(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        if (damager is Player && isOpen(damager)) event.isCancelled = true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        close(event.player)
    }
}
