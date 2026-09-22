package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Viewport

/**
 * The canvas a page is laid out on, and the one number the game will not tell us.
 *
 * Everything a player types goes through [Viewport.parse]: a format off their settings
 * screen, a resolution, or a nudge in units. A wrong answer here is a page that does not
 * fit anybody's screen, so it is worth pinning down.
 */
class ViewportTest {

    @Test
    fun `a screen is read however the player spells it`() {
        assertEquals(1820, Viewport.parse("16:9")!!.width, "16:9")
        assertEquals(1820, Viewport.parse("1920x1080")!!.width, "то же самое разрешением")
        assertEquals(1820, Viewport.parse(" 16 : 9 ")!!.width, "с пробелами")
        assertEquals(1365, Viewport.parse("4:3")!!.width, "4:3")
        assertEquals(1280, Viewport.parse("5:4")!!.width, "5:4")
        assertEquals(1931, Viewport.parse("1920x1018")!!.width, "окно с заголовком — не 16:9")
        assertEquals(1931, Viewport.parse("1931")!!.width, "точная ширина в единицах")
    }

    @Test
    fun `nonsense is refused rather than guessed at`() {
        listOf("", "экран", "16:0", "0:9", "-3", "10", "99999", "16:9:4").forEach {
            assertNull(Viewport.parse(it), "«$it» приняли за формат экрана")
        }
    }

    @Test
    fun `every screen anyone plays on falls into a class`() {
        assertEquals(Viewport.Class.COMPACT, Viewport.parse("4:3")!!.size)
        assertEquals(Viewport.Class.COMPACT, Viewport.parse("5:4")!!.size)
        assertEquals(Viewport.Class.REGULAR, Viewport.parse("16:10")!!.size)
        assertEquals(Viewport.Class.REGULAR, Viewport.parse("16:9")!!.size)
        assertEquals(Viewport.Class.WIDE, Viewport.parse("21:9")!!.size)
        assertEquals(3, Viewport.parse("16:9")!!.by(compact = 2, regular = 3, wide = 4))
        assertEquals(2, Viewport.parse("4:3")!!.by(compact = 2, regular = 3, wide = 4))
    }

    @Test
    fun `the safe band is on screen whatever the shape`() {
        // A page laid out for one screen and opened on a narrower one loses the difference
        // off both sides. What is left is this, and it is where everything readable goes.
        Viewport.PRESETS.values.forEach { screen ->
            assertTrue(
                screen.safeWidth <= screen.width,
                "безопасная полоса шире самого экрана ${screen.width}",
            )
            assertTrue(
                screen.safeWidth >= minOf(screen.width, Viewport.parse("4:3")!!.width),
                "безопасная полоса уже, чем 4:3, на экране ${screen.width}",
            )
        }
    }

    @Test
    fun `a panel keeps to its limit and takes the middle`() {
        // max-width with margin: 0 auto. The page fills a narrow window and holds a
        // readable column on a wide one.
        val screen = Viewport.parse("21:9")!!
        val placement = Layout.centred(
            Panel(
                width = Size.Fixed(screen.width),
                height = Size.Fixed(screen.height),
                align = Align.START,
                children = listOf(
                    Panel(id = "body", width = Size.Fill, maxWidth = 1278, height = Size.Fixed(100)),
                ),
            ),
            screen.width,
            screen.height,
        )
        val body = placement.regions.single { it.id == "body" }
        assertEquals(1278, body.width, "панель переросла свой предел")
        assertEquals((screen.width - 1278) / 2, body.x, "панель не встала по центру")
    }

    @Test
    fun `counting columns never asks for none`() {
        val narrow = Viewport.parse("4:3")!!
        assertTrue(narrow.columns(ideal = 260, min = 2, max = 5, gap = 12) in 2..5)
        assertEquals(2, narrow.columns(ideal = 10_000, min = 2, max = 5), "не меньше минимума")
        assertEquals(5, narrow.columns(ideal = 1, min = 2, max = 5), "не больше максимума")
    }
}
