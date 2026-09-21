package ru.voidrp.ui.page

import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.render.BossBarRenderer
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.render.Sprite

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
    val player: Player,
    val page: Page,
    private val renderer: BossBarRenderer,
    /**
     * Canvas units per degree of turn, read fresh each tick so it can be tuned while a
     * page is open. Higher means the pointer crosses the screen for less head movement.
     */
    private val sensitivity: () -> Double,
) {

    var cursorX = Shaders.CANVAS_WIDTH / 2
        private set
    var cursorY = Shaders.CANVAS_HEIGHT / 2
        private set

    var hovered: String? = null
        private set

    private var anchorYaw = 0f
    private var anchorPitch = 0f
    private var regions: List<Layout.Region> = emptyList()
    private var closed = false

    fun open() {
        page.session = this
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
        val location = player.location
        val turnedX = wrapDegrees(location.yaw - anchorYaw)
        val turnedY = location.pitch - anchorPitch

        val speed = sensitivity()
        val x = (Shaders.CANVAS_WIDTH / 2 + turnedX * speed).toInt()
            .coerceIn(0, Shaders.CANVAS_WIDTH - 1)
        val y = (Shaders.CANVAS_HEIGHT / 2 + turnedY * speed).toInt()
            .coerceIn(0, Shaders.CANVAS_HEIGHT - 1)
        if (x == cursorX && y == cursorY) return
        cursorX = x
        cursorY = y

        val under = regions.lastOrNull { it.contains(cursorX, cursorY) }?.id
        if (under != hovered) {
            hovered = under
            render()
        } else {
            draw()
        }
    }

    fun click(button: Button) {
        if (closed) return
        hovered?.let { page.onClick(it, button) }
    }

    fun scroll(direction: Int) {
        if (closed) return
        page.onScroll(direction)
    }

    /** Builds the page again from scratch and sends it. */
    fun render() {
        if (closed) return
        val placement = Layout.centred(page.view(), Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT)
        regions = placement.regions
        // The page is encoded once and kept: the cursor moves every tick, the page does not.
        encoded = GlyphEncoder.encode(placement.nodes)
        hovered = regions.lastOrNull { it.contains(cursorX, cursorY) }?.id
        draw()
    }

    private var encoded: Component = Component.empty()

    /** Sends what is already encoded, with the pointer on top. */
    private fun draw() {
        renderer.render(
            player,
            Component.text().append(encoded).append(GlyphEncoder.encode(cursor(cursorX, cursorY))).build(),
        )
    }

    fun close() {
        if (closed) return
        closed = true
        renderer.clear(player)
        page.onClose()
        page.session = null
    }

    private companion object {

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
