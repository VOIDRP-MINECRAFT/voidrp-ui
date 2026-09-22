package ru.voidrp.ui.style

import java.util.concurrent.ConcurrentHashMap
import ru.voidrp.ui.pack.Glyphs

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
    private val expressed = ConcurrentHashMap<Long, Paint>()

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

    /**
     * How to draw [target] when [over] is known to be behind it.
     *
     * Naming a colour outright spends the whole budget on one axis: ten bits, and at the
     * dark end of them the steps are as coarse as the colours a dark interface is made of
     * — a page at #060711 and a card at #090b16 both land on the same entry, and the card
     * stops being a card. Opacity is a second axis with sixteen steps of its own, and what
     * the eye sees is the two composed. So both are chosen together: of every palette
     * colour at every opacity, the pair whose result over [over] looks nearest.
     *
     * It lands the site's own surface colours within a unit or two, where naming them
     * missed by ten to twenty.
     */
    fun express(target: Int, over: Int, after: Int? = null): Paint {
        val key = (target.toLong() shl 40) or (over.toLong() shl 16) or ((after ?: -1).toLong() and 0xFFFF)
        return expressed.getOrPut(key) { searchExpressed(target, over, after) }
    }

    private fun searchExpressed(target: Int, over: Int, after: Int?): Paint {
        var best = Paint(nearest(target))
        var bestDistance = Double.MAX_VALUE
        val wanted = oklab(target shr 16 and 0xFF, target shr 8 and 0xFF, target and 0xFF)
        // In a wash, what the eye picks out is not a stripe being a unit off the colour it
        // wanted — it is one stripe sitting far from the next. So where two pairs are
        // nearly as good, the one that follows on from the stripe before wins.
        val previous = after?.let { oklab(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) }
        for (code in 0 until (1 shl 10)) {
            val colour = rgbOf(code)
            for (step in 1..Glyphs.ALPHA_LEVELS) {
                val alpha = step.toDouble() / Glyphs.ALPHA_LEVELS
                val r = mix(colour shr 16 and 0xFF, over shr 16 and 0xFF, alpha)
                val g = mix(colour shr 8 and 0xFF, over shr 8 and 0xFF, alpha)
                val b = mix(colour and 0xFF, over and 0xFF, alpha)
                val lab = oklab(r, g, b)
                val distance = (lab[0] - wanted[0]) * (lab[0] - wanted[0]) +
                    (lab[1] - wanted[1]) * (lab[1] - wanted[1]) +
                    (lab[2] - wanted[2]) * (lab[2] - wanted[2])
                val cost = if (previous == null) {
                    distance
                } else {
                    distance + CONTINUITY * (
                        (lab[0] - previous[0]) * (lab[0] - previous[0]) +
                            (lab[1] - previous[1]) * (lab[1] - previous[1]) +
                            (lab[2] - previous[2]) * (lab[2] - previous[2])
                        )
                }
                if (cost < bestDistance) {
                    bestDistance = cost
                    best = Paint(colour, alpha)
                }
            }
        }
        return best
    }

    /** How much a stripe is pulled towards the one before it, against its own colour. */
    private const val CONTINUITY = 2.0

    /** What a colour over another actually comes out as — the composite the eye will see. */
    fun composite(paint: Paint, over: Int): Int {
        val colour = nearest(paint.rgb)
        val alpha = Glyphs.alphaLevel(paint.alpha).toDouble() / Glyphs.ALPHA_LEVELS
        return (mix(colour shr 16 and 0xFF, over shr 16 and 0xFF, alpha) shl 16) or
            (mix(colour shr 8 and 0xFF, over shr 8 and 0xFF, alpha) shl 8) or
            mix(colour and 0xFF, over and 0xFF, alpha)
    }

    private fun mix(top: Int, bottom: Int, alpha: Double): Int =
        Math.round(top * alpha + bottom * (1.0 - alpha)).toInt().coerceIn(0, 255)

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
