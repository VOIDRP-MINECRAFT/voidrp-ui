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
        assertEquals(1820, Viewport.parse("1920x1080")!!.width, "the same, as a resolution")
        assertEquals(1820, Viewport.parse(" 16 : 9 ")!!.width, "with spaces")
        assertEquals(1365, Viewport.parse("4:3")!!.width, "4:3")
        assertEquals(1280, Viewport.parse("5:4")!!.width, "5:4")
        assertEquals(1931, Viewport.parse("1920x1018")!!.width, "a window with a title bar — not 16:9")
        assertEquals(1931, Viewport.parse("1931")!!.width, "an exact width in units")
    }

    @Test
    fun `nonsense is refused rather than guessed at`() {
        // "экран" (screen) is deliberately Russian: a word, not a format, in the players' own
        // language, which is the likeliest nonsense to arrive.
        listOf("", "экран", "16:0", "0:9", "-3", "10", "99999", "16:9:4").forEach {
            assertNull(Viewport.parse(it), "«$it» was taken for a screen format")
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
                "the safe band is wider than the screen itself, ${screen.width}",
            )
            assertTrue(
                screen.safeWidth >= minOf(screen.width, Viewport.parse("4:3")!!.width),
                "the safe band is narrower than 4:3 on a screen of ${screen.width}",
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
        assertEquals(1278, body.width, "the panel outgrew its limit")
        assertEquals((screen.width - 1278) / 2, body.x, "the panel is not centred")
    }

    @Test
    fun `a row that runs out of width carries on underneath`() {
        // flex-wrap, for the case a grid does not cover: things of different widths that
        // should fill the line and then start another.
        val widths = listOf(300, 300, 300, 200)
        val panel = Panel(
            direction = ru.voidrp.ui.layout.Direction.ROW,
            wrap = true,
            gap = 10,
            lineGap = 20,
            width = Size.Fixed(700),
            children = widths.mapIndexed { index, w ->
                Panel(id = "cell$index", width = Size.Fixed(w), height = Size.Fixed(40))
            },
        )
        val placement = Layout.place(panel, 0, 0, 700, 400)
        val cells = (0..3).map { index -> placement.regions.single { it.id == "cell$index" } }

        assertEquals(0, cells[0].x, "the first card is not at the left edge")
        assertEquals(310, cells[1].x, "the second does not follow the first after the gap")
        assertEquals(0, cells[2].x, "the third did not wrap onto a new line")
        assertEquals(310, cells[3].x, "the fourth is not next to the third")

        assertEquals(cells[0].y, cells[1].y, "the first line is uneven in height")
        assertEquals(60, cells[2].y, "the second line is not one line plus its gap down")
        assertEquals(cells[2].y, cells[3].y, "the second line is uneven in height")

        // And the panel itself knows how tall it came out: two rows of 40 and one 20 gap.
        assertEquals(100, Layout.measure(panel, 700, 400).height, "the panel is the wrong height")
    }

    @Test
    fun `a row only wraps when it is asked to`() {
        val children = (0..3).map { Panel(id = "c$it", width = Size.Fixed(300), height = Size.Fixed(40)) }
        val plain = Layout.place(
            Panel(direction = ru.voidrp.ui.layout.Direction.ROW, gap = 10, children = children),
            0, 0, 700, 400,
        )
        // Without wrap the row still fits itself into the width it was given, by squeezing.
        assertEquals(1, plain.regions.map { it.y }.distinct().size, "a row without wrap wrapped anyway")
    }

    @Test
    fun `counting columns never asks for none`() {
        val narrow = Viewport.parse("4:3")!!
        assertTrue(narrow.columns(ideal = 260, min = 2, max = 5, gap = 12) in 2..5)
        assertEquals(2, narrow.columns(ideal = 10_000, min = 2, max = 5), "no fewer than the minimum")
        assertEquals(5, narrow.columns(ideal = 1, min = 2, max = 5), "no more than the maximum")
    }
}
