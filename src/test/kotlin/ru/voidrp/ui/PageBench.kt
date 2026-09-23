package ru.voidrp.ui

import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Viewport
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
            "home" to HomePage(),
            "shop" to ShopPage(),
            "demo" to DemoPage(),
        ).forEach { (name, page) ->
            repeat(50) { measure(page) }
            val runs = (1..200).map { measure(page) }
            val nodes = Layout.centred(page.view(), Viewport.DEFAULT.width, Viewport.HEIGHT).nodes
            val line = GlyphEncoder.encode(nodes)
            val length = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(line).length
            // What actually goes out: every run carries its own colour and font, so the
            // packet is several times the text in it.
            val onWire = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                .serialize(line).toByteArray(Charsets.UTF_8).size
            val pieces = generateSequence(listOf(line)) { level ->
                level.flatMap { it.children() }.takeIf { it.isNotEmpty() }
            }.sumOf { it.size }
            println(
                "%-9s %4d shapes, %6d characters, %5d runs, %6d B on the wire, %.2f ms (median), %.2f ms (worst)".format(
                    name,
                    nodes.size,
                    length,
                    pieces,
                    onWire,
                    runs.sorted()[runs.size / 2] / 1_000_000.0,
                    runs.max() / 1_000_000.0,
                ),
            )
        }

        // What a page costs when nothing about it has changed. The session compares the
        // description it was given with the one it last drew and sends nothing if they
        // match, so this is the price of finding that out against the price of not
        // bothering to.
        listOf<Pair<String, Page>>("home" to HomePage(), "shop" to ShopPage()).forEach { (name, page) ->
            val a = page.view()
            repeat(200) { a == page.view() }
            val compares = (1..2000).map {
                val started = System.nanoTime()
                val same = a == page.view()
                check(same)
                System.nanoTime() - started
            }.sorted()
            val renders = (1..200).map { measure(page) }.sorted()
            println(
                "%-9s unchanged: %.3f ms to notice, against %.2f ms to draw it again — %.0f× cheaper".format(
                    name,
                    compares[compares.size / 2] / 1_000_000.0,
                    renders[renders.size / 2] / 1_000_000.0,
                    renders[renders.size / 2].toDouble() / compares[compares.size / 2].coerceAtLeast(1),
                ),
            )
        }

        repeat(200) { cursorFrame() }
        val frames = (1..2000).map { cursorFrame() }.sorted()
        val median = frames[frames.size / 2] / 1_000_000.0
        val rate = ru.voidrp.ui.page.PageManager.DEFAULT_FRAME_RATE
        println(
            "cursor frame: %.3f ms (median) — at %d frames a second, %d players per core".format(
                median,
                rate,
                (1000.0 / rate / median).toInt(),
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
        val nodes = Layout.centred(page.view(), Viewport.DEFAULT.width, Viewport.HEIGHT).nodes
        GlyphEncoder.encode(nodes)
        return System.nanoTime() - start
    }
}
