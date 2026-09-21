package ru.voidrp.ui.render

import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.style.Fill
import ru.voidrp.ui.style.Gradient
import ru.voidrp.ui.style.GradientDirection
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

    /** How tall a stripe of a gradient is by default, in canvas units. */
    private const val BAND = 3

    /** The order neighbouring stripes are sent up or down the palette in. */
    private val DITHER = doubleArrayOf(0.125, 0.625, 0.375, 0.875)


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
            is Sprite -> out += node.copy(x = node.x + dx, y = node.y + dy)
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
        style.background?.let {
            fill(
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

    /** Fills a shape with whatever it is filled with: one colour, or a gradient in stripes. */
    fun fill(x: Int, y: Int, width: Int, height: Int, radius: Int, fill: Fill, out: MutableList<Node>) {
        when (fill) {
            is Paint -> rounded(x, y, width, height, radius, fill, out)
            is Gradient -> gradient(x, y, width, height, radius, fill, out)
        }
    }

    /** A filled rectangle with rounded corners: four quarter discs and three bands. */
    fun rounded(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        paint: Paint,
        out: MutableList<Node>,
        roundStart: Boolean = true,
        roundEnd: Boolean = true,
        along: GradientDirection = GradientDirection.VERTICAL,
    ) {
        if (width <= 0 || height <= 0 || !paint.visible) return
        if (radius <= 0 || (!roundStart && !roundEnd)) {
            out += Rect(x, y, width, height, paint)
            return
        }
        val r = radius
        if (along == GradientDirection.VERTICAL) {
            // Rounded at the top, the bottom, or both; the rest is one plain band. A stripe
            // in the middle of a gradient is square at both ends, and the stripes at the
            // ends carry the corners.
            var top = y
            var bottom = y + height
            if (roundStart) {
                out += CornerPiece(x, y, r, Glyphs.Corner.TOP_LEFT, paint)
                out += CornerPiece(x + width - r, y, r, Glyphs.Corner.TOP_RIGHT, paint)
                out += Rect(x + r, y, width - 2 * r, r, paint)
                top = y + r
            }
            if (roundEnd) {
                out += CornerPiece(x, y + height - r, r, Glyphs.Corner.BOTTOM_LEFT, paint)
                out += CornerPiece(x + width - r, y + height - r, r, Glyphs.Corner.BOTTOM_RIGHT, paint)
                out += Rect(x + r, y + height - r, width - 2 * r, r, paint)
                bottom = y + height - r
            }
            out += Rect(x, top, width, bottom - top, paint)
        } else {
            var left = x
            var right = x + width
            if (roundStart) {
                out += CornerPiece(x, y, r, Glyphs.Corner.TOP_LEFT, paint)
                out += CornerPiece(x, y + height - r, r, Glyphs.Corner.BOTTOM_LEFT, paint)
                out += Rect(x, y + r, r, height - 2 * r, paint)
                left = x + r
            }
            if (roundEnd) {
                out += CornerPiece(x + width - r, y, r, Glyphs.Corner.TOP_RIGHT, paint)
                out += CornerPiece(x + width - r, y + height - r, r, Glyphs.Corner.BOTTOM_RIGHT, paint)
                out += Rect(x + width - r, y + r, r, height - 2 * r, paint)
                right = x + width - r
            }
            out += Rect(left, y, right - left, height, paint)
        }
    }

    /**
     * A gradient, drawn as stripes across the shape.
     *
     * The first and last stripe carry the rounded corners; everything between them is
     * square, which is what makes a rounded panel with a wash down it look right.
     */
    private fun gradient(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        gradient: Gradient,
        out: MutableList<Node>,
    ) {
        val vertical = gradient.direction == GradientDirection.VERTICAL
        val span = if (vertical) height else width
        if (span <= 0 || width <= 0 || height <= 0) return
        val steps = (gradient.steps ?: (span / BAND)).coerceIn(2, span.coerceAtLeast(2))

        for (step in 0 until steps) {
            val start = span * step / steps
            val end = span * (step + 1) / steps
            if (end <= start) continue
            val exact = blend(gradient.from, gradient.to, (step + 0.5) / steps)
            val paint = if (gradient.dither) dither(exact, DITHER[step % DITHER.size]) else exact
            if (vertical) {
                rounded(
                    x, y + start, width, end - start, radius, paint, out,
                    roundStart = step == 0,
                    roundEnd = step == steps - 1,
                    along = GradientDirection.VERTICAL,
                )
            } else {
                rounded(
                    x + start, y, end - start, height, radius, paint, out,
                    roundStart = step == 0,
                    roundEnd = step == steps - 1,
                    along = GradientDirection.HORIZONTAL,
                )
            }
        }
    }

    /**
     * Nudges a stripe to the palette colour above or below the one it wants, by turns.
     *
     * Colour travels in ten bits, so between violet and fuchsia there are only three or
     * four colours to be had: more stripes cannot invent more of them, and a gradient
     * drawn honestly comes out in bands. Neighbouring stripes are therefore sent to
     * neighbouring palette entries in a repeating pattern, and at a few pixels wide the
     * eye mixes them back into the colour that was asked for — the same trick a printer
     * plays with dots. Opacity, which has sixteen steps, is dithered the same way.
     */
    private fun dither(paint: Paint, bias: Double): Paint {
        fun channel(shift: Int, levels: Int): Int {
            val value = ((paint.rgb shr shift) and 0xFF) / 255.0
            val level = Math.floor(value * levels + bias).toInt().coerceIn(0, levels)
            return Math.round(level * 255.0 / levels).toInt()
        }
        val alphaLevels = Glyphs.ALPHA_LEVELS
        val alphaLevel = Math.floor(paint.alpha * alphaLevels + bias).toInt().coerceIn(0, alphaLevels)
        return Paint(
            (channel(16, 7) shl 16) or (channel(8, 15) shl 8) or channel(0, 7),
            alphaLevel.toDouble() / alphaLevels,
        )
    }

    /** One colour part of the way to another, opacity included. */
    private fun blend(from: Paint, to: Paint, position: Double): Paint {
        fun channel(shift: Int): Int {
            val a = (from.rgb shr shift) and 0xFF
            val b = (to.rgb shr shift) and 0xFF
            return (a + (b - a) * position).toInt().coerceIn(0, 255)
        }
        return Paint(
            (channel(16) shl 16) or (channel(8) shl 8) or channel(0),
            from.alpha + (to.alpha - from.alpha) * position,
        )
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
