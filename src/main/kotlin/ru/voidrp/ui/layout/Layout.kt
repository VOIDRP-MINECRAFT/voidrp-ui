package ru.voidrp.ui.layout

import ru.voidrp.ui.pack.Icons
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Box
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.CornerPiece
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.render.Painter
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.render.Sprite
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme

/**
 * Works out where everything goes.
 *
 * Two passes, the way a browser does it: measure what each thing wants to be, then place
 * it in the space it actually got. A panel's own size comes from its children unless it
 * was given one; a child that asks to fill gets whatever is left after the fixed ones and
 * the gaps between them.
 *
 * The result is a flat list of positioned shapes in painting order — a panel before the
 * things inside it — which is exactly what the encoder wants.
 */
object Layout {

    /** How much room the little bar at the side of a scroll takes. */
    private const val SCROLLBAR = 10

    /** Measured size of a view, in canvas units. */
    data class Extent(val width: Int, val height: Int)

    /** Where a named panel ended up, so the cursor can be told what it is over. */
    data class Region(val id: String, val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(px: Int, py: Int): Boolean =
            px >= x && px < x + width && py >= y && py < y + height
    }

    /** A laid-out page: what to draw, and what can be pointed at. */
    data class Placement(val nodes: List<Node>, val regions: List<Region>)

    /** Lays a page out inside a rectangle of the canvas. */
    fun place(view: View, x: Int, y: Int, width: Int, height: Int): Placement {
        val nodes = mutableListOf<Node>()
        val regions = mutableListOf<Region>()
        arrange(view, x, y, width, height, nodes, regions)
        return Placement(nodes, regions)
    }

    /** Lays a page out at its own size, centred on the canvas. */
    fun centred(view: View, canvasWidth: Int, canvasHeight: Int): Placement {
        val size = measure(view, canvasWidth, canvasHeight)
        return place(
            view,
            (canvasWidth - size.width) / 2,
            (canvasHeight - size.height) / 2,
            size.width,
            size.height,
        )
    }

    /**
     * How big a view wants to be. [availableWidth] and [availableHeight] are what the
     * parent can offer, which is what "fill" resolves to.
     */
    fun measure(view: View, availableWidth: Int, availableHeight: Int): Extent = when (view) {
        is Text -> {
            val lines = lines(view, availableWidth)
            val width = lines.maxOfOrNull { TextFonts.width(it, view.weight, view.size) } ?: 0
            Extent(width, lineHeight(view) * lines.size)
        }

        is Gap -> Extent(view.size, view.size)

        is Image -> Icons.nearestSize(view.size).let { Extent(it, it) }

        is Scroll -> Extent(
            resolve(view.width, contentHeight(view, availableWidth).second, availableWidth),
            resolve(view.height, contentHeight(view, availableWidth).first, availableHeight),
        )

        is Raw -> Extent(0, 0)

        is Panel -> {
            val frame = frame(view)
            val innerWidth = (availableWidth - frame.width).coerceAtLeast(0)
            val innerHeight = (availableHeight - frame.height).coerceAtLeast(0)
            val content = measureChildren(view, innerWidth, innerHeight)
            Extent(
                resolve(view.width, content.width + frame.width, availableWidth),
                resolve(view.height, content.height + frame.height, availableHeight),
            )
        }
    }

    /** How tall everything in a scroll is together, and how wide the widest of it is. */
    private fun contentHeight(scroll: Scroll, availableWidth: Int): Pair<Int, Int> {
        var height = 0
        var width = 0
        scroll.children.forEachIndexed { index, child ->
            val size = measure(child, availableWidth, Int.MAX_VALUE / 4)
            height += size.height + if (index > 0) scroll.gap else 0
            width = maxOf(width, size.width)
        }
        return height to width
    }

    /** How far down a scroll can go before it runs out of content. */
    fun maxOffset(scroll: Scroll, width: Int, height: Int): Int =
        (contentHeight(scroll, width).first - height).coerceAtLeast(0)

    /**
     * From one line's top to the next. The typeface's own line box is tight, so a little
     * air is added — the same thing a stylesheet does with `line-height`.
     */
    private fun lineHeight(text: Text): Int =
        text.lineHeight ?: (TextFonts.sheet(text.weight, text.size).cellHeight + text.size / 5)

