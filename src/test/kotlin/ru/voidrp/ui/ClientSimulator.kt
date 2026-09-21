package ru.voidrp.ui

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import ru.voidrp.ui.pack.Icons
import ru.voidrp.ui.pack.PackBuilder

/**
 * What the client would do with a line we send it.
 *
 * Every page is one line of text, laid out by the client, and the server has to predict
 * the pen exactly: a mistake of a few pixels moves the whole page sideways, because the
 * line is balanced to zero width and the boss bar centres it. Three of the worst bugs in
 * this project were that mistake in three different disguises — a space that was not four
 * pixels wide, an item picture narrower than its texture, a pointer narrower than its box
 * — and each was found by a person looking at a screenshot.
 *
 * So this reads the pack we actually build, measures every glyph in it the way the client
 * measures one (the width of its ink, plus a pixel), and adds up a line. What it disagrees
 * with is a bug, and it says so before anyone logs in.
 */
class ClientSimulator(pack: File) {

    /** Advances by font name, then by code point. */
    private val fonts: Map<String, Map<Int, Int>>

    init {
        val advances = mutableMapOf<String, MutableMap<Int, Int>>()
        ZipFile(pack).use { zip ->
            val textures = zip.entries().asSequence()
                .filter { it.name.startsWith("assets/voidrp/textures/") && it.name.endsWith(".png") }
                .associate { entry -> entry.name to zip.getInputStream(entry).readBytes() }

            zip.entries().asSequence()
                .filter { it.name.startsWith("assets/voidrp/font/") && it.name.endsWith(".json") }
                .forEach { entry ->
                    val name = entry.name.removePrefix("assets/voidrp/font/").removeSuffix(".json")
                    val json = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                    advances[name] = readFont(name, json, textures)
                }
        }
        fonts = advances
    }

    /** How wide the client would make this line, in canvas units. */
    fun width(component: Component): Int = runs(component).sumOf { (text, font) ->
        text.codePoints().toArray().sumOf { code -> advance(font, code) }
    }

    /**
     * Where the pen stands when the client reaches the first drawn glyph — which is where
     * whatever comes first on the page will land.
     */
    fun penBeforeFirstDrawn(component: Component): Int {
        var pen = 0
        runs(component).forEach { (text, font) ->
            text.codePoints().toArray().forEach { code ->
                if (!isSpacer(code)) return pen
                pen += advance(font, code)
            }
        }
        return pen
    }

    /** Every code point the line asks for, so a missing glyph can be spotted. */
    fun missingGlyphs(component: Component): List<Pair<String, Int>> = runs(component).flatMap { (text, font) ->
        text.codePoints().toArray()
            .filter { code -> code >= 0xE000 && fonts[font]?.containsKey(code) != true }
            .map { font to it }
    }

    private fun advance(font: String, code: Int): Int = fonts[font]?.get(code) ?: 0

    private fun isSpacer(code: Int): Boolean = code in 0xE800..0xE8FF

    private fun runs(component: Component): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        fun walk(node: Component, inherited: String) {
            val font = node.style().font()?.value() ?: inherited
            if (node is TextComponent && node.content().isNotEmpty()) out += node.content() to font
            node.children().forEach { walk(it, font) }
        }
        walk(component, "")
        return out
    }

    /** Reads one font file the way the client reads it: space advances, then bitmaps. */
    private fun readFont(font: String, json: String, textures: Map<String, ByteArray>): MutableMap<Int, Int> {
        val out = mutableMapOf<Int, Int>()
        val providers = com.google.gson.JsonParser.parseString(json)
            .asJsonObject.getAsJsonArray("providers")
        providers.forEach { element ->
            val provider = element.asJsonObject
            when (provider.get("type").asString) {
                "space" -> provider.getAsJsonObject("advances").entrySet().forEach { (char, value) ->
                    // Earlier providers win, exactly as in the client.
                    out.putIfAbsent(char.codePointAt(0), value.asInt)
                }

                "bitmap" -> {
                    val file = provider.get("file").asString
                    // An item picture is the client's own texture, which is not ours to
                    // measure — but the pack still declares the glyph, and the width
                    // shipped with the plugin is what the encoder counts on.
                    if (!file.startsWith("voidrp:")) {
                        if (font.startsWith("icons_")) {
                            val size = font.removePrefix("icons_").toInt()
                            val item = file.removePrefix("minecraft:item/").removeSuffix(".png")
                            provider.getAsJsonArray("chars").forEach { row ->
                                row.asString.codePoints().toArray().forEach { code ->
                                    out.putIfAbsent(code, Icons.advance(item, size))
                                }
                            }
                        }
                        return@forEach
                    }
                    val path = "assets/voidrp/textures/" + file.removePrefix("voidrp:")
                    val image = textures[path]?.let { ImageIO.read(ByteArrayInputStream(it)) } ?: return@forEach
                    val rows = provider.getAsJsonArray("chars").map { it.asString }
                    val columns = rows.firstOrNull()?.let { it.codePointCount(0, it.length) } ?: 0
                    if (rows.isEmpty() || columns == 0) return@forEach
                    val cellWidth = image.width / columns
                    val cellHeight = image.height / rows.size
                    val scale = provider.get("height").asInt.toDouble() / cellHeight

                    rows.forEachIndexed { row, line ->
                        line.codePoints().toArray().forEachIndexed { column, code ->
                            if (code == 0) return@forEachIndexed
                            val ink = inkWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight)
                            out.putIfAbsent(code, Math.round(ink * scale).toInt() + 1)
                        }
                    }
                }
            }
        }
        return out
    }

    private fun inkWidth(
        image: java.awt.image.BufferedImage,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Int {
        for (column in width - 1 downTo 0) {
            for (row in 0 until height) {
                if (image.getRGB(x + column, y + row) ushr 24 != 0) return column + 1
            }
        }
        return 0
    }

    companion object {

        /** Builds the pack once and reads it back, which is what the client is given. */
        fun build(): ClientSimulator {
            val file = File.createTempFile("voidrp-ui-test", ".zip").apply { deleteOnExit() }
            PackBuilder().build(file)
            return ClientSimulator(file)
        }
    }
}
