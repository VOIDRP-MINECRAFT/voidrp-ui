package ru.voidrp.ui.render

import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.style.Fill
import ru.voidrp.ui.style.Gradient
import ru.voidrp.ui.style.GradientDirection
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Palette
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

    /**
     * How many layers a fade is laid down in.
     *
     * The first takes the opacity step below what a band wants, the second makes up the
     * remainder. A third buys nothing — each layer's own opacity comes in the same
     * sixteenths, so stacking them shrinks the step by the fraction left uncovered and no
     * further.
     */
    private const val FADE_LAYERS = 2

    /** The order neighbouring stripes are sent up or down the palette in. */
    private val DITHER = doubleArrayOf(0.125, 0.625, 0.375, 0.875)


    fun flatten(nodes: List<Node>): List<Node> {
        val out = mutableListOf<Node>()
        nodes.forEach { paint(it, 0, 0, out) }
        return out
    }

    /** The same shape, somewhere else. A box moves with everything inside it. */
    fun moved(node: Node, dx: Int, dy: Int): Node = when (node) {
        is Box -> node.copy(x = node.x + dx, y = node.y + dy)
        is Rect -> node.copy(x = node.x + dx, y = node.y + dy)
        is CornerPiece -> node.copy(x = node.x + dx, y = node.y + dy)
        is GlowPiece -> node.copy(x = node.x + dx, y = node.y + dy)
        is Label -> node.copy(x = node.x + dx, y = node.y + dy)
        is Sprite -> node.copy(x = node.x + dx, y = node.y + dy)
    }

    private fun paint(node: Node, dx: Int, dy: Int, out: MutableList<Node>) {
        when (node) {
            is Box -> paintBox(node, dx, dy, out)
            is Rect -> out += node.copy(x = node.x + dx, y = node.y + dy)
            is CornerPiece -> out += node.copy(x = node.x + dx, y = node.y + dy)
            is Label -> out += node.copy(x = node.x + dx, y = node.y + dy)
            is Sprite -> out += node.copy(x = node.x + dx, y = node.y + dy)
            is GlowPiece -> out += node.copy(x = node.x + dx, y = node.y + dy)
        }
    }

    private fun paintBox(box: Box, dx: Int, dy: Int, out: MutableList<Node>) {
        val x = box.x + dx
        val y = box.y + dy
        val style = box.style
        val radius = Glyphs.nearestRadius(style.radius, minOf(box.width, box.height) / 2)

        style.shadow?.let { halo(x, y, box.width, box.height, radius, it.paint, it.offsetY, out) }
        style.glow?.let { halo(x, y, box.width, box.height, radius, it, 0, out) }

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

        style.overlay?.let {
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

        style.highlight?.takeIf { it.visible }?.let {
            val edge = radius.coerceAtLeast(inset)
            out += Rect(x + edge, y + inset, box.width - edge * 2, 1, it)
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
        // A cap cannot be wider than what it caps. Asked for more, it used to hang over the
        // shape beside it — two stripes of a gradient blended where they overlapped and the
        // end of the button came out a brighter colour than anything in the gradient.
        //
        // How much room there is depends on which ends are rounded: two corners sit one
        // above the other across the band, and side by side only if both ends are capped.
        val across = if (along == GradientDirection.VERTICAL) width else height
        val alongRoom = if (along == GradientDirection.VERTICAL) height else width
        val r = radius.coerceAtMost(
            minOf(across / 2, if (roundStart && roundEnd) alongRoom / 2 else alongRoom),
        )
        if (r <= 0 || (!roundStart && !roundEnd)) {
            out += Rect(x, y, width, height, paint)
            return
        }
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
        // A fade in opacity alone is the kind this transport carries best, and it wants
        // thin stripes: neighbouring opacity steps differ by a sixteenth of one colour,
        // which the eye mixes, where neighbouring palette colours differ by a jump it
        // sees as a stripe.
        gradient.over?.let { backdrop ->
            expressed(x, y, width, height, radius, gradient, backdrop, span, vertical, out)
            return
        }
        val opacityOnly = gradient.from.rgb == gradient.to.rgb
        if (opacityOnly && gradient.dither) {
            fade(x, y, width, height, radius, gradient, span, vertical, out)
            return
        }
        val steps = (gradient.steps ?: (span / BAND)).coerceIn(2, span.coerceAtLeast(2))

        for (step in 0 until steps) {
            val start = span * step / steps
            val end = span * (step + 1) / steps
            if (end <= start) continue
            val exact = blend(gradient.from, gradient.to, gradient.at((step + 0.5) / steps))
            val paint = when {
                !gradient.dither -> exact
                opacityOnly -> dither(exact, DITHER[step % DITHER.size], colour = false)
                else -> dither(exact, DITHER[step % DITHER.size])
            }
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
     * Lays a run of stripes along a box, merging the ones that came out the same.
     *
     * The stripes at the two ends carry the box's rounded caps, so neither may be narrower
     * than the radius: a three-unit stripe asked to cap a twelve-unit rounding drew that
     * cap over its neighbour, and the two blended into a band brighter than any colour in
     * the gradient. Where that happens the end stripe simply takes more of the box.
     */
    private fun stripes(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        span: Int,
        steps: Int,
        vertical: Boolean,
        out: MutableList<Node>,
        paintOf: (Int) -> Paint?,
    ) {
        data class Run(var from: Int, var to: Int, val paint: Paint)

        val runs = mutableListOf<Run>()
        var step = 0
        while (step < steps) {
            var last = step
            val paint = paintOf(step)
            while (last + 1 < steps && paintOf(last + 1) == paint) last++
            if (paint != null) {
                runs += Run(span * step / steps, span * (last + 1) / steps, paint)
            }
            step = last + 1
        }
        if (runs.isEmpty()) return

        // Make room for the caps at both ends.
        val cap = radius.coerceAtMost(span / 2)
        while (runs.size > 1 && runs.first().to < cap) {
            runs[1].from = runs[0].from
            runs.removeAt(0)
        }
        while (runs.size > 1 && span - runs.last().from < cap) {
            runs[runs.size - 2].to = runs.last().to
            runs.removeAt(runs.size - 1)
        }

        runs.forEachIndexed { index, run ->
            val first = index == 0 && run.from == 0
            val last = index == runs.lastIndex && run.to == span
            if (vertical) {
                rounded(
                    x, y + run.from, width, run.to - run.from, radius, run.paint, out,
                    roundStart = first,
                    roundEnd = last,
                    along = GradientDirection.VERTICAL,
                )
            } else {
                rounded(
                    x + run.from, y, run.to - run.from, height, radius, run.paint, out,
                    roundStart = first,
                    roundEnd = last,
                    along = GradientDirection.HORIZONTAL,
                )
            }
        }
    }

    /**
     * A wash over a surface whose colour is known, which is the good way to draw one.
     *
     * Naming a stripe's colour outright spends everything on ten bits, and along the line
     * from violet to the page's dark there are four colours to be had: the fade comes out
     * in slabs. Fading the opacity instead gives sixteen steps of one colour, which is a
     * stripe every ten units of blue — dithering those only trades the slabs for a
     * corduroy, because a jump of ten is not something the eye blends away.
     *
     * Knowing what is underneath changes the arithmetic. A colour at an opacity is a
     * colour mixed with the backdrop, so the reachable set is every palette entry at every
     * opacity — and near a backdrop of its own colour family, that set is dense: a step of
     * one or two units, where naming colours gave twenty. So a flat layer is laid down
     * first, at the colour the middle of the fade wants, and every stripe is then expressed
     * against what that layer actually came out as. Thirty-nine reachable shades along this
     * line instead of four.
     *
     * Stripes that land on the same pair are merged, so a wash is a few dozen rectangles.
     */
    private fun expressed(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        gradient: Gradient,
        backdrop: Int,
        span: Int,
        vertical: Boolean,
        out: MutableList<Node>,
    ) {
        val steps = (gradient.steps ?: (span / BAND)).coerceIn(2, span.coerceAtLeast(2))

        // The floor: the middle of the fade, flat, under the whole thing.
        val middle = blend(gradient.from, gradient.to, gradient.at(0.5))
        val floor = Palette.express(middle.rgb, backdrop)
        rounded(x, y, width, height, radius, floor, out)
        val under = Palette.composite(floor, backdrop)

        // And the stripes, each expressed against the floor rather than against the page.
        var previous: Int? = null
        val paints = Array(steps) { step ->
            val wanted = blend(gradient.from, gradient.to, gradient.at((step + 0.5) / steps)).rgb
            val paint = Palette.express(wanted, under, previous)
            previous = Palette.composite(paint, under)
            paint
        }
        stripes(x, y, width, height, radius, span, steps, vertical, out) { paints[it] }
    }

    /**
     * One colour fading out, drawn twice.
     *
     * Opacity comes in sixteen steps, and a fade that only uses part of that range has
     * only the steps inside it — six or seven for a wash from a half to a tenth, which is
     * few enough to see as bands. Dithering between two steps trades the bands for a
     * corduroy of thin stripes, which at these widths is no better.
     *
     * So the fade is laid down in two passes. The first takes the opacity step just below
     * what the band wants. The second makes up what is left — a fraction of a step, which
     * cannot be drawn exactly either, so it is the one that gets dithered: bands either
     * side of a boundary are sent up and down by turns, and the eye reads the ramp instead
     * of the edge.
     *
     * The pattern therefore only appears where a hard edge would otherwise be: away from a
     * boundary every band agrees and merges back into a single rectangle. On the home page
     * the whole wash costs some two thousand characters of line and a third of a millisecond.
     */
    private fun fade(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        gradient: Gradient,
        span: Int,
        vertical: Boolean,
        out: MutableList<Node>,
    ) {
        val levels = Glyphs.ALPHA_LEVELS
        val steps = (gradient.steps ?: (span / BAND)).coerceIn(2, span.coerceAtLeast(2))

        // What each band wants, as a stack of opacity steps: each layer makes up what the
        // ones under it left short.
        val passes = List(FADE_LAYERS) { IntArray(steps) }
        for (step in 0 until steps) {
            val wanted = blend(gradient.from, gradient.to, gradient.at((step + 0.5) / steps)).alpha.coerceIn(0.0, 1.0)
            var covered = 0.0
            passes.forEachIndexed { index, pass ->
                val left = if (covered >= 1.0) 0.0 else (wanted - covered) / (1.0 - covered)
                val exact = left * levels
                // The last layer is the fine one, and where it falls between two steps the
                // bands either side of the boundary are sent up and down by turns. Away
                // from a boundary every band agrees and merges back into one rectangle, so
                // the pattern only ever appears where a hard edge would otherwise be.
                val level = if (index == FADE_LAYERS - 1) {
                    Math.floor(exact + DITHER[step % DITHER.size]).toLong()
                } else {
                    Math.floor(exact).toLong()
                }
                // Zero is nothing at all; anything else has to be a step the client draws.
                val wanted2 = level.toInt().coerceIn(0, levels)
                pass[step] = if (wanted2 == 0) 0 else wanted2.coerceAtLeast(Glyphs.MIN_ALPHA_LEVEL)
                covered = 1.0 - (1.0 - covered) * (1.0 - pass[step].toDouble() / levels)
            }
        }

        passes.forEach { pass ->
            stripes(x, y, width, height, radius, span, steps, vertical, out) { step ->
                pass[step].takeIf { it > 0 }?.let { Paint(gradient.from.rgb, it.toDouble() / levels) }
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
    private fun dither(paint: Paint, bias: Double, colour: Boolean = true): Paint {
        fun channel(shift: Int, levels: Int): Int {
            val value = ((paint.rgb shr shift) and 0xFF) / 255.0
            val level = Math.floor(value * levels + bias).toInt().coerceIn(0, levels)
            return Math.round(level * 255.0 / levels).toInt()
        }
        val alphaLevels = Glyphs.ALPHA_LEVELS
        val alphaLevel = Math.floor(paint.alpha * alphaLevels + bias).toInt().coerceIn(0, alphaLevels)
        val rgb = if (colour) {
            (channel(16, 7) shl 16) or (channel(8, 15) shl 8) or channel(0, 7)
        } else {
            paint.rgb
        }
        return Paint(rgb, alphaLevel.toDouble() / alphaLevels)
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
    /** The outline of a rounded box: four sides and four arcs. */
    fun outline(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        thickness: Int,
        paint: Paint,
        out: MutableList<Node>,
    ) = ring(x, y, width, height, radius, thickness, paint, out)

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
            // An outline, not a filled quarter: the fill goes over these, and a filled one
            // would blend twice and light the corner up brighter than the sides.
            val ring = thickness == 1
            out += CornerPiece(x, y, r, Glyphs.Corner.TOP_LEFT, paint, ring)
            out += CornerPiece(x + width - r, y, r, Glyphs.Corner.TOP_RIGHT, paint, ring)
            out += CornerPiece(x, y + height - r, r, Glyphs.Corner.BOTTOM_LEFT, paint, ring)
            out += CornerPiece(x + width - r, y + height - r, r, Glyphs.Corner.BOTTOM_RIGHT, paint, ring)
        }
    }

    /**
     * The halo around a panel: a shadow under it, or a glow around it.
     *
     * Four corners with a round falloff and four sides tiled along their length, which is
     * as far as a medium without blurring can go — and at these sizes it is not far from
     * what a blur would have left.
     */
    private fun halo(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        boxRadius: Int,
        paint: Paint,
        offsetY: Int,
        out: MutableList<Node>,
    ) {
        if (!paint.visible || width <= 0 || height <= 0) return
        val spread = Glyphs.GLOW_SPREAD
        val top = y + offsetY
        // The corner tiles follow the panel's own rounding and reach inwards by it, so the
        // sides start where the arcs end.
        val r = Glyphs.nearestGlowRadius(boxRadius).coerceAtMost(minOf(width, height) / 2)

        out += GlowPiece(x - spread, top - spread, Glyphs.GlowPart.CORNER, Glyphs.Corner.TOP_LEFT, 1, paint, r)
        out += GlowPiece(x + width - r, top - spread, Glyphs.GlowPart.CORNER, Glyphs.Corner.TOP_RIGHT, 1, paint, r)
        out += GlowPiece(x - spread, top + height - r, Glyphs.GlowPart.CORNER, Glyphs.Corner.BOTTOM_LEFT, 1, paint, r)
        out += GlowPiece(
            x + width - r,
            top + height - r,
            Glyphs.GlowPart.CORNER,
            Glyphs.Corner.BOTTOM_RIGHT,
            1,
            paint,
            r,
        )

        val along = width - 2 * r
        var covered = 0
        while (covered < along) {
            val step = Glyphs.GLOW_STEPS.lastOrNull { it <= along - covered } ?: break
            val at = x + r + covered
            out += GlowPiece(at, top - spread, Glyphs.GlowPart.HORIZONTAL, Glyphs.Corner.TOP_LEFT, step, paint)
            out += GlowPiece(at, top + height, Glyphs.GlowPart.HORIZONTAL, Glyphs.Corner.BOTTOM_LEFT, step, paint)
            covered += step
        }
        val down = height - 2 * r
        covered = 0
        while (covered < down) {
            val step = Glyphs.GLOW_STEPS.lastOrNull { it <= down - covered } ?: break
            val at = top + r + covered
            out += GlowPiece(x - spread, at, Glyphs.GlowPart.VERTICAL, Glyphs.Corner.TOP_LEFT, step, paint)
            out += GlowPiece(x + width, at, Glyphs.GlowPart.VERTICAL, Glyphs.Corner.TOP_RIGHT, step, paint)
            covered += step
        }
    }
}
