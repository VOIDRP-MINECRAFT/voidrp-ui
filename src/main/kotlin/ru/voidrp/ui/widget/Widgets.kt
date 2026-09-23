package ru.voidrp.ui.widget

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Icon
import ru.voidrp.ui.layout.TextAlign
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Overlay
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Scroll
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.page.Page
import ru.voidrp.ui.style.Border
import ru.voidrp.ui.style.Insets
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme

/**
 * The controls every interface ends up needing, written once.
 *
 * They are ordinary views with ids, not a widget framework of their own: a tick box is a
 * panel that looks ticked when your state says so, and clicking it tells you it was
 * clicked. The page keeps the state, as it keeps everything else, so there is nothing here
 * that can drift out of step with what the page believes.
 *
 * They hang off [Page] so they can see what the cursor is over and light up without being
 * told.
 */

/**
 * The root of a page: a panel exactly the size of this player's screen.
 *
 * Every page starts with one, and writing it out by hand means writing the viewport into
 * each page and remembering to do it again on the next one. The height is always 1024; the
 * width is whatever shape the player's window is.
 *
 * ```kotlin
 * override fun view(): View = screen(style = Theme.scrim, align = Align.CENTER) {
 *     listOf(card())
 * }
 * ```
 */
fun Page.screen(
    style: Style = Style(),
    direction: Direction = Direction.COLUMN,
    justify: Justify = Justify.START,
    align: Align = Align.START,
    gap: Int = 0,
    id: String? = null,
    children: List<View>,
): View = Panel(
    children = children,
    style = style,
    direction = direction,
    gap = gap,
    justify = justify,
    align = align,
    width = Size.Fixed(viewport.width),
    height = Size.Fixed(viewport.height),
    id = id,
)

/** A button. Clicking it calls `onClick` with [id]. */
fun Page.button(
    caption: String,
    id: String,
    style: Style = Theme.buttonPrimary,
    width: Size = Size.Auto,
    height: Int = 44,
): View = Panel(
    // The height is given, so the style's vertical padding has nothing left to do but
    // fight it: the inner box comes out shorter than the line of text, and the caption is
    // pushed down against the bottom edge instead of sitting in the middle. Only the
    // padding that still means something — the one that decides how wide the button is —
    // is kept.
    style = (if (hovered == id) style.hover() else style).let {
        it.copy(padding = Insets(0, it.padding.right, 0, it.padding.left))
    },
    width = width,
    height = Size.Fixed(height),
    justify = Justify.CENTER,
    align = Align.CENTER,
    id = id,
    children = listOf(Text(caption, style.textSize, style.textColour, style.textWeight, wrap = false)),
)

/** A tick box. The state is yours; this shows it and reports the click. */
fun Page.checkbox(
    label: String,
    id: String,
    checked: Boolean,
    style: Style = Theme.card,
    width: Size = Size.Auto,
): View = Panel(
    style = if (hovered == id) style.hover() else style,
    width = width,
    direction = Direction.ROW,
    gap = Theme.SPACE_3,
    align = Align.CENTER,
    id = id,
    children = listOf(
        Panel(
            style = Style(
                background = Paint(Theme.LINE, 0.12),
                border = Border(1, Paint(if (checked) Theme.VIOLET else Theme.LINE, 0.5)),
                radius = 4,
            ),
            width = Size.Fixed(18),
            height = Size.Fixed(18),
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = if (checked) {
                listOf(
                    Panel(
                        style = Style(background = Paint(Theme.VIOLET, 0.95), radius = 2),
                        width = Size.Fixed(10),
                        height = Size.Fixed(10),
                    )
                )
            } else {
                emptyList()
            },
        ),
        Text(label, Theme.TEXT_BODY, Theme.INK, wrap = false),
    ),
)

/**
 * A number with a minus and a plus, reporting `"<id>:-"` and `"<id>:+"`:
 *
 * ```kotlin
 * override fun onClick(id: String, button: Button) {
 *     when (id) {
 *         "amount:-" -> { amount--; refresh() }
 *         "amount:+" -> { amount++; refresh() }
 *     }
 * }
 * ```
 */
