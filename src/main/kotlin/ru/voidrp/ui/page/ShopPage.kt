package ru.voidrp.ui.page

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Image
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.RichText
import ru.voidrp.ui.layout.Scroll
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
import ru.voidrp.ui.widget.scrollFromBar
import ru.voidrp.ui.widget.stepper
import ru.voidrp.ui.widget.tooltipPanel

/**
 * A shop: a long list in a short window, with what is under the cursor explained beside it.
 *
 * It exercises most of what the library can do — a scrolling list whose bar can be grabbed,
 * tooltips that follow the pointer, an amount to pick, prices set in two colours on one
 * line — and it is written the same way any page is: one function of the state above it.
 */
class ShopPage : Page() {

    private data class Offer(val item: String, val name: String, val price: Int, val about: String)

    private val offers = listOf(
        Offer("diamond", "Алмаз", 120, "Годится на инструменты, броню и стол зачарования."),
        Offer("emerald", "Изумруд", 90, "Валюта жителей. Берут охотно, дают неохотно."),
        Offer("netherite_ingot", "Незеритовый слиток", 2400, "Не горит в лаве и не ломается об обиду."),
        Offer("golden_apple", "Золотое яблоко", 260, "Регенерация на две минуты и щит на пять сердец."),
        Offer("enchanted_book", "Зачарованная книга", 540, "Случайное зачарование. Удача решает."),
        Offer("totem_of_undying", "Тотем бессмертия", 1800, "Одна смерть в кармане. Держите во второй руке."),
        Offer("elytra", "Элитры", 3600, "Полёт с фейерверками. Ломаются тихо и не вовремя."),
        Offer("shulker_shell", "Панцирь шалкера", 420, "Два на ящик, который переживает взрыв."),
        Offer("nether_star", "Звезда Ада", 5200, "Сердце маяка. Выпадает из того, кто не хочет отдавать."),
        Offer("trident", "Трезубец", 2100, "С Верностью возвращается. Без неё — остаётся в океане."),
        Offer("heart_of_the_sea", "Сердце моря", 1500, "Проводник, если найдёте восемь панцирей наутилуса."),
        Offer("ancient_debris", "Древние обломки", 980, "Ищите на глубине 15. Взрывчаткой быстрее."),
        Offer("blaze_rod", "Огненный стержень", 75, "Порошок для зелий и глаз Эндера."),
        Offer("ender_pearl", "Жемчуг Эндера", 110, "Телепорт ценой трёх сердец."),
        Offer("experience_bottle", "Пузырёк опыта", 60, "Быстрые уровни без шахты."),
        Offer("beacon", "Маяк", 4800, "Скорость и сила всем в округе — если есть чем застроить пирамиду."),
    )

    private val width = 760
    private val inner = width - Theme.SPACE_6 * 2 - 2
    private val listHeight = 340

    private var offset = 0
    private var amount = 1
    private var balance = 12_400

    private fun list() = Scroll(
        width = Size.Fixed(inner),
        height = Size.Fixed(listHeight),
        gap = Theme.SPACE_2,
        offset = offset,
        id = "list",
        children = offers.map { row(it) },
    )

    override fun view(): View {
        val page = Panel(
            style = Theme.page,
            width = Size.Fixed(width),
            gap = Theme.SPACE_4,
            children = listOf(
                Panel(
                    direction = Direction.ROW,
                    width = Size.Fill,
                    align = Align.CENTER,
                    children = listOf(
                        Panel(
                            gap = 2,
                            children = listOf(
                                Text("Магазин", Theme.TEXT_H2, Theme.INK, TextFonts.Weight.SEMIBOLD),
                                Text("Колесо или полоса справа — прокрутка", Theme.TEXT_CAPTION, Theme.INK_DIM),
                            ),
                        ),
                        Panel(width = Size.Fill),
                        Panel(
                            style = Theme.cardAccent,
                            gap = 2,
                            children = listOf(
                                Text("Баланс", Theme.TEXT_CAPTION, Theme.INK_DIM),
                                RichText(
                                    spans = listOf(
                                        Span(balance.toString(), Theme.INK, TextFonts.Weight.SEMIBOLD),
                                        Span(" ₽", Theme.INK_DIM),
                                    ),
                                    size = Theme.TEXT_H3,
                                ),
                            ),
                        ),
                    ),
                ),
                Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),
                list(),
                Panel(
                    direction = Direction.ROW,
                    width = Size.Fill,
                    gap = Theme.SPACE_3,
                    align = Align.CENTER,
                    children = listOf(
                        Text("Количество", Theme.TEXT_BODY, Theme.INK_SOFT, wrap = false),
                        stepper("amount", amount.toString()),
                        Panel(width = Size.Fill),
                        button("Назад", "back", Theme.buttonGhost, Size.Fixed(150)),
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

    /** What the cursor is over, explained — the reason a shop needs tooltips at all. */
    override fun tooltip(): View? {
        val offer = offers.firstOrNull { it.item == hovered } ?: return null
        return tooltipPanel(
            offer.name,
            listOf(
                offer.about,
                "Цена: ${offer.price} ₽ · за ${amount} шт.: ${offer.price * amount} ₽",
            ),
        )
    }

    private fun row(offer: Offer) = Panel(
        style = if (hovered == offer.item) Theme.cardAccent else Theme.card,
        width = Size.Fill,
        direction = Direction.ROW,
        gap = Theme.SPACE_3,
        align = Align.CENTER,
        id = offer.item,
        children = listOf(
            Image(offer.item, 32),
            Panel(
                gap = 2,
                width = Size.Fixed(360),
                children = listOf(
                    Text(offer.name, Theme.TEXT_LEAD, Theme.INK, wrap = false),
                    Text(offer.about, Theme.TEXT_CAPTION, Theme.INK_DIM, maxLines = 1),
                ),
            ),
            Panel(width = Size.Fill),
            RichText(
                spans = listOf(
                    Span(offer.price.toString(), Theme.GOLD, TextFonts.Weight.SEMIBOLD),
                    Span(" ₽", Theme.INK_DIM),
                ),
                size = Theme.TEXT_H3,
            ),
        ),
    )

    override fun onScroll(direction: Int) {
        offset = (offset + direction * 48).coerceAtLeast(0)
        refresh()
    }

    /** Grabbing the bar: the page is told where the cursor is and moves the list to match. */
    override fun onDrag(id: String, x: Int, y: Int) {
        if (!id.startsWith("list:")) return
        offset = scrollFromBar(list(), "list", inner, listHeight)
        refresh()
    }

    override fun onClick(id: String, button: Button) {
        when {
            id == "back" -> {
                back()
                return
            }

            id == "amount:-" -> amount = (amount - 1).coerceAtLeast(1)
            id == "amount:+" -> amount = (amount + 1).coerceAtMost(64)
            id.startsWith("list:") -> {
                offset = scrollFromBar(list(), "list", inner, listHeight)
            }

            else -> {
                val offer = offers.firstOrNull { it.item == id } ?: return
                val total = offer.price * amount
                if (balance >= total) {
                    balance -= total
                    player.sendMessage("Куплено: ${offer.name} ×$amount за $total ₽")
                } else {
                    player.sendMessage("Не хватает ${total - balance} ₽")
                }
            }
        }
        refresh()
    }
}
