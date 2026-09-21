package ru.voidrp.ui.layout

import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme

/**
 * What a page is written with.
 *
 * A page describes what it contains, not where each piece goes: a panel holds a row or a
 * column of things, with a gap between them and a rule for lining them up, and works out
 * its own size from what is inside it. That is the difference between porting a design
 * from the site and re-typing its coordinates — the same structure the stylesheet has
 * survives the trip.
 *
 * [Layout] turns a view into the shapes on the canvas.
 */
sealed interface View

/** How big something wants to be along one axis. */
sealed interface Size {
    /** As big as the content needs. */
    data object Auto : Size

    /** Everything the parent can spare. */
    data object Fill : Size

    /** Exactly this many canvas units. */
    data class Fixed(val value: Int) : Size

    /** A fraction of what the parent offers — a progress bar at two thirds. */
    data class Percent(val fraction: Double) : Size
}

/** Which way a panel stacks its children. */
enum class Direction { ROW, COLUMN }

/** Where the children sit along the direction they stack in. */
enum class Justify { START, CENTER, END, SPACE_BETWEEN }

/** Where a child sits across that direction. */
enum class Align { START, CENTER, END, STRETCH }

/** How the lines of a paragraph line up with each other. */
enum class TextAlign { START, CENTER, END }

/**
 * A styled container. Give it children and it lays them out; give it a size and it keeps
 * to it, otherwise it takes the size of what it holds plus its padding.
 */
data class Panel(
    val children: List<View> = emptyList(),
    val style: Style = Style(),
    val direction: Direction = Direction.COLUMN,
    val gap: Int = 0,
    val justify: Justify = Justify.START,
    val align: Align = Align.START,
    val width: Size = Size.Auto,
    val height: Size = Size.Auto,
    /**
     * Names a panel the player can point at. The layout remembers where a named panel
     * ended up, so hovering and clicking are answered on the server by asking which named
     * rectangle the cursor is inside — no guessing on the client, and nothing to trust it
     * about.
     */
    val id: String? = null,
) : View

/** One piece of a line with its own look: a price in gold inside a sentence in grey. */
data class Span(
    val text: String,
    val colour: Int? = null,
    val weight: TextFonts.Weight? = null,
    val size: Int? = null,
)

/**
 * A line made of pieces, each with its own colour, weight or size.
 *
 * For a sentence that changes part way through — a name in white and a price in gold — and
 * it stays on one line, because a page that needs a paragraph of mixed styling usually
 * wants several [Text]s in a column instead.
 */
data class RichText(
    val spans: List<Span>,
    val size: Int = Theme.TEXT_BODY,
    val colour: Int = Theme.INK,
    val weight: TextFonts.Weight = TextFonts.Weight.REGULAR,
    val align: TextAlign = TextAlign.START,
) : View

/**
 * Text. It measures itself, so a panel around it fits it exactly, and it breaks into
 * lines when the space it is given is narrower than the words in it.
 */
data class Text(
    val value: String,
    val size: Int = Theme.TEXT_BODY,
    val colour: Int = Theme.INK,
    val weight: TextFonts.Weight = TextFonts.Weight.REGULAR,
    val align: TextAlign = TextAlign.START,
    /** Whether long text is broken across lines rather than running past the panel. */
    val wrap: Boolean = true,
    /** Distance from one line's top to the next; the typeface's own spacing by default. */
    val lineHeight: Int? = null,
    /** At most this many lines; what does not fit ends in an ellipsis. */
    val maxLines: Int? = null,
    /** Extra air after every letter, the way a stylesheet spaces out a small caps label. */
    val tracking: Int = 0,
) : View

/**
 * A picture of an item, drawn from the client's own texture for it. Names it the way the
 * game does — `diamond`, `minecraft:golden_apple` — and it measures itself square.
 */
data class Image(val item: String, val size: Int = 32) : View

/**
 * A column of things taller than the space it is given, shown through a window into it.
 *
 * Nothing can be clipped halfway on the way to the client — a glyph is drawn whole or not
 * at all — so what falls outside the window is cut where it can be (a rectangle) and left
 * out where it cannot (a word, an icon). At the sizes a list is built from, that reads as
 * an ordinary scrolling panel.
 *
 * [offset] belongs to the page, like everything else it shows: the wheel changes a number,
 * the page draws itself again.
 */
data class Scroll(
    val children: List<View> = emptyList(),
    val offset: Int = 0,
    val gap: Int = 0,
    val width: Size = Size.Fill,
    val height: Size = Size.Fill,
    /** Whether to draw the little bar showing where in the list we are. */
    val bar: Boolean = true,
    val id: String? = null,
) : View

/**
 * A grid: children laid out in rows of [columns], wrapping as they go.
 *
 * An inventory, a shop or a set of rewards is a grid, and building one out of rows and
 * columns by hand means doing the wrapping yourself every time.
 */
data class Grid(
    val children: List<View> = emptyList(),
    val columns: Int = 4,
    val gap: Int = 0,
    val rowGap: Int = gap,
    val width: Size = Size.Auto,
    val align: Align = Align.START,
) : View

/** Empty space, for when a gap is not enough — the flexible kind pushes things apart. */
data class Gap(val size: Int = 0, val grow: Boolean = false) : View

/** An escape hatch: something already positioned, for what the layout has no word for yet. */
data class Raw(val node: Node) : View
