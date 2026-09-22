package ru.voidrp.ui.page

import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.render.BossBarRenderer
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.Node
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
     * How much lower the cursor's own boss bar draws its line than the page's. Bars stack,
     * so the second one starts further down the screen, and what is drawn on it has to be
     * lifted by that much to land where the page thinks it should.
     */
    private val cursorBarOffset: () -> Int,
) {

    /** The last reading of the player's aim, in canvas units. */
    private var targetX = (Shaders.CANVAS_WIDTH / 2).toDouble()
    private var targetY = (Shaders.CANVAS_HEIGHT / 2).toDouble()

    /**
     * Where the pointer is reckoned to be between readings, and how fast it is going.
     *
     * The client reports its aim twenty times a second and the screen draws sixty, so four
     * frames in five have no reading of their own. Holding the last one makes the pointer
     * step; easing towards it makes the pointer lag. This is the third answer: carry on at
     * the speed the readings have been showing, and when the next one lands, correct both
     * the position and the speed by a fraction of how wrong they turned out to be. The
     * same filter a radar uses to draw a smooth track from a dish that sweeps.
     */
    private var estimateX = targetX
    private var estimateY = targetY
    private var speedX = 0.0
    private var speedY = 0.0
    private var sampleAt = System.nanoTime()
    private var frameAt = System.nanoTime()

    /** Where the pointer is drawn: eased towards the target between ticks. */
    private var drawnX = targetX
    private var drawnY = targetY

    val cursorX: Int get() = drawnX.toInt()
    val cursorY: Int get() = drawnY.toInt()

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
        page.session = this
        sounds.open(player)
        anchorYaw = player.location.yaw
        // The look is levelled once, on opening. Pitch stops at straight down, so a page
        // opened while looking at the ground had no room left to move the cursor lower —
        // from the horizon there is as much room below as above. One packet, sent once, is
        // nothing like the tick-by-tick correction that made the screen shake.
        anchorPitch = 0f
        player.setRotation(anchorYaw, 0f)
        render()
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
        val under = regions.lastOrNull { it.contains(cursorX, cursorY) }?.id
        if (under != hovered) {
            hovered = under
            render()
        }
    }

    /**
     * Where the aim says the pointer should be, read fresh.
     *
     * The client sends its look twenty times a second, and the server tick is another
     * twenty — two clocks that do not line up, so a look read only on the tick can be a
     * whole tick stale before it is ever drawn. Reading it again on each frame costs a few
     * field reads and takes fifty milliseconds of lag off the pointer.
     */
    private fun readAim(): Boolean {
        // Frames run off the server thread, so this is a plain read of the player's own
        // numbers and never anything more. If the server ever objects, the pointer keeps
        // the position it had rather than the frame loop dying with it.
        val location = runCatching { player.location }.getOrNull() ?: return false
        val turnedX = wrapDegrees(location.yaw - anchorYaw)
        val turnedY = location.pitch - anchorPitch
        val speed = sensitivity()

        val x = (Shaders.CANVAS_WIDTH / 2 + turnedX * speed)
            .coerceIn(0.0, (Shaders.CANVAS_WIDTH - 1).toDouble())
        val y = (Shaders.CANVAS_HEIGHT / 2 + turnedY * speed)
            .coerceIn(0.0, (Shaders.CANVAS_HEIGHT - 1).toDouble())
        if (x == targetX && y == targetY) return false
        targetX = x
        targetY = y
        return true
    }

    /**
     * Draws one frame, more often than the server thinks.
     *
     * A client reports where it is looking twenty times a second, and that is the ceiling
     * on knowing where the pointer should be — but not on drawing it. Between two reports
     * the pointer eases towards the last one it was told about, sent at the rate a screen
     * refreshes, which is the difference between a pointer that steps and one that moves.
     * The page itself is already encoded, so a frame costs one small run and a packet.
     */
    fun frame() {
        if (closed) return
        val before = cursorX to cursorY
        val wasOver = under
        val now = System.nanoTime()
        val step = ((now - frameAt) / 1_000_000_000.0).coerceIn(0.001, 0.1)
        frameAt = now

        // Carry on at the speed we think the hand is going.
        estimateX += speedX * step
        estimateY += speedY * step

        if (readAim()) {
            // A reading landed. However far off it found us is corrected in part now, and
            // the rest of it is taken as news about the speed — a pointer consistently
            // behind means the hand is moving faster than we thought.
            val interval = ((now - sampleAt) / 1_000_000_000.0).coerceIn(0.01, 0.25)
            sampleAt = now
            val offX = targetX - estimateX
            val offY = targetY - estimateY
            estimateX += offX * CATCH_UP
            estimateY += offY * CATCH_UP
            speedX += offX * SPEED_CATCH_UP / interval
            speedY += offY * SPEED_CATCH_UP / interval
        } else if (now - sampleAt > STALE_AFTER) {
            // Nothing new for a while: the hand has stopped, so the speed dies away rather
            // than carrying the pointer past where the player is looking.
            speedX *= SPEED_DECAY
            speedY *= SPEED_DECAY
        }
        // And never far ahead of the last thing actually known. Reckoning is for bridging
        // the gap between readings, not for deciding where the player is looking.
        estimateX = estimateX.coerceIn(targetX - RUN_AHEAD, targetX + RUN_AHEAD)
        estimateY = estimateY.coerceIn(targetY - RUN_AHEAD, targetY + RUN_AHEAD)
        estimateX = estimateX.coerceIn(0.0, (Shaders.CANVAS_WIDTH - 1).toDouble())
        estimateY = estimateY.coerceIn(0.0, (Shaders.CANVAS_HEIGHT - 1).toDouble())

        // A light smoothing over the top, which takes out the jitter in the estimate
        // without adding any of the lag that hiding the steps used to cost.
        drawnX += (estimateX - drawnX) * EASING
        drawnY += (estimateY - drawnY) * EASING
        if (Math.abs(estimateX - drawnX) < 0.5) drawnX = estimateX
        if (Math.abs(estimateY - drawnY) < 0.5) drawnY = estimateY
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
        if (closed) return
        stack.addLast(page)
        page = next
        next.session = this
        render()
    }

    /** Goes back to the page underneath, and says whether there was one. */
    fun back(): Boolean {
        if (closed || stack.isEmpty()) return false
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
        Prompt.show(plugin, player, title, label, initial, hint, maxLength, onSubmit)
    }

    fun scroll(direction: Int) {
        if (closed) return
        page.onScroll(direction)
    }

    fun key(number: Int) {
        if (closed) return
        page.onKey(number)
    }

    /** The tooltip as the page last described it; re-laid out as the cursor moves. */
    private var tooltip: ru.voidrp.ui.layout.View? = null
    private var tooltipEncoded: Component? = null
    private var tooltipAt = Int.MIN_VALUE

    /** Builds the page again from scratch and sends it. */
    fun render() {
        if (closed) return
        val placement = Layout.centred(page.view(), Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT)
        regions = placement.regions
        // The page is encoded once and kept: the cursor moves every tick, the page does not.
        hovered = regions.lastOrNull { it.contains(cursorX, cursorY) }?.id
        tooltip = page.tooltip()
        tooltipEncoded = null
        under = regions.lastOrNull { it.contains(cursorX, cursorY) }
        renderer.render(player, GlyphEncoder.encode(placement.nodes))
        draw()
    }

    /** Sends what is already encoded, with the pointer on top. */
    private fun draw() {
        val lift = cursorBarOffset()
        val line = Component.text()
        halo(lift)?.let { line.append(it) }
        tooltipAt(cursorX, cursorY - lift)?.let { line.append(it) }
        line.append(GlyphEncoder.encode(cursor(cursorX, cursorY - lift)))
        renderer.cursor(player, line.build())
    }

    /** A thin outline around whatever the pointer is over, drawn with the pointer. */
    private fun halo(lift: Int): Component? {
        val region = under ?: return null
        val paint = Paint(Theme.VIOLET, 0.55)
        val top = region.y - lift
        // Along the panel's own corners. A square drawn around a rounded card is the first
        // thing anyone notices, and the cursor lands on rounded cards all day.
        val nodes = mutableListOf<ru.voidrp.ui.render.Node>()
        ru.voidrp.ui.render.Painter.outline(region.x, top, region.width, region.height, region.radius, 1, paint, nodes)
        return GlyphEncoder.encode(nodes)
    }

    /**
     * Lays the tooltip out beside the cursor, keeping it on screen.
     *
     * Re-done only when the cursor has actually moved a few units, because at sixty frames
     * a second the difference between following the mouse and chasing it is not worth the
     * work.
     */
    private fun tooltipAt(x: Int, y: Int): Component? {
        val view = tooltip ?: return null
        val moved = Math.abs(x + y * 2 - tooltipAt) >= TOOLTIP_STEP
        tooltipEncoded?.takeIf { !moved }?.let { return it }
        tooltipAt = x + y * 2

        val size = Layout.measure(view, Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT)
        val left = (x + TOOLTIP_OFFSET).coerceAtMost(Shaders.CANVAS_WIDTH - size.width - 4)
        val top = (y + TOOLTIP_OFFSET).coerceAtMost(Shaders.CANVAS_HEIGHT - size.height - 4)
        val placement = Layout.place(view, left.coerceAtLeast(4), top.coerceAtLeast(4), size.width, size.height)
        return GlyphEncoder.encode(placement.nodes).also { tooltipEncoded = it }
    }

    fun close() {
        if (closed) return
        closed = true
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
        /**
         * How the pointer follows the estimate, and the estimate follows the readings.
         *
         * Found by simulation rather than by feel: a hand moving steadily, a hand tracing a
         * curve, and a flick that stops dead, each sampled at the rate a client reports and
         * drawn at the rate a screen refreshes. Against the easing this replaces, the
         * pointer is about three times closer to where the player is actually looking and
         * the worst jolt between two frames is half the size.
         */
        const val EASING = 0.45

        /** How much of the gap a fresh reading closes at once. Gentler is smoother. */
        const val CATCH_UP = 0.55

        /**
         * And how much of it is taken as news about the speed.
         *
         * These two are not free of each other. Correct the speed harder than the position
         * can settle and the tracker rings: every reading tells it that it overshot, so it
         * turns around, overshoots the other way, and the pointer flies about. The bound is
         * the critically damped one — the speed term is the square of the position term
         * over two minus it — and this sits on it.
         */
        const val SPEED_CATCH_UP = CATCH_UP * CATCH_UP / (2 - CATCH_UP)

        /**
         * How far the reckoning may get ahead of the last reading.
         *
         * A hand cannot stop dead, but a reading can: the last one before a stop still
         * shows full speed, so without a limit the pointer sails on for a frame or two and
         * comes back. Thirty units is a finger's width on screen.
         */
        const val RUN_AHEAD = 30.0

        /** After this long without a new reading, the hand is taken to have stopped. */
        const val STALE_AFTER = 120_000_000L

        /** How quickly the speed dies away once it has. */
        const val SPEED_DECAY = 0.85

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

        /** How far the cursor moves before a tooltip is laid out again. */
        const val TOOLTIP_STEP = 6

        /** How far from the cursor a tooltip sits, so the pointer does not cover it. */
        const val TOOLTIP_OFFSET = 16

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
