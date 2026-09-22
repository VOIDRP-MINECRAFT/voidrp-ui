package ru.voidrp.ui.pack

/**
 * The character map shared by the pack (which defines the glyphs) and the encoder (which
 * writes them). Both sides must agree on every code point, so they live in one place.
 *
 * All characters sit in the Private Use Area, so they can never collide with real text.
 *
 * ### Where opacity comes from
 *
 * A text component carries no alpha — 24 bits of colour and nothing else — and those bits
 * are already spent on the vertical position and the fill. So opacity is not sent with the
 * element at all: the same alphabet is baked at several opacities, one font each, and the
 * encoder picks the font. A run drawn in `ui_a2` comes out at a quarter opacity because
 * its glyph textures are, and the shader never learns about it.
 */
object Glyphs {

    /**
     * Rectangle sides go up to 2^9 = 512 canvas units in each direction.
     *
     * A full-screen fill was not drawn at all while the alphabet went up to 1024: the
     * client renders a 512-unit glyph happily and a 1024-unit one not at all, so shapes
     * are built from pieces no larger than that.
     */
    const val MAX_EXP = 9

    /** Opacity steps: font `ui_aN` draws at N/16, so 1 is 6.25% and 16 is opaque. */
    const val ALPHA_LEVELS = 16

    /**
     * How lopsided a rectangle glyph may be: at most 2^7 = 128 to 1.
     *
     * A glyph's texture is stored in the client's font atlas at its own resolution, and a
     * very flat rectangle needs a very wide texture — a 512×1 strip for a one-pixel rule.
     * Past a couple of hundred pixels the atlas will not take it, the glyph silently
     * disappears, and because its width is then not what the encoder counted on, the whole
     * page slides sideways. Flat shapes are split into several pieces instead.
     */
    const val MAX_ASPECT_EXP = 7

    /**
     * Corner radii, in canvas pixels. A rounded box is four of these plus plain
     * rectangles, so only these sizes exist — like a design system's radius scale.
     */
    val RADII = listOf(3, 4, 5, 6, 7, 8, 11, 12, 15, 16, 19, 20, 23, 24)

    /** The pointer, drawn from a texture of its own so it costs one glyph, not thirty. */
    const val CURSOR_SIZE = Pointer.SIZE

    private const val CURSOR_CODE = 0xE900
    private const val RECT_BASE = 0xE000
    private const val CORNER_BASE = 0xE400
    private const val RING_BASE = 0xE600
    private const val SPACER_BASE = 0xE800

    /** The font that draws shapes at [level]/8 opacity. */
    fun fontName(level: Int): String = "ui_a${level.coerceIn(1, ALPHA_LEVELS)}"

    /** Rounds an opacity to the nearest step the alphabet is baked at; 0 means invisible. */
    fun alphaLevel(alpha: Double): Int =
        Math.round(alpha * ALPHA_LEVELS).toInt().coerceIn(0, ALPHA_LEVELS)

    /** The nearest radius the alphabet is baked at, never larger than [max]. */
    fun nearestRadius(radius: Int, max: Int): Int {
        val usable = RADII.filter { it <= max }
        if (radius <= 0 || usable.isEmpty()) return 0
        return usable.minByOrNull { Math.abs(it - radius) }!!
    }

    fun cursor(): String = cp(CURSOR_CODE)

    fun cursorAdvance(): Int = Pointer.INK_WIDTH + 1

    /** Whether a rectangle of these proportions exists in the alphabet. */
    fun hasRect(w: Int, h: Int): Boolean = Math.abs(w - h) <= MAX_ASPECT_EXP

    /** The glyph that draws a rectangle 2^w wide and 2^h tall. */
    fun rect(w: Int, h: Int): String = cp(RECT_BASE + w * (MAX_EXP + 1) + h)

    /** Rectangles with the same aspect ratio share a texture. */
    fun textureName(w: Int, h: Int): String = when {
        w == h -> "rect_1x1"
        w > h -> "rect_${1 shl (w - h)}x1"
        else -> "rect_1x${1 shl (h - w)}"
    }

    /** Which corner of a box a rounded glyph fills the outside of. */
    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** The glyph that rounds one corner of a box with radius [radius]. */
    fun corner(radius: Int, corner: Corner): String =
        cp(CORNER_BASE + RADII.indexOf(radius) * Corner.entries.size + corner.ordinal)

    fun cornerTextureName(radius: Int, corner: Corner): String =
        "corner_${radius}_${corner.name.lowercase()}"

    /**
     * The outline of a corner rather than the whole of it.
     *
     * A border drawn with filled quarter discs looked wrong: the card's own fill goes over
     * them, so the corners blended twice and came out brighter than the straight sides —
     * four pale brackets around every panel. An arc one unit thick leaves nothing under
     * the fill to blend with.
     */
    fun ringCorner(radius: Int, corner: Corner): String =
        cp(RING_BASE + RADII.indexOf(radius) * Corner.entries.size + corner.ordinal)

    fun ringTextureName(radius: Int, corner: Corner): String =
        "ring_${radius}_${corner.name.lowercase()}"

    /**
     * A bitmap glyph advances the pen by its width plus one pixel of letter spacing. The
     * encoder counts on this to know where the pen stands after each shape.
     */
    fun rectAdvance(w: Int): Int = (1 shl w) + 1

    fun cornerAdvance(radius: Int): Int = radius + 1

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
