package com.example.uidemo

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Grid
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.page.Button
import ru.voidrp.ui.page.Page
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.Tone
import ru.voidrp.ui.widget.button
import ru.voidrp.ui.widget.card
import ru.voidrp.ui.widget.notice
import ru.voidrp.ui.widget.screen
import ru.voidrp.ui.widget.statTile
import ru.voidrp.ui.widget.tabs

/**
 * A page answers one question: what do I look like right now.
 *
 * State is ordinary fields. Change one, call [refresh], and the engine builds the description
 * again and sends it to the player. There is no "update that element over there", so the
 * interface has no way of drifting out of step with the data.
 */
class DemoPage(private val who: String) : Page() {

    private var tab = "stats"
    private var clicks = 0

    // Fills that have to reach the edges of the window even when the screen's shape was
    // guessed a little wrong. Colour only, never content — and every layer the screen style
    // paints, or the strips come out a shade apart from the page and read as a frame.
    override val bleed get() = bleedOf(Theme.scrim)

    override fun view(): View = screen(
        style = Theme.scrim,
        justify = Justify.CENTER,
        align = Align.CENTER,
        children = listOf(
            Panel(
                // Fill the screen, but do not stretch into a bedsheet on an ultrawide.
                width = Size.Fill,
                maxWidth = 900,
                gap = Theme.SPACE_4,
                children = listOf(
                    card(
                        "Hello, $who",
                        icon = "user",
                        trailing = tabs(
                            "tab",
                            listOf("stats" to "Statistics", "about" to "About"),
                            tab,
                        ),
                        children = listOf(
                            if (tab == "stats") stats() else about(),
                            button("Press again", "click", Theme.buttonPrimary, Size.Fixed(260)),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun stats(): View = Grid(
        // Three columns on an ordinary screen, two on a narrow one: the canvas is always 1024
        // tall, so a narrow screen gets narrower cards rather than extra rows.
        columns = viewport.by(compact = 2, regular = 3),
        gap = Theme.SPACE_2,
        children = listOf(
            statTile("Presses", clicks.toString(), "zap", accent = Theme.GOLD),
            statTile("Screen", "${viewport.width}", "grid"),
            statTile("Online", "1", "users"),
        ),
    )

    private fun about(): View = Panel(
        gap = Theme.SPACE_2,
        width = Size.Fill,
        children = listOf(
            Text(
                "This page is drawn by a vanilla client: no mods, no launcher — " +
                    "only the resource pack the server handed out itself.",
                Theme.TEXT_BODY,
                Theme.INK_SOFT,
            ),
            notice("Everything you see is one line of text in an invisible boss bar", Tone.INFO),
        ),
    )

    override fun onClick(id: String, button: Button) {
        when {
            id == "click" -> {
                clicks++
                refresh()
            }

            id.startsWith("tab:") -> {
                tab = id.removePrefix("tab:")
                refresh()
            }
        }
    }
}
