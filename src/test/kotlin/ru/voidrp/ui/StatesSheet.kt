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
import ru.voidrp.ui.widget.button
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
class StatesSheet(private val hover: String? = null) : Page() {

    override val hovered: String? get() = hover

    override fun view(): View = Panel(
        style = Theme.scrim,
        width = Size.Fixed(ru.voidrp.ui.pack.Shaders.CANVAS_WIDTH),
        height = Size.Fixed(ru.voidrp.ui.pack.Shaders.CANVAS_HEIGHT),
        justify = ru.voidrp.ui.layout.Justify.CENTER,
        align = Align.CENTER,
        children = listOf(
            Panel(
                style = Theme.page,
                width = Size.Fixed(1180),
                gap = Theme.SPACE_4,
                children = listOf(
                    row(
                        "кнопки",
                        button("Основная", "b1", Theme.buttonPrimary, Size.Fixed(190)),
                        button("Тихая", "b2", Theme.buttonGhost, Size.Fixed(150)),
                        button("Наведение", "hover:button", Theme.buttonPrimary, Size.Fixed(190)),
                    ),
                    row(
                        "флажок, счётчик",
                        Panel(width = Size.Fixed(230), children = listOf(checkbox("Включено", "c1", true))),
                        Panel(width = Size.Fixed(230), children = listOf(checkbox("Выключено", "c2", false))),
                        Panel(width = Size.Fixed(230), children = listOf(stepper("q", "7"))),
                    ),
                    row(
                        "ползунок",
                        Panel(width = Size.Fixed(230), children = listOf(slider("s0", 0.0))),
                        Panel(width = Size.Fixed(230), children = listOf(slider("s1", 0.5))),
                        Panel(width = Size.Fixed(230), children = listOf(slider("s2", 1.0))),
                    ),
                    row(
                        "полоса и метки",
                        Panel(width = Size.Fixed(230), children = listOf(progress("Пусто", 0.0, "0 / 50"))),
                        Panel(width = Size.Fixed(230), children = listOf(progress("Онлайн", 0.35, "21 / 50"))),
                        Panel(
                            direction = Direction.ROW,
                            gap = Theme.SPACE_2,
                            children = listOf(chip("Обычная"), chip("Акцент", Theme.chipAccent)),
                        ),
                    ),
                    row(
                        "список",
                        Panel(width = Size.Fixed(230), children = listOf(select("m1", MODES, 0, false))),
                        Panel(width = Size.Fixed(230), children = listOf(select("m2", MODES, 1, true))),
                        Panel(width = Size.Fixed(300), children = listOf(tooltipPanel("Алмаз", listOf("120 ₽", "12 шт")))),
                    ),
                    row(
                        "длинный текст и прокрутка",
                        Panel(
                            style = Theme.card,
                            width = Size.Fixed(300),
                            children = listOf(
                                Text(
                                    "Строка, которая заведомо не помещается в свою карточку и обязана оборваться",
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
                                            children = listOf(Text("Строка $it", Theme.TEXT_BODY, Theme.INK_SOFT)),
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
                            children = listOf(Text("Акцентная карточка", Theme.TEXT_LEAD, Theme.INK)),
                        ),
                    ),
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
        val MODES = listOf("Выживание", "Творческий", "Приключение")
    }
}