fun Page.stepper(
    id: String,
    value: String,
    style: Style = Theme.card,
): View = Panel(
    style = style,
    direction = Direction.ROW,
    gap = Theme.SPACE_2,
    align = Align.CENTER,
    children = listOf(
        button("−", "$id:-", stepperButton, Size.Fixed(36), 32),
        Panel(
            width = Size.Fixed(64),
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = listOf(Text(value, Theme.TEXT_LEAD, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false)),
        ),
        button("+", "$id:+", stepperButton, Size.Fixed(36), 32),
    ),
)

/**
 * A list of choices. The closed control reports `"<id>"`, each option reports
 * `"<id>:option:<index>"`, and whether the list is open is a field on your page.
 */
fun Page.select(
    id: String,
    options: List<String>,
    selected: Int,
    open: Boolean,
    width: Size = Size.Fill,
): View = Panel(
    gap = 4,
    width = width,
    children = buildList {
        add(
            Panel(
                // Keeping only the padding that decides the width, as a button does: with
                // a height of its own, the vertical padding just pushes the word down.
                style = (if (hovered == id) Theme.buttonGhost.hover() else Theme.buttonGhost).let {
                    it.copy(padding = Insets(0, it.padding.right, 0, it.padding.left))
                },
                width = Size.Fill,
                height = Size.Fixed(40),
                direction = Direction.ROW,
                align = Align.CENTER,
                id = id,
                children = listOf(
                    Text(options.getOrElse(selected) { "—" }, Theme.TEXT_BODY, Theme.INK, wrap = false),
                    Panel(width = Size.Fill),
                    Text(if (open) "▲" else "▼", Theme.TEXT_CAPTION, Theme.INK_DIM, wrap = false),
                ),
            )
        )
        if (open) {
            // The open list stands over the page instead of pushing it: a dropdown that
            // moves everything below it as it opens is a dropdown that moves the thing you
            // were about to click.
            add(
                Overlay(
                    Panel(
                        style = Theme.menu,
                        gap = 1,
                        width = Size.Fill,
                        children = options.mapIndexed { index, option ->
                            option(id, index, option, index == selected)
                        },
                    ),
                ),
            )
        }
    },
)

/**
 * One line of an open list.
 *
 * A row, not a card: options built out of the card style came with a border and a card's
 * padding each, so an open list read as a stack of little panels rather than a menu. The
 * panel around them is the surface; a row only lights up under the pointer and marks the
 * one already chosen.
 */
private fun Page.option(id: String, index: Int, option: String, selected: Boolean): View {
    val optionId = "$id:option:$index"
    val background = when {
        hovered == optionId -> Paint(Theme.VIOLET, 0.28)
        selected -> Paint(Theme.LINE, 0.10)
        else -> null
    }
    return Panel(
        style = Style(
            background = background,
            radius = Theme.R_SM,
            padding = Insets.symmetric(0, Theme.SPACE_3),
        ),
        width = Size.Fill,
        height = Size.Fixed(32),
        direction = Direction.ROW,
        align = Align.CENTER,
        id = optionId,
        children = buildList {
            add(Text(option, Theme.TEXT_BODY, if (selected) Theme.INK else Theme.INK_SOFT, wrap = false))
            if (selected) {
                add(Panel(width = Size.Fill))
                add(Icon("check", 14, Theme.VIOLET_SOFT))
            }
        },
    )
}

/**
 * A slider. Clicking or dragging it reports [id]; [sliderValue] says what was meant.
 *
 * It fills what it is given rather than taking a width of its own: a control with a fixed
 * width inside a panel that had to give up room is a control hanging over the edge.
 */
