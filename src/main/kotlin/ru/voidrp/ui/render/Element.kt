package ru.voidrp.ui.render

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.pack.Shaders

/**
 * A filled rectangle in canvas coordinates (1920×1080, stretched over the window).
 *
 * This is the whole vocabulary the renderer knows for now. Layout, data binding and
 * reactivity will all end up producing a list of these.
 */
data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    /** 0xRRGGBB. Quantised to RGB 3-4-3 on the way to the client. */
    val colour: Int = 0xFFFFFF,
)

/**
 * Turns rectangles into the single line of text the client draws.
 *
 * The client lays the line out left to right, so horizontal placement is spacer glyphs
 * that move its pen; each rectangle is split into power-of-two pieces baked into the
 * font; and every piece's colour carries its y and fill for the shader to read.
 */
object GlyphEncoder {

    private val FONT: Key = Key.key("voidrp", "ui")
    private const val Y_MAX = (1 shl Shaders.Y_BITS) - 1

    fun encode(rects: List<Rect>): Component {
        val line = Component.text().font(FONT)
        var pen = 0

        for (rect in rects) {
            if (rect.width <= 0 || rect.height <= 0) continue
            val fill = quantise(rect.colour)
            var top = rect.y

            // Rows from the largest piece down, pieces left to right within each row.
            for (h in powersOfTwo(rect.height)) {
                val colour = TextColor.color(pack(top, fill))
                var left = rect.x
                for (w in powersOfTwo(rect.width)) {
                    line.append(spacer(left - pen))
                    line.append(piece(w, h, colour))
                    pen = left + Glyphs.rectAdvance(w)
                    left += 1 shl w
                }
                top += 1 shl h
            }
        }
        // Bring the pen back to zero so the whole line is zero wide: the boss bar centres
        // its text, and a zero-width line starts exactly at the centre of the screen,
        // which is what the shader measures x from.
        line.append(spacer(-pen))
        return line.build()
    }

    /** Exponents whose powers of two sum to [value], largest first (e.g. 600 → 9, 6, 4, 3). */
    private fun powersOfTwo(value: Int): List<Int> {
        val out = mutableListOf<Int>()
        var rest = value.coerceIn(0, (1 shl (Glyphs.MAX_EXP + 1)) - 1)
        for (k in Glyphs.MAX_EXP downTo 0) {
            if (rest >= 1 shl k) {
                out += k
                rest -= 1 shl k
            }
        }
        return out
    }

    private fun spacer(delta: Int): TextComponent = Component.text(Glyphs.moveBy(delta))

    private fun piece(w: Int, h: Int, colour: TextColor): TextComponent =
        Component.text(Glyphs.rect(w, h))
            .color(colour)
            // The shadow is separate vertices in a darkened colour the shader cannot
            // recognise; left alone it would sit stranded where the text was laid out.
            .shadowColor(ShadowColor.none())

    /** 0xRRGGBB → RGB 3-4-3 (red 0..7, green 0..15, blue 0..7). */
    private fun quantise(rgb: Int): Int {
        // Rounded, not truncated: truncation turned a dark navy #0B1220 into green-black.
        val r = Math.round((rgb shr 16 and 0xFF) * 7 / 255.0).toInt()
        val g = Math.round((rgb shr 8 and 0xFF) * 15 / 255.0).toInt()
        val b = Math.round((rgb and 0xFF) * 7 / 255.0).toInt()
        return (r shl 7) or (g shl 3) or b
    }

    /** Marker nibble, then y (10 bits), then fill (10 bits). */
    private fun pack(y: Int, fill: Int): Int {
        val qy = Math.round(y.toDouble() * Y_MAX / Shaders.CANVAS_HEIGHT).toInt().coerceIn(0, Y_MAX)
        val bits = (qy shl Shaders.COLOUR_BITS) or fill
        return (Shaders.MARKER shl 20) or bits
    }
}
