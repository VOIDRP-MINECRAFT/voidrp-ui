package ru.voidrp.ui.page

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Raw
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.layout.Viewport
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.style.Border
import ru.voidrp.ui.style.Insets
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.button
import ru.voidrp.ui.widget.eyebrow

/**
 * Where the player tells the server what shape their screen is.
 *
 * The one thing the game will not say. A vanilla client sends its language and its view
 * distance and never the size of its window, so instead of guessing, the player is shown
 * a frame drawn at the width the server believes and asked whether it sits on the edges of
 * their screen. If it does not, they pick another shape and watch the frame move — the
 * page is drawn again at the new width the moment they click, so the answer is visible
 * rather than described.
 *
 * It takes about five seconds and is asked once in a player's life.
 */
class ScreenPage(
    private val choose: (Viewport?) -> Unit,
    /** Where the player goes when they are done — the page they actually asked for. */
    private val done: () -> Unit = {},
) : Page() {

    /** Whether [done] takes the player somewhere, or this page has to close itself. */
    var isFollowed: Boolean = false

    private companion object {
        /** Canvas units a nudge moves the edge by — about eight pixels on a 1080p screen. */
        const val NUDGE = 8
    }

    override fun view(): View {
        val screen = viewport
        val inset = 3
        return Panel(
            width = Size.Fixed(screen.width),
            height = Size.Fixed(screen.height),
            style = Style(background = Paint(0x000000, 0.9)),
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = listOf(frame(screen, inset)) + corners(screen, inset) + listOf(card(screen)),
        )
    }

    /** The frame itself: a hairline all the way round the canvas the page believes in. */
    private fun frame(screen: Viewport, inset: Int): View = Raw(
        ru.voidrp.ui.render.Box(
            inset,
            inset,
            screen.width - inset * 2,
            screen.height - inset * 2,
            Style(border = Border(2, Paint(Theme.VIOLET_SOFT, 0.9))),
        ),
    )

    /** Thicker marks in the four corners, which is where an edge is easiest to judge. */
    private fun corners(screen: Viewport, inset: Int): List<View> {
        val arm = 64
        val thick = 6
        val paint = Paint(Theme.VIOLET_SOFT, 1.0)
        val right = screen.width - inset - arm
        val bottom = screen.height - inset - thick
        return listOf(
            Rect(inset, inset, arm, thick, paint),
            Rect(inset, inset, thick, arm, paint),
            Rect(right, inset, arm, thick, paint),
            Rect(screen.width - inset - thick, inset, thick, arm, paint),
            Rect(inset, bottom, arm, thick, paint),
            Rect(inset, screen.height - inset - arm, thick, arm, paint),
            Rect(right, bottom, arm, thick, paint),
            Rect(screen.width - inset - thick, screen.height - inset - arm, thick, arm, paint),
        ).map { Raw(it) }
    }

    private fun card(screen: Viewport): View = Panel(
        style = Theme.card.copy(padding = Insets.all(Theme.SPACE_5)),
        width = Size.Fixed(620),
        gap = Theme.SPACE_3,
        align = Align.CENTER,
        children = listOf(
            eyebrow("Настройка экрана"),
            Text(
                "Рамка должна лежать точно по краям экрана",
                Theme.TEXT_LEAD,
                Theme.INK,
                TextFonts.Weight.BOLD,
                align = ru.voidrp.ui.layout.TextAlign.CENTER,
            ),
            Text(
                "Игра не сообщает серверу размер окна, поэтому формат выбираете вы. " +
                    "Нажимайте варианты, пока рамка не сядет по краям — страница сразу " +
                    "перерисуется под него.",
                Theme.TEXT_BODY,
                Theme.INK_SOFT,
                align = ru.voidrp.ui.layout.TextAlign.CENTER,
            ),
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                justify = Justify.CENTER,
                align = Align.CENTER,
                width = Size.Fill,
                children = Viewport.PRESETS.map { (name, preset) ->
                    button(
                        name,
                        "screen:$name",
                        if (preset.width == screen.width) Theme.buttonPrimary else Theme.buttonGhost,
                        height = 38,
                    )
                },
            ),
            // No window is exactly a named format: a title bar and a task bar take a slice
            // out of the height, so a maximised 1920×1080 screen is nearer 1.89 than 1.78.
            // These two put the frame on the edge exactly.
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                justify = Justify.CENTER,
                align = Align.CENTER,
                width = Size.Fill,
                children = listOf(
                    button("← уже", "screen:narrower", Theme.buttonGhost, height = 34),
                    Text(
                        "${Viewport.name(screen)} · ${screen.width}×${screen.height}",
                        Theme.TEXT_CAPTION,
                        Theme.INK_DIM,
                        wrap = false,
                    ),
                    button("шире →", "screen:wider", Theme.buttonGhost, height = 34),
                ),
            ),
            Text(
                "Точная подгонка — по 8 единиц за нажатие",
                Theme.TEXT_CAPTION,
                Theme.INK_DIM,
                align = ru.voidrp.ui.layout.TextAlign.CENTER,
            ),
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                justify = Justify.CENTER,
                width = Size.Fill,
                children = listOf(
                    button("Готово", "screen:done", Theme.buttonPrimary),
                    button("Как на сервере", "screen:auto", Theme.buttonGhost),
                ),
            ),
        ),
    )

    override fun onClick(id: String, button: Button) {
        when {
            id == "screen:done" -> {
                done()
                if (!isFollowed) if (!back()) close()
            }
            id == "screen:auto" -> {
                choose(null)
                refresh()
            }

            id == "screen:narrower" -> {
                choose(Viewport((viewport.width - NUDGE).coerceAtLeast(640)))
                refresh()
            }

            id == "screen:wider" -> {
                choose(Viewport((viewport.width + NUDGE).coerceAtMost(4096)))
                refresh()
            }

            id.startsWith("screen:") -> {
                Viewport.PRESETS[id.removePrefix("screen:")]?.let { choose(it) }
                refresh()
            }
        }
    }
}