fun Page.slider(
    id: String,
    value: Double,
    width: Size = Size.Fill,
    height: Int = 18,
): View = Panel(
    style = Style(background = Paint(Theme.LINE, 0.12), radius = height / 2),
    width = width,
    height = Size.Fixed(height),
    // A row, not a column: in a column the fill would be centred across the track, which
    // is what made a slider at seven tenths look like a quarter, in the middle.
    direction = Direction.ROW,
    justify = Justify.START,
    align = Align.CENTER,
    id = id,
    children = listOf(
        Panel(
            style = Style(background = Paint(Theme.VIOLET, if (hovered == id) 1.0 else 0.9), radius = height / 2),
            width = Size.Percent(value.coerceIn(0.0, 1.0)),
            height = Size.Fill,
        )
    ),
)

/** Where along a slider the cursor is, from 0 to 1. */
fun Page.sliderValue(id: String): Double {
    val region = region(id) ?: return 0.0
    if (region.width <= 0) return 0.0
    return ((cursorX - region.x).toDouble() / region.width).coerceIn(0.0, 1.0)
}

/**
 * Where a scrolling list should be after the player grabbed its bar — hand this back as
 * the list's offset.
 */
fun Page.scrollFromBar(scroll: Scroll, id: String, viewportWidth: Int, viewportHeight: Int): Int {
    val track = region("$id:track") ?: return scroll.offset
    if (track.height <= 0) return scroll.offset
    val limit = Layout.maxOffset(scroll, viewportWidth, viewportHeight)
    val position = ((cursorY - track.y).toDouble() / track.height).coerceIn(0.0, 1.0)
    return (limit * position).toInt()
}

/**
 * Moves a list by notches of the wheel: a row at a time, and never past either end.
 *
 * **A row at a time**, because a glyph is drawn whole or not at all. A list stopped between
 * two rows cuts the top card through its middle, and what survives of it is whatever text
 * happened to fit: a description with no title over it, a unit with no number in front of
 * it. Seen on a live client, it reads as broken rather than as scrolled. Stopping on the top
 * of a row is what an inventory does, and it is the only stop at which every card is whole.
 * The last stop is the bottom of the list, wherever that falls, so the end is reachable.
 * Pass [step] for a list that should move by a fixed distance instead.
 *
 * **Never past either end**, because the offset is the page's own number and a page that
 * only stops it at the top lets it run on past the bottom: the picture stops moving, the
 * number keeps growing, and the wheel turned back then does nothing for as many notches as
 * were spent past the end.
 */
fun scrolled(scroll: Scroll, direction: Int, viewportWidth: Int, viewportHeight: Int, step: Int? = null): Int {
    val limit = Layout.maxOffset(scroll, viewportWidth, viewportHeight).coerceAtLeast(0)
    val current = scroll.offset.coerceIn(0, limit)
    if (step != null) return (current + direction * step).coerceIn(0, limit)
    val stops = (Layout.rowStarts(scroll, viewportWidth).filter { it < limit } + limit).distinct().sorted()
    var at = current
    repeat(Math.abs(direction)) {
        at = if (direction > 0) stops.firstOrNull { it > at } ?: limit else stops.lastOrNull { it < at } ?: 0
    }
    return at
}

/**
 * A tooltip: a title, and lines of explanation under it.
 *
 * Built from the menu's surface rather than the page's, because it floats over whatever
 * the cursor happens to be on and every other surface here is a tint that would let that
 * show through.
 */
fun tooltipPanel(title: String, lines: List<String> = emptyList(), width: Int = 260): View = Panel(
    style = Theme.menu.copy(padding = Insets.all(Theme.SPACE_3)),
    width = Size.Fixed(width),
    gap = 4,
    children = buildList {
        add(Text(title, Theme.TEXT_LEAD, Theme.INK, TextFonts.Weight.SEMIBOLD))
        lines.forEach { add(Text(it, Theme.TEXT_CAPTION, Theme.INK_SOFT)) }
    },
)

/** The minus and plus are symbols, not words: bigger, brighter, centred. */
private val stepperButton = Theme.buttonGhost.copy(
    textSize = Theme.TEXT_H3,
    textColour = Theme.INK,
    padding = Insets.NONE,
)

