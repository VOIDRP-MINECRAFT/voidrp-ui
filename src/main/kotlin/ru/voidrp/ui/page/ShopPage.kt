package ru.voidrp.ui.page

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Image
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Scroll
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme

/**
 * A long list in a short window: what a shop, a leaderboard or a quest log is made of.
 *
 * The wheel changes a number and the page draws itself again, which is all scrolling is
 * here — the list has no state of its own to fall out of step with.
 */
class ShopPage : Page() {

    private data class Offer(val item: String, val name: String, val price: Int)

    private val offers = listOf(
        Offer("diamond", "Алмаз", 120),
        Offer("emerald", "Изумруд", 90),
        Offer("netherite_ingot", "Незеритовый слиток", 2400),
        Offer("golden_apple", "Золотое яблоко", 260),
        Offer("enchanted_book", "Зачарованная книга", 540),
        Offer("totem_of_undying", "Тотем бессмертия", 1800),
        Offer("elytra", "Элитры", 3600),
        Offer("shulker_shell", "Панцирь шалкера", 420),
        Offer("nether_star", "Звезда Ада", 5200),
        Offer("trident", "Трезубец", 2100),
        Offer("heart_of_the_sea", "Сердце моря", 1500),
        Offer("ancient_debris", "Древние обломки", 980),
        Offer("blaze_rod", "Огненный стержень", 75),
        Offer("ender_pearl", "Жемчуг Эндера", 110),
        Offer("experience_bottle", "Пузырёк опыта", 60),
        Offer("beacon", "Маяк", 4800),
    )

    private var offset = 0
    private var balance = 12_400

    override fun view(): View {
        val width = 720
        val inner = width - Theme.SPACE_6 * 2 - 2

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
                                Text("Колесо мыши — прокрутка", Theme.TEXT_CAPTION, Theme.INK_DIM),
                            ),
                        ),
                        Panel(width = Size.Fill),
                        Panel(
                            style = Theme.cardAccent,
                            gap = 2,
                            children = listOf(
                                Text("Баланс", Theme.TEXT_CAPTION, Theme.INK_DIM),
                                Text("$balance ₽", Theme.TEXT_H3, Theme.INK, TextFonts.Weight.SEMIBOLD),
                            ),
                        ),
                    ),
                ),
                Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),
                Scroll(
                    width = Size.Fixed(inner),
                    height = Size.Fixed(360),
                    gap = Theme.SPACE_2,
                    offset = offset,
                    children = offers.map { row(it) },
                ),
                Text("Shift — назад · void-rp.ru", Theme.TEXT_CAPTION, Theme.INK_DIM),
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
                children = listOf(
                    Text(offer.name, Theme.TEXT_LEAD, Theme.INK),
                    Text("в наличии", Theme.TEXT_CAPTION, Theme.INK_DIM),
                ),
            ),
            Panel(width = Size.Fill),
            Text("${offer.price} ₽", Theme.TEXT_H3, Theme.GOLD, TextFonts.Weight.SEMIBOLD),
        ),
    )

    override fun onScroll(direction: Int) {
        offset = (offset + direction * 48).coerceAtLeast(0)
        refresh()
    }

    override fun onClick(id: String, button: Button) {
        val offer = offers.firstOrNull { it.item == id } ?: return
        if (balance >= offer.price) {
            balance -= offer.price
            player.sendMessage("Куплено: ${offer.name} за ${offer.price} ₽")
        } else {
            player.sendMessage("Не хватает ${offer.price - balance} ₽")
        }
        refresh()
    }
}
