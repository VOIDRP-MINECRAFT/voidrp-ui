package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import ru.voidrp.ui.render.Painter
import ru.voidrp.ui.style.Gradient
import ru.voidrp.ui.style.GradientDirection
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Palette

/**
 * The transport carries ten bits of colour, and the dark end of that grid is coarse enough
 * that a careless landing changes the hue rather than the shade. These are the colours the
 * theme actually uses, and what matters about each of them.
 */
class PaletteTest {

    private fun shown(rgb: Int): Triple<Int, Int, Int> {
        val out = Palette.nearest(rgb)
        return Triple(out shr 16 and 0xFF, out shr 8 and 0xFF, out and 0xFF)
    }

    @Test
    fun `a dark violet is still violet`() {
        // Rounding each channel on its own turned this one into a warm grey — a lilac card
        // came out brown.
        listOf(0x141033, 0x0F1526, 0x1B1140, 0x05060D).forEach { rgb ->
            val (r, g, b) = shown(rgb)
            assertTrue(b >= r, "#%06x turned warm: r=$r g=$g b=$b".format(rgb))
        }
    }

    @Test
    fun `an accent keeps its hue`() {
        val (r, g, b) = shown(0x8B7BFF)
        assertTrue(b > r && r > g, "the accent is no longer violet: r=$r g=$g b=$b")
    }

    @Test
    fun `gold and green do not swap places`() {
        val (gr, gg, gb) = shown(0xFBBF24)
        assertTrue(gr > gg && gg > gb, "gold is not gold: r=$gr g=$gg b=$gb")
        val (er, eg, eb) = shown(0x34D399)
        assertTrue(eg > er && eg > eb, "green is not green: r=$er g=$eg b=$eb")
    }

    @Test
    fun `no colour lands further away than plain rounding would`() {
        // The search may only improve on the obvious answer, never lose to it.
        var worse = 0
        for (rgb in 0 until 0x1000000 step 977) {
            val ours = Palette.nearest(rgb)
            val plain = Palette.rgbOf(
                (Math.round((rgb shr 16 and 0xFF) * 7 / 255.0).toInt() shl 7) or
                    (Math.round((rgb shr 8 and 0xFF) * 15 / 255.0).toInt() shl 3) or
                    Math.round((rgb and 0xFF) * 7 / 255.0).toInt(),
            )
            if (distance(rgb, ours) > distance(rgb, plain) + 1e-9) worse++
        }
        assertTrue(worse == 0, "$worse colours landed further away than plain rounding puts them")
    }

    @Test
    fun `a wash over a known surface has no visible steps`() {
        // The complaint that started this: a fade drawn by interpolating the colour, or by
        // fading the opacity of one, came out in stripes ten units of blue apart. Told what
        // is behind it, the painter lays a floor and expresses each stripe against that,
        // and the steps come down to a few units — which at these widths cannot be picked
        // out. If this ever climbs back, the welcome panel is banded again.
        val backdrop = 0x070710
        val out = mutableListOf<ru.voidrp.ui.render.Node>()
        Painter.fill(
            0,
            0,
            944,
            186,
            0,
            Gradient(
                Paint(0x32295F),
                Paint(0x16112C),
                direction = GradientDirection.HORIZONTAL,
                over = backdrop,
                stop = 0.45,
            ),
            out,
        )
        val rects = out.filterIsInstance<ru.voidrp.ui.render.Rect>()
        val floor = Palette.composite(rects.first().paint, backdrop)
        val shades = rects.drop(1).sortedBy { it.x }.map { Palette.composite(it.paint, floor) }
        assertTrue(shades.size > 8, "only ${shades.size} stripes — the fade came out too coarse")
        val worst = shades.zipWithNext().maxOf { (a, b) ->
            maxOf(
                Math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)),
                Math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)),
                Math.abs((a and 0xFF) - (b and 0xFF)),
            )
        }
        assertTrue(worst <= 6, "$worst units between neighbouring stripes — that shows as a band")
    }

    private fun distance(a: Int, b: Int): Double {
        fun lab(rgb: Int): DoubleArray {
            fun linear(c: Int): Double {
                val v = c / 255.0
                return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
            }
            val lr = linear(rgb shr 16 and 0xFF)
            val lg = linear(rgb shr 8 and 0xFF)
            val lb = linear(rgb and 0xFF)
            val l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
            val m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
            val s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
            return doubleArrayOf(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
            )
        }
        val x = lab(a)
        val y = lab(b)
        return (x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2])
    }
}
