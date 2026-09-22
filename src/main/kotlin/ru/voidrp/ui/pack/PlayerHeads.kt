package ru.voidrp.ui.pack

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Faces, cut out of players' skins.
 *
 * A head is eight pixels square in the skin — the face, with the hat layer laid over it —
 * and here it becomes a glyph like any other picture. Skins live in
 * `plugins/VoidRpUI/heads/` as `<nick>.png`: whatever is dropped there is used as it is,
 * and names listed in the config are fetched into it once.
 *
 * The honest limit is the pack: a head has to be in it before a page can draw it, and the
 * pack is built when the plugin starts. So this is for a list that holds still — the
 * staff, the season's champions — and not for whoever happens to be online.
 */
object PlayerHeads {

    /** Sizes a head is offered at. Eight-pixel art, so these are all multiples of eight. */
    val SIZES = listOf(8, 16, 24, 32, 48, 64, 96, 128)

    private const val BASE = 0xED00

    private var heads: Map<String, BufferedImage> = emptyMap()
    private var order: List<String> = emptyList()

    val names: List<String> get() = order

    /** Reads the folder: every PNG in it is somebody's skin, named after them. */
    fun load(folder: File?) {
        if (folder == null || !folder.isDirectory) {
            heads = emptyMap()
            order = emptyList()
            return
        }
        val found = LinkedHashMap<String, BufferedImage>()
        folder.listFiles { file -> file.isFile && file.extension.lowercase() == "png" }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file ->
                val skin = runCatching { ImageIO.read(file) }.getOrNull() ?: return@forEach
                face(skin)?.let { found[file.nameWithoutExtension.lowercase()] = it }
            }
        heads = found
        order = found.keys.toList()
    }

    /**
     * The face out of a skin: the eight by eight at (8,8), with the hat over it.
     *
     * Every skin since the beginning has those two in the same place, whether the file is
     * the old 64×32 or the modern 64×64, so this needs to know nothing about which it is.
     */
    private fun face(skin: BufferedImage): BufferedImage? {
        if (skin.width < 64 || skin.height < 32) return null
        val scale = skin.width / 64
        val head = BufferedImage(8 * scale, 8 * scale, BufferedImage.TYPE_INT_ARGB)
        val g = head.createGraphics()
        g.drawImage(skin, 0, 0, head.width, head.height, 8 * scale, 8 * scale, 16 * scale, 16 * scale, null)
        g.drawImage(skin, 0, 0, head.width, head.height, 40 * scale, 8 * scale, 48 * scale, 16 * scale, null)
        g.dispose()
        return head
    }

    fun has(name: String): Boolean = heads.containsKey(name.lowercase())

    fun glyph(name: String): String? =
        order.indexOf(name.lowercase()).takeIf { it >= 0 }?.let { String(Character.toChars(BASE + it)) }

    fun fontName(size: Int): String = "ui_heads_${nearestSize(size)}"

    fun nearestSize(size: Int): Int = SIZES.minByOrNull { Math.abs(it - size) } ?: SIZES.first()

    fun textureName(name: String): String = "heads/${name.lowercase()}.png"

    /** A face is square and drawn whole, so the pen moves by its size plus one. */
    fun advance(name: String, size: Int): Int = if (has(name)) nearestSize(size) + 1 else 0

    fun textures(): Map<String, ByteArray> = heads.entries.associate { (name, image) ->
        textureName(name) to ByteArrayOutputStream().also { ImageIO.write(image, "PNG", it) }.toByteArray()
    }

    fun fontJson(size: Int): String {
        val advances = Glyphs.spacers().entries.joinToString(", ") { (char, advance) ->
            "\"${Fonts.escapeJson(char)}\": $advance"
        }
        val providers = order.mapIndexed { index, name ->
            """{"type": "bitmap", "file": "voidrp:${textureName(name)}", "ascent": 0,
                "height": ${nearestSize(size)},
                "chars": ["${Fonts.escapeJson(String(Character.toChars(BASE + index)))}"]}"""
        }
        return Fonts.compact(
            """{"providers": [{"type": "space", "advances": { $advances }}${
                if (providers.isEmpty()) "" else ", " + providers.joinToString(", ")
            }]}"""
        )
    }

    /** For tests: a skin made up on the spot rather than read from disk. */
    fun loadForTest(name: String, skin: BufferedImage) {
        face(skin)?.let {
            heads = mapOf(name.lowercase() to it)
            order = listOf(name.lowercase())
        }
    }
}
