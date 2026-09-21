package ru.voidrp.ui.page

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Grid
import ru.voidrp.ui.layout.Image
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.RichText
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Span
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.pack.TextFonts
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
import ru.voidrp.ui.widget.sliderValue
import ru.voidrp.ui.widget.tooltipPanel

/**
 * The library's own front page, set the way the site is set: a tracked-out label above a
 * heavy heading, pills for the facts, a thin bar for a number out of a number, and one
 * violet button that is the thing to press.
 *
 * It is written as one function of its own fields — tick a box, the field changes, the
 * page says what it looks like now.
 */
class DemoPage : Page() {

    private val rewards = listOf(
        "diamond" to "Алмаз",
        "emerald" to "Изумруд",
        "golden_apple" to "Золотое яблоко",
        "netherite_ingot" to "Незерит",
        "totem_of_undying" to "Тотем",
        "enchanted_book" to "Книга",
        "elytra" to "Элитры",
        "beacon" to "Маяк",
    )

    private val modes = listOf("Выживание", "Творческий", "Приключение")

    private var notifications = true
    private var mode = 0
    private var modeOpen = false
    private var volume = 0.7
    private var note = "нажмите, чтобы ввести"
    private var lastKey: Int? = null
    private var claimed: String? = null

    override val usesKeys: Boolean get() = true

    override fun view(): View {
        val width = 720
        val inner = width - Theme.SPACE_6 * 2 - 2

        val page = Panel(
            style = Theme.page,
            width = Size.Fixed(width),
            gap = Theme.SPACE_5,
            children = listOf(
                header(),
                Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),
                progress("Игроков онлайн", 0.42, "21 / 50"),
                seasonRewards(inner),
                settings(),
                Panel(
                    direction = Direction.ROW,
                    width = Size.Fill,
                    gap = Theme.SPACE_3,
                    align = Align.CENTER,
                    children = listOf(
                        button("Открыть магазин", "shop", Theme.buttonPrimary, Size.Fill),
                        button("Закрыть", "close", Theme.buttonGhost, Size.Fixed(150)),
                    ),
                ),
                Panel(
                    direction = Direction.ROW,
                    width = Size.Fill,
                    align = Align.CENTER,
                    children = listOf(
                        eyebrow(lastKey?.let { "выбран слот $it" } ?: "цифры 1–9 — выбор слота"),
                        Panel(width = Size.Fill),
                        eyebrow("void-rp.ru"),
                    ),
                ),
            ),
        )

        return Panel(
            width = Size.Fixed(Shaders.CANVAS_WIDTH),
            height = Size.Fixed(Shaders.CANVAS_HEIGHT),
            style = Style(background = Paint(0x05060D, 0.93)),
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = listOf(page),
        )
    }

    private fun header() = Panel(
        direction = Direction.ROW,
        width = Size.Fill,
        align = Align.CENTER,
        children = listOf(
            Panel(
                gap = 4,
                children = listOf(
                    eyebrow("VoidRP", Theme.VIOLET_SOFT),
                    Text("Origins", Theme.TEXT_H1, Theme.INK, TextFonts.Weight.BOLD, wrap = false),
                    RichText(
                        spans = listOf(
                            Span("Прицел — курсор, "),
                            Span("ЛКМ", Theme.INK, TextFonts.Weight.SEMIBOLD),
                            Span(" — нажать, "),
                            Span("Shift", Theme.INK, TextFonts.Weight.SEMIBOLD),
                            Span(" — назад"),
                        ),
                        colour = Theme.INK_SOFT,
                    ),
                ),
            ),
            Panel(width = Size.Fill),
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                align = Align.CENTER,
                children = listOf(
                    chip("MC 26.2"),
                    chip("paper"),
                    chip("Открытый", Theme.chipAccent),
                ),
            ),
        ),
    )

    private fun seasonRewards(inner: Int) = Panel(
        style = Theme.card,
        width = Size.Fill,
        gap = Theme.SPACE_3,
        children = listOf(
            Panel(
                direction = Direction.ROW,
                width = Size.Fill,
                align = Align.CENTER,
                children = listOf(
                    eyebrow("Награды сезона"),
                    Panel(width = Size.Fill),
                    eyebrow(claimed?.let { "забрано: $it" } ?: "наведите на предмет"),
                ),
            ),
            Grid(
                columns = 8,
                gap = Theme.SPACE_2,
                rowGap = Theme.SPACE_2,
                width = Size.Fill,
                children = rewards.map { (item, _) ->
                    val id = "reward:$item"
                    Panel(
                        style = when {
                            claimed == item -> Theme.cardSelected
                            hovered == id -> Theme.cardAccent
                            else -> Theme.card
                        },
                        width = Size.Fixed(56),
                        height = Size.Fixed(56),
                        justify = Justify.CENTER,
                        align = Align.CENTER,
                        id = id,
                        children = listOf(Image(item, 32)),
                    )
                },
            ),
        ),
    )

    private fun settings() = Panel(
        direction = Direction.ROW,
        width = Size.Fill,
        gap = Theme.SPACE_3,
        align = Align.START,
        children = listOf(
            Panel(
                gap = Theme.SPACE_2,
                width = Size.Fixed(300),
                children = listOf(
                    checkbox("Уведомления", "notifications", notifications, width = Size.Fill),
                    Panel(
                        style = Theme.card,
                        width = Size.Fill,
                        gap = Theme.SPACE_2,
                        children = listOf(
                            eyebrow("Громкость"),
                            slider("volume", volume),
                        ),
                    ),
                ),
            ),
            Panel(
                gap = Theme.SPACE_2,
                width = Size.Fixed(200),
                children = listOf(
                    eyebrow("Режим"),
                    select("mode", modes, mode, modeOpen),
                ),
            ),
            Panel(
                style = if (hovered == "note") Theme.cardAccent else Theme.card,
                width = Size.Fill,
                gap = 4,
                id = "note",
                children = listOf(
                    eyebrow("Заметка"),
                    Text(note, Theme.TEXT_LEAD, Theme.INK, wrap = false),
                ),
            ),
        ),
    )

    override fun tooltip(): View? {
        val hovered = hovered ?: return null
        if (!hovered.startsWith("reward:")) return null
        val reward = rewards.firstOrNull { it.first == hovered.removePrefix("reward:") } ?: return null
        return tooltipPanel(reward.second, listOf("Награда сезона", "Выдаётся за уровень пропуска"))
    }

    override fun onClick(id: String, button: Button) {
        when {
            id == "close" -> {
                close()
                return
            }

            id == "shop" -> {
                push(ShopPage())
                return
            }

            id.startsWith("reward:") -> claimed = id.removePrefix("reward:")
            id == "notifications" -> notifications = !notifications
            id == "mode" -> modeOpen = !modeOpen
            id.startsWith("mode:option:") -> {
                mode = id.removePrefix("mode:option:").toIntOrNull() ?: mode
                modeOpen = false
            }

            id == "volume" -> volume = sliderValue("volume")

            id == "note" -> {
                prompt(
                    title = "Заметка",
                    label = "Текст",
                    initial = note,
                    hint = "Поле ввода — родное окно игры: страница остаётся на экране.",
                    maxLength = 48,
                ) { value ->
                    note = value.ifBlank { "пусто" }
                    refresh()
                }
                return
            }
        }
        refresh()
    }

    /** Dragging the slider is clicking it, repeatedly. */
    override fun onDrag(id: String, x: Int, y: Int) {
        if (id != "volume") return
        volume = sliderValue("volume")
        refresh()
    }

    override fun onKey(key: Int) {
        lastKey = key
        refresh()
    }
}
