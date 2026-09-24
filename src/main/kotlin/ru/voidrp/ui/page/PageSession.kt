package ru.voidrp.ui.page

import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.render.BossBarRenderer
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.PageParts
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.render.Painter
import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.render.Sprite
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Theme

/**
 * One open page, and the cursor the player drives it with.
 *
 * A vanilla client has no mouse to lend us, so the cursor is the player's own aim: how far
 * they have turned since the page opened is where the pointer sits. The canvas is about
 * forty degrees across, so a small turn of the head reaches any corner of it.
 *
 * Everything else follows from that. Hovering is a rectangle test against the layout, so
 * the server always knows what the player is pointing at and the client is never asked.
 * A click is an ordinary swing or use packet, which is why this works on a client with
 * nothing installed.
 */
class PageSession(
    private val plugin: org.bukkit.plugin.Plugin,
    val player: Player,
    first: Page,
    private val renderer: BossBarRenderer,
    private val sounds: ru.voidrp.ui.Sounds,
    /**
     * Canvas units per degree of turn, read fresh each tick so it can be tuned while a
     * page is open. Higher means the pointer crosses the screen for less head movement.
     */
    private val sensitivity: () -> Double,
    /**
     * How many reading gaps the pointer is given to walk a reading's distance, read fresh
     * each frame so it can be tuned with a page open — which is the only way to tune it,
     * since the whole of it is how the motion feels.
     */
    private val smoothing: () -> Double,
    /** How far past the last reading the pointer aims; read fresh each frame, like [smoothing]. */
    private val prediction: () -> Double,
    /**
     * How much lower each boss bar draws its line than the one above it. Bars stack, so
     * every bar after the first starts further down the screen, and what is drawn on it
     * has to be lifted by that much for each bar above it to land where the page thinks.
     */
    private val cursorBarOffset: () -> Int,
    /**
     * Whether a page is drawn again when the pointer moves onto something else.
     *
     * Off by default: the highlight under the pointer rides the pointer's own bar, and the
     * page stays as it was. Worth turning on only for a page whose contents really change
     * with what is hovered.
     */
    private val redrawOnHover: () -> Boolean,
    /** The look as the wire gave it, when there is a wire to take it from. */
    private val aim: ru.voidrp.ui.input.PacketAim,
    /**
     * The shape of this player's screen, read fresh so that changing it takes effect at
     * once — the page is laid out against this width the way a web page is laid out
     * against the width of the browser window.
     */
    private val screen: () -> ru.voidrp.ui.layout.Viewport = { ru.voidrp.ui.layout.Viewport.DEFAULT },
    /** The server's own wording, for the few strings the engine itself puts on screen. */
    private val say: (String) -> String = { it },
    /**
     * Told when this session is over.
     *
     * A page closing itself — a button that opens the world again — used to leave the
     * session sitting in the manager's list, closed but still answering to the player. Every
     * swing and every click on a block went on being cancelled, so the player could not mine
     * anything until they crouched, which is the one path that took the session off the
     * list.
     */
    private val forget: (PageSession) -> Unit = {},
) {

    /** The canvas this player's page is drawn on. */
    val viewport: ru.voidrp.ui.layout.Viewport get() = screen()

    /**
     * The pointer's own reckoning: where the aim was last read, where it is between
     * readings, and where it is drawn. All of the arithmetic lives in [Pointer], with the
     * measurements that chose its numbers.
     */
    private val pointer = ru.voidrp.ui.input.Pointer(
        (screen().width / 2).toDouble(),
        (Shaders.CANVAS_HEIGHT / 2).toDouble(),
        smoothing = smoothing(),
        prediction = prediction(),
    )

    /** The round trip to this player, refreshed now and then rather than every frame. */
    private var roundTrip = 0
    private var pingAt = 0L

    /** The round trip to this player, asked for now and then rather than every frame. */
    private fun ping(): Int {
        val now = System.nanoTime()
        if (now - pingAt > PING_EVERY) {
            pingAt = now
            roundTrip = runCatching { player.ping }.getOrDefault(roundTrip)
        }
        return roundTrip
    }

    val cursorX: Int get() = pointer.x.toInt()
    val cursorY: Int get() = pointer.y.toInt()

    /**
     * A recording of the pointer, frame by frame, for as long as it is asked for.
     *
     * "The mouse feels bad" cannot be acted on; four columns can. Each line is the moment,
     * where the last reading put the aim, where the tracker reckons it is, and where it was
     * actually drawn — so a graph shows at once whether the pointer lags the hand, steps
     * between readings, or sails past and comes back.
     */
    private var trace: java.io.Writer? = null
    private var traceUntil = 0L

    fun trace(seconds: Int, into: java.io.File): Boolean = runCatching {
        trace?.close()
        into.parentFile?.mkdirs()
        trace = into.bufferedWriter().apply { write("ms,target_x,target_y,estimate_x,drawn_x,drawn_y\n") }
        traceUntil = System.nanoTime() + seconds * 1_000_000_000L
        true
    }.getOrDefault(false)

    private fun record(now: Long) {
        val writer = trace ?: return
        if (now > traceUntil) {
            runCatching { writer.close() }
            trace = null
            return
        }
        runCatching {
            writer.write(
                "%d,%.1f,%.1f,%.1f,%.1f,%.1f\n".format(
                    (now / 1_000_000) % 1_000_000,
                    pointer.targetX,
                    pointer.targetY,
                    pointer.estimateX,
                    pointer.x,
                    pointer.y,
                ),
            )
        }
    }

    /**
     * What the pointer's own chain costs right now, for anyone asking why it lags.
     *
     * Feelings about a pointer are hard to act on; these are the numbers behind them.
     */
    fun timing(): String {
        val now = System.nanoTime()
        val believed = Math.round(pointer.trust(now) * 100)
        return "at ${cursorX},${cursorY} on ${viewport.width}×${Shaders.CANVAS_HEIGHT} over ${under?.id ?: "nothing"} · " +
            "ping ${ping()}ms · lead ${Math.round(pointer.lead(ping()) * 1000)}ms · " +
            "readings every ${Math.round(pointer.gap * 1000)}ms, " +
            "last ${pointer.age(now)}ms ago ($believed% believed) · " +
            "walking at ${Math.round(Math.hypot(pointer.speedX, pointer.speedY))} units/s · " +
            "page sent $sent times in $sentParts bar updates, hover sent $hoverSends times, $unchanged asks came out the same, $merged folded into a tick"
    }

    var hovered: String? = null
        private set

    /** The page on screen, and the ones it was opened from. */
    var page: Page = first
        private set

    private val stack = ArrayDeque<Page>()

    private var anchorYaw = 0f
    private var anchorPitch = 0f
    private var regions: List<Layout.Region> = emptyList()
    private var closed = false

    fun open() {
        // A bar left behind by an earlier life of the plugin would push this page down a
        // line, and the pointer with it.
        runCatching { renderer.clearOrphans(player) }
        page.session = this
        page.onOpen()
        sounds.open(player)
        anchorYaw = player.location.yaw
        // The look is levelled once, on opening. Pitch stops at straight down, so a page
        // opened while looking at the ground had no room left to move the cursor lower —
        // from the horizon there is as much room below as above. One packet, sent once, is
        // nothing like the tick-by-tick correction that made the screen shake.
        anchorPitch = 0f
        player.setRotation(anchorYaw, 0f)
        // Levelled, the aim is the middle of the canvas — and this canvas may not be the
        // one the last page was drawn on, if the player has just said what shape their
        // screen is.
        pointer.place((viewport.width / 2).toDouble(), (Shaders.CANVAS_HEIGHT / 2).toDouble())
        render()
    }

    /**
     * Something other than the player has turned them — a teleport that sets a direction, a
     * respawn — so the aim is taken from where they now face, and the pointer stays where it
     * was on the page.
     *
     * The pointer is the turn since the page opened, so a teleport that swings the player
     * round swings the pointer with it: on a live client, coming back from death left it
     * against the left edge of the screen. The pitch is levelled again, as on opening, so
     * there is as much room below as above.
     */
    fun reanchor() {
        if (closed) return
        val location = runCatching { player.location }.getOrNull() ?: return
        val speed = sensitivity()
        if (speed <= 0.0) return
        player.setRotation(location.yaw, 0f)
        anchorYaw = location.yaw - ((pointer.targetX - viewport.width / 2) / speed).toFloat()
        anchorPitch = 0f - ((pointer.targetY - Shaders.CANVAS_HEIGHT / 2) / speed).toFloat()
    }

    /**
     * Puts the cursor where the player is aiming.
     *
     * The position is read straight off the current look — the turn since the page opened,
     * times a sensitivity — and nothing is ever sent back to the client. An earlier version
     * held the view still by setting the rotation back every tick, and the client, which
     * keeps sending its own, fought it: the whole screen shook. Letting the player turn
     * freely and simply following the aim costs a little head movement and is perfectly
     * smooth.
     */
    fun tick() {
        if (closed) return
        // The aim is read by the frames, sixty times a second, and reading it here as well
        // would eat the very readings the tracker is waiting for. This tick only asks what
        // the pointer is over now, because answering that means drawing the page again and
        // that can only happen on this thread.
        val over = regions.lastOrNull { it.contains(cursorX, cursorY) }
        if (over?.id != hovered) {
            // Whatever the pointer has just left, and whatever it has just reached: if
            // either of them cannot be highlighted on the pointer's own bar, the page draws
            // its own hover style instead and has to be sent again for it.
            val handedToPage = !fitsOnCursorBar(regions.firstOrNull { it.id == hovered }) ||
                !fitsOnCursorBar(over)
            hovered = over?.id
            // Drawing the page again for a hover costs thirteen kilobytes of packet and a
            // couple of milliseconds of this thread, sixty times a second if the pointer is
            // sweeping — which is felt as the pointer stuttering exactly when it crosses
            // things. The highlight rides the pointer's own bar instead, where it costs a
            // few glyphs and arrives at frame rate. A page that really does need to be
            // rebuilt when the pointer moves over it can ask for it.
            if (redrawOnHover() || page.redrawsOnHover || handedToPage) {
                render()
            } else {
                // The tooltip belongs to what is hovered, not to the page, and it rides the
                // pointer's bar — so it is asked for here, without drawing the page again.
                // It used to be asked for only when the page was drawn, which for a page
                // that does not redraw on hover meant never: on a live client the shop's
                // tooltips appeared only after a scroll happened to redraw the page.
                takeTooltip()
                draw()
            }
        }
    }

    /**
     * Whether a highlight for this region fits on the pointer's bar.
     *
     * That bar draws its line lower than the page's, so everything on it is lifted by the
     * gap — and for something at the very top of the screen that lands above the canvas,
     * where a y cannot go. Those few are left to the page, which has no such offset.
     */
    private fun fitsOnCursorBar(region: Layout.Region?): Boolean =
        region == null || region.y - hoverLift() >= -ru.voidrp.ui.pack.Shaders.SHIFT

    /** How far the hover bar is below the first one: it comes after all of the page's. */
    private fun hoverLift(): Int = cursorBarOffset() * BossBarRenderer.HOVER_BAR

    /** And the pointer's, which comes last. */
    private fun cursorLift(): Int = cursorBarOffset() * BossBarRenderer.CURSOR_BAR

    /**
     * Where the aim says the pointer should be, read fresh.
     *
     * The client sends its look twenty times a second, and the server tick is another
     * twenty — two clocks that do not line up, so a look read only on the tick can be a
     * whole tick stale before it is ever drawn. Reading it again on each frame costs a few
     * field reads and takes fifty milliseconds of lag off the pointer.
     */
    private fun readAim(now: Long = System.nanoTime()): Boolean {
        // Frames run off the server thread, so this is a plain read of the player's own
        // numbers and never anything more. If the server ever objects, the pointer keeps
        // the position it had rather than the frame loop dying with it.
        // Straight off the wire if the server can give it to us, and otherwise whatever
        // the last tick left on the player.
        val wire = aim.look(player.uniqueId)
        val yaw: Float
        val pitch: Float
        if (wire != null) {
            yaw = wire[0]
            pitch = wire[1]
        } else {
            val location = runCatching { player.location }.getOrNull() ?: return false
            yaw = location.yaw
            pitch = location.pitch
        }
        // The first look after a dialog closes is not the player's: grabbing the mouse back,
        // the game turns the head by however far the pointer was from the middle of the
        // window — to the Done button at the foot of the dialog, typically. On a live client
        // that threw the pointer to the bottom edge, out of sight. The turn is taken into the
        // anchor instead, so the pointer stays where the player left it.
        dialogLook?.let { (atYaw, atPitch) ->
            if (Math.abs(wrapDegrees(yaw - atYaw)) < 0.01f && Math.abs(pitch - atPitch) < 0.01f) return false
            dialogLook = null
            anchorYaw += wrapDegrees(yaw - atYaw)
            anchorPitch += pitch - atPitch
            // And the head levelled again, as on opening, so there is room to move both ways.
            runCatching { org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { reanchor() }) }
        }
        val turnedX = wrapDegrees(yaw - anchorYaw)
        val turnedY = pitch - anchorPitch
        val speed = sensitivity()

        val x = (viewport.width / 2 + turnedX * speed)
            .coerceIn(0.0, (viewport.width - 1).toDouble())
        val y = (Shaders.CANVAS_HEIGHT / 2 + turnedY * speed)
            .coerceIn(0.0, (Shaders.CANVAS_HEIGHT - 1).toDouble())
        if (x == pointer.targetX && y == pointer.targetY) return false
        pointer.sample(x, y, now)
        return true
    }

    /**
     * Draws one frame, more often than the server thinks.
     *
     * A client reports where it is looking twenty times a second at best, and that is the
     * ceiling on knowing where the pointer should be — but not on drawing it. Between two
     * readings it is reckoned forward by [ru.voidrp.ui.input.Pointer], which is the
     * difference between a pointer that steps and one that moves. The page itself is
     * already encoded, so a frame costs one small run and a packet.
     */
    fun frame() {
        if (closed) return
        val before = cursorX to cursorY
        val wasOver = under
        val now = System.nanoTime()
        readAim(now)
        pointer.smoothing = smoothing()
        pointer.prediction = prediction()
        pointer.frame(now, ping(), viewport.width, Shaders.CANVAS_HEIGHT)
        record(now)
        under = regions.lastOrNull { it.contains(cursorX, cursorY) }
        if (under?.id != null && under?.id != wasOver?.id) sounds.hover(player)
        if (before == cursorX to cursorY && wasOver?.id == under?.id) return
        draw()
    }

    /**
     * What the cursor is over right now, found at frame rate.
     *
     * The page itself can only be redrawn on the server thread, twenty times a second, so
     * the outline under the pointer is drawn on the cursor's own bar instead: the feedback
     * is immediate even though the panel's own style follows a tick later.
     */
    private var under: Layout.Region? = null

    /**
     * A click, once per press.
     *
     * The game gives us a swing of the arm, not a button going down: holding the button on
     * a block swings it again every few ticks, which arrived as a button being pressed over
     * and over. A press is therefore taken as the first swing after a pause — held down,
     * the swings keep arriving too close together to count as anything new.
     */
    /** Opens another page on top of this one; crouching, or back(), returns here. */
    fun push(next: Page) {
        if (closed || elsewhere { push(next) }) return
        stack.addLast(page)
        page = next
        next.session = this
        next.onOpen()
        render()
    }

    /** Goes back to the page underneath, and says whether there was one. */
    fun back(): Boolean {
        if (closed || stack.isEmpty()) return false
        if (elsewhere { back() }) return true
        val previous = stack.removeLast()
        page.onClose()
        page.session = null
        page = previous
        previous.session = this
        render()
        return true
    }

    /** Where a named panel is right now, for a page that needs to know. */
    fun region(id: String): Layout.Region? = regions.lastOrNull { it.id == id }

    fun click(button: Button) {
        if (closed) return
        val now = System.currentTimeMillis()
        val pressed = now - lastSwing > HOLD_GAP_MS
        val held = !pressed && now - lastSwing < DRAG_GAP_MS
        lastSwing = now
        if (held) {
            // A held button over something is a drag: the swings that mean "still down"
            // arrive every tick, which is as good a stream of drag events as we can get.
            dragging?.let { page.onDrag(it, cursorX, cursorY) }
            return
        }
        if (!pressed) return
        dragging = hovered
        hovered?.let {
            sounds.click(player)
            page.onClick(it, button)
        }
    }

    private var dragging: String? = null

    private var lastSwing = 0L

    fun prompt(
        title: String,
        label: String,
        initial: String,
        hint: String?,
        maxLength: Int,
        onSubmit: (String) -> Unit,
    ) {
        if (closed) return
        dialogLook = currentLook()
        Prompt.show(
            plugin,
            player,
            title,
            label,
            initial,
            hint,
            maxLength,
            say("prompt.submit"),
            say("prompt.cancel"),
            onSubmit,
        )
    }

    /**
     * Where the player was looking when a dialog took the mouse, until the first look after
     * it closes. See [readAim].
     */
    @Volatile
    private var dialogLook: Pair<Float, Float>? = null

    private fun currentLook(): Pair<Float, Float>? {
        aim.look(player.uniqueId)?.let { return it[0] to it[1] }
        return runCatching { player.location }.getOrNull()?.let { it.yaw to it.pitch }
    }

    fun scroll(direction: Int) {
        if (closed) return
        page.onScroll(direction)
    }

    fun key(number: Int) {
        if (closed) return
        page.onKey(number)
    }

    /**
     * The page as it last described itself, and the canvas it was drawn on.
     *
     * A page is sent as one boss bar title, which is the whole picture every time any of it
     * changes — ninety kilobytes for a rich one. So the description is compared before any
     * of that is spent: a page that answers `view()` with the same tree draws the same
     * picture, and the tree is immutable data, so comparing it is a walk over a few hundred
     * small objects against a layout, an encode and a packet.
     */
    private var drawn: ru.voidrp.ui.layout.View? = null
    private var drawnOn: ru.voidrp.ui.layout.Viewport? = null

    /** The tooltip as the page last described it; re-laid out as the cursor moves. */
    private var tooltip: ru.voidrp.ui.layout.View? = null

    /**
     * The page says it has changed; send it again, at most once a tick.
     *
     * A wheel spun hard puts several notches into one tick, and a page that answers each of
     * them separately lays itself out, encodes and sends itself several times for one
     * scroll — tens of kilobytes each. The first change in a tick is drawn at once, so
     * nothing feels delayed, and the rest of that tick collapses into one more draw on the
     * next: the only picture that mattered was the last one anyway.
     */
    fun refresh() {
        if (closed || elsewhere { refresh() }) return
        val tick = currentTick()
        if (tick != renderedAtTick) {
            renderedAtTick = tick
            render()
            return
        }
        merged++
        if (renderQueued) return
        renderQueued = true
        val queued = runCatching {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                renderQueued = false
                renderedAtTick = currentTick()
                if (!closed) render()
            })
            true
        }.getOrDefault(false)
        // No scheduler to hand — a test, a shutdown — so draw it here rather than lose it.
        if (!queued) {
            renderQueued = false
            render()
        }
    }

    private var renderedAtTick = Int.MIN_VALUE
    private var renderQueued = false

    /**
     * What the page has cost since it opened: how many times it was really sent, how many
     * times it asked and came out the same, and how many asks were folded into another
     * one in the same tick. The last two are what these two measures save.
     */
    private var sent = 0
    private var sentParts = 0
    private var hoverSends = 0
    private var unchanged = 0
    private var merged = 0

    private fun currentTick(): Int = runCatching { org.bukkit.Bukkit.getCurrentTick() }.getOrDefault(-1)

    /** Builds the page again from scratch and sends it — unless it comes out the same. */
    fun render() {
        if (closed) return
        val canvas = viewport
        val view = page.view()
        if (view == drawn && canvas == drawnOn) {
            unchanged++
            // Same description, same canvas: the picture on the screen is already this one.
            // The tooltip is still asked for, because it follows the cursor rather than the
            // page, and the cursor is redrawn as always.
            takeTooltip()
            draw()
            return
        }
        var described = view
        var placement = Layout.centred(described, canvas.width, canvas.height)
        // The page was described with what the pointer was over before this change. When
        // the change itself moves things under a still pointer — a list scrolling past it —
        // that is no longer what it is over, and a page that styles its hovered row lights
        // up the row that just left while the pointer's own highlight marks the one that
        // arrived: two rows lit at once. So it is asked once more, with the answer the new
        // layout gives. Once is enough; a hover style that moved things again would be a
        // page fighting itself, and a second pass would not settle that either.
        val nowOver = placement.regions.lastOrNull { it.contains(cursorX, cursorY) }?.id
        if (nowOver != hovered) {
            hovered = nowOver
            described = page.view()
            placement = Layout.centred(described, canvas.width, canvas.height)
        }
        drawn = described
        drawnOn = canvas
        // Painted first and wider than the canvas: the page was laid out for the screen
        // the player said they have, and any difference from the real one is a strip of
        // the world down the side. See Page.bleed.
        val bleed = page.bleed.size
        val nodes = if (page.bleed.isEmpty()) {
            placement.nodes
        } else {
            page.bleed.map { paint ->
                ru.voidrp.ui.render.Rect(
                    -ru.voidrp.ui.layout.Viewport.BLEED,
                    0,
                    canvas.width + ru.voidrp.ui.layout.Viewport.BLEED * 2,
                    canvas.height,
                    paint,
                )
            } + placement.nodes
        }
        regions = placement.regions
        // The page is encoded once and kept: the cursor moves every tick, the page does not.
        hovered = regions.lastOrNull { it.contains(cursorX, cursorY) }?.id
        takeTooltip()
        under = regions.lastOrNull { it.contains(cursorX, cursorY) }
        send(nodes, placement.cuts.map { it + bleed }, canvas.width / 2)
        draw()
    }

    /**
     * What each of the page's bars carries now, as shapes — compared before anything is
     * encoded, so a piece that came out the same is not sent at all.
     */
    private val parts = arrayOfNulls<List<Node>>(BossBarRenderer.PAGE_BARS)

    /**
     * Shares the page out over its bars and sends the pieces that changed.
     *
     * Cut at the edges of a scrolling list, a scroll changes one piece: the list goes again
     * and the rest of the page, the heaviest part of it, stays where it is on the screen.
     */
    private fun send(page: List<Node>, cuts: List<Int>, centre: Int) {
        // Panels taken apart first, so that even a page that is one big panel can be halved,
        // and the cuts moved to where their nodes' shapes begin.
        val nodes = ArrayList<Node>()
        val starts = IntArray(page.size + 1)
        page.forEachIndexed { index, node ->
            starts[index] = nodes.size
            nodes += Painter.flatten(listOf(node))
        }
        starts[page.size] = nodes.size
        val runs = PageParts.split(nodes.size, cuts.map { starts[it.coerceIn(0, page.size)] }, BossBarRenderer.PAGE_BARS)
        val step = cursorBarOffset()
        for (index in 0 until BossBarRenderer.PAGE_BARS) {
            val piece = runs.getOrNull(index)?.let { nodes.subList(it.first, it.last + 1) }.orEmpty()
            if (piece == parts[index]) continue
            parts[index] = piece
            renderer.part(player, index, GlyphEncoder.encode(piece, centre, step * index))
            sentParts++
        }
        sent++
    }

    /** Asks the page for its tooltip, and throws away the drawn one if it has changed. */
    private fun takeTooltip() {
        val next = page.tooltip()
        if (next == tooltip) return
        tooltip = next
    }

    /**
     * Hands a call made off the server thread over to it, and says whether it did.
     *
     * A page that loads what it shows — prices from a web service, a player's stats — gets
     * its answer on some other thread and calls [refresh] from there. Drawn right there, the
     * page was laid out and sent at the same moment as the server thread might be doing the
     * same, and whichever finished last won. Now every such call waits for the next tick.
     */
    private fun elsewhere(work: () -> Unit): Boolean {
        val primary = runCatching { org.bukkit.Bukkit.isPrimaryThread() }.getOrDefault(true)
        if (primary) return false
        runCatching { org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { work() }) }
        return true
    }

    /**
     * Held while the pointer and what it is over are being sent.
     *
     * Two threads draw them: the frames, at eighty-five a second, and the server thread,
     * whenever the page changes. Without this the two could pass each other — one decides
     * the highlight needs sending and is overtaken by the other, whose newer highlight then
     * arrives first — and the stale one stayed on screen, since as far as the session knew
     * the right one had already gone.
     */
    private val drawing = Any()

    /** Sends the pointer, and what it is over when that has changed. */
    private fun draw() = synchronized(drawing) {
        drawHover()
        val lift = cursorLift()
        renderer.cursor(player, GlyphEncoder.encode(cursor(cursorX, cursorY - lift), viewport.width / 2))
    }

    /**
     * What the hover bar was last sent for: the region, the tooltip, where the tooltip was
     * pinned, and the canvas. Anything else and the bar is sent again.
     */
    private var hoverSent: List<Any?>? = null

    /** Where the tooltip was pinned. See [TooltipPin]. */
    private val pin = TooltipPin()

    /**
     * The highlight around what the pointer is over, and its tooltip — sent only when one of
     * them changes, on a bar of their own.
     *
     * The tooltip is pinned where the pointer was when it appeared — see [TooltipPin] —
     * so moving over the same thing sends nothing but the pointer.
     */
    private fun drawHover() {
        val region = under?.takeIf { fitsOnCursorBar(it) }
        val view = tooltip
        pin.update(region?.id, region?.height ?: 0, view, cursorX, cursorY)
        val key = listOf(region, view, pin.x, pin.y, viewport)
        if (key == hoverSent) return
        hoverSent = key

        val lift = hoverLift()
        val nodes = mutableListOf<Node>()
        region?.let { halo(it, nodes) }
        view?.let { tooltipNodes(it, nodes) }
        renderer.hover(player, GlyphEncoder.encode(nodes, viewport.width / 2, lift))
        hoverSends++
    }

    /** A thin outline around whatever the pointer is over, with a wash inside it. */
    private fun halo(region: Layout.Region, nodes: MutableList<Node>) {
        // A wash inside the outline, so that what the pointer is on reads at a glance now
        // that the page itself no longer changes underneath it.
        Painter.fill(region.x, region.y, region.width, region.height, region.radius, Paint(Theme.VIOLET, 0.16), nodes)
        // Along the panel's own corners. A square drawn around a rounded card is the first
        // thing anyone notices, and the cursor lands on rounded cards all day.
        Painter.outline(region.x, region.y, region.width, region.height, region.radius, 1, Paint(Theme.VIOLET, 0.55), nodes)
    }

    /** The tooltip, laid out beside where it was pinned and kept on screen. */
    private fun tooltipNodes(view: ru.voidrp.ui.layout.View, nodes: MutableList<Node>) {
        val canvas = viewport
        val size = Layout.measure(view, canvas.width, canvas.height)
        val left = (pin.x + TooltipPlacement.OFFSET).coerceAtMost(canvas.width - size.width - 4).coerceAtLeast(4)
        val top = TooltipPlacement.top(under?.let { it.y to it.height }, pin.y, size.height, canvas.height)
        nodes += Painter.flatten(Layout.place(view, left, top, size.width, size.height).nodes)
    }

    /** Whether this session is over; a closed one answers to nothing. */
    val isClosed: Boolean get() = closed

    fun close() {
        if (closed || elsewhere { close() }) return
        closed = true
        forget(this)
        sounds.close(player)
        renderer.clear(player)
        page.onClose()
        page.session = null
        stack.forEach { it.session = null }
        stack.clear()
    }

    private companion object {

        /**
         * How much of the way to the target the pointer moves each frame. Enough to feel
         * immediate, gentle enough to hide that the aim itself arrives in steps.
         */
        /** How often the round trip is asked for; it does not change by the frame. */
        const val PING_EVERY = 2_000_000_000L

        /**
         * Swings closer together than this are one press being held.
         *
         * Measured on a live client: holding the button swings every tick, exactly fifty
         * milliseconds apart, whether the crosshair is on a block or on the sky, while
         * clicking as fast as a hand can manage leaves at least a hundred and forty. The
         * two never meet, so the line sits between them and a held button is one press
         * while every real click counts. Eighty leaves room for a swing that arrives a
         * little late without letting a held button through.
         */
        const val HOLD_GAP_MS = 80L

        /** Swings this close together are the same press still being held: a drag. */
        const val DRAG_GAP_MS = 200L




        fun wrapDegrees(value: Float): Float {
            var wrapped = value % 360f
            if (wrapped >= 180f) wrapped -= 360f
            if (wrapped < -180f) wrapped += 360f
            return wrapped
        }

        /** The pointer: one glyph, drawn with its own colours. */
        fun cursor(x: Int, y: Int): List<Node> =
            listOf(Sprite(x, y, Glyphs.cursor(), Glyphs.cursorAdvance()))
    }
}
