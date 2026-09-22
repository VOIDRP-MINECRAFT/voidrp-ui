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
import ru.voidrp.ui.widget.screen
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
        return screen(
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = marks(screen) + listOf(card(screen)),
        )
    }

    /**
     * The frame, and the brackets at its corners.
     *
     * Deliberately drawn over the world rather than over a wash of our own: the player is
     * being asked where the edge of their screen is, and the world showing outside the
     * frame is what tells them the canvas does not reach it yet.
     */
    private fun marks(screen: Viewport): List<View> {
        val line = Paint(Theme.VIOLET_SOFT, 0.45)
        val bracket = Paint(Theme.VIOLET_SOFT, 1.0)
        val thin = 1
        val thick = 4
        val arm = 72
        val right = screen.width - thin
        val bottom = screen.height - thin
        val shapes = listOf(
            // The canvas, dimmed. It is the strongest signal on the page: everything
            // inside the canvas is darker than the world, so the player is lining up a
            // shaded rectangle with their screen rather than hunting for a hairline.
            Rect(0, 0, screen.width, screen.height, Paint(0x05060E, 0.55)),
            // The frame itself, a hairline right on the edge of the canvas.
            Rect(0, 0, screen.width, thin, line),
            Rect(0, bottom, screen.width, thin, line),
            Rect(0, 0, thin, screen.height, line),
            Rect(right, 0, thin, screen.height, line),
            // Brackets, which is where an edge is easiest to judge by eye.
            Rect(0, 0, arm, thick, bracket),
            Rect(0, 0, thick, arm, bracket),
            Rect(screen.width - arm, 0, arm, thick, bracket),
            Rect(screen.width - thick, 0, thick, arm, bracket),
            Rect(0, screen.height - thick, arm, thick, bracket),
            Rect(0, screen.height - arm, thick, arm, bracket),
            Rect(screen.width - arm, screen.height - thick, arm, thick, bracket),
            Rect(screen.width - thick, screen.height - arm, thick, arm, bracket),
        )
        return shapes.map { Raw(it) }
    }

    private fun card(screen: Viewport): View = Panel(
        // Nearly opaque: this card is read over whatever the player happens to be
        // standing in front of, which may be a snowfield at noon.
        style = Theme.card.copy(
            background = Paint(0x0B0D18, 0.98),
            padding = Insets.all(Theme.SPACE_5),
        ),
        width = Size.Fixed(660),
        gap = Theme.SPACE_3,
        align = Align.CENTER,
        children = listOf(
            eyebrow("Шаг 1 из 1 · настройка экрана"),
            Text(
                "Подгоните рамку под края экрана",
                Theme.TEXT_H3,
                Theme.INK,
                TextFonts.Weight.BOLD,
                align = ru.voidrp.ui.layout.TextAlign.CENTER,
            ),
            Text(
                "Размер окна игра серверу не сообщает, поэтому формат задаёте вы — один раз. " +
                    "Уголки должны сойтись с углами экрана.",
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
                        height = 40,
                    )
                },
            ),
            // No window is exactly a named format: a title bar and a task bar take a slice
            // out of the height, so a maximised 1920×1080 screen is nearer 1.89 than 1.78.
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_3,
                justify = Justify.CENTER,
                align = Align.CENTER,
                width = Size.Fill,
                children = listOf(
                    button("←  уже", "screen:narrower", Theme.buttonGhost, Size.Fixed(120), height = 40),
                    Panel(
                        width = Size.Fixed(190),
                        align = Align.CENTER,
                        gap = 2,
                        children = listOf(
                            Text(
                                Viewport.name(screen),
                                Theme.TEXT_LEAD,
                                Theme.INK,
                                TextFonts.Weight.BOLD,
                                wrap = false,
                                align = ru.voidrp.ui.layout.TextAlign.CENTER,
                            ),
                            Text(
                                "${screen.width} × ${screen.height}",
                                Theme.TEXT_CAPTION,
                                Theme.INK_DIM,
                                wrap = false,
                                align = ru.voidrp.ui.layout.TextAlign.CENTER,
                            ),
                        ),
                    ),
                    button("шире  →", "screen:wider", Theme.buttonGhost, Size.Fixed(120), height = 40),
                ),
            ),
            Text(
                "Рамки не видно? Значит экран уже, чем думает сервер — жмите «уже».",
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
                    button("Готово", "screen:done", Theme.buttonPrimary, Size.Fixed(200)),
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
