package ru.voidrp.ui.layout

import ru.voidrp.ui.pack.Icons
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Box
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.render.Sprite

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
            val sheet = TextFonts.sheet(view.weight, view.size)
            Extent(TextFonts.width(view.value, view.weight, view.size), sheet.cellHeight)
        }

        is Gap -> Extent(view.size, view.size)

        is Image -> Icons.nearestSize(view.size).let { Extent(it, it) }

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

            is Text -> out += Label(x, y, view.value, view.size, view.colour, view.weight)

            is Image -> {
                val size = Icons.nearestSize(view.size)
                Icons.glyph(view.item)?.let { glyph ->
                    out += Sprite(x, y, glyph, Icons.advance(view.item, size), font = Icons.fontName(size))
                }
            }

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