/** The same style, a little brighter — what "hovered" means throughout. */
private fun Style.hover(): Style {
    val paint = background as? Paint ?: return this
    return copy(background = paint.alpha(minOf(1.0, paint.alpha + 0.15)))
}

/**
 * The small uppercase label the site puts above everything: tracked out, dim, and quiet.
 *
 * It exists as a helper rather than a style because the letters themselves change — an
 * eyebrow is set in capitals, and doing that at the call site is one more thing to forget.
 */
fun eyebrow(
    text: String,
    colour: Int = Theme.INK_DIM,
    size: Int = Theme.TEXT_CAPTION,
    tracking: Int = Theme.TRACKING,
): View = Text(text.uppercase(), size, colour, TextFonts.Weight.SEMIBOLD, tracking = tracking, wrap = false)

/** A pill with a word in it: a version, a mode, a state. */
fun chip(text: String, style: Style = Theme.chip): View = Panel(
    style = style,
    justify = Justify.CENTER,
    align = Align.CENTER,
    // A chip squeezed by a row that ran out of width is not a narrower chip, it is a word
    // with an ellipsis in it. It keeps its size and the row gives up the difference.
    shrink = false,
    children = listOf(Text(text, style.textSize, style.textColour, style.textWeight, wrap = false)),
)

/**
 * A progress bar with its own caption row — the way the site shows players online: a
 * tracked-out label on the left, the value in bold on the right, a thin track underneath.
 */
fun progress(
    caption: String,
    value: Double,
    valueText: String,
    colour: Int = Theme.GREEN,
    width: Size = Size.Fill,
): View = Panel(
    width = width,
    gap = 6,
    children = listOf(
        Panel(
            direction = Direction.ROW,
            width = Size.Fill,
            align = Align.CENTER,
            children = listOf(
                eyebrow(caption),
                Panel(width = Size.Fill),
                Text(valueText, Theme.TEXT_BODY, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false),
            ),
        ),
        Panel(
            style = Style(background = Paint(Theme.LINE, 0.12), radius = 4),
            width = Size.Fill,
            height = Size.Fixed(6),
            direction = Direction.ROW,
            children = listOf(
                Panel(
                    style = Style(background = Paint(colour, 0.95), radius = 4),
                    width = Size.Percent(value),
                    height = Size.Fill,
                )
            ),
        ),
    ),
)

/**
 * A card with a heading: an icon, a title in small caps, and whatever you put in it.
 *
 * Every page ends up writing this by hand — a panel, a row with an icon and a label, a gap,
 * the contents — and every page writes it slightly differently. This is the one the pages
 * that ship with the plugin use.
 */
fun card(
    title: String,
    children: List<View>,
    icon: String? = null,
    style: Style = Theme.card,
    width: Size = Size.Fill,
    height: Size = Size.Auto,
    gap: Int = Theme.SPACE_3,
    trailing: View? = null,
): View = Panel(
    style = style,
    width = width,
    height = height,
    gap = gap,
    children = listOf(
        Panel(
            direction = Direction.ROW,
            width = Size.Fill,
            gap = Theme.SPACE_2,
            align = Align.CENTER,
            children = buildList {
                icon?.let { add(Icon(it, Theme.TEXT_LEAD, Theme.INK_SOFT)) }
                add(eyebrow(title, Theme.INK))
                if (trailing != null) {
                    add(Panel(width = Size.Fill))
                    add(trailing)
                }
            },
        ),
    ) + children,
)

/**
 * A row of tabs. The chosen one is filled; the rest are quiet.
 *
 * Each reports `"<id>:<key>"`, so a page switches on the suffix and keeps the key it is on
 * in a field of its own.
 */