    /**
     * Breaks text into lines that fit, at spaces where it can and mid-word when a single
     * word is longer than the space allowed. Text that is not allowed to wrap, or that has
     * no width to wrap into, stays as it was written.
     */
    private fun lines(text: Text, availableWidth: Int): List<String> {
        val explicit = text.value.split("\n")
        if (!text.wrap || availableWidth <= 0) return explicit
        val out = mutableListOf<String>()
        explicit.forEach { paragraph ->
            var line = StringBuilder()
            paragraph.split(' ').forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (TextFonts.width(candidate, text.weight, text.size) <= availableWidth) {
                    line = StringBuilder(candidate)
                    return@forEach
                }
                if (line.isNotEmpty()) {
                    out += line.toString()
                    line = StringBuilder()
                }
                // A word that cannot fit on a line of its own is broken where it must be.
                var rest = word
                while (TextFonts.width(rest, text.weight, text.size) > availableWidth && rest.length > 1) {
                    var cut = rest.length
                    while (cut > 1 && TextFonts.width(rest.take(cut), text.weight, text.size) > availableWidth) cut--
                    out += rest.take(cut)
                    rest = rest.drop(cut)
                }
                line = StringBuilder(rest)
            }
            out += line.toString()
        }
        return out
    }

    /** The space a panel's own border and padding take, before any content. */
    private fun frame(panel: Panel): Extent {
        val border = (panel.style.border?.width ?: 0) * 2
        return Extent(
            border + panel.style.padding.horizontal,
            border + panel.style.padding.vertical,
        )
    }

    private fun measureChildren(panel: Panel, innerWidth: Int, innerHeight: Int): Extent {
        if (panel.children.isEmpty()) return Extent(0, 0)
        var along = 0
        var across = 0
        panel.children.forEach { child ->
            val size = measure(child, innerWidth, innerHeight)
            if (panel.direction == Direction.ROW) {
                along += size.width
                across = maxOf(across, size.height)
            } else {
                along += size.height
                across = maxOf(across, size.width)
            }
        }
        along += panel.gap * (panel.children.size - 1)
        return if (panel.direction == Direction.ROW) Extent(along, across) else Extent(across, along)
    }

    private fun resolve(size: Size, content: Int, available: Int): Int = when (size) {
        is Size.Fixed -> size.value
        // Never smaller than what it holds: measuring a greedy child against no space at
        // all is how its own size is found, below.
        is Size.Fill -> maxOf(content, available)
        is Size.Auto -> content
    }

    private fun arrange(
        view: View,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        out: MutableList<Node>,
        regions: MutableList<Region>,
    ) {
        when (view) {
            is Raw -> out += view.node
            is Gap -> Unit

            is Text -> {
                val step = lineHeight(view)
                lines(view, width).forEachIndexed { index, line ->
                    val lineWidth = TextFonts.width(line, view.weight, view.size)
                    val offset = when (view.align) {
                        TextAlign.START -> 0
                        TextAlign.CENTER -> (width - lineWidth) / 2
                        TextAlign.END -> width - lineWidth
                    }
                    out += Label(x + offset, y + index * step, line, view.size, view.colour, view.weight)
                }
            }

            is Image -> {
                val size = Icons.nearestSize(view.size)
                Icons.glyph(view.item)?.let { glyph ->
                    out += Sprite(x, y, glyph, Icons.advance(view.item, size), font = Icons.fontName(size))
                }
            }

            is Scroll -> arrangeScroll(view, x, y, width, height, out, regions)

            is Panel -> {
                view.id?.let { regions += Region(it, x, y, width, height) }
                // The panel itself is painted first, then filled: a box with no children of
                // its own, because everything inside is placed here as a sibling.
                out += Box(x, y, width, height, view.style)

                val border = view.style.border?.width ?: 0
                val innerX = x + border + view.style.padding.left
                val innerY = y + border + view.style.padding.top
                val innerWidth = (width - frame(view).width).coerceAtLeast(0)
                val innerHeight = (height - frame(view).height).coerceAtLeast(0)
                arrangeChildren(view, innerX, innerY, innerWidth, innerHeight, out, regions)
            }
        }
    }

    /**
     * Lays the contents out as if there were room for all of it, then keeps only what
     * shows through the window — rectangles cut to the edge, anything that cannot be cut
     * kept only while it fits whole.
     */
    private fun arrangeScroll(
        scroll: Scroll,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        out: MutableList<Node>,
        regions: MutableList<Region>,
    ) {
        val barWidth = if (scroll.bar) SCROLLBAR else 0
        val innerWidth = (width - barWidth).coerceAtLeast(0)
        val limit = maxOffset(scroll, innerWidth, height)
        val offset = scroll.offset.coerceIn(0, limit)

        val inner = mutableListOf<Node>()
        val innerRegions = mutableListOf<Region>()
        var cursor = y - offset
        scroll.children.forEach { child ->
            val size = measure(child, innerWidth, Int.MAX_VALUE / 4)
            arrange(child, x, cursor, innerWidth, size.height, inner, innerRegions)
            cursor += size.height + scroll.gap
        }

        // Panels become rectangles before anything is cut, because a panel is drawn as
        // shapes and it is the shapes that have to fit the window.
        Painter.flatten(inner).forEach { node -> clip(node, x, y, width, height)?.let { out += it } }
        innerRegions.forEach { region ->
            if (region.y + region.height > y && region.y < y + height) {
                regions += Region(
                    region.id,
                    region.x,
                    region.y.coerceAtLeast(y),
                    region.width,
                    minOf(region.y + region.height, y + height) - region.y.coerceAtLeast(y),
                )
            }
        }

        if (scroll.bar && limit > 0) {
            val trackX = x + width - SCROLLBAR + 2
            val thumbHeight = (height.toDouble() * height / (height + limit)).toInt().coerceAtLeast(16)
            val thumbY = y + ((height - thumbHeight).toDouble() * offset / limit).toInt()
            out += Box(trackX, y, SCROLLBAR - 4, height, Style(background = Paint(Theme.LINE, 0.1), radius = 4))
            out += Box(trackX, thumbY, SCROLLBAR - 4, thumbHeight, Style(background = Paint(Theme.LINE, 0.35), radius = 4))
        }
    }

    /** What is left of a shape once the window has had its way with it. */
    private fun clip(node: Node, x: Int, y: Int, width: Int, height: Int): Node? {
        val bottom = y + height
        return when (node) {
            is Rect -> {
                val top = node.y.coerceAtLeast(y)
                val end = (node.y + node.height).coerceAtMost(bottom)
                if (end <= top) null else node.copy(y = top, height = end - top)
            }
            // A letter, an icon or a rounded corner is drawn whole or not at all.
            is Label -> node.takeIf { it.y >= y && it.y + it.size <= bottom }
            is Sprite -> node.takeIf { it.y >= y && it.y + it.advance <= bottom }
            is CornerPiece -> node.takeIf { it.y >= y && it.y + it.radius <= bottom }
            else -> node
        }
    }

    private fun arrangeChildren(
        panel: Panel,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        out: MutableList<Node>,
        regions: MutableList<Region>,
    ) {
        if (panel.children.isEmpty()) return
        val row = panel.direction == Direction.ROW
        val span = if (row) width else height

        // What every child wants, and which of them are willing to share the leftovers.
        // A greedy child is measured against no space along this axis, so it asks only for
        // what it holds — otherwise it would claim the whole row for itself and then be
        // handed the leftovers on top, pushing everything after it off the panel.
        val wanted = panel.children.map { child ->
            val greedy = child.growsAlong(panel.direction)
            val size = when {
                row && greedy -> measure(child, 0, height)
                greedy -> measure(child, width, 0)
                else -> measure(child, width, height)
            }
            if (row) size.width else size.height
        }
        val greedy = panel.children.mapIndexed { index, child ->
            index.takeIf { child.growsAlong(panel.direction) }
        }.filterNotNull()

        val used = wanted.sum() + panel.gap * (panel.children.size - 1)
        val spare = (span - used).coerceAtLeast(0)
        val sizes = wanted.toMutableList()
        if (greedy.isNotEmpty() && spare > 0) {
            val share = spare / greedy.size
            greedy.forEachIndexed { position, index ->
                sizes[index] += if (position == greedy.lastIndex) spare - share * greedy.lastIndex else share
            }
        }

        val leftover = (span - (sizes.sum() + panel.gap * (panel.children.size - 1))).coerceAtLeast(0)
        var cursor = when (panel.justify) {
            Justify.START, Justify.SPACE_BETWEEN -> 0
            Justify.CENTER -> leftover / 2
            Justify.END -> leftover
        }
        val extraGap = if (panel.justify == Justify.SPACE_BETWEEN && panel.children.size > 1) {
            leftover / (panel.children.size - 1)
        } else {
            0
        }

        panel.children.forEachIndexed { index, child ->
            val alongSize = sizes[index]
            val crossWanted = measure(child, width, height).let { if (row) it.height else it.width }
            val crossSpan = if (row) height else width
            val crossSize = if (panel.align == Align.STRETCH || child.fillsAcross(panel.direction)) {
                crossSpan
            } else {
                crossWanted
            }
            val crossOffset = when (panel.align) {
                Align.START, Align.STRETCH -> 0
                Align.CENTER -> (crossSpan - crossSize) / 2
                Align.END -> crossSpan - crossSize
            }

            if (row) {
                arrange(child, x + cursor, y + crossOffset, alongSize, crossSize, out, regions)
            } else {
                arrange(child, x + crossOffset, y + cursor, crossSize, alongSize, out, regions)
            }
            cursor += alongSize + panel.gap + extraGap
        }
    }

    /** Whether a child wants the space left over along the panel's own direction. */
    private fun View.growsAlong(direction: Direction): Boolean = when (this) {
        is Gap -> grow
        is Panel -> (if (direction == Direction.ROW) width else height) is Size.Fill
        else -> false
    }

    /** Whether a child wants the full width of the panel across its direction. */
    private fun View.fillsAcross(direction: Direction): Boolean = when (this) {
        is Panel -> (if (direction == Direction.ROW) height else width) is Size.Fill
        else -> false
    }
}
