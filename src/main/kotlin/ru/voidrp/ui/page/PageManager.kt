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
    private val messages: ru.voidrp.ui.Messages,
    private val sounds: ru.voidrp.ui.Sounds,
    /** How a player is handed the resource pack; the API exposes it to other plugins. */
    private val packSender: (Player) -> Unit = {},
    /** Whether this player's client has the pack, and can therefore draw anything. */
    private val packReady: (Player) -> Boolean = { true },
    /**
     * The shape of a player's screen, which decides how wide their canvas is.
     *
     * A page is laid out against it, so this is what makes an interface adapt rather than
     * be squashed onto whatever window it lands in.
     */
    private val screens: ru.voidrp.ui.layout.Screens? = null,
    /**
     * Whether a player who has never said what shape their screen is gets asked before
     * their first page.
     *
     * Five seconds, once in their life, and after it every page is laid out for the window
     * they actually have. Without it the server has to guess, and a guess that is too wide
     * takes a slice off both sides of every page they ever open.
     */
    private val asksScreen: () -> Boolean = { true },
    /**
     * Keeps the page on the first boss bar when another plugin is showing one of its own.
     *
     * Needs PacketEvents: a bar someone else owns is invisible to the server otherwise.
     */
    private val bars: ru.voidrp.ui.input.BossBarGuard = ru.voidrp.ui.input.BossBarGuard(),
) : Listener, ru.voidrp.ui.api.VoidRpUi {

    private val sessions = java.util.concurrent.ConcurrentHashMap<UUID, PageSession>()

    /** Records the pointer for a few seconds, so that a feeling can be looked at. */
    fun traceCursor(player: Player, seconds: Int, into: java.io.File): Boolean =
        session(player)?.trace(seconds, into) ?: false

    /** What the pointer's chain costs this player right now, or null if no page is open. */
    fun cursorTiming(player: Player): String? = session(player)?.timing()

    /** Draws this player's page again, if one is open — after their screen changed, say. */
    fun refresh(player: Player) {
        session(player)?.render()
    }

    /**
     * Where the player is looking, straight off the wire when the server has PacketEvents.
     *
     * Without it the look is whatever `player.location` knows, which is only refreshed
     * when the server reads its inbound queue — once a tick, so up to fifty milliseconds
     * after the client said so. The pointer is driven by that look, and fifty milliseconds
     * is about half of all the lag it has.
     */
    private val aim = ru.voidrp.ui.input.PacketAim()

    /**
     * Whether the look is coming from the wire. Logged once at startup.
     *
     * Set when the pages start rather than when this object is made: a plugin is
     * constructed during the load phase, when the plugin it wants is loaded but not yet
     * enabled — so asking then always answered no, and every ordinary server start fell
     * back to reading the aim once a tick. Only a reload after the server was up ever got
     * the fast path, which is why it looked as though it worked.
     */
    var readsPackets: Boolean = false
        private set

    /** Whether foreign bars can be pushed below the page. Logged once at startup. */
    var ordersBars: Boolean = false
        private set

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
    override var sensitivity: Double = plugin.config.getDouble("input.sensitivity", 10.0)

    /**
     * The gap between one boss bar's line and the next, in canvas units. The cursor rides
     * a bar of its own so that it can be sent sixty times a second without the page going
     * with it, and bars stack, so what is drawn on the second one needs lifting by this.
     */
    var cursorBarOffset: Int = plugin.config.getInt("input.cursor-bar-offset", 19)

    /**
     * Whether moving the pointer onto something redraws the page.
     *
     * The page is one long line of text; sending it again costs a dozen kilobytes, and
     * doing that every time the pointer crosses a card is felt as the pointer stuttering.
     * The highlight rides the pointer's own bar instead.
     */
    var redrawOnHover: Boolean = plugin.config.getBoolean("input.redraw-on-hover", false)

    /**
     * How often the pointer is drawn, in frames a second.
     *
     * The client reports its aim twenty times a second and that is the ceiling on *knowing*
     * where the pointer is, but not on drawing it: between two readings the pointer is
     * reckoned forward, and the more often that reckoning is sent the less of a step there
     * is between one position and the next. Eighty-five is about one frame of a 144 Hz
     * screen; past that the packets cost more than the smoothness is worth.
     */
    val frameRate: Int = plugin.config.getInt("input.frame-rate", DEFAULT_FRAME_RATE).coerceIn(20, 144)

    /**
     * How many gaps between readings the pointer is given to cover the distance one shows.
     *
     * Higher is smoother and further behind the hand; see [ru.voidrp.ui.input.Pointer].
     */
    var smoothing: Double = plugin.config
        .getDouble("input.smoothing", ru.voidrp.ui.input.Pointer.SMOOTHING)
        .coerceIn(ru.voidrp.ui.input.Pointer.SMOOTHING_MIN, ru.voidrp.ui.input.Pointer.SMOOTHING_MAX)

    /** Starts drawing frames at about the rate a screen refreshes. */
    fun start() {
        // Now, not in the constructor: by the time a plugin is enabled, the plugins it
        // asked to come first are enabled too.
        readsPackets = runCatching { aim.install() }.getOrDefault(false)
        ordersBars = runCatching { bars.install() }.getOrDefault(false)
        frames.scheduleAtFixedRate(
            {
                runCatching { sessions.values.forEach { it.frame() } }
            },
            0,
            (1000L / frameRate).coerceAtLeast(6L),
            java.util.concurrent.TimeUnit.MILLISECONDS,
        )
    }

    fun shutdown() {
        frames.shutdownNow()
        bars.uninstall()
        // Before the sessions, because a listener left registered outlives this plugin's
        // class loader and throws on every packet that arrives after it.
        aim.uninstall()
        closeAll()
    }

    /**
     * Opens a page, unless the client has nothing to draw it with.
     *
     * Without the pack the glyphs are not in any font the client knows, and the page comes
     * out as a row of broken squares — so the player is told what happened instead.
     */
    /** The canvas this player's pages are drawn on. */
    fun viewportOf(player: Player): ru.voidrp.ui.layout.Viewport =
        screens?.of(player) ?: ru.voidrp.ui.layout.Viewport.DEFAULT

    override fun viewport(player: Player): ru.voidrp.ui.layout.Viewport = viewportOf(player)

    override fun setViewport(player: Player, viewport: ru.voidrp.ui.layout.Viewport?) {
        val store = screens ?: return
        if (viewport == null) store.clear(player) else store.set(player, viewport)
        refresh(player)
    }

    override fun askScreen(player: Player, then: Page?): Boolean {
        val store = screens ?: return false
        val ask = ScreenPage(
            choose = { chosen -> if (chosen == null) store.clear(player) else store.set(player, chosen) },
            done = {
                // Pressing Done without choosing counts as agreeing with the server's
                // guess: the question is asked once whatever the player does with it.
                if (!store.isSet(player)) store.set(player, viewportOf(player))
                then?.let { open(player, it) }
            },
            say = { key -> messages.text(key) },
        ).also { it.isFollowed = then != null }
        return open(player, ask)
    }

    override fun open(player: Player, page: Page): Boolean {
        if (!packReady(player)) {
            player.sendMessage(messages.get("pack.missing"))
            packSender(player)
            return false
        }
        // The one thing the game never tells us, asked once and then never again.
        if (screens?.isSet(player) == false && asksScreen() && page !is ScreenPage) {
            return askScreen(player, then = page)
        }
        close(player)
        val session = PageSession(
                plugin,
                player,
                page,
                renderer,
                sounds,
                { sensitivity },
                { smoothing },
                { cursorBarOffset },
                { redrawOnHover },
                aim,
                { viewportOf(player) },
                { key -> messages.text(key) },
            ) { over ->
                sessions.remove(over.player.uniqueId, over)
                // A tick later, and only if no page took its place: going from one page to
                // the next closes one session and opens another, and putting the bars back
                // in between would flash them across the screen.
                runCatching {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        if (over.player.isOnline && !isOpen(over.player)) bars.showOthers(over.player)
                    })
                }
            }
        // Opened before it is listed: the frame thread walks this list sixty times a second
        // and draws the pointer, and bars stack in the order they first appear. Listed
        // first, the pointer's bar could be created before the page's — and then the page
        // is a line lower than it thinks, which shows as a gap along the top of the screen.
        session.open()
        sessions[player.uniqueId] = session
        // Now that the page's own bars exist, anything another plugin was already showing
        // is sent again so that it lands underneath them rather than pushing the page down.
        if (plugin.config.getBoolean("input.bar-priority", true)) bars.hideOthers(player)
        return true
    }

    override fun close(player: Player) {
        sessions.remove(player.uniqueId)?.close()
        aim.forget(player.uniqueId)
    }

    /** A player who left takes their bars with them. */
    fun forget(player: Player) {
        bars.forget(player.uniqueId)
    }

    override fun isOpen(player: Player): Boolean = session(player) != null

    override fun current(player: Player): Page? = session(player)?.page

    override fun sendPack(player: Player) = packSender(player)

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

    // A closed session answers to nothing, whichever list it is still on.
    private fun session(player: Player): PageSession? = sessions[player.uniqueId]?.takeIf { !it.isClosed }

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
            plugin.logger.info("swing ${event.player.name}: +${now - lastTrace} ms")
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

    /**
     * The wheel and the number keys arrive as the same packet — a change of held slot — so
     * they are told apart by how far the slot moved: one step is the wheel, a jump is a
     * key. Nothing is cancelled, because cancelling moves the slot back on the server and
     * not on the client, and from then on the two disagree about what the next change
     * means.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onScroll(event: PlayerItemHeldEvent) {
        val session = session(event.player) ?: return
        val raw = event.newSlot - event.previousSlot
        val step = when {
            raw > 4 -> raw - 9
            raw < -4 -> raw + 9
            else -> raw
        }
        if (traceClicks) plugin.logger.info("slot ${event.player.name}: ${event.previousSlot} → ${event.newSlot}")
        when {
            step == 1 || step == -1 -> session.scroll(step)
            session.page.usesKeys -> session.key(event.newSlot + 1)
            step != 0 -> session.scroll(if (step > 0) 1 else -1)
        }
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

    companion object {

        /** How often the pointer is drawn when nothing says otherwise. */
        const val DEFAULT_FRAME_RATE = 85
    }
}
