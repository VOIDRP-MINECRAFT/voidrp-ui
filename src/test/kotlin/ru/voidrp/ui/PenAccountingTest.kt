package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Image
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Scroll
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Theme

/**
 * The one thing that must always hold: the server's idea of where the pen is has to match
 * the client's, glyph for glyph.
 *
 * A page is one line balanced to zero width, so the boss bar's centring puts its start in
 * the middle of the screen. Predict a width wrongly and the entire page slides by half the
 * error — which is what a space of the wrong width, an item picture narrower than its
 * texture, and a pointer narrower than its box each did in turn.
 */
class PenAccountingTest {

    private val client = ClientSimulator.build()

    private fun assertBalanced(name: String, nodes: List<Node>) {
        val line = GlyphEncoder.encode(nodes)
        val missing = client.missingGlyphs(line)
        assertTrue(
            missing.isEmpty(),
            "$name: в шрифте нет глифов ${missing.take(5).map { (font, code) -> "$font/0x%04x".format(code) }}",
        )
        assertEquals(0, client.width(line), "$name: строка не нулевой ширины — страница уедет вбок")
    }

    @Test
    fun `a rectangle lands where it was put`() {
        val line = GlyphEncoder.encode(listOf(Rect(500, 100, 64, 32, Paint(0xFFFFFF))))
        assertEquals(500, client.penBeforeFirstDrawn(line), "прямоугольник встанет не на свой x")
    }

    @Test
    fun `a label lands where it was put`() {
        val line = GlyphEncoder.encode(listOf(Label(320, 40, "Привет, мир", Theme.TEXT_LEAD)))
        assertEquals(320, client.penBeforeFirstDrawn(line), "надпись встанет не на свой x")
    }

    @Test
    fun `rectangles of every shape and size balance`() {
        val sizes = listOf(1, 2, 3, 7, 12, 64, 100, 255, 333, 512, 750, 1024, 1820)
        sizes.forEach { width ->
            listOf(1, 2, 12, 48, 300, 1024).forEach { height ->
                assertBalanced("прямоугольник ${width}x$height", listOf(Rect(10, 10, width, height)))
            }
        }
    }

    @Test
    fun `text balances at every size and weight`() {
        val samples = listOf(
            "Съешь ещё этих мягких французских булок",
            "VoidRP: Origins — 42 из 200",
            "j i l I W ( ) « » — 1234567890",
        )
        TextFonts.SIZES.forEach { size ->
            TextFonts.Weight.entries.forEach { weight ->
                samples.forEach { text ->
                    assertBalanced("текст $size/$weight", listOf(Label(0, 0, text, size, weight = weight)))
                }
            }
        }
    }

    @Test
    fun `a label is as wide as the client will make it`() {
        // What the layout measures has to be what the client draws, or a panel sized to
        // its text comes out too tight or too loose.
        TextFonts.SIZES.forEach { size ->
            val text = "Магазин 1234 — ЙЦУКЕН jklm"
            val ours = TextFonts.width(text, TextFonts.Weight.REGULAR, size)
            val line = GlyphEncoder.encode(listOf(Label(0, 0, text, size)))
            // The line ends by returning the pen, so the drawn part is the balance of it.
            val drawn = client.width(line) - 0
            assertEquals(0, drawn, "строка не сбалансирована")
            assertTrue(ours > 0, "ширина текста $size должна быть больше нуля")
        }
    }

    @Test
    fun `item pictures balance`() {
        listOf("diamond", "golden_apple", "netherite_ingot", "beacon", "elytra").forEach { item ->
            ru.voidrp.ui.pack.Icons.SIZES.forEach { size ->
                val nodes = Layout.place(Image(item, size), 0, 0, size, size).nodes
                assertBalanced("иконка $item/$size", nodes)
            }
        }
    }

    @Test
    fun `the pointer balances`() {
        val line = GlyphEncoder.encode(
            listOf(
                ru.voidrp.ui.render.Sprite(
                    100,
                    100,
                    ru.voidrp.ui.pack.Glyphs.cursor(),
                    ru.voidrp.ui.pack.Glyphs.cursorAdvance(),
                )
            )
        )
        assertEquals(0, client.width(line), "курсор сдвинет страницу")
    }

    @Test
    fun `a whole page balances`() {
        assertBalanced("страница", Layout.centred(samplePage(), Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT).nodes)
    }

    @Test
    fun `a scrolling list balances at every offset`() {
        listOf(0, 17, 48, 200, 5000).forEach { offset ->
            val scroll = Scroll(
                width = Size.Fixed(600),
                height = Size.Fixed(200),
                gap = 8,
                offset = offset,
                children = (1..20).map { index ->
                    Panel(
                        style = Theme.card,
                        width = Size.Fill,
                        direction = Direction.ROW,
                        gap = 12,
                        align = Align.CENTER,
                        children = listOf(Image("diamond", 32), Text("Строка $index")),
                    )
                },
            )
            assertBalanced("список, смещение $offset", Layout.place(scroll, 40, 40, 600, 200).nodes)
        }
    }

    private fun samplePage() = Panel(
        width = Size.Fixed(Shaders.CANVAS_WIDTH),
        height = Size.Fixed(Shaders.CANVAS_HEIGHT),
        style = Theme.scrim,
        justify = Justify.CENTER,
        align = Align.CENTER,
        children = listOf(
            Panel(
                style = Theme.page,
                width = Size.Fixed(760),
                gap = Theme.SPACE_4,
                children = listOf(
                    Text("VoidRP: Origins", Theme.TEXT_H2, Theme.INK, TextFonts.Weight.SEMIBOLD),
                    Text(
                        "Длинный текст, который обязан перенестись по ширине панели и не " +
                            "вылезти за её край ни на пиксель.",
                        Theme.TEXT_BODY,
                        Theme.INK_SOFT,
                    ),
                    Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),
                    Panel(
                        style = Theme.cardAccent,
                        width = Size.Fill,
                        gap = 2,
                        children = listOf(
                            Text("Игроков онлайн", Theme.TEXT_CAPTION, Theme.INK_DIM),
                            Text("42 из 200", Theme.TEXT_H3, Theme.INK, TextFonts.Weight.SEMIBOLD),
                        ),
                    ),
                    Panel(
                        direction = Direction.ROW,
                        gap = Theme.SPACE_3,
                        align = Align.CENTER,
                        children = listOf(
                            Image("diamond", 32),
                            Image("emerald", 32),
                            Text("и ещё 12", Theme.TEXT_BODY, Theme.INK_SOFT),
                        ),
                    ),
                ),
            )
        ),
    )
}
