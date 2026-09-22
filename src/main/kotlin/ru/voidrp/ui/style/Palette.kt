package ru.voidrp.ui.style

import java.util.concurrent.ConcurrentHashMap

/**
 * The colours the transport can actually carry, and how to land on the right one.
 *
 * Ten bits of the glyph's colour are the fill: three for red, four for green, three for
 * blue. Rounding each channel on its own is the obvious way to land on that grid and the
 * wrong one at the dark end, where the steps are as large as the colour itself. The
 * panel colour `#141033` — a dark violet — has 20 of red, 16 of green and 51 of blue;
 * round each channel to its nearest step and red climbs to 36 while blue falls to 36. The
 * numbers are as close as they can be and the colour is no longer violet: it comes out a
 * warm grey, which is how a lilac card ends up looking brown.
 *
 * So the choice is made over the whole colour rather than one channel at a time: of the
 * eight steps around the target, take the one that looks nearest. "Looks" is Oklab, where
 * a straight distance matches what the eye reports — in it, the blue-violet neighbour wins
 * over the numerically closer grey one.
 */
object Palette {

    private const val RED_STEPS = 7
    private const val GREEN_STEPS = 15
    private const val BLUE_STEPS = 7

    private val cache = ConcurrentHashMap<Int, Int>()

    /** The ten bits to send for a colour: RGB 3-4-3. */
    fun code(rgb: Int): Int = cache.getOrPut(rgb and 0xFFFFFF) { search(rgb) }

    /** The colour the client will actually draw for a code — what the eye will see. */
    fun rgbOf(code: Int): Int {
        val r = Math.round((code shr 7 and 7) * 255.0 / RED_STEPS).toInt()
        val g = Math.round((code shr 3 and 15) * 255.0 / GREEN_STEPS).toInt()
        val b = Math.round((code and 7) * 255.0 / BLUE_STEPS).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    /** The colour a paint will really come out as, for anything that has to match it. */
    fun nearest(rgb: Int): Int = rgbOf(code(rgb))

    private fun search(rgb: Int): Int {
        val target = oklab(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
        var best = 0
        var bestDistance = Double.MAX_VALUE
        neighbours(rgb shr 16 and 0xFF, RED_STEPS).forEach { r ->
            neighbours(rgb shr 8 and 0xFF, GREEN_STEPS).forEach { g ->
                neighbours(rgb and 0xFF, BLUE_STEPS).forEach { b ->
                    val code = (r shl 7) or (g shl 3) or b
                    val shown = rgbOf(code)
                    val lab = oklab(shown shr 16 and 0xFF, shown shr 8 and 0xFF, shown and 0xFF)
                    val distance = (lab[0] - target[0]) * (lab[0] - target[0]) +
                        (lab[1] - target[1]) * (lab[1] - target[1]) +
                        (lab[2] - target[2]) * (lab[2] - target[2])
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = code
                    }
                }
            }
        }
        return best
    }

    /** The steps either side of a channel's value — the grid points worth trying. */
    private fun neighbours(value: Int, steps: Int): IntArray {
        val exact = value * steps / 255.0
        val low = Math.floor(exact).toInt().coerceIn(0, steps)
        val high = Math.ceil(exact).toInt().coerceIn(0, steps)
        return if (low == high) intArrayOf(low) else intArrayOf(low, high)
    }

    private fun oklab(r: Int, g: Int, b: Int): DoubleArray {
        val lr = linear(r)
        val lg = linear(g)
        val lb = linear(b)
        val l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
        val m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
        val s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
}
