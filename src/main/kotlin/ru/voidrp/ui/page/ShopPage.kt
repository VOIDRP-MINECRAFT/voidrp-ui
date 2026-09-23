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
import ru.voidrp.ui.widget.screen
import ru.voidrp.ui.widget.button
import ru.voidrp.ui.widget.eyebrow
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
open class ShopPage : Page() {

    private data class Offer(val item: String, val name: String, val price: Int, val about: String)

    private val offers = listOf(
        Offer("diamond", "Diamond", 120, "Good for tools, armour and an enchanting table."),
        Offer("emerald", "Emerald", 90, "Villager currency. Taken gladly, given reluctantly."),
        Offer("netherite_ingot", "Netherite ingot", 2400, "Does not burn in lava and does not break out of spite."),
        Offer("golden_apple", "Golden apple", 260, "Two minutes of regeneration and five hearts of shield."),
        Offer("enchanted_book", "Enchanted book", 540, "A random enchantment. Luck decides."),
        Offer("totem_of_undying", "Totem of undying", 1800, "One death in your pocket. Hold it in your off hand."),
        Offer("elytra", "Elytra", 3600, "Flight, with rockets. They break quietly and at the wrong moment."),
        Offer("shulker_shell", "Shulker shell", 420, "Two make a box that survives an explosion."),
        Offer("nether_star", "Nether star", 5200, "The heart of a beacon. Dropped by something unwilling to part with it."),
        Offer("trident", "Trident", 2100, "With Loyalty it comes back. Without it, it stays in the ocean."),
        Offer("heart_of_the_sea", "Heart of the sea", 1500, "A conduit, once you find eight nautilus shells."),
        Offer("ancient_debris", "Ancient debris", 980, "Look at depth 15. Explosives are quicker."),
        Offer("blaze_rod", "Blaze rod", 75, "Powder for potions and eyes of ender."),
        Offer("ender_pearl", "Ender pearl", 110, "A teleport for three hearts."),
        Offer("experience_bottle", "Bottle o' enchanting", 60, "Quick levels without the mine."),
        Offer("beacon", "Beacon", 4800, "Speed and strength for everyone nearby — if you can build the pyramid."),
    )

    private val width = 760
    private val inner = width - Theme.SPACE_6 * 2 - 2
    /** Four rows and the gaps between them, so the list never shows a sliver of a fifth. */
    private val rowHeight = 64
    private val listHeight = rowHeight * 4 + Theme.SPACE_2 * 3

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

    /** The page's own wash, carried past the edges of the canvas. See [Page.bleed]. */
    override val bleed = listOfNotNull(Theme.scrim.background as? Paint)

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
                                eyebrow("VoidRP"),
                                Text("Shop", Theme.TEXT_H1, Theme.INK, TextFonts.Weight.BOLD, wrap = false),
                            ),
                        ),
                        Panel(width = Size.Fill),
                        Panel(
                            style = Theme.cardSelected,
                            gap = 2,
                            children = listOf(
                                eyebrow("Balance"),
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
                        eyebrow("Amount"),
                        stepper("amount", amount.toString()),
                        Panel(width = Size.Fill),
                        button("Back", "back", Theme.buttonGhost, Size.Fixed(150)),
                    ),
                ),
            ),
        )

        return screen(
            style = Theme.scrim,
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
                "Price: ${offer.price} · for ${amount}: ${offer.price * amount}",
            ),
        )
    }

    private fun row(offer: Offer) = Panel(
        style = if (hovered == offer.item) Theme.cardAccent else Theme.card,
        width = Size.Fill,
        height = Size.Fixed(rowHeight),
        direction = Direction.ROW,
        gap = Theme.SPACE_3,
        align = Align.CENTER,
        id = offer.item,
        children = listOf(
            Image(offer.item, 32),
            Panel(
                gap = 2,
                width = Size.Fill,
                children = listOf(
                    Text(offer.name, Theme.TEXT_LEAD, Theme.INK, wrap = false),
                    Text(offer.about, Theme.TEXT_CAPTION, Theme.INK_DIM, maxLines = 1),
                ),
            ),
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
                    player.sendMessage("Bought ${offer.name} ×$amount for $total")
                } else {
                    player.sendMessage("Short by ${total - balance}")
                }
            }
        }
        refresh()
    }
}