fun Page.tabs(
    id: String,
    tabs: List<Pair<String, String>>,
    selected: String,
    height: Int = 36,
): View = Panel(
    direction = Direction.ROW,
    gap = Theme.SPACE_1,
    align = Align.CENTER,
    children = tabs.map { (key, caption) ->
        button(
            caption,
            "$id:$key",
            if (key == selected) Theme.buttonPrimary else Theme.buttonGhost,
            height = height,
        )
    },
)

/**
 * A number with a word under it — what a dashboard is made of.
 *
 * The label goes above the number and in small caps, because the eye reads the number and
 * only then asks what it is.
 */
fun statTile(
    label: String,
    value: String,
    icon: String? = null,
    style: Style = Theme.card,
    width: Size = Size.Fill,
    accent: Int = Theme.INK,
): View = Panel(
    style = style,
    width = width,
    // Six, not four: a line of text is taller than the size it is set at, and at four the
    // label sat on the icon above it.
    gap = 6,
    align = Align.CENTER,
    justify = Justify.CENTER,
    children = buildList {
        icon?.let { add(Icon(it, Theme.TEXT_LEAD, Theme.INK_SOFT)) }
        add(eyebrow(label))
        // The line box of a big number carries the room a descender would need, and there
        // is no descender in "184 200" — so the tile looked bottom-heavy while its boxes
        // were centred exactly. The number's box is trimmed to what it actually draws.
        add(
            Text(
                value,
                Theme.TEXT_H3,
                accent,
                TextFonts.Weight.BOLD,
                wrap = false,
                lineHeight = Theme.TEXT_H3,
            ),
        )
    },
)

/** A square button with nothing but an icon in it — a rail, a toolbar, a close button. */
fun Page.iconButton(
    icon: String,
    id: String,
    size: Int = 42,
    style: Style = Theme.buttonGhost,
    selected: Boolean = false,
    // Twenty-four, not twenty: the set is drawn on a 24-unit grid, and at that size a
    // stroke lands on whole pixels instead of between two of them.
    iconSize: Int = 24,
): View = Panel(
    // Without the padding the style carries for text. A button's padding is sized for
    // words, and inside a square that leaves an inner box a few units wide — the icon then
    // sits in the corner of it rather than in the middle of the button, which is exactly
    // what it looked like.
    style = when {
        selected -> Theme.buttonPrimary
        hovered == id -> style.hover()
        else -> style
    }.copy(padding = Insets.NONE),
    width = Size.Fixed(size),
    height = Size.Fixed(size),
    justify = Justify.CENTER,
    align = Align.CENTER,
    id = id,
    children = listOf(Icon(icon, iconSize, if (selected) 0xFFFFFF else Theme.INK_DIM)),
)

/** A switch: the same state a tick box holds, where the page wants it to read as on or off. */
fun Page.toggle(
    id: String,
    on: Boolean,
    label: String? = null,
): View = Panel(
    direction = Direction.ROW,
    gap = Theme.SPACE_2,
    align = Align.CENTER,
    id = id,
    children = buildList {
        add(
            Panel(
                style = Style(
                    background = Paint(if (on) Theme.VIOLET else Theme.LINE, if (on) 0.9 else 0.16),
                    radius = 11,
                ),
                width = Size.Fixed(40),
                height = Size.Fixed(22),
                direction = Direction.ROW,
                align = Align.CENTER,
                justify = if (on) Justify.END else Justify.START,
                children = listOf(
                    Panel(
                        style = Style(background = Paint(0xFFFFFF, if (on) 1.0 else 0.65), radius = 9),
                        width = Size.Fixed(18),
                        height = Size.Fixed(18),
                    ),
                ),
            ),
        )
        label?.let { add(Text(it, Theme.TEXT_BODY, if (on) Theme.INK else Theme.INK_SOFT, wrap = false)) }
    },
)

/** A hairline across a panel, for where a gap is not enough of a break. */
fun divider(width: Size = Size.Fill, paint: Paint = Paint(Theme.LINE, 0.12)): View = Panel(
    style = Style(background = paint),
    width = width,
    height = Size.Fixed(1),
)

