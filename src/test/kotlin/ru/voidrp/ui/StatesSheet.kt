package ru.voidrp.ui

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Scroll
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.page.Button
import ru.voidrp.ui.page.Page
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.style.Insets
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.Tone
import ru.voidrp.ui.widget.button
import ru.voidrp.ui.widget.card
import ru.voidrp.ui.widget.divider
import ru.voidrp.ui.widget.emptyState
import ru.voidrp.ui.widget.iconButton
import ru.voidrp.ui.widget.notice
import ru.voidrp.ui.widget.statTile
import ru.voidrp.ui.widget.tabs
import ru.voidrp.ui.widget.toggle
import ru.voidrp.ui.widget.checkbox
import ru.voidrp.ui.widget.chip
import ru.voidrp.ui.widget.eyebrow
import ru.voidrp.ui.widget.progress
import ru.voidrp.ui.widget.select
import ru.voidrp.ui.widget.slider
import ru.voidrp.ui.widget.stepper
import ru.voidrp.ui.widget.tooltipPanel

/**
 * Every control, in every state it has, on one page.
 *
 * Bugs in an interface hide in the states a screenshot of a fresh page never shows: the
 * box that is checked, the menu that is open, the row the cursor is on, the slider at
 * nought and at full. This is drawn by the preview so all of them can be looked at side by
 * side, and by the tests so none of them can quietly stop balancing.
 */
class StatesSheet(private val hover: String? = null, private val part: Int = 1) : Page() {

    override val hovered: String? get() = hover

    override fun view(): View = Panel(
        style = Theme.scrim,
        width = Size.Fixed(viewport.width),
        height = Size.Fixed(viewport.height),
        justify = ru.voidrp.ui.layout.Justify.CENTER,
        align = Align.CENTER,
        children = listOf(
            Panel(
                style = Theme.page,
                width = Size.Fixed(1180),
                gap = Theme.SPACE_4,
                // Two sheets, not one: everything on a single page no longer fits the
                // canvas, and a layout that does not fit is compressed — the gaps go
                // first, so every control would be shown a little tighter than it really
                // is. Exactly the thing this sheet exists to catch.
                children = (if (part == 1) first() else second()).map { it },
            ),
        ),
    )

    private fun first(): List<View> = listOf(
                    row(
                        "buttons",
                        button("Primary", "b1", Theme.buttonPrimary, Size.Fixed(190)),
                        button("Quiet", "b2", Theme.buttonGhost, Size.Fixed(150)),
                        button("Hovered", "hover:button", Theme.buttonPrimary, Size.Fixed(190)),
                    ),
                    row(
                        "tick box, stepper",
                        Panel(width = Size.Fixed(230), children = listOf(checkbox("On", "c1", true))),
                        Panel(width = Size.Fixed(230), children = listOf(checkbox("Off", "c2", false))),
                        Panel(width = Size.Fixed(230), children = listOf(stepper("q", "7"))),
                    ),
                    row(
                        "slider",
                        Panel(width = Size.Fixed(230), children = listOf(slider("s0", 0.0))),
                        Panel(width = Size.Fixed(230), children = listOf(slider("s1", 0.5))),
                        Panel(width = Size.Fixed(230), children = listOf(slider("s2", 1.0))),
                    ),
                    row(
                        "progress, chips",
                        Panel(width = Size.Fixed(230), children = listOf(progress("Empty", 0.0, "0 / 50"))),
                        Panel(width = Size.Fixed(230), children = listOf(progress("Online", 0.35, "21 / 50"))),
                        Panel(
                            direction = Direction.ROW,
                            gap = Theme.SPACE_2,
                            children = listOf(chip("Plain"), chip("Accent", Theme.chipAccent)),
                        ),
                    ),
                    row(
                        "a row that wraps",
                        Panel(
                            width = Size.Fixed(700),
                            direction = Direction.ROW,
                            wrap = true,
                            gap = Theme.SPACE_2,
                            lineGap = Theme.SPACE_2,
                            children = listOf(
                                "Survival", "Peaceful", "Hardcore", "Adventure",
                                "Creative", "Spectator", "Challenge",
                            ).map { chip(it) },
                        ),
                    ),
                    row(
                        "long text, scrolling",
                        Panel(
                            style = Theme.card,
                            width = Size.Fixed(300),
                            children = listOf(
                                Text(
                                    "A line that plainly does not fit its card and has to be cut short",
                                    Theme.TEXT_BODY,
                                    Theme.INK,
                                    maxLines = 2,
                                ),
                            ),
                        ),
                        Panel(
                            style = Theme.card,
                            width = Size.Fixed(300),
                            height = Size.Fixed(110),
                            children = listOf(
                                Scroll(
                                    id = "list",
                                    offset = 40,
                                    height = Size.Fixed(84),
                                    width = Size.Fill,
                                    gap = 6,
                                    children = (1..8).map {
                                        Panel(
                                            style = Theme.card.copy(padding = Insets.symmetric(6, 10)),
                                            width = Size.Fill,
                                            height = Size.Fixed(30),
                                            justify = ru.voidrp.ui.layout.Justify.CENTER,
                                            children = listOf(Text("Row $it", Theme.TEXT_BODY, Theme.INK_SOFT)),
                                        )
                                    },
                                ),
                            ),
                        ),
                        Panel(
                            style = Theme.cardAccent,
                            width = Size.Fixed(300),
                            height = Size.Fixed(110),
                            justify = ru.voidrp.ui.layout.Justify.CENTER,
                            align = Align.CENTER,
                            children = listOf(Text("Accent card", Theme.TEXT_LEAD, Theme.INK)),
                        ),
                    ),
    )

