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

    private val sessions = java.util.concurrent.ConcurrentHashMap<UUID, PageSession>()

    /**
     * Frames are drawn off the server thread, because the server thread only runs twenty
     * times a second and a pointer that moves twenty times a second looks like it is
     * stuttering. Nothing here touches the world: the page is already encoded, and a frame
     * only eases the pointer along and sends the result.
     */
    private val frames = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "VoidRpUI-frames").apply { isDaemon = true }
    }

    /**
     * Canvas units per degree of turn. At 10 the canvas is a wide, easy sweep of the head —
     * the value that felt right in play. Configurable, because what suits one player
     * depends on their own mouse sensitivity.
     */
    var sensitivity: Double = plugin.config.getDouble("input.sensitivity", 10.0)

    /**
     * The gap between one boss bar's line and the next, in canvas units. The cursor rides
     * a bar of its own so that it can be sent sixty times a second without the page going
     * with it, and bars stack, so what is drawn on the second one needs lifting by this.
     */
    var cursorBarOffset: Int = plugin.config.getInt("input.cursor-bar-offset", 19)

    /** Starts drawing frames at about the rate a screen refreshes. */
    fun start() {
        frames.scheduleAtFixedRate(
            {
                runCatching { sessions.values.forEach { it.frame() } }
            },
            0,
            16,
            java.util.concurrent.TimeUnit.MILLISECONDS,
        )
    }

    fun shutdown() {
        frames.shutdownNow()
        closeAll()
    }

    fun open(player: Player, page: Page) {
        close(player)
        val session = PageSession(plugin, player, page, renderer, { sensitivity }, { cursorBarOffset })
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

    /** Set by /vui clicks: prints how fast swings arrive, to tune what counts as a press. */
    var traceClicks = false
    private var lastTrace = 0L

    @EventHandler(priority = EventPriority.LOWEST)
    fun onSwing(event: PlayerAnimationEvent) {
        if (event.animationType != PlayerAnimationType.ARM_SWING) return
        val session = session(event.player) ?: return
        event.isCancelled = true
        if (traceClicks) {
            val now = System.currentTimeMillis()
            plugin.logger.info("взмах ${event.player.name}: +${now - lastTrace} мс")
            lastTrace = now
        }
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

    /** Crouching goes back a page, or closes the last one — like the escape key. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) return
        val session = session(event.player) ?: return
        if (!session.back()) close(event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onBreak(event: BlockBreakEvent) {
        if (isOpen(event.player)) event.isCancelled = true
    }

    /** Stops a block from taking damage while its owner is busy pressing buttons. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onDamageBlock(event: org.bukkit.event.block.BlockDamageEvent) {
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