/** What a page says when there is nothing to show — an invitation, not an apology. */
fun emptyState(
    title: String,
    hint: String? = null,
    icon: String = "inbox",
): View = Panel(
    width = Size.Fill,
    gap = Theme.SPACE_3,
    align = Align.CENTER,
    justify = Justify.CENTER,
    style = Style(padding = Insets.symmetric(Theme.SPACE_5, Theme.SPACE_4)),
    children = buildList {
        add(Icon(icon, Theme.TEXT_H2, Theme.INK_DIM))
        add(Text(title, Theme.TEXT_LEAD, Theme.INK_SOFT, TextFonts.Weight.SEMIBOLD, align = TextAlign.CENTER))
        hint?.let { add(Text(it, Theme.TEXT_CAPTION, Theme.INK_DIM, align = TextAlign.CENTER)) }
    },
)

/** How a notice is meant to be read. */
enum class Tone { INFO, GOOD, WARN, BAD }

/**
 * A line of explanation with a colour to it: what went wrong, what is about to happen,
 * what just did.
 */
fun notice(text: String, tone: Tone = Tone.INFO, width: Size = Size.Fill): View {
    val colour = when (tone) {
        Tone.INFO -> Theme.VIOLET_SOFT
        Tone.GOOD -> Theme.GREEN
        Tone.WARN -> Theme.GOLD
        Tone.BAD -> Theme.RED
    }
    return Panel(
        style = Style(
            background = Paint(colour, 0.1),
            border = Border(1, Paint(colour, 0.35)),
            radius = Theme.R_MD,
            padding = Insets.symmetric(Theme.SPACE_2, Theme.SPACE_3),
        ),
        width = width,
        direction = Direction.ROW,
        gap = Theme.SPACE_2,
        align = Align.CENTER,
        children = listOf(
            Icon(
                when (tone) {
                    Tone.GOOD -> "check"
                    Tone.BAD -> "alert"
                    Tone.WARN -> "alert"
                    Tone.INFO -> "sparkles"
                },
                Theme.TEXT_LEAD,
                colour,
            ),
            Text(text, Theme.TEXT_BODY, Theme.INK_SOFT),
        ),
    )
}

/**
 * A question with two answers, standing over the page.
 *
 * Reports `"<id>:yes"` and `"<id>:no"`. Put it in an [Overlay] at the root of the page, so
 * that it covers what it is asking about.
 */
fun Page.dialog(
    id: String,
    title: String,
    text: String? = null,
    yes: String = "Yes",
    no: String = "Cancel",
    tone: Tone = Tone.INFO,
    width: Int = 420,
): View = Panel(
    width = Size.Fixed(viewport.width),
    height = Size.Fixed(viewport.height),
    style = Style(background = Paint(0x000000, 0.6)),
    justify = Justify.CENTER,
    align = Align.CENTER,
    children = listOf(
        Panel(
            style = Theme.menu.copy(padding = Insets.all(Theme.SPACE_5)),
            width = Size.Fixed(width),
            gap = Theme.SPACE_3,
            align = Align.CENTER,
            children = buildList {
                add(Text(title, Theme.TEXT_LEAD, Theme.INK, TextFonts.Weight.BOLD, align = TextAlign.CENTER))
                text?.let { add(Text(it, Theme.TEXT_BODY, Theme.INK_SOFT, align = TextAlign.CENTER)) }
                add(
                    Panel(
                        direction = Direction.ROW,
                        gap = Theme.SPACE_2,
                        justify = Justify.CENTER,
                        width = Size.Fill,
                        children = listOf(
                            button(
                                yes,
                                "$id:yes",
                                if (tone == Tone.BAD) Theme.buttonPrimary.copy(background = Paint(Theme.RED, 0.9)) else Theme.buttonPrimary,
                                Size.Fixed(150),
                            ),
                            button(no, "$id:no", Theme.buttonGhost, Size.Fixed(150)),
                        ),
                    ),
                )
            },
        ),
    ),
)
