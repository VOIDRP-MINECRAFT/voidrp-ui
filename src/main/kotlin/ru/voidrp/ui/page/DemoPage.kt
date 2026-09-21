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
import ru.voidrp.ui.widget.select
import ru.voidrp.ui.widget.slider
import ru.voidrp.ui.widget.sliderValue
import ru.voidrp.ui.widget.tooltipPanel

/**
 * Everything in one page, so it can be judged by eye: controls, a grid, tooltips, keys.
 *
 * It is written as one function of its own fields — tick a box, the field changes, the
 * page says what it looks like now. Nothing is updated by hand.
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

    override val usesKeys: Boolean get() = true

    override fun view(): View {
        val width = 780

        val page = Panel(
            style = Theme.page,
            width = Size.Fixed(width),
            gap = Theme.SPACE_4,
            children = listOf(
                Panel(
                    gap = 2,
                    children = listOf(
                        Text("VoidRP: Origins", Theme.TEXT_H2, Theme.INK, TextFonts.Weight.SEMIBOLD),
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
                Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),

                Panel(
                    style = Theme.card,
                    width = Size.Fill,
                    gap = Theme.SPACE_2,
                    children = listOf(
                        Text("Награды сезона", Theme.TEXT_CAPTION, Theme.INK_DIM),
                        Grid(
                            columns = 8,
                            gap = Theme.SPACE_2,
                            rowGap = Theme.SPACE_2,
                            width = Size.Fill,
                            children = rewards.map { (item, _) ->
                                Panel(
                                    style = if (hovered == "reward:$item") Theme.cardAccent else Theme.card,
                                    width = Size.Fixed(56),
                                    height = Size.Fixed(56),
                                    justify = Justify.CENTER,
                                    align = Align.CENTER,
                                    id = "reward:$item",
                                    children = listOf(Image(item, 32)),
                                )
                            },
                        ),
                    ),
                ),

                Panel(
                    direction = Direction.ROW,
                    width = Size.Fill,
                    gap = Theme.SPACE_4,
                    align = Align.START,
                    children = listOf(
                        Panel(
                            gap = Theme.SPACE_2,
                            width = Size.Fixed(320),
                            children = listOf(
                                checkbox("Уведомления", "notifications", notifications),
                                Panel(
                                    style = Theme.card,
                                    width = Size.Fill,
                                    gap = Theme.SPACE_2,
                                    children = listOf(
                                        Text("Громкость", Theme.TEXT_CAPTION, Theme.INK_DIM),
                                        slider("volume", volume, width = 272),
                                    ),
                                ),
                            ),
                        ),
                        Panel(
                            gap = Theme.SPACE_2,
                            children = listOf(
                                Text("Режим", Theme.TEXT_CAPTION, Theme.INK_DIM),
                                select("mode", modes, mode, modeOpen, width = 240),
                            ),
                        ),
                        Panel(width = Size.Fill),
                        Panel(
                            style = if (hovered == "note") Theme.cardAccent else Theme.card,
                            gap = 2,
                            id = "note",
                            children = listOf(
                                Text("Заметка", Theme.TEXT_CAPTION, Theme.INK_DIM),
                                Text(note, Theme.TEXT_LEAD, Theme.INK, wrap = false),
                            ),
                        ),
                    ),
                ),

                Panel(
                    direction = Direction.ROW,
                    width = Size.Fill,
                    gap = Theme.SPACE_3,
                    align = Align.CENTER,
                    children = listOf(
                        button("Магазин", "shop", Theme.buttonPrimary, Size.Fixed(180)),
                        button("Закрыть", "close", Theme.buttonGhost, Size.Fixed(150)),
                        Panel(width = Size.Fill),
                        Text(
                            lastKey?.let { "Выбран слот $it" } ?: "Цифры 1–9 — выбор слота",
                            Theme.TEXT_CAPTION,
                            Theme.INK_DIM,
                            wrap = false,
                        ),
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
