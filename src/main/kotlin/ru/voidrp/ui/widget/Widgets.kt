package ru.voidrp.ui.widget

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Layout
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

/** A button. Clicking it calls `onClick` with [id]. */
fun Page.button(
    caption: String,
    id: String,
    style: Style = Theme.buttonPrimary,
    width: Size = Size.Auto,
    height: Int = 44,
): View = Panel(
    style = if (hovered == id) style.hover() else style,
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
                style = if (hovered == id) Theme.buttonGhost.hover() else Theme.buttonGhost,
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
            options.forEachIndexed { index, option ->
                val optionId = "$id:option:$index"
                add(
                    Panel(
                        style = if (hovered == optionId) Theme.cardAccent else Theme.card,
                        width = Size.Fill,
                        height = Size.Fixed(34),
                        direction = Direction.ROW,
                        align = Align.CENTER,
                        id = optionId,
                        children = listOf(
                            Text(
                                option,
                                Theme.TEXT_BODY,
                                if (index == selected) Theme.INK else Theme.INK_SOFT,
                                wrap = false,
                            )
                        ),
                    )
                )
            }
        }
    },
)

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

/** A tooltip: a title, and lines of explanation under it. */
fun tooltipPanel(title: String, lines: List<String> = emptyList(), width: Int = 260): View = Panel(
    style = Theme.page.copy(padding = Insets.all(Theme.SPACE_3), radius = Theme.R_MD),
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
