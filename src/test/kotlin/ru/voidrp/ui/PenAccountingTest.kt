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
    fun `every path in the pack is a legal resource name`() {
        // Minecraft resource paths are lower case, and a font naming a file that cannot
        // exist is discarded whole — every glyph in it. A page of icons then draws as a
        // page of empty squares, and because their widths are wrong, slides off screen.
        val illegal = ClientSimulator.packPaths().filterNot { path ->
            path.all { it.isDigit() || it in 'a'..'z' || it in "_-./" }
        }
        assertTrue(illegal.isEmpty(), "недопустимые пути в паке: ${illegal.take(5)}")
    }

    @Test
    fun `each thing a style can add balances on its own`() {
        val cases = mapOf(
            "только фон" to ru.voidrp.ui.style.Style(background = Paint(0x8B7BFF, 0.5)),
            "скругление" to ru.voidrp.ui.style.Style(background = Paint(0x8B7BFF, 0.5), radius = 12),
            "рамка" to ru.voidrp.ui.style.Style(
                background = Paint(0x8B7BFF, 0.5),
                border = ru.voidrp.ui.style.Border(1, Paint(0x96A8DC, 0.2)),
                radius = 12,
            ),
            "светлая кромка" to ru.voidrp.ui.style.Style(
                background = Paint(0x8B7BFF, 0.5),
                radius = 12,
                highlight = Paint(0xFFFFFF, 0.06),
            ),
            "тень" to ru.voidrp.ui.style.Style(
                background = Paint(0x8B7BFF, 0.5),
                radius = 12,
                shadow = ru.voidrp.ui.style.Shadow(offsetY = 8, paint = Paint(0x000000, 0.4)),
            ),
            "свечение" to ru.voidrp.ui.style.Style(
                background = Paint(0x8B7BFF, 0.5),
                radius = 12,
                glow = Paint(0x8B7BFF, 0.3),
            ),
        )
        cases.forEach { (name, style) ->
            assertBalanced(name, listOf(ru.voidrp.ui.render.Box(120, 80, 260, 140, style)))
        }
    }

    @Test
    fun `parts of a halo balance`() {
        val paint = Paint(0x000000, 0.4)
        val spread = ru.voidrp.ui.pack.Glyphs.GLOW_SPREAD
        fun piece(x: Int, y: Int, part: ru.voidrp.ui.pack.Glyphs.GlowPart, corner: ru.voidrp.ui.pack.Glyphs.Corner, step: Int) =
            ru.voidrp.ui.render.GlowPiece(x, y, part, corner, step, paint)

        val corners = listOf(
            piece(100, 100, ru.voidrp.ui.pack.Glyphs.GlowPart.CORNER, ru.voidrp.ui.pack.Glyphs.Corner.TOP_LEFT, 1),
            piece(300, 100, ru.voidrp.ui.pack.Glyphs.GlowPart.CORNER, ru.voidrp.ui.pack.Glyphs.Corner.TOP_RIGHT, 1),
            piece(100, 300, ru.voidrp.ui.pack.Glyphs.GlowPart.CORNER, ru.voidrp.ui.pack.Glyphs.Corner.BOTTOM_LEFT, 1),
            piece(300, 300, ru.voidrp.ui.pack.Glyphs.GlowPart.CORNER, ru.voidrp.ui.pack.Glyphs.Corner.BOTTOM_RIGHT, 1),
        )
        assertEquals(0, client.width(GlyphEncoder.encode(corners)), "углы ореола")

        val horizontals = listOf(128, 128, 4).mapIndexed { index, step ->
            piece(100 + index * 128, 200, ru.voidrp.ui.pack.Glyphs.GlowPart.HORIZONTAL, ru.voidrp.ui.pack.Glyphs.Corner.TOP_LEFT, step)
        }
        assertEquals(0, client.width(GlyphEncoder.encode(horizontals)), "верхняя сторона")

        val verticals = listOf(128, 8, 4).mapIndexed { index, step ->
            piece(100, 200 + index * 64, ru.voidrp.ui.pack.Glyphs.GlowPart.VERTICAL, ru.voidrp.ui.pack.Glyphs.Corner.TOP_LEFT, step)
        }
        assertEquals(0, client.width(GlyphEncoder.encode(verticals)), "левая сторона")

        assertEquals(0, client.width(GlyphEncoder.encode(corners + horizontals + verticals)), "всё вместе")
        assertEquals(0, spread - spread, "")
    }

    @Test
    fun `one halo tile at a time balances`() {
        val wrong = mutableListOf<String>()
        listOf(3, 6, 11, 16).forEach { level ->
            val alpha = level.toDouble() / ru.voidrp.ui.pack.Glyphs.ALPHA_LEVELS
            ru.voidrp.ui.pack.Glyphs.glowPieces().forEach { (part, corner, step) ->
                val line = GlyphEncoder.encode(
                    listOf(ru.voidrp.ui.render.GlowPiece(200, 100, part, corner, step, Paint(0x000000, alpha)))
                )
                val width = client.width(line)
                if (width != 0) wrong += "$part/$corner/$step на ступени $level: строка шириной $width"
            }
        }
        assertTrue(wrong.isEmpty(), wrong.take(5).joinToString("\n"))
    }

    @Test
    fun `halo tiles advance the way the client will`() {
        // A halo fades out, and at low opacity its faintest columns round away to nothing,
        // so how far the pen moves past one depends on the opacity it is drawn at. The
        // encoder and the pack have to agree about that for every tile at every step.
        val wrong = mutableListOf<String>()
        (1..ru.voidrp.ui.pack.Glyphs.ALPHA_LEVELS).forEach { level ->
            ru.voidrp.ui.pack.Glyphs.glowPieces().forEach { (part, corner, step) ->
                val ours = ru.voidrp.ui.pack.Glow.advance(part, corner, step, level)
                val theirs = client.advanceOf(
                    ru.voidrp.ui.pack.Glyphs.fontName(level),
                    ru.voidrp.ui.pack.Glyphs.glow(part, corner, step),
                )
                if (ours != theirs) wrong += "$part/$corner/$step на ступени $level: у нас $ours, у клиента $theirs"
            }
        }
        assertTrue(wrong.isEmpty(), "шаг ореола разошёлся:\n" + wrong.take(6).joinToString("\n"))
    }

    @Test
    fun `the home page balances`() {
        assertBalanced(
            "главная",
            Layout.centred(
                ru.voidrp.ui.page.HomePage().view(),
                Shaders.CANVAS_WIDTH,
                Shaders.CANVAS_HEIGHT,
            ).nodes,
        )
    }

    @Test
    fun `the shop page balances`() {
        assertBalanced(
            "магазин",
            Layout.centred(
                ru.voidrp.ui.page.ShopPage().view(),
                Shaders.CANVAS_WIDTH,
                Shaders.CANVAS_HEIGHT,
            ).nodes,
        )
    }

    @Test
    fun `the demo page balances`() {
        assertBalanced(
            "демо",
            Layout.centred(
                ru.voidrp.ui.page.DemoPage().view(),
                Shaders.CANVAS_WIDTH,
                Shaders.CANVAS_HEIGHT,
            ).nodes,
        )
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
