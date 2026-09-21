package ru.voidrp.ui.page

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
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
                stat("Игроков онлайн", "42 из 200", Theme.card, "stat-online"),
                stat("Нажатий", clicks.toString(), Theme.cardAccent, "stat-clicks"),
                Panel(
                    style = Style(background = Paint(Theme.LINE, 0.14), radius = 6),
                    width = Size.Fill,
                    height = Size.Fixed(12),
                    children = listOf(
                        Panel(
                            style = Style(background = Paint(Theme.VIOLET, 0.95), radius = 6),
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
                        button("Закрыть", Theme.buttonGhost, "close"),
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
        style = if (hovered == id) style.copy(background = style.background?.let { it.alpha(minOf(1.0, it.alpha + 0.15)) }) else style,
        width = Size.Fixed(190),
        height = Size.Fixed(48),
        justify = Justify.CENTER,
        align = Align.CENTER,
        id = id,
        children = listOf(Text(caption, style.textSize, style.textColour, style.textWeight)),
    )
}
