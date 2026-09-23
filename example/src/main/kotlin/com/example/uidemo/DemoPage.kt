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
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.Tone
import ru.voidrp.ui.widget.button
import ru.voidrp.ui.widget.card
import ru.voidrp.ui.widget.notice
import ru.voidrp.ui.widget.screen
import ru.voidrp.ui.widget.statTile
import ru.voidrp.ui.widget.tabs

/**
 * Страница отвечает на один вопрос: как я выгляжу прямо сейчас.
 *
 * Состояние — обычные поля класса. Поменяли поле, позвали [refresh] — движок собрал
 * описание заново и отправил игроку. Никакого «обновить вон тот элемент» нет, поэтому
 * разойтись с данными интерфейсу нечем.
 */
class DemoPage(private val who: String) : Page() {

    private var tab = "stats"
    private var clicks = 0

    // Заливки, которые обязаны доставать до краёв окна, даже если формат экрана угадан
    // неточно. Содержимое туда не выносим — только цвет.
    override val bleed = listOf(Paint(0x000000, 0.9))

    override fun view(): View = screen(
        style = Theme.scrim,
        justify = Justify.CENTER,
        align = Align.CENTER,
        children = listOf(
            Panel(
                // Заполнить экран, но не растягиваться в простыню на ультравайде.
                width = Size.Fill,
                maxWidth = 900,
                gap = Theme.SPACE_4,
                children = listOf(
                    card(
                        "Привет, $who",
                        icon = "user",
                        trailing = tabs(
                            "tab",
                            listOf("stats" to "Статистика", "about" to "О сервере"),
                            tab,
                        ),
                        children = listOf(
                            if (tab == "stats") stats() else about(),
                            button("Нажать ещё раз", "click", Theme.buttonPrimary, Size.Fixed(260)),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun stats(): View = Grid(
        // Три колонки на обычном экране, две на узком: холст всегда 1024 в высоту, так что
        // на узком экране карточки делают уже, а не переносят в лишние ряды.
        columns = viewport.by(compact = 2, regular = 3),
        gap = Theme.SPACE_2,
        children = listOf(
            statTile("Нажатий", clicks.toString(), "zap", accent = Theme.GOLD),
            statTile("Экран", "${viewport.width}", "grid"),
            statTile("Онлайн", "1", "users"),
        ),
    )

    private fun about(): View = Panel(
        gap = Theme.SPACE_2,
        width = Size.Fill,
        children = listOf(
            Text(
                "Эта страница нарисована ванильным клиентом: ни модов, ни лаунчера — " +
                    "только ресурспак, который сервер выдал сам.",
                Theme.TEXT_BODY,
                Theme.INK_SOFT,
            ),
            notice("Всё, что вы видите, — одна строка текста в невидимом босс-баре", Tone.INFO),
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
