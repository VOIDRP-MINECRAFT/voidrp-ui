package ru.voidrp.ui.pack

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Pictures a server puts in itself: a logo, a banner, a seasonal badge.
 *
 * Drop PNGs into `plugins/VoidRpUI/images/` and a page can draw them by name. They are
 * copied into the pack as they are — one texture each, no scaling on our side — and
 * declared at a ladder of heights, because the client scales a bitmap glyph to the height
 * its font gives and keeps the proportions. So one file serves every size a page asks for.
 *
 * Unlike the interface's own icons these are not tinted: they go on screen in their own
 * colours, which is the point of a logo.
 */
object ServerImages {

    /** Heights a picture is offered at. A page gets the nearest, not a blurry scale. */
    val HEIGHTS = listOf(16, 24, 32, 48, 64, 96, 128)

    private const val BASE = 0xEC00

    /** What was found in the folder: the file, its size, and how wide its ink is. */
    data class Picture(val name: String, val width: Int, val height: Int, val ink: Int, val png: ByteArray)

    private var pictures: Map<String, Picture> = emptyMap()
    private var order: List<String> = emptyList()

    val names: List<String> get() = order

    /**
     * Reads the folder. Called when the pack is built, which is when a server's own
     * pictures can change — adding one means the pack changes, so players fetch it again.
     */
    fun load(folder: File?) {
        if (folder == null || !folder.isDirectory) {
            pictures = emptyMap()
            order = emptyList()
            return
        }
        val found = LinkedHashMap<String, Picture>()
        folder.listFiles { file -> file.isFile && file.extension.lowercase() == "png" }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file ->
                val bytes = file.readBytes()
                val image = runCatching { ImageIO.read(file) }.getOrNull() ?: return@forEach
                if (image.width <= 0 || image.height <= 0) return@forEach
                val name = file.nameWithoutExtension.lowercase()
                found[name] = Picture(name, image.width, image.height, inkWidth(image), bytes)
            }
        pictures = found
        order = found.keys.toList()
    }

    fun has(name: String): Boolean = pictures.containsKey(name.lowercase())

    fun glyph(name: String): String? =
        order.indexOf(name.lowercase()).takeIf { it >= 0 }?.let { String(Character.toChars(BASE + it)) }

    fun fontName(height: Int): String = "ui_images_${nearestHeight(height)}"

    fun nearestHeight(height: Int): Int = HEIGHTS.minByOrNull { Math.abs(it - height) } ?: HEIGHTS.first()

    fun textureName(name: String): String = "images/${name.lowercase()}.png"

    fun png(name: String): ByteArray? = pictures[name.lowercase()]?.png

    /** How wide a picture comes out at this height, with its proportions kept. */
    fun width(name: String, height: Int): Int {
        val picture = pictures[name.lowercase()] ?: return 0
        return Math.round(picture.width.toDouble() * nearestHeight(height) / picture.height).toInt()
    }

    /**
     * How far the pen moves past it: the ink of the picture scaled to the height it is
     * drawn at, plus the one pixel the client adds — the same rule as everything else here.
     */
    fun advance(name: String, height: Int): Int {
        val picture = pictures[name.lowercase()] ?: return 0
        return Math.round(picture.ink.toDouble() * nearestHeight(height) / picture.height).toInt() + 1
    }

    /** One font per height, each naming every picture at that height. */
    fun fontJson(height: Int): String {
        val advances = Glyphs.spacers().entries.joinToString(", ") { (char, advance) ->
            "\"${Fonts.escapeJson(char)}\": $advance"
        }
        val providers = order.mapIndexed { index, name ->
            """{"type": "bitmap", "file": "voidrp:${textureName(name)}", "ascent": 0,
                "height": ${nearestHeight(height)},
                "chars": ["${Fonts.escapeJson(String(Character.toChars(BASE + index)))}"]}"""
        }
        return Fonts.compact(
            """{"providers": [{"type": "space", "advances": { $advances }}${
                if (providers.isEmpty()) "" else ", " + providers.joinToString(", ")
            }]}"""
        )
    }

    /** Every texture, for the pack to copy in. */
    fun textures(): Map<String, ByteArray> = pictures.values.associate { textureName(it.name) to it.png }

    private fun inkWidth(image: BufferedImage): Int {
        for (x in image.width - 1 downTo 0) {
            if ((0 until image.height).any { y -> image.getRGB(x, y) ushr 24 != 0 }) return x + 1
        }
        return 0
    }

    /** For tests: a picture made up on the spot rather than read from disk. */
    fun loadForTest(name: String, image: BufferedImage) {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "PNG", it) }.toByteArray()
        pictures = mapOf(name.lowercase() to Picture(name.lowercase(), image.width, image.height, inkWidth(image), bytes))
        order = listOf(name.lowercase())
    }
}
