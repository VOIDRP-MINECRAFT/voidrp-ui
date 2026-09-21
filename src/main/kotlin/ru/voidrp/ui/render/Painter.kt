package ru.voidrp.ui.render

import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Shadow
import ru.voidrp.ui.style.Style

/**
 * Turns styled boxes into the shapes the client can actually draw — the job a browser does
 * between `border-radius: 12px` and the pixels on screen.
 *
 * A box becomes, in painting order: its shadow, the border ring, the background inside that
 * ring, and then whatever it contains. Children are positioned from the inside of the
 * padding, so a box can be moved without touching anything in it.
 */
object Painter {

    fun flatten(nodes: List<Node>): List<Node> {
        val out = mutableListOf<Node>()
        nodes.forEach { paint(it, 0, 0, out) }
        return out
    }

    private fun paint(node: Node, dx: Int, dy: Int, out: MutableList<Node>) {
        when (node) {
            is Box -> paintBox(node, dx, dy, out)
            is Rect -> out += node.copy(x = node.x + dx, y = node.y + dy)
            is CornerPiece -> out += node.copy(x = node.x + dx, y = node.y + dy)
            is Label -> out += node.copy(x = node.x + dx, y = node.y + dy)
        }
    }

    private fun paintBox(box: Box, dx: Int, dy: Int, out: MutableList<Node>) {
        val x = box.x + dx
        val y = box.y + dy
        val style = box.style
        val radius = Glyphs.nearestRadius(style.radius, minOf(box.width, box.height) / 2)

        style.shadow?.let { shadow(x, y, box.width, box.height, radius, it, out) }

        val border = style.border?.takeIf { it.width > 0 && it.paint.visible }
        if (border != null) {
            ring(x, y, box.width, box.height, radius, border.width, border.paint, out)
        }

        val inset = border?.width ?: 0
        style.background?.takeIf { it.visible }?.let {
            rounded(
                x + inset,
                y + inset,
                box.width - inset * 2,
                box.height - inset * 2,
                Glyphs.nearestRadius(radius - inset, (minOf(box.width, box.height) / 2 - inset).coerceAtLeast(0)),
                it,
                out,
            )
        }

        val contentX = x + inset + style.padding.left
        val contentY = y + inset + style.padding.top
        box.children.forEach { paint(it, contentX, contentY, out) }
    }

    /** A filled rectangle with rounded corners: four quarter discs and three bands. */
    fun rounded(x: Int, y: Int, width: Int, height: Int, radius: Int, paint: Paint, out: MutableList<Node>) {
        if (width <= 0 || height <= 0 || !paint.visible) return
        if (radius <= 0) {
            out += Rect(x, y, width, height, paint)
            return
        }
        val r = radius
        out += CornerPiece(x, y, r, Glyphs.Corner.TOP_LEFT, paint)
        out += CornerPiece(x + width - r, y, r, Glyphs.Corner.TOP_RIGHT, paint)
        out += CornerPiece(x, y + height - r, r, Glyphs.Corner.BOTTOM_LEFT, paint)
        out += CornerPiece(x + width - r, y + height - r, r, Glyphs.Corner.BOTTOM_RIGHT, paint)
        out += Rect(x + r, y, width - 2 * r, r, paint)
        out += Rect(x + r, y + height - r, width - 2 * r, r, paint)
        out += Rect(x, y + r, width, height - 2 * r, paint)
    }

    /**
     * The border itself: four straight edges between the corners, and the corners in the
     * border's own colour. The background is then drawn inset by the border width with a
     * smaller radius, which leaves exactly the ring visible — the same construction CSS
     * ends up with.
     */
    private fun ring(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        thickness: Int,
        paint: Paint,
        out: MutableList<Node>,
    ) {
        val r = radius
        out += Rect(x + r, y, width - 2 * r, thickness, paint)
        out += Rect(x + r, y + height - thickness, width - 2 * r, thickness, paint)
        out += Rect(x, y + r, thickness, height - 2 * r, paint)
        out += Rect(x + width - thickness, y + r, thickness, height - 2 * r, paint)
        if (r > 0) {
            out += CornerPiece(x, y, r, Glyphs.Corner.TOP_LEFT, paint)
            out += CornerPiece(x + width - r, y, r, Glyphs.Corner.TOP_RIGHT, paint)
            out += CornerPiece(x, y + height - r, r, Glyphs.Corner.BOTTOM_LEFT, paint)
            out += CornerPiece(x + width - r, y + height - r, r, Glyphs.Corner.BOTTOM_RIGHT, paint)
        }
    }

    /**
     * Depth without a blur: two larger, dimmer copies of the box sitting under it and
     * slightly lower. A vertex shader cannot blur, and stacking two soft-edged layers is
     * close enough to a drop shadow at this scale.
     */
    private fun shadow(x: Int, y: Int, width: Int, height: Int, radius: Int, shadow: Shadow, out: MutableList<Node>) {
        if (!shadow.paint.visible) return
        for (step in 2 downTo 1) {
            val grow = shadow.spread * step
            val drop = shadow.offsetY * step / 2
            rounded(
                x - grow,
                y - grow + drop,
                width + grow * 2,
                height + grow * 2,
                Glyphs.nearestRadius(radius + grow, (height + grow * 2) / 2),
                shadow.paint,
                out,
            )
        }
    }
}
