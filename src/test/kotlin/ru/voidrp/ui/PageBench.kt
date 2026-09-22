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
    }

    private fun measure(page: Page): Long {
        val start = System.nanoTime()
        val nodes = Layout.centred(page.view(), Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT).nodes
        GlyphEncoder.encode(nodes)
        return System.nanoTime() - start
    }
}
