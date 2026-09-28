package ru.voidrp.ui.page

import ru.voidrp.ui.input.CursorPrefs
import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Raw
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.TextAlign
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.style.Insets
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.button
import ru.voidrp.ui.widget.eyebrow
import ru.voidrp.ui.widget.screen

/**
 * Where a player sets the pointer up for themselves: how fast it goes, whether their client
 * moves it (smooth) or it is sent frame by frame, and — for the smooth kind — how far their
 * client's clock runs ahead, lined up by eye against a ruler drawn at the top of the screen.
 *
 * Kept per player ([CursorPrefs]); anything left alone follows the server.
 */
class CursorPage(
    private val prefs: CursorPrefs,
    private val id: java.util.UUID,
    /** The server's own settings, for what the player has not changed. */
    private val serverSensitivity: () -> Double,
    private val serverOffset: () -> Double,
    /** Whether this client can move the pointer itself at all: the pack it loaded. */
    private val motionPossible: () -> Boolean,
    /** Whether this client can draw a page in the world: the same. */
    private val worldPossible: () -> Boolean = { false },
    private val done: () -> Unit = {},
    private val say: (String) -> String = { ru.voidrp.ui.Messages.bundled(it) },
) : Page() {

    var isFollowed: Boolean = false

    private val mine get() = prefs.of(id)
    private val sensitivity get() = mine.sensitivity ?: serverSensitivity()
    private val smooth get() = motionPossible() && mine.motion != false
    private val inWorld get() = worldPossible() && mine.world == true
    private val offset get() = mine.clockOffset ?: serverOffset()

    override fun onOpen() {
        session?.clockProbe = smooth
    }

    override fun onClose() {
        session?.clockProbe = false
    }

    override fun view(): View = screen(
        justify = Justify.CENTER,
        align = Align.CENTER,
        children = listOf(
            Raw(Rect(0, 0, viewport.width, viewport.height, Paint(0x05060E, 0.6))),
            Panel(height = Size.Fixed(150)),
            card(),
        ),
    )

    private fun card(): View = Panel(
        style = Theme.card.copy(background = Paint(0x0B0D18, 0.98), padding = Insets.all(Theme.SPACE_5)),
        width = Size.Fixed(760),
        gap = Theme.SPACE_4,
        align = Align.CENTER,
        children = listOfNotNull(
            eyebrow(say("cursor-page.eyebrow")),
            Text(say("cursor-page.title"), Theme.TEXT_H3, Theme.INK, TextFonts.Weight.BOLD, align = TextAlign.CENTER),
            // Experimental, offered only to whoever holds voidrp.ui.world.
            if (worldPossible()) setting(
                say("cursor-page.where"),
                say("cursor-page.where-hint"),
                listOf(
                    button(say("cursor-page.on-screen"), "cur:screen", if (!inWorld) Theme.buttonPrimary else Theme.buttonGhost, height = 40),
                    button(say("cursor-page.in-world"), "cur:world", if (inWorld) Theme.buttonPrimary else Theme.buttonGhost, height = 40),
                ),
            ) else null,
            setting(
                say("cursor-page.speed"),
                say("cursor-page.speed-hint"),
                listOf(
                    button("−", "cur:slower", Theme.buttonGhost, Size.Fixed(48), height = 40),
                    value("%.1f".format(sensitivity)),
                    button("+", "cur:faster", Theme.buttonGhost, Size.Fixed(48), height = 40),
                ),
            ),
            setting(
                say("cursor-page.mode"),
                say(if (motionPossible()) "cursor-page.mode-hint" else "cursor-page.mode-unavailable"),
                listOf(
                    button(say("cursor-page.smooth"), "cur:smooth", if (smooth) Theme.buttonPrimary else Theme.buttonGhost, height = 40),
                    button(say("cursor-page.frames"), "cur:frames", if (!smooth) Theme.buttonPrimary else Theme.buttonGhost, height = 40),
                ),
            ),
            if (smooth) setting(
                say("cursor-page.clock"),
                say("cursor-page.clock-hint"),
                listOf(
                    button("−", "cur:earlier", Theme.buttonGhost, Size.Fixed(48), height = 40),
                    value("%.1f".format(offset)),
                    button("+", "cur:later", Theme.buttonGhost, Size.Fixed(48), height = 40),
                ),
            ) else null,
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                justify = Justify.CENTER,
                width = Size.Fill,
                children = listOf(
                    button(say("cursor-page.done"), "cur:done", Theme.buttonPrimary, Size.Fixed(200)),
                    button(say("cursor-page.server"), "cur:reset", Theme.buttonGhost),
                ),
            ),
        ),
    )

    private fun value(text: String): View = Panel(
        width = Size.Fixed(80),
        align = Align.CENTER,
        children = listOf(Text(text, Theme.TEXT_LEAD, Theme.INK, TextFonts.Weight.BOLD, wrap = false, align = TextAlign.CENTER)),
    )

    /** A row: what it is and a line about it on the left, the controls on the right. */
    private fun setting(title: String, hint: String, controls: List<View>): View = Panel(
        direction = Direction.ROW,
        width = Size.Fill,
        gap = Theme.SPACE_4,
        align = Align.CENTER,
        children = listOf(
            Panel(
                width = Size.Fill,
                gap = 4,
                children = listOf(
                    Text(title, Theme.TEXT_LEAD, Theme.INK, TextFonts.Weight.SEMIBOLD),
                    Text(hint, Theme.TEXT_CAPTION, Theme.INK_DIM),
                ),
            ),
            Panel(direction = Direction.ROW, gap = Theme.SPACE_2, align = Align.CENTER, shrink = false, children = controls),
        ),
    )

    override fun onClick(id: String, button: Button) {
        when (id) {
            "cur:slower" -> prefs.update(this.id) { it.copy(sensitivity = step(sensitivity, -1)) }
            "cur:faster" -> prefs.update(this.id) { it.copy(sensitivity = step(sensitivity, +1)) }
            "cur:screen" -> prefs.update(this.id) { it.copy(world = false) }
            "cur:world" -> if (worldPossible()) prefs.update(this.id) { it.copy(world = true) }
            "cur:smooth" -> prefs.update(this.id) { it.copy(motion = true) }
            "cur:frames" -> prefs.update(this.id) { it.copy(motion = false) }
            "cur:earlier" -> prefs.update(this.id) { it.copy(clockOffset = nudge(offset, -OFFSET_STEP)) }
            "cur:later" -> prefs.update(this.id) { it.copy(clockOffset = nudge(offset, +OFFSET_STEP)) }
            "cur:reset" -> prefs.reset(this.id)
            "cur:done" -> {
                session?.clockProbe = false
                done()
                if (!isFollowed) if (!back()) close()
                return
            }
            else -> return
        }
        session?.clockProbe = smooth
        refresh()
    }

    /** A tenth of the way faster or slower, so the steps feel the same at any speed. */
    private fun step(value: Double, direction: Int): Double {
        val next = if (direction > 0) value * 1.1 else value / 1.1
        return (Math.round(next * 10) / 10.0).coerceIn(CursorPrefs.SENSITIVITY_MIN, CursorPrefs.SENSITIVITY_MAX)
    }

    private fun nudge(value: Double, by: Double): Double =
        (Math.round((value + by) * 10) / 10.0).coerceIn(CursorPrefs.OFFSET_MIN, CursorPrefs.OFFSET_MAX)

    private companion object {
        const val OFFSET_STEP = 0.1
    }
}
