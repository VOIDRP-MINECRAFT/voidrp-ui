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

    /** Where the aim says the pointer should be: updated when the player's look arrives. */
    private var targetX = (Shaders.CANVAS_WIDTH / 2).toDouble()
    private var targetY = (Shaders.CANVAS_HEIGHT / 2).toDouble()

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
        readAim()

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
    private fun readAim() {
        // Frames run off the server thread, so this is a plain read of the player's own
        // numbers and never anything more. If the server ever objects, the pointer keeps
        // the position it had rather than the frame loop dying with it.
        val location = runCatching { player.location }.getOrNull() ?: return
        val turnedX = wrapDegrees(location.yaw - anchorYaw)
        val turnedY = location.pitch - anchorPitch
        val speed = sensitivity()

        targetX = (Shaders.CANVAS_WIDTH / 2 + turnedX * speed)
            .coerceIn(0.0, (Shaders.CANVAS_WIDTH - 1).toDouble())
        targetY = (Shaders.CANVAS_HEIGHT / 2 + turnedY * speed)
            .coerceIn(0.0, (Shaders.CANVAS_HEIGHT - 1).toDouble())
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
        readAim()
        // Smoothing is there to hide that the aim arrives in steps, and every bit of it is
        // lag. So it is spent where it is needed and nowhere else: a small movement is
        // eased, a large one — a flick across the page — is followed outright.
        val gap = Math.hypot(targetX - drawnX, targetY - drawnY)
        val factor = (EASING + gap / SNAP_WITHIN).coerceAtMost(1.0)
        drawnX += (targetX - drawnX) * factor
        drawnY += (targetY - drawnY) * factor
        if (Math.abs(targetX - drawnX) < 0.5) drawnX = targetX
        if (Math.abs(targetY - drawnY) < 0.5) drawnY = targetY
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
        const val EASING = 0.5

        /**
         * A gap this wide is closed in one frame.
         *
         * Below it the pointer eases, which is what keeps a slow, careful movement from
         * looking like it steps twenty times a second; above it there is nothing to hide —
         * the hand is moving fast and what it wants is to be followed.
         */
        const val SNAP_WITHIN = 90.0

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