    private fun second(): List<View> = listOf(
                    row(
                        "tabs, switches, icon buttons",
                        Panel(
                            width = Size.Fixed(300),
                            children = listOf(
                                tabs("tab", listOf("all" to "All", "arms" to "Weapons", "food" to "Food"), "arms"),
                            ),
                        ),
                        Panel(
                            width = Size.Fixed(230),
                            gap = Theme.SPACE_2,
                            children = listOf(toggle("t1", true, "On"), toggle("t2", false, "Off")),
                        ),
                        Panel(
                            direction = Direction.ROW,
                            gap = Theme.SPACE_2,
                            children = listOf(
                                iconButton("home", "i1", selected = true),
                                iconButton("market", "i2"),
                                iconButton("settings", "hover:icon"),
                            ),
                        ),
                    ),
                    row(
                        "tiles, divider",
                        Panel(width = Size.Fixed(230), children = listOf(statTile("Balance", "184 200", "coins", accent = Theme.GOLD))),
                        Panel(width = Size.Fixed(230), children = listOf(statTile("Kills", "1 204", "swords"))),
                        Panel(
                            width = Size.Fixed(220),
                            gap = Theme.SPACE_2,
                            children = listOf(
                                Text("Above the line", Theme.TEXT_BODY, Theme.INK_SOFT),
                                divider(),
                                Text("Below it", Theme.TEXT_BODY, Theme.INK_SOFT),
                            ),
                        ),
                        Panel(
                            style = Theme.card,
                            width = Size.Fixed(330),
                            children = listOf(emptyState("Nothing here yet", "Buy your first item on the market")),
                        ),
                    ),
                    row(
                        "notices, empty state",
                        Panel(width = Size.Fixed(360), gap = Theme.SPACE_2, children = listOf(
                            notice("Saved", Tone.GOOD),
                            notice("Not enough coins", Tone.BAD),
                        )),
                        Panel(width = Size.Fixed(360), gap = Theme.SPACE_2, children = listOf(
                            notice("A wipe is coming", Tone.WARN),
                            notice("The season ends in three days", Tone.INFO),
                        )),
                    ),
                    row(
                        "card with a heading",
                        Panel(
                            width = Size.Fixed(480),
                            children = listOf(
                                card(
                                    "Quick access",
                                    icon = "grid",
                                    trailing = chip("6"),
                                    children = listOf(
                                        Panel(
                                            direction = Direction.ROW,
                                            gap = Theme.SPACE_2,
                                            width = Size.Fill,
                                            children = listOf(
                                                statTile("Quests", "57", "quest", Theme.cardAccent),
                                                statTile("Market", "12", "market", Theme.cardAccent),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        Panel(width = Size.Fixed(600), children = listOf(dialogPreview())),
                    ),
                    row(
                        "dropdown",
                        Panel(width = Size.Fixed(230), children = listOf(select("m1", MODES, 0, false))),
                        Panel(width = Size.Fixed(230), children = listOf(select("m2", MODES, 1, true))),
                        Panel(width = Size.Fixed(300), children = listOf(tooltipPanel("Diamond", listOf("120 coins", "12 in stock")))),
                    ),
    )

    /**
     * The dialog draws itself over the whole screen, which is right in a page and wrong in
     * a sheet of controls, so here it is shown at the size of its own card.
     */
    private fun dialogPreview(): View = Panel(
        style = Theme.menu.copy(padding = Insets.all(Theme.SPACE_5)),
        width = Size.Fixed(420),
        gap = Theme.SPACE_3,
        align = Align.CENTER,
        children = listOf(
            Text("Sell for 120 coins?", Theme.TEXT_LEAD, Theme.INK, TextFonts.Weight.BOLD),
            Text("The item goes at once and this cannot be undone.", Theme.TEXT_BODY, Theme.INK_SOFT),
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                justify = ru.voidrp.ui.layout.Justify.CENTER,
                width = Size.Fill,
                children = listOf(
                    button("Sell", "d:yes", Theme.buttonPrimary, Size.Fixed(150)),
                    button("Cancel", "d:no", Theme.buttonGhost, Size.Fixed(150)),
                ),
            ),
        ),
    )

    private fun row(title: String, vararg cells: View): View = Panel(
        width = Size.Fill,
        gap = Theme.SPACE_2,
        children = listOf(
            eyebrow(title),
            Panel(
                direction = Direction.ROW,
                width = Size.Fill,
                gap = Theme.SPACE_3,
                align = Align.START,
                children = cells.toList(),
            ),
        ),
    )

    override fun onClick(id: String, button: Button) = Unit

    private companion object {
        val MODES = listOf("Survival", "Creative", "Adventure")
    }
}
