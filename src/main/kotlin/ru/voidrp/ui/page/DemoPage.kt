package ru.voidrp.ui.page

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Image
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme

/**
 * A page that answers back: the buttons light up under the cursor and do something when
 * clicked. It is written as one function of its own state, so "the bar moved" is a field
 * changing and a redraw, not a redraw written by hand.
 */
class DemoPage : Page() {

    private var progress = 0.4
    private var clicks = 0
    private var note = "нажмите, чтобы ввести"

    override fun view(): View {
        val width = 760
        val inner = width - Theme.SPACE_6 * 2 - 2

        val page = Panel(
            style = Theme.page,
            width = Size.Fixed(width),
            gap = Theme.SPACE_4,
            children = listOf(
                Panel(
                    gap = 2,
                    children = listOf(
                        Text("VoidRP: Origins", Theme.TEXT_H2, Theme.INK, TextFonts.Weight.SEMIBOLD),
                        Text("Наведите прицел и нажмите", Theme.TEXT_BODY, Theme.INK_SOFT),
                    ),
                ),
                Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),
                Text(
                    "Страница живёт на сервере: всё, что вы видите, нарисовал обычный клиент " +
                        "без единого мода. Текст переносится сам, по ширине того, во что его положили.",
                    Theme.TEXT_BODY,
                    Theme.INK_SOFT,
                ),
                stat("Игроков онлайн", "42 из 200", Theme.card, "stat-online"),
                stat("Нажатий", clicks.toString(), Theme.cardAccent, "stat-clicks"),
                // Item pictures come from the client's own textures, so they cost the pack
                // nothing at all.
                Panel(
                    style = Theme.card,
                    width = Size.Fill,
                    gap = Theme.SPACE_2,
                    children = listOf(
                        Text("Награды сезона", Theme.TEXT_CAPTION, Theme.INK_DIM),
                        Panel(
                            direction = Direction.ROW,
                            gap = Theme.SPACE_3,
                            align = Align.CENTER,
                            children = listOf(
                                Image("diamond", 32),
                                Image("netherite_ingot", 32),
                                Image("golden_apple", 32),
                                Image("emerald", 32),
                                Image("enchanted_book", 32),
                                Image("totem_of_undying", 32),
                                Text("и ещё 12", Theme.TEXT_BODY, Theme.INK_SOFT),
                            ),
                        ),
                    ),
                ),
                Panel(
                    style = Style(background = Paint(Theme.LINE, 0.14), radius = 6),
                    width = Size.Fill,
                    height = Size.Fixed(12),
                    children = listOf(
                        Panel(
                            style = Style(background = Theme.accentBar, radius = 6),
                            width = Size.Fixed((inner * progress).toInt().coerceAtLeast(12)),
                            height = Size.Fill,
                        )
                    ),
                ),
                Panel(
                    direction = Direction.ROW,
                    gap = Theme.SPACE_3,
                    width = Size.Fill,
                    children = listOf(
                        button("Добавить", Theme.buttonPrimary, "add"),
                        button("Сбросить", Theme.buttonGhost, "reset"),
                        Panel(width = Size.Fill),
                        button("Магазин", Theme.buttonGhost, "shop"),
                        button("Закрыть", Theme.buttonGhost, "close"),
                    ),
                ),
                Panel(
                    style = Theme.card,
                    width = Size.Fill,
                    gap = 2,
                    id = "note",
                    children = listOf(
                        Text("Заметка", Theme.TEXT_CAPTION, Theme.INK_DIM),
                        Text(note, Theme.TEXT_LEAD, Theme.INK),
                    ),
                ),
                Text("Колесо мыши — шкала · Shift — закрыть · void-rp.ru", Theme.TEXT_CAPTION, Theme.INK_DIM),
            ),
        )

        return Panel(
            width = Size.Fixed(Shaders.CANVAS_WIDTH),
            height = Size.Fixed(Shaders.CANVAS_HEIGHT),
            // Nearly opaque on purpose: aiming turns the player's head, and behind a solid
            // backdrop that is invisible.
            style = Style(background = Paint(0x05060D, 0.93)),
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = listOf(page),
        )
    }

    override fun onClick(id: String, button: Button) {
        when (id) {
            "add" -> {
                clicks++
                progress = (progress + 0.1).coerceAtMost(1.0)
            }

            "reset" -> {
                clicks = 0
                progress = 0.0
            }

            "close" -> {
                close()
                return
            }

            "shop" -> {
                push(ShopPage())
                return
            }

            "note" -> {
                prompt(
                    title = "Заметка",
                    label = "Текст",
                    initial = note,
                    hint = "Поле ввода — это родное окно игры: страница остаётся на экране.",
                    maxLength = 64,
                ) { value ->
                    note = value.ifBlank { "пусто" }
                    refresh()
                }
                return
            }
        }
        refresh()
    }

    override fun onScroll(direction: Int) {
        progress = (progress - direction * 0.05).coerceIn(0.0, 1.0)
        refresh()
    }

    private fun stat(caption: String, value: String, style: Style, id: String) = Panel(
        style = if (hovered == id) style.copy(border = style.border?.copy(paint = Paint(Theme.VIOLET, 0.5))) else style,
        width = Size.Fill,
        gap = 2,
        id = id,
        children = listOf(
            Text(caption, Theme.TEXT_CAPTION, Theme.INK_DIM),
            Text(value, Theme.TEXT_H3, Theme.INK, TextFonts.Weight.SEMIBOLD),
        ),
    )

    /** A button knows it is being pointed at, which is all "hover" ever was. */
    private fun button(caption: String, style: Style, id: String) = Panel(
        style = if (hovered == id) style.copy(background = (style.background as? Paint)?.let { it.alpha(minOf(1.0, it.alpha + 0.15)) } ?: style.background) else style,
        width = Size.Fixed(165),
        height = Size.Fixed(48),
        justify = Justify.CENTER,
        align = Align.CENTER,
        id = id,
        children = listOf(Text(caption, style.textSize, style.textColour, style.textWeight)),
    )
}
