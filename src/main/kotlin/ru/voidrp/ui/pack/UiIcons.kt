package ru.voidrp.ui.pack

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * The interface's own icons — a wallet, a sword, a clock — as opposed to pictures of items.
 *
 * They are the same set the website and the in-game WebGUI use, rendered from those very
 * SVGs at each size a page asks for, because a stroke icon scaled by the client turns to
 * mush. They are baked white, so the fill colour in the glyph tints them like any other
 * shape: the same icon is dim beside a caption and violet inside a chosen card.
 *
 * Lucide icons, ISC licence — see THIRD-PARTY.md.
 */
object UiIcons {

    /** Sizes the set is baked at. A page gets the nearest one rather than a blurry scale. */
    val SIZES = listOf(12, 16, 20, 24, 32, 48, 64)

    private const val BASE = 0xEA00

    /** Every icon in the set, in the order their code points run. */
    val NAMES: List<String> by lazy {
        UiIcons::class.java.getResourceAsStream("/icons/ui/index.txt")
            ?.bufferedReader()
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.sorted()
            ?: emptyList()
    }

    private val index: Map<String, Int> by lazy { NAMES.withIndex().associate { (i, name) -> name to i } }

    /**
     * A resource path in Minecraft must be lower case, and a font whose provider names a
     * file that cannot exist is thrown away whole — every glyph in it, not just that one.
     * That is what turned a page of icons into a page of empty squares: the set has names
     * like `chevronRight`, and the file beside them had to be `chevronright`.
     */
    private fun key(name: String): String = name.lowercase()

    fun fontName(size: Int): String = "ui_icons_$size"

    fun nearestSize(size: Int): Int = SIZES.minByOrNull { Math.abs(it - size) } ?: SIZES.first()

    fun has(name: String): Boolean = index.containsKey(key(name))

    fun glyph(name: String): String? = index[key(name)]?.let { String(Character.toChars(BASE + it)) }

    /** The picture itself, as it is shipped — the pack copies these in. */
    fun png(name: String, size: Int): ByteArray? =
        UiIcons::class.java.getResourceAsStream("/icons/ui/${key(name)}_${nearestSize(size)}.png")?.readBytes()

    fun textureName(name: String, size: Int): String = "ui/${key(name)}_${nearestSize(size)}.png"

    /**
     * How far the pen moves past an icon: the width of its ink plus one, measured from the
     * picture we ship — the same rule the client applies, and the reason a page with icons
     * on it does not drift sideways.
     */
    fun advance(name: String, size: Int): Int =
        inkWidths.getValue(nearestSize(size))[key(name)]?.plus(1) ?: (size + 1)

    private val inkWidths: Map<Int, Map<String, Int>> by lazy {
        SIZES.associateWith { size ->
            NAMES.mapNotNull { name ->
                val bytes = png(name, size) ?: return@mapNotNull null
                val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@mapNotNull null
                var ink = 0
                for (x in image.width - 1 downTo 0) {
                    if ((0 until image.height).any { y -> image.getRGB(x, y) ushr 24 != 0 }) {
                        ink = x + 1
                        break
                    }
                }
                name to ink
            }.toMap()
        }
    }

    /** One font per size, each naming every icon at that size. */
    fun fontJson(size: Int): String {
        val advances = Glyphs.spacers().entries.joinToString(", ") { (char, advance) ->
            "\"${Fonts.escapeJson(char)}\": $advance"
        }
        val providers = NAMES.mapIndexed { i, name ->
            """{"type": "bitmap", "file": "voidrp:${textureName(name, size)}", "ascent": 0,
                "height": ${nearestSize(size)}, "chars": ["${Fonts.escapeJson(String(Character.toChars(BASE + i)))}"]}"""
        }
        return Fonts.compact(
            """{"providers": [{"type": "space", "advances": { $advances }},
            ${providers.joinToString(", ")}]}"""
        )
    }
}
