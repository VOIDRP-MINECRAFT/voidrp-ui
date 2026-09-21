package ru.voidrp.ui.page

import org.bukkit.entity.Player
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.render.BossBarRenderer
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.style.Paint

/**
 * One open page, and the cursor the player drives it with.
 *
 * A vanilla client has no mouse to lend us, so the cursor is the player's own aim: how far
 * they have turned since the page opened is how far the pointer has moved. Every tick the
 * look is read, turned into canvas units and put back where it was, which keeps the world
 * still while the pointer moves — the player is, in effect, moving a mouse.
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
    /** Canvas units per degree of turn. About a screen's width per quarter turn. */
    private val sensitivity: Double = 20.0,
    /** Whether the player's view is held still while the page is open. */
    private val lockView: Boolean = true,
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
        anchorPitch = player.location.pitch
        render()
    }

    /**
     * Reads how far the player has turned and moves the cursor by that much.
     *
     * The anchor moves with the cursor when it runs into an edge, so turning further does
     * nothing until the player turns back — the pointer stops at the edge of the screen
     * instead of drifting out of step with the view.
     */
    fun tick() {
        if (closed) return
        val location = player.location
        val turnedX = wrapDegrees(location.yaw - anchorYaw)
        val turnedY = location.pitch - anchorPitch
        if (turnedX == 0f && turnedY == 0f) return

        val wantX = cursorX + turnedX * sensitivity
        val wantY = cursorY + turnedY * sensitivity
        cursorX = wantX.toInt().coerceIn(0, Shaders.CANVAS_WIDTH - 1)
        cursorY = wantY.toInt().coerceIn(0, Shaders.CANVAS_HEIGHT - 1)

        if (lockView) {
            player.setRotation(anchorYaw, anchorPitch)
        } else {
            anchorYaw = location.yaw
            anchorPitch = location.pitch
        }

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
        nodes = placement.nodes
        hovered = regions.lastOrNull { it.contains(cursorX, cursorY) }?.id
        draw()
    }

    private var nodes: List<Node> = emptyList()

    /** Sends what is already laid out, with the pointer on top. */
    private fun draw() {
        renderer.render(player, nodes + cursor(cursorX, cursorY))
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

        /**
         * A pointer, drawn as a stack of rows that narrow towards the tip, with a dark
         * edge under a light one so it reads against anything behind it.
         */
        fun cursor(x: Int, y: Int): List<Node> {
            val shadow = Paint(0x000000, 0.55)
            val ink = Paint(0xFFFFFF, 0.95)
            val out = mutableListOf<Node>()
            for (row in 0 until 14) {
                val width = (14 - row).coerceAtLeast(2)
                out += Rect(x, y + row, width + 2, 1, shadow)
                out += Rect(x + 1, y + row, width, 1, ink)
            }
            return out
        }
    }
}
