package ru.voidrp.ui.pack

/**
 * The character map shared by the pack (which defines the glyphs) and the encoder (which
 * writes them). Both sides must agree on every code point, so they live in one place.
 *
 * All characters sit in the Private Use Area, so they can never collide with real text.
 */
object Glyphs {

    /** Rectangle sides go up to 2^10 = 1024 canvas pixels in each direction. */
    const val MAX_EXP = 10

    private const val RECT_BASE = 0xE000
    private const val SPACER_BASE = 0xE200

    /** The glyph that draws a rectangle 2^w wide and 2^h tall. */
    fun rect(w: Int, h: Int): String = cp(RECT_BASE + w * (MAX_EXP + 1) + h)

    /** Rectangles with the same aspect ratio share a texture. */
    fun textureName(w: Int, h: Int): String = when {
        w == h -> "rect_1x1"
        w > h -> "rect_${1 shl (w - h)}x1"
        else -> "rect_1x${1 shl (h - w)}"
    }

    /**
     * A bitmap glyph advances the pen by its width plus one pixel of letter spacing. The
     * encoder counts on this to know where the pen stands after each rectangle.
     */
    fun rectAdvance(w: Int): Int = (1 shl w) + 1

    /** Spacer characters: index 2k moves the pen by +2^k, index 2k+1 by −2^k. */
    fun spacers(): Map<String, Int> = buildMap {
        for (k in 0..MAX_EXP) {
            put(spacer(k, forward = true), 1 shl k)
            put(spacer(k, forward = false), -(1 shl k))
        }
    }

    fun spacer(k: Int, forward: Boolean): String = cp(SPACER_BASE + k * 2 + if (forward) 0 else 1)

    /** Spacer characters that together move the pen by exactly [delta] pixels. */
    fun moveBy(delta: Int): String {
        if (delta == 0) return ""
        val forward = delta > 0
        var rest = kotlin.math.abs(delta)
        val out = StringBuilder()
        var k = MAX_EXP
        while (rest > 0) {
            val step = 1 shl k
            while (rest >= step) {
                out.append(spacer(k, forward))
                rest -= step
            }
            k--
        }
        return out.toString()
    }

    private fun cp(codePoint: Int): String = String(Character.toChars(codePoint))
}
