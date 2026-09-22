package ru.voidrp.ui

import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.page.DemoPage
import ru.voidrp.ui.page.HomePage
import ru.voidrp.ui.page.Page
import ru.voidrp.ui.page.ShopPage
import ru.voidrp.ui.render.GlyphEncoder

/**
 * What a page costs to draw: `./gradlew bench`.
 *
 * A page is laid out, painted and encoded every time anything on it changes, and at sixty
 * frames a second for whatever moves. The numbers that matter are how many shapes it comes
 * to, how long the line is, and how long the three steps take together.
 */
object PageBench {

    @JvmStatic
    fun main(args: Array<String>) {
        listOf<Pair<String, Page>>(
            "главная" to HomePage(),
            "магазин" to ShopPage(),
            "демо" to DemoPage(),
        ).forEach { (name, page) ->
            repeat(50) { measure(page) }
            val runs = (1..200).map { measure(page) }
            val nodes = Layout.centred(page.view(), Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT).nodes
            val line = GlyphEncoder.encode(nodes)
            val length = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(line).length
            println(
                "%-9s %4d фигур, %6d символов, %.2f мс (медиана), %.2f мс (худшая)".format(
                    name,
                    nodes.size,
                    length,
                    runs.sorted()[runs.size / 2] / 1_000_000.0,
                    runs.max() / 1_000_000.0,
                ),
            )
        }

        repeat(200) { cursorFrame() }
        val frames = (1..2000).map { cursorFrame() }.sorted()
        val median = frames[frames.size / 2] / 1_000_000.0
        println(
            "кадр курсора: %.3f мс (медиана) — при 62 кадрах в секунду это %d игроков на ядро".format(
                median,
                (1000.0 / 62 / median).toInt(),
            ),
        )
    }

    /**
     * What one player costs the frame thread, sixty times a second.
     *
     * The page is encoded once and kept; what goes out every frame is the pointer, the
     * highlight under it and any tooltip. This measures that, so "how many players can have
     * a page open" is a number rather than a guess.
     */
    private fun cursorFrame(): Long {
        val start = System.nanoTime()
        val nodes = mutableListOf<ru.voidrp.ui.render.Node>()
        ru.voidrp.ui.render.Painter.fill(
            1168, 439, 123, 78, 12,
            ru.voidrp.ui.style.Paint(ru.voidrp.ui.style.Theme.VIOLET, 0.16),
            nodes,
        )
        ru.voidrp.ui.render.Painter.outline(
            1168, 439, 123, 78, 12, 1,
            ru.voidrp.ui.style.Paint(ru.voidrp.ui.style.Theme.VIOLET, 0.55),
            nodes,
        )
        nodes += ru.voidrp.ui.render.Sprite(
            900,
            500,
            ru.voidrp.ui.pack.Glyphs.cursor(),
            ru.voidrp.ui.pack.Glyphs.cursorAdvance(),
        )
        GlyphEncoder.encode(nodes)
        return System.nanoTime() - start
    }

    private fun measure(page: Page): Long {
        val start = System.nanoTime()
        val nodes = Layout.centred(page.view(), Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT).nodes
        GlyphEncoder.encode(nodes)
        return System.nanoTime() - start
    }
}
