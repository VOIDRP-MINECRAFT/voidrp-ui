package ru.voidrp.ui.layout

import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.pack.Icons
import ru.voidrp.ui.pack.UiIcons
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

    /**
     * Where a named panel ended up, so the cursor can be told what it is over.
     *
     * It carries the panel's rounding as well: whatever draws a highlight around it has to
     * follow the same corners, or the outline is a square around a rounded thing.
     */
    data class Region(
        val id: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val radius: Int = 0,
    ) {
        fun contains(px: Int, py: Int): Boolean =
            px >= x && px < x + width && py >= y && py < y + height
    }

    /**
     * A laid-out page: what to draw, and what can be pointed at.
     *
     * [cuts] are the places in [nodes] where something that changes on its own begins or
     * ends — a scrolling list, whatever stands above the page. A page is sent over several
     * boss bars, and cutting it there is what lets a scroll send the list again and not
     * the whole page with it.
     */
    data class Placement(val nodes: List<Node>, val regions: List<Region>, val cuts: List<Int> = emptyList())

    /** Lays a page out inside a rectangle of the canvas. */
    fun place(view: View, x: Int, y: Int, width: Int, height: Int): Placement = pass {
        val nodes = mutableListOf<Node>()
        val regions = mutableListOf<Region>()
        val outer = cutting.get()
        val cuts = Cuts(nodes)
        cutting.set(cuts)
        try {
            arrange(view, x, y, width, height, nodes, regions)
        } finally {
            cutting.set(outer)
        }
        // What stands above the page goes on last, so a menu covers the card under it
        // rather than the other way round. Its regions come last too: the cursor asks the
        // list in reverse, so the topmost thing under it answers first.
        val above = overlay.get()
        if (above.nodes.isNotEmpty()) cuts.at += nodes.size
        Placement(nodes + above.nodes, regions + above.regions, cuts.at.distinct().sorted())
    }

    /** Where the page being laid out can be cut: see [Placement.cuts]. */
    private class Cuts(val into: List<Node>) {
        val at = mutableListOf<Int>()
    }

    private val cutting = ThreadLocal<Cuts?>()

    /** Lays a page out at its own size, centred on the canvas. */
    fun centred(view: View, canvasWidth: Int, canvasHeight: Int): Placement = pass {
        val size = measure(view, canvasWidth, canvasHeight)
        place(
            view,
            (canvasWidth - size.width) / 2,
            (canvasHeight - size.height) / 2,
            size.width,
            size.height,
        )
    }

    /** The width this panel will actually have, when that is known before measuring. */
    private fun Panel.declaredWidth(available: Int): Int = clampWidth(
        when (width) {
            is Size.Fixed -> width.value
            is Size.Percent -> resolve(width, available, available)
            else -> available
        },
    )

    private fun Panel.declaredHeight(available: Int): Int = clampHeight(
        when (height) {
            is Size.Fixed -> height.value
            is Size.Percent -> resolve(height, available, available)
            else -> available
        },
    )

    /** Inside the panel's own limits: `max-width` and `min-width`, both optional. */
    private fun Panel.clampWidth(value: Int): Int =
        value.coerceAtMost(maxWidth ?: Int.MAX_VALUE).coerceAtLeast(minWidth ?: 0)

    private fun Panel.clampHeight(value: Int): Int =
        value.coerceAtMost(maxHeight ?: Int.MAX_VALUE).coerceAtLeast(minHeight ?: 0)

    /**
     * What has already been measured during this layout.
     *
     * Measuring is recursive and asked for repeatedly — a panel measures its children to
     * find its own size, then again to share out what is left, then once more to line them
     * up across — so without this the work doubles with every level of nesting. A shop
     * page took eleven milliseconds to lay out; the same page with this takes a fraction
     * of that, and thirty players hovering at once stops being a problem.
     *
     * Views are immutable, so a measurement is good for the whole pass and no longer.
     */
    private val measured = ThreadLocal.withInitial { java.util.IdentityHashMap<View, MutableMap<Long, Extent>>() }

    private val depth = ThreadLocal.withInitial { 0 }

    /** What was put above the page during this pass. */
    private class Above {
        val nodes = mutableListOf<Node>()
        val regions = mutableListOf<Region>()
    }

    private val overlay = ThreadLocal.withInitial { Above() }

    /**
     * Starts a page, or joins the one already being laid out.
     *
     * Only the outermost call clears what was measured — laying out centred content calls
     * back into placing it, and an inner pass that wiped the cache would defeat the whole
     * point of having one.
     */
    private fun <T> pass(block: () -> T): T {
        depth.set(depth.get() + 1)
        return try {
            block()
        } finally {
            val level = depth.get() - 1
            depth.set(level)
            if (level == 0) {
                measured.get().clear()
                overlay.set(Above())
            }
        }
    }

    /**
     * How big a view wants to be. [availableWidth] and [availableHeight] are what the
     * parent can offer, which is what "fill" resolves to.
     */
    fun measure(view: View, availableWidth: Int, availableHeight: Int): Extent {
        val key = (availableWidth.toLong() shl 32) or (availableHeight.toLong() and 0xFFFFFFFFL)
        val forView = measured.get().getOrPut(view) { HashMap(4) }
        forView[key]?.let { return it }
        return measureUncached(view, availableWidth, availableHeight).also { forView[key] = it }
    }

    private fun measureUncached(view: View, availableWidth: Int, availableHeight: Int): Extent = when (view) {
        is Text -> {
            val lines = lines(view, availableWidth)
            val width = lines.maxOfOrNull { line ->
                TextFonts.width(line, view.weight, view.size) + view.tracking * (line.length - 1).coerceAtLeast(0)
            } ?: 0
            Extent(width, lineHeight(view) * lines.size)
        }

        is Gap -> Extent(view.size, view.size)

        // Behind everything and part of nothing: like a hand-placed shape, it takes no room.
        is Particles -> Extent(0, 0)

        is Image -> Icons.nearestSize(view.size).let { Extent(it, it) }

        is Icon -> UiIcons.nearestSize(view.size).let { Extent(it, it) }

        is Picture -> {
            val height = ru.voidrp.ui.pack.ServerImages.nearestHeight(view.height)
            Extent(ru.voidrp.ui.pack.ServerImages.width(view.name, height), height)
        }

        is Head -> ru.voidrp.ui.pack.PlayerHeads.nearestSize(view.size).let { Extent(it, it) }

        is RichText -> {
            var width = 0
            var height = 0
            view.spans.forEach { span ->
                val sheet = TextFonts.sheet(span.weight ?: view.weight, span.size ?: view.size)
                width += sheet.width(span.text)
                height = maxOf(height, sheet.cellHeight)
            }
            Extent(width, height)
        }

        is Grid -> {
            val cells = cellSize(view, availableWidth)
            val rows = (view.children.size + view.columns - 1) / view.columns
            Extent(
                resolve(view.width, cells.width * view.columns + view.gap * (view.columns - 1), availableWidth),
                (cells.height * rows + view.rowGap * (rows - 1)).coerceAtLeast(0),
            )
        }

        is Scroll -> {
            val content = contentHeight(view, availableWidth)
            Extent(
                resolve(view.width, content.second, availableWidth),
                resolve(view.height, content.first, availableHeight),
            )
        }

        // Neither takes room: one is drawn over the page, the other placed by hand.
        is Overlay -> Extent(0, 0)
        is Raw -> Extent(0, 0)

        is Panel -> {
            val frame = frame(view)
            // A panel that was given a width of its own measures its children against
            // *that*, not against whatever the parent could spare. It matters as soon as a
            // child's size depends on the room it gets: a wrapping row in a 700-wide panel
            // measured against 1130 reported one line where it draws two, and the row below
            // it was laid on top of the second.
            val innerWidth = (view.declaredWidth(availableWidth) - frame.width).coerceAtLeast(0)
            val innerHeight = (view.declaredHeight(availableHeight) - frame.height).coerceAtLeast(0)
            val content = measureChildren(view, innerWidth, innerHeight)
            Extent(
                view.clampWidth(resolve(view.width, content.width + frame.width, availableWidth)),
                view.clampHeight(resolve(view.height, content.height + frame.height, availableHeight)),
            )
        }
    }

    /**
     * How big one cell of a grid is: as big as the largest child, so the grid lines up.
     */
    private fun cellSize(grid: Grid, availableWidth: Int): Extent {
        val perCell = if (grid.columns > 0) {
            (availableWidth - grid.gap * (grid.columns - 1)) / grid.columns
        } else {
            availableWidth
        }.coerceAtLeast(0)

        var widest = 0
        var tallest = 0
        grid.children.forEach { child ->
            val size = measure(child, perCell, UNBOUNDED)
            widest = maxOf(widest, size.width)
            tallest = maxOf(tallest, size.height)
        }

        // A grid told how wide it is divides that width and keeps to it. Letting the cell
        // grow to its contents instead is how a two-column stat panel ended up wider than
        // the panel around it, with its right-hand column printed over the panel beside it.
        val width = if (grid.width is Size.Auto) widest else minOf(widest, perCell)
        return Extent(width, tallest)
    }

    /** How tall everything in a scroll is together, and how wide the widest of it is. */
    private fun contentHeight(scroll: Scroll, availableWidth: Int): Pair<Int, Int> {
        var height = 0
        var width = 0
        scroll.children.forEachIndexed { index, child ->
            val size = measure(child, availableWidth, UNBOUNDED)
            height += size.height + if (index > 0) scroll.gap else 0
            width = maxOf(width, size.width)
        }
        return height to width
    }

    /** How far down a scroll can go before it runs out of content. */
    fun maxOffset(scroll: Scroll, width: Int, height: Int): Int =
        (contentHeight(scroll, width).first - height).coerceAtLeast(0)

    /** Where each child of a scroll starts, measured from the top of its content. */
    fun rowStarts(scroll: Scroll, width: Int): List<Int> {
        var y = 0
        return scroll.children.mapIndexed { index, child ->
            if (index > 0) y += scroll.gap
            val start = y
            y += measure(child, width, UNBOUNDED).height
            start
        }
    }

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
        // Text told not to wrap still cannot be allowed to run past its panel: it is cut
        // and ended with an ellipsis, which is what "no wrapping" means everywhere else.
        if (!text.wrap) return if (availableWidth <= 0) explicit else explicit.map { cut(text, it, availableWidth) }
        if (availableWidth <= 0) return explicit
        val sheet = TextFonts.sheet(text.weight, text.size)
        val space = sheet.spaceAdvance
        val out = mutableListOf<String>()
        explicit.forEach { paragraph ->
            val para = mutableListOf<String>()
            val line = StringBuilder()
            // Width is carried along rather than measured again for every word: measuring
            // the whole line once per word made laying out a page of text quadratic.
            var width = 0
            paragraph.split(' ').forEach { word ->
                val wordWidth = sheet.width(word)
                val added = if (line.isEmpty()) wordWidth else space + wordWidth
                if (width + added <= availableWidth) {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(word)
                    width += added
                    return@forEach
                }
                if (line.isNotEmpty()) {
                    para += line.toString()
                    line.setLength(0)
                    width = 0
                }
                // A word that cannot fit on a line of its own is broken where it must be.
                var rest = word
                var restWidth = wordWidth
                while (restWidth > availableWidth && rest.length > 1) {
                    var cut = rest.length
                    while (cut > 1 && sheet.width(rest.take(cut)) > availableWidth) cut--
                    para += rest.take(cut)
                    rest = rest.drop(cut)
                    restWidth = sheet.width(rest)
                }
                line.append(rest)
                width = restWidth
            }
            para += line.toString()
            out += withoutWidow(para, sheet, availableWidth)
        }
        return limit(text, out, availableWidth)
    }

    /**
     * Keeps the last word of a paragraph from standing on a line by itself.
     *
     * Filled greedily, "Meet at spawn at 8" came out as a full line and an "8" underneath
     * it, alone at the start of a card. When the last line is one short word, the word before
     * it is brought down to keep it company — provided the two fit, and the line above keeps
     * a word of its own.
     */
    private fun withoutWidow(para: List<String>, sheet: TextFonts.Sheet, availableWidth: Int): List<String> {
        if (para.size < 2) return para
        val last = para.last()
        val above = para[para.size - 2]
        if (' ' in last || last.isEmpty()) return para
        if (sheet.width(last) * 3 > availableWidth) return para
        val split = above.lastIndexOf(' ')
        if (split <= 0) return para
        val moved = above.substring(split + 1)
        val joined = "$moved $last"
        if (sheet.width(joined) > availableWidth) return para
        return para.dropLast(2) + above.substring(0, split) + joined
    }

    /** One line, cut to fit and ended in an ellipsis if it had to be. */
    private fun cut(text: Text, line: String, availableWidth: Int): String {
        val sheet = TextFonts.sheet(text.weight, text.size)
        if (sheet.width(line) <= availableWidth) return line
        var kept = line
        while (kept.isNotEmpty() && sheet.width("$kept…") > availableWidth) kept = kept.dropLast(1).trimEnd()
        return "$kept…"
    }

    /**
     * Keeps a paragraph to the number of lines it is allowed, ending it in an ellipsis —
     * the honest way to show that something was cut rather than letting it run on.
     */
    private fun limit(text: Text, lines: List<String>, availableWidth: Int): List<String> {
        val max = text.maxLines ?: return lines
        if (max <= 0 || lines.size <= max) return lines
        val sheet = TextFonts.sheet(text.weight, text.size)
        val kept = lines.take(max).toMutableList()
        var last = kept.removeAt(kept.size - 1)
        val ellipsis = "…"
        while (last.isNotEmpty() && sheet.width(last + ellipsis) > availableWidth) {
            last = last.dropLast(1).trimEnd()
        }
        kept += last + ellipsis
        return kept
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
        if (panel.wraps(innerWidth)) {
            val lines = wrapLines(panel, innerWidth, innerHeight)
            val widest = lines.maxOfOrNull { line -> lineWidth(panel, line, innerWidth, innerHeight) } ?: 0
            val tall = lines.sumOf { line -> lineHeight(line, innerWidth, innerHeight) }
            return Extent(widest, tall + panel.lineGapOr * (lines.size - 1).coerceAtLeast(0))
        }
        var along = 0
        var across = 0
        // Only what stands in the flow. A dropdown's open list is an Overlay: it takes no
        // room, so it must not earn a gap either — with one the panel came out four units
        // taller than it draws, and in a row of centred cells the open list sat two units
        // higher than the closed one beside it.
        var counted = 0
        panel.children.forEach { child ->
            if (!child.inFlow()) return@forEach
            counted++
            val size = measure(child, innerWidth, innerHeight)
            if (panel.direction == Direction.ROW) {
                along += size.width
                across = maxOf(across, size.height)
            } else {
                along += size.height
                across = maxOf(across, size.width)
            }
        }
        along += panel.gap * (counted - 1).coerceAtLeast(0)
        return if (panel.direction == Direction.ROW) Extent(along, across) else Extent(across, along)
    }

    /**
     * What is passed for "as much room as you like".
     *
     * A grid measuring a cell, or a scroll measuring its contents, has no limit to offer
     * along one axis. Something asking to fill would then be as big as that number, which
     * is how a panel of tiles came out two hundred million units tall and took the page
     * with it. Past this mark, filling means "as big as what is inside".
     */
    const val UNBOUNDED = Int.MAX_VALUE / 8

    private fun resolve(size: Size, content: Int, available: Int): Int = when (size) {
        is Size.Fixed -> size.value
        is Size.Percent -> (available * size.fraction).toInt().coerceIn(0, available)
        // Never smaller than what it holds: measuring a greedy child against no space at
        // all is how its own size is found, below.
        is Size.Fill -> if (available >= UNBOUNDED) content else maxOf(content, available)
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
            // Laid out here, drawn last: the pass collects it and puts it on top of the
            // finished page.
            is Overlay -> {
                val above = overlay.get()
                arrange(view.view, x, y, width, height, above.nodes, above.regions)
            }

            // Placed by hand, but inside its parent like anything else: the coordinates
            // in the node are read from wherever the layout put it.
            is Raw -> out += Painter.moved(view.node, x, y)
            is Gap -> Unit

            is Text -> {
                val step = lineHeight(view)
                lines(view, width).forEachIndexed { index, line ->
                    val lineWidth = TextFonts.width(line, view.weight, view.size) +
                        view.tracking * (line.length - 1).coerceAtLeast(0)
                    val offset = when (view.align) {
                        TextAlign.START -> 0
                        TextAlign.CENTER -> (width - lineWidth) / 2
                        TextAlign.END -> width - lineWidth
                    }
                    view.glow?.takeIf { it.visible }?.let { glow ->
                        // Drawn around the word before the word itself, so the letters
                        // stay crisp and the light sits behind them.
                        listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dx, dy) ->
                            out += Label(
                                x + offset + dx,
                                y + index * step + dy,
                                line,
                                view.size,
                                glow.rgb,
                                view.weight,
                                view.tracking,
                            )
                        }
                    }
                    out += Label(x + offset, y + index * step, line, view.size, view.colour, view.weight, view.tracking)
                }
            }

            is Head -> {
                val size = ru.voidrp.ui.pack.PlayerHeads.nearestSize(view.size)
                ru.voidrp.ui.pack.PlayerHeads.glyph(view.player)?.let { glyph ->
                    out += Sprite(
                        x,
                        y,
                        glyph,
                        ru.voidrp.ui.pack.PlayerHeads.advance(view.player, size),
                        0xFFFFFF,
                        ru.voidrp.ui.pack.PlayerHeads.fontName(size),
                    )
                }
            }

            is Picture -> {
                val height = ru.voidrp.ui.pack.ServerImages.nearestHeight(view.height)
                ru.voidrp.ui.pack.ServerImages.glyph(view.name)?.let { glyph ->
                    out += Sprite(
                        x,
                        y,
                        glyph,
                        ru.voidrp.ui.pack.ServerImages.advance(view.name, height),
                        0xFFFFFF,
                        ru.voidrp.ui.pack.ServerImages.fontName(height),
                    )
                }
            }

            is Icon -> {
                val size = UiIcons.nearestSize(view.size)
                UiIcons.glyph(view.name)?.let { glyph ->
                    out += Sprite(
                        x,
                        y,
                        glyph,
                        UiIcons.advance(view.name, size),
                        colour = view.colour,
                        font = UiIcons.fontName(size),
                    )
                }
            }

            is Image -> {
                val size = Icons.nearestSize(view.size)
                val glyph = Icons.glyph(view.item)
                if (glyph != null) {
                    out += Sprite(
                        x, y, glyph, Icons.advance(view.item, size),
                        colour = Icons.tint(view.item),
                        font = Icons.fontName(size),
                    )
                } else {
                    // The client has no picture of this — a few items are drawn from models
                    // rather than a texture. An empty slot says so; nothing at all looks
                    // like a bug in the page.
                    out += Rect(x, y, size, size, Paint(Theme.LINE, 0.12))
                }
            }

            is Particles -> {
                // A stable scatter: the same seed lays the field out the same way every
                // time, so the page does not shimmer when it is drawn again.
                val random = java.util.Random(view.seed.toLong())
                repeat(view.count) {
                    val size = if (random.nextInt(5) == 0) view.size + 1 else view.size
                    out += Rect(
                        x + random.nextInt(width.coerceAtLeast(1)),
                        y + random.nextInt(height.coerceAtLeast(1)),
                        size,
                        size,
                        Paint(view.colour, if (random.nextBoolean()) view.alpha else view.alpha * 0.6),
                        drift = true,
                    )
                }
            }

            is Scroll -> arrangeScroll(view, x, y, width, height, out, regions)

            is Grid -> {
                val cells = cellSize(view, width)
                val rows = (view.children.size + view.columns - 1) / view.columns
                // A grid told to grow was handed more height than its rows asked for, and
                // they share it out. Otherwise nothing changes: the row is as tall as the
                // tallest thing in it.
                val rowHeight = if (view.grow && rows > 0) {
                    maxOf(cells.height, (height - view.rowGap * (rows - 1)) / rows)
                } else {
                    cells.height
                }
                view.children.forEachIndexed { index, child ->
                    val column = index % view.columns
                    val row = index / view.columns
                    arrange(
                        child,
                        x + column * (cells.width + view.gap),
                        y + row * (rowHeight + view.rowGap),
                        cells.width,
                        rowHeight,
                        out,
                        regions,
                    )
                }
            }

            is RichText -> {
                val total = measure(view, width, height).width
                var pen = x + when (view.align) {
                    TextAlign.START -> 0
                    TextAlign.CENTER -> (width - total) / 2
                    TextAlign.END -> width - total
                }
                // Every piece sits on the same baseline. Left at the same top edge, a
                // smaller span floats above the line it belongs to — "12400 coins" had the
                // word hanging off the top of the number. Their sheets share a typeface,
                // so lining the bottoms of the cells up lines the baselines up.
                val tallest = view.spans.maxOfOrNull { span ->
                    TextFonts.sheet(span.weight ?: view.weight, span.size ?: view.size).cellHeight
                } ?: 0
                view.spans.forEach { span ->
                    val weight = span.weight ?: view.weight
                    val size = span.size ?: view.size
                    val sheet = TextFonts.sheet(weight, size)
                    out += Label(
                        pen,
                        y + tallest - sheet.cellHeight,
                        span.text,
                        size,
                        span.colour ?: view.colour,
                        weight,
                    )
                    pen += sheet.width(span.text)
                }
            }

            is Panel -> {
                // A panel wider than it allows itself keeps to its limit and takes the
                // middle of the room it was given, which is `margin: 0 auto` — the reason
                // a page can fill a narrow window and still hold its content to a readable
                // column on a wide one.
                val boxWidth = view.clampWidth(width)
                val boxHeight = view.clampHeight(height)
                val boxX = x + (width - boxWidth) / 2
                val boxY = y + (height - boxHeight) / 2
                view.id?.let {
                    regions += Region(
                        it,
                        boxX,
                        boxY,
                        boxWidth,
                        boxHeight,
                        Glyphs.nearestRadius(view.style.radius, minOf(boxWidth, boxHeight) / 2),
                    )
                }
                // The panel itself is painted first, then filled: a box with no children of
                // its own, because everything inside is placed here as a sibling.
                out += Box(boxX, boxY, boxWidth, boxHeight, view.style)

                val border = view.style.border?.width ?: 0
                val innerX = boxX + border + view.style.padding.left
                val innerY = boxY + border + view.style.padding.top
                val innerWidth = (boxWidth - frame(view).width).coerceAtLeast(0)
                val innerHeight = (boxHeight - frame(view).height).coerceAtLeast(0)
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
            val size = measure(child, innerWidth, UNBOUNDED)
            // A child the window left a few units of is not shown at all. Kept, it is a
            // line lying at the edge of the list, with the corners it was rounded with as
            // two stray squares beside it and its lit top edge as a hairline below —
            // every piece of a card except the card.
            val top = cursor.coerceAtLeast(y)
            val end = (cursor + size.height).coerceAtMost(y + height)
            val whole = cursor >= y && cursor + size.height <= y + height
            if (whole || end - top >= minOf(CLIPPED_MINIMUM, size.height)) {
                arrange(child, x, cursor, innerWidth, size.height, inner, innerRegions)
            }
            cursor += size.height + scroll.gap
        }

        // Only a list drawn straight onto the page can be sent on its own: one inside a menu
        // or another list is already part of something that is.
        val cuts = cutting.get()?.takeIf { it.into === out }
        cuts?.at?.add(out.size)

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
            // The bar is something the player can grab, so it gets regions of its own.
            scroll.id?.let { id ->
                regions += Region("$id:track", trackX, y, SCROLLBAR - 4, height)
            }
            val thumbHeight = (height.toDouble() * height / (height + limit)).toInt().coerceAtLeast(16)
            val thumbY = y + ((height - thumbHeight).toDouble() * offset / limit).toInt()
            out += Box(trackX, y, SCROLLBAR - 4, height, Style(background = Paint(Theme.LINE, 0.1), radius = 4))
            out += Box(trackX, thumbY, SCROLLBAR - 4, thumbHeight, Style(background = Paint(Theme.LINE, 0.35), radius = 4))
            scroll.id?.let { id ->
                regions += Region("$id:thumb", trackX, thumbY, SCROLLBAR - 4, thumbHeight)
            }
        }
        cuts?.at?.add(out.size)
    }

    /** What is left of a shape once the window has had its way with it. */
    private fun clip(node: Node, x: Int, y: Int, width: Int, height: Int): Node? {
        val bottom = y + height

        /**
         * What is left of a shape that runs from [from] to [to], or null.
         *
         * Whatever is inside the window, and nothing of what is not.
         */
        fun visible(from: Int, to: Int): IntRange? {
            val top = from.coerceAtLeast(y)
            val end = to.coerceAtMost(bottom)
            return if (end <= top) null else top until end
        }

        return when (node) {
            is Rect -> visible(node.y, node.y + node.height)
                ?.let { node.copy(y = it.first, height = it.last - it.first + 1) }

            // A letter or an icon is drawn whole or not at all.
            is Label -> node.takeIf { it.y >= y && it.y + it.size <= bottom }
            is Sprite -> node.takeIf { it.y >= y && it.y + it.advance <= bottom }

            // A rounded corner cut in half is a notch out of the card, so what the window
            // cuts gets a square corner instead: the quarter disc becomes the part of its
            // own square that is still inside. A border's corner has no square to fall back
            // on and simply goes.
            is CornerPiece -> when {
                node.y >= y && node.y + node.radius <= bottom -> node
                node.ring -> null
                else -> visible(node.y, node.y + node.radius)
                    ?.let { Rect(node.x, it.first, node.radius, it.last - it.first + 1, node.paint) }
            }

            else -> node
        }
    }

    /** Below this, what the window left of a shape reads as an artefact rather than a card. */
    private const val CLIPPED_MINIMUM = 14

    /** The gap between wrapped lines: its own, or the one between children. */
    private val Panel.lineGapOr: Int get() = lineGap ?: gap

    /** Whether this panel actually wraps here — only a row, and only with a width to fill. */
    private fun Panel.wraps(width: Int): Boolean =
        wrap && direction == Direction.ROW && width in 1 until UNBOUNDED

    /** Whether this view gives up room when the row it is in is too small. */
    private fun View.shrinks(): Boolean = this !is Panel || shrink

    /** Something that takes no room in the flow cannot start a new line either. */
    private fun View.inFlow(): Boolean = this !is Raw && this !is Overlay && this !is Particles

    /**
     * Packs the children into lines that fit, greedily, the way `flex-wrap` does.
     *
     * A child wider than the whole panel gets a line of its own and is squeezed there,
     * rather than being dropped or pushing the rest off the edge.
     */
    private fun wrapLines(panel: Panel, width: Int, height: Int): List<List<View>> {
        val lines = mutableListOf<MutableList<View>>(mutableListOf())
        var used = 0
        panel.children.forEach { child ->
            if (!child.inFlow()) {
                lines.last() += child
                return@forEach
            }
            val wants = measure(child, width, height).width
            val line = lines.last()
            val needs = if (line.any { it.inFlow() }) used + panel.gap + wants else wants
            if (needs > width && line.any { it.inFlow() }) {
                lines += mutableListOf(child)
                used = wants
            } else {
                line += child
                used = needs
            }
        }
        return lines.filter { it.isNotEmpty() }
    }

    private fun lineWidth(panel: Panel, line: List<View>, width: Int, height: Int): Int {
        val inFlow = line.filter { it.inFlow() }
        if (inFlow.isEmpty()) return 0
        return inFlow.sumOf { measure(it, width, height).width } + panel.gap * (inFlow.size - 1)
    }

    private fun lineHeight(line: List<View>, width: Int, height: Int): Int =
        line.filter { it.inFlow() }.maxOfOrNull { measure(it, width, height).height } ?: 0

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
        if (panel.wraps(width)) {
            var top = y
            wrapLines(panel, width, height).forEach { line ->
                val tall = lineHeight(line, width, height)
                arrangeLine(panel, line, x, top, width, tall, out, regions)
                top += tall + panel.lineGapOr
            }
            return
        }
        arrangeLine(panel, panel.children, x, y, width, height, out, regions)
    }

    /** One line of children: the ordinary case, and each line of a wrapped row. */
    private fun arrangeLine(
        panel: Panel,
        children: List<View>,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        out: MutableList<Node>,
        regions: MutableList<Region>,
    ) {
        if (children.isEmpty()) return
        val row = panel.direction == Direction.ROW
        val span = if (row) width else height

        // What every child wants, and which of them are willing to share the leftovers.
        // A greedy child is measured against no space along this axis, so it asks only for
        // what it holds — otherwise it would claim the whole row for itself and then be
        // handed the leftovers on top, pushing everything after it off the panel.
        val wanted = children.map { child ->
            val greedy = child.growsAlong(panel.direction)
            val size = when {
                row && greedy -> measure(child, 0, height)
                greedy -> measure(child, width, 0)
                else -> measure(child, width, height)
            }
            if (row) size.width else size.height
        }
        val greedy = children.mapIndexed { index, child ->
            index.takeIf { child.growsAlong(panel.direction) }
        }.filterNotNull()

        val gaps = panel.gap * (children.count { it.inFlow() } - 1).coerceAtLeast(0)
        val used = wanted.sum() + gaps
        val sizes = wanted.toMutableList()

        if (used > span) {
            // Too much to fit: the room is taken out of everything in proportion to what
            // it asked for, which is what a browser does and what keeps a row inside its
            // panel instead of hanging out over the edge of the page.
            //
            // Except what said it would rather not: a chip squeezed by twenty units is not
            // a narrower chip, it is a word with an ellipsis in it. Those keep their size
            // and the rest of the row gives up more — unless nothing in the row is willing,
            // in which case everything does, because overflowing is worse.
            val firm = children.mapIndexed { index, child -> index.takeIf { !child.shrinks() } }
                .filterNotNull()
                .takeIf { it.size < children.size }
                .orEmpty()
            val fixed = firm.sumOf { wanted[it] }
            val room = (span - gaps - fixed).coerceAtLeast(0)
            val asked = wanted.filterIndexed { index, _ -> index !in firm }.sum().coerceAtLeast(1)
            var handed = 0
            var last = -1
            sizes.indices.forEach { index ->
                if (index in firm) return@forEach
                val share = (wanted[index].toLong() * room / asked).toInt()
                sizes[index] = share
                handed += share
                last = index
            }
            // Rounding leaves a unit or two over; the widest child that gave up room takes
            // them.
            val widest = sizes.indices.filter { it !in firm }.maxByOrNull { sizes[it] } ?: last
            if (widest >= 0) sizes[widest] += room - handed
        } else {
            val spare = span - used
            if (greedy.isNotEmpty() && spare > 0) {
                val share = spare / greedy.size
                greedy.forEachIndexed { position, index ->
                    sizes[index] += if (position == greedy.lastIndex) spare - share * greedy.lastIndex else share
                }
            }
        }

        val leftover = (span - (sizes.sum() + panel.gap * (children.size - 1))).coerceAtLeast(0)
        var cursor = when (panel.justify) {
            Justify.START, Justify.SPACE_BETWEEN -> 0
            Justify.CENTER -> leftover / 2
            Justify.END -> leftover
        }
        val extraGap = if (panel.justify == Justify.SPACE_BETWEEN && children.size > 1) {
            leftover / (children.size - 1)
        } else {
            0
        }

        children.forEachIndexed { index, child ->
            // A hand-placed shape, or a field of specks, is measured from the corner its
            // parent holds and given the whole of it: neither takes any room, so a slot in
            // the flow would only tell it about the children around it.
            if (child is Raw || child is Particles) {
                arrange(child, x, y, width, height, out, regions)
                return@forEachIndexed
            }
            // Something standing above the page starts where the flow has reached — under
            // the button that opened it — and is given the rest of the panel to use,
            // without taking any of it from the children that follow.
            if (child is Overlay) {
                // It gets the room it asks for rather than the room that is left. Handed
                // the leftovers, a menu of three lines opening near the bottom of a card
                // would be squeezed until its options sat on top of each other — and it is
                // not inside the card anyway, it is over the page.
                val wants = measure(child.view, width, height)
                if (row) {
                    arrange(child, x + cursor, y, wants.width, height, out, regions)
                } else {
                    arrange(child, x, y + cursor, width, wants.height, out, regions)
                }
                return@forEachIndexed
            }
            val alongSize = sizes[index]
            val crossWanted = measure(child, width, height).let { if (row) it.height else it.width }
            val crossSpan = if (row) height else width
            // Never wider than what it is inside. A child with a size of its own, in a
            // panel that had to give up room, would otherwise stick out sideways and lie
            // over its neighbour — which is how a dropdown ended up on top of a card.
            val crossSize = if (panel.align == Align.STRETCH || child.fillsAcross(panel.direction)) {
                crossSpan
            } else {
                minOf(crossWanted, crossSpan)
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
        is Grid -> grow && direction == Direction.COLUMN
        is Panel -> (if (direction == Direction.ROW) width else height) is Size.Fill
        else -> false
    }

    /** Whether a child wants the full width of the panel across its direction. */
    private fun View.fillsAcross(direction: Direction): Boolean = when (this) {
        is Panel -> (if (direction == Direction.ROW) height else width) is Size.Fill
        else -> false
    }
}
