package ru.voidrp.ui.pack

/**
 * Writing font files.
 *
 * This used to build fonts out of Minecraft's own glyph textures at larger sizes. Inter
 * replaced them — the interface should look like the rest of VoidRP, not like a chat
 * message — and all that survived is the escaping, which is the part that was subtly
 * wrong for a while and is worth keeping in one place.
 */
object Fonts {

    /**
     * Squeezes the whitespace out of a font file.
     *
     * These files declare a glyph per line for thousands of items, and the indentation
     * that makes the generator readable is a megabyte the player has to download.
     */
    fun compact(json: String): String = json.replace(Regex("\\s*\\n\\s*"), " ")


    /**
     * JSON escaping that survives characters outside the basic plane.
     *
     * Writing every code point as \uXXXX broke them: a five-digit escape like \u10330 is
     * read as \u1033 followed by "0", which shifted whole rows of a font's grid and left
     * every letter missing. Only what JSON actually requires is escaped; the rest goes out
     * as UTF-8.
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
}
