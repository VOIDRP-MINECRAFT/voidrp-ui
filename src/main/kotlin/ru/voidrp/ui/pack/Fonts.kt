package ru.voidrp.ui.pack

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Text sizes, built from the client's own font textures.
 *
 * The pack declares fonts that point at `minecraft:font/ascii.png` and friends with a
 * bigger glyph height, so the client draws large letters itself and we never ship Mojang's
 * artwork. One font per size; the page picks a size, not a scale.
 *
 * The encoder also has to know how wide every letter is, because the pen has to land in
 * the right place for whatever comes next. Those widths are measured once from the same
 * textures (see `tools_extract_widths.py`) and shipped as a table.
 */
object Fonts {

    /** Multipliers of the 8-pixel cell: 8, 16, 24 and 32 canvas pixels tall. */
    val SIZES = listOf(1, 2, 3, 4)

    /** Vanilla's ascii ascent; everything else is positioned relative to it. */
    private const val BASE_ASCENT = 7

    private val table: GlyphTable by lazy {
        val stream = Fonts::class.java.getResourceAsStream("/font/vanilla_glyphs.json")
            ?: error("В плагине нет таблицы шрифтов /font/vanilla_glyphs.json")
        Gson().fromJson(stream.reader(Charsets.UTF_8), GlyphTable::class.java)
    }

    fun fontName(size: Int): String = "text_$size"

    /**
     * How far the pen moves for one character, including the pixel of letter spacing that
     * a bitmap glyph always adds, scaled so large text is not cramped.
     */
    fun advance(char: Char, size: Int): Int {
        if (char == ' ') return table.space * size
        val width = table.widths[char.toString()] ?: return table.space * size
        return width * size + size
    }

    fun width(text: String, size: Int): Int = text.sumOf { advance(it, size) }

    /** True when the client can draw this character in our fonts. */
    fun known(char: Char): Boolean = char == ' ' || table.widths.containsKey(char.toString())

    /**
     * One font per size: vanilla's own providers at a bigger height, plus the spacers, so a
     * whole label — letters and the gaps between them — is a single run of one font.
     *
     * Ascent 0 puts the top of a glyph cell on the baseline, matching the rectangles, so a
     * label's y is the top of its line. Providers that sit higher in vanilla (accents) keep
     * that difference.
     */
    fun fontJson(size: Int): String {
        val providers = table.providers.map { p ->
            val chars = p.chars.joinToString(", ") { "\"${escapeJson(it)}\"" }
            """{"type": "bitmap", "file": "${p.file}", "height": ${p.height * size},
               "ascent": ${(p.ascent - BASE_ASCENT) * size}, "chars": [$chars]}"""
        }
        val advances = buildList {
            add("\" \": ${table.space * size}")
            Glyphs.spacers().forEach { (char, advance) -> add("\"${escapeJson(char)}\": $advance") }
        }
        // The space provider goes first because the client lets the earliest provider win:
        // vanilla's own font is ordered the same way, and behind ascii.png the space would
        // otherwise come out as its blank bitmap glyph, one pixel wide, closing up every
        // gap between words and throwing the pen off for everything that follows.
        return """{"providers": [{"type": "space", "advances": {${advances.joinToString(", ")}}},
            ${providers.joinToString(", ")}]}"""
    }

    /**
     * JSON escaping that survives characters outside the basic plane. Writing every code
     * point as \\uXXXX broke them — a five-digit escape like \\u10330 is read as \\u1033
     * followed by "0", which shifted whole rows of the font grid and left every letter
     * missing. Only what JSON actually requires is escaped; the rest goes out as UTF-8.
     */
    fun escapeJson(text: String): String = buildString {
        text.forEach { ch ->
            when {
                ch == '"' || ch == '\\' -> append('\\').append(ch)
                ch.code < 0x20 -> append("\\u%04x".format(ch.code))
                else -> append(ch)
            }
        }
    }

    private data class GlyphTable(
        val cell: Int,
        val space: Int,
        val providers: List<Provider>,
        val widths: Map<String, Int>,
    )

    private data class Provider(
        val file: String,
        val ascent: Int,
        val height: Int,
        @SerializedName("cell_height") val cellHeight: Int,
        val chars: List<String>,
    )
}
