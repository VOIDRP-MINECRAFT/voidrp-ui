package ru.voidrp.ui.layout

import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.style.Paint
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
    /**
     * Never wider than this, however much room it is given.
     *
     * The `max-width` of a web page, and it centres what is left over the way `margin: 0
     * auto` does. It is what lets a page fill a 4:3 window and still read well on an
     * ultrawide one, where a column stretched to the full width would be a line of text a
     * metre long. Pair it with [Size.Fill]: fill the room, up to this much.
     */
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    /** Never narrower than this, even where there is not the room — it will overflow. */
    val minWidth: Int? = null,
    val minHeight: Int? = null,
    /**
     * Whether a row that runs out of width carries on underneath.
     *
     * `flex-wrap`, and it is for the case a [Grid] does not cover: things of different
     * widths — chips, tags, a hand of items — that should fill the line and then start
     * another. A grid puts everything in cells of one size, which is right for tiles and
     * wrong for words.
     *
     * Only a row wraps. In a column it is ignored, because a column that wraps into
     * another column is not a layout anyone wants.
     */
    val wrap: Boolean = false,
    /** The gap between wrapped lines; the panel's own [gap] when it is not given. */
    val lineGap: Int? = null,
    /**
     * Whether this panel gives up room when the row it is in has too little.
     *
     * A row that does not fit takes the difference out of everything in it, in proportion
     * to what each asked for — which is what a browser does. For most things that is
     * right, and for a few it is not: a chip squeezed by twenty units does not become a
     * narrower chip, it becomes a word with an ellipsis in it. Those say `shrink = false`
     * and keep their size; the rest of the row gives up the room instead.
     */
    val shrink: Boolean = true,
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
    /**
     * A light behind the letters.
     *
     * Text cannot be blurred, so this is the same word drawn a few times around itself in
     * a faint colour — which is what a one-pixel text-shadow amounts to anyway.
     */
    val glow: Paint? = null,
) : View

/**
 * One of the interface's own icons — a wallet, a clock, a shield.
 *
 * It takes the colour it is given, because the pictures are baked white: the same icon
 * reads as a quiet label beside a caption and as the accent inside a chosen card.
 */
data class Icon(
    val name: String,
    val size: Int = Theme.TEXT_LEAD,
    val colour: Int = Theme.INK_SOFT,
) : View

/**
 * A picture of an item, drawn from the client's own texture for it. Names it the way the
 * game does — `diamond`, `minecraft:golden_apple` — and it measures itself square.
 */
data class Image(val item: String, val size: Int = 32) : View

/**
 * A picture the server put in itself — a logo, a banner — by the name of its file in
 * `plugins/VoidRpUI/images/`.
 *
 * [height] is what it is drawn at; the width follows from the picture's own proportions,
 * so a wide banner stays wide. Unlike [Icon] it keeps its own colours.
 */
data class Picture(val name: String, val height: Int = 64) : View

/**
 * A player's face, cut out of their skin.
 *
 * The skin has to be in `plugins/VoidRpUI/heads/` when the pack is built — see
 * [ru.voidrp.ui.pack.PlayerHeads] for why that is a real limit and what it is good for.
 */
data class Head(val player: String, val size: Int = 32) : View

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
    /**
     * Whether the rows share out the room their panel has left over.
     *
     * Off, the grid is as tall as its rows need and any spare space stays at the bottom of
     * the card. On, the rows divide it between them, which is how a panel of tiles reaches
     * the bottom edge instead of floating above a third of it.
     *
     * It is a flag rather than a [Size] on purpose: a size takes part in measuring, and a
     * grid that asks to fill while its own panel is being measured claims every unit the
     * page has and takes the layout with it.
     */
    val grow: Boolean = false,
    val align: Align = Align.START,
) : View

/** Empty space, for when a gap is not enough — the flexible kind pushes things apart. */
data class Gap(val size: Int = 0, val grow: Boolean = false) : View

/**
 * Something laid out where it stands but painted over everything else.
 *
 * A dropdown is the reason this exists. As an ordinary child it pushes the rest of the
 * panel down as it opens and is painted before the things that come after it, so the row
 * of buttons below covers its last option. Inside an [Overlay] it takes no room, nothing
 * moves when it opens, and it goes on last — over the card underneath, as a menu should.
 *
 * The same goes for a tooltip, or anything else that stands above the page for a moment.
 */
data class Overlay(val view: View) : View

/**
 * A field of drifting specks behind the page.
 *
 * It takes no room and moves nothing, like [Raw] — it is scattered over the whole canvas
 * and painted wherever it is put in the list, so it goes first, behind everything.
 *
 * The specks move by themselves: each carries a marker the shader knows, and the shader
 * works out where it is from the time of day. So the page is still sent once and never
 * again, and the motion costs nothing — no frames, no packets, no server thread. Where the
 * pack was built without that branch (`effects.particles: false`), the same specks are
 * sent as an ordinary still field.
 */
data class Particles(
    val count: Int = 60,
    val colour: Int = Theme.INK,
    /** How faint. Two shades are used, so a field has some depth to it. */
    val alpha: Double = 0.45,
    /** A speck is one or two units; the bigger ones are rarer. */
    val size: Int = 1,
    /** Changes the scatter — the same seed always lays them out the same way. */
    val seed: Int = 7,
) : View

/**
 * An escape hatch: a shape positioned by hand, for what the layout has no word for yet.
 *
 * It takes no room and moves nothing else, and its coordinates are read from the corner of
 * whatever holds it — so a page can scatter stars across the whole canvas, or lay a glow
 * inside one panel, without either needing to know where the other ended up.
 */
data class Raw(val node: Node) : View
