package ru.voidrp.ui.layout

/**
 * The window a page is drawn into, measured in canvas units.
 *
 * ### Why a page needs one at all
 *
 * The height is fixed: 1024 units is the height of the player's window, whatever its
 * resolution and whatever GUI scale they play at. The width is not. A window is as wide as
 * it is, and a unit has to stay square — a circle that goes oval on a 4:3 monitor is the
 * difference between an interface and a stretched picture of one.
 *
 * So the width is the shape of the player's screen: 1820 on 16:9, 1365 on 4:3, 2389 on
 * 21:9. A page is laid out against that number the way a web page is laid out against the
 * width of the browser window, and the same layout rules — fill the space, share it out,
 * wrap when there is not enough — do the rest.
 *
 * ### Where the number comes from
 *
 * The server cannot see the player's window: a vanilla client never sends its size, and
 * there is no packet to ask with. What it can do is be told — the player picks their
 * screen once and it is remembered — and until then assume the commonest shape.
 *
 * Being wrong is not a disaster, because a page keeps what matters inside [safeWidth], the
 * band that is on screen whatever the shape. Backgrounds and decoration may run past it;
 * nothing a player has to read or click does.
 */
data class Viewport(val width: Int, val height: Int = HEIGHT) {

    /** Width over height — 1.78 on 16:9. */
    val aspect: Double get() = width.toDouble() / height

    /**
     * The width that is on screen for everyone, centred.
     *
     * A page laid out for 16:9 and opened on 4:3 loses a quarter of its width off the
     * sides. This is what is left when that happens — the 4:3 band — and putting content
     * inside it is what makes a page survive a screen it was not laid out for.
     */
    val safeWidth: Int get() = minOf(width, SAFE)

    /** How much is cut off each side if the player's screen is narrower than believed. */
    val sideBleed: Int get() = (width - safeWidth) / 2

    /** Which of the three shapes this is, for pages that lay out differently on each. */
    val size: Class
        get() = when {
            width < COMPACT_MAX -> Class.COMPACT
            width < REGULAR_MAX -> Class.REGULAR
            else -> Class.WIDE
        }

    /** Picks the value for this screen, the way a stylesheet picks by breakpoint. */
    fun <T> by(compact: T, regular: T, wide: T = regular): T = when (size) {
        Class.COMPACT -> compact
        Class.REGULAR -> regular
        Class.WIDE -> wide
    }

    /**
     * How many columns of [ideal] units fit, between [min] and [max].
     *
     * The everyday responsive decision: three cards across on a wide screen, two on a
     * narrow one, without the page working it out from raw numbers every time.
     */
    fun columns(ideal: Int, min: Int = 1, max: Int = 6, gap: Int = 0, room: Int = width): Int =
        ((room + gap) / (ideal + gap)).coerceIn(min, max)

    /** The shapes a screen comes in, named the way a stylesheet names its breakpoints. */
    enum class Class { COMPACT, REGULAR, WIDE }

    companion object {
        /** Canvas units from the top of the window to the bottom, always. */
        const val HEIGHT = 1024

        /** 4:3 — the narrowest shape anyone still plays on, and so the safe band. */
        const val SAFE = 1365

        /** Below this a screen is 4:3 or 5:4: the page needs fewer columns. */
        const val COMPACT_MAX = 1500

        /** At or above this the screen is ultrawide and a page can spread out. */
        const val REGULAR_MAX = 2100

        /** What a player is assumed to have until they say otherwise. */
        val DEFAULT = of(16, 9)

        /** The screen shapes players actually have, in the order they are offered. */
        val PRESETS: Map<String, Viewport> = linkedMapOf(
            "5:4" to of(5, 4),
            "4:3" to of(4, 3),
            "3:2" to of(3, 2),
            "16:10" to of(16, 10),
            "16:9" to of(16, 9),
            "21:9" to of(21, 9),
            "32:9" to of(32, 9),
        )

        /** The canvas for a screen of this shape — 16 by 9, or 1920 by 1080, both work. */
        fun of(across: Int, down: Int): Viewport =
            Viewport(Math.round(HEIGHT.toDouble() * across / down).toInt())

        /**
         * Reads what a player typed: `16:9`, `1920x1080`, or a width in canvas units.
         *
         * Resolutions are what a player can actually read off their settings screen, so
         * both spellings are accepted and both mean the same thing — only the shape counts.
         */
        fun parse(text: String): Viewport? {
            val cleaned = text.trim().lowercase().replace(',', '.')
            PRESETS[cleaned]?.let { return it }
            val parts = cleaned.split(':', 'x', '/', '×')
            if (parts.size == 2) {
                val across = parts[0].trim().toDoubleOrNull() ?: return null
                val down = parts[1].trim().toDoubleOrNull() ?: return null
                if (across <= 0 || down <= 0) return null
                return Viewport(Math.round(HEIGHT * across / down).toInt().coerceIn(640, 4096))
            }
            val width = cleaned.toIntOrNull() ?: return null
            return if (width in 640..4096) Viewport(width) else null
        }

        /** The preset closest to this canvas, for showing a player what they are set to. */
        fun name(viewport: Viewport): String =
            PRESETS.entries.minByOrNull { Math.abs(it.value.width - viewport.width) }
                ?.takeIf { Math.abs(it.value.width - viewport.width) <= 24 }?.key
                ?: "${viewport.width}×${viewport.height}"
    }
}
