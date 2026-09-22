package ru.voidrp.ui.pack

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * The soft edge behind a panel: a shadow, or a glow in the accent colour.
 *
 * Blurring is impossible here — a vertex shader cannot see its neighbours — but a soft
 * edge is only a picture whose alpha fades, and that can be baked. A halo is built the way
 * a stylesheet's box-shadow would be if it had to be made of tiles: four corners with a
 * round falloff and four sides that repeat along their length.
 *
 * The pictures are white, so the glyph's colour decides whether a halo reads as shadow or
 * as light — one set of tiles serves both.
 *
 * The pack and the encoder both come here, because the two have to agree to the pixel: the
 * client moves the pen by the width of a glyph's ink, and at low opacity the faintest
 * columns of a fading picture round away to nothing, making that width depend on which
 * opacity the halo is drawn at.
 */
object Glow {

    private val cache = mutableMapOf<String, BufferedImage>()

    fun image(part: Glyphs.GlowPart, corner: Glyphs.Corner, step: Int, level: Int): BufferedImage =
        cache.getOrPut("$part/$corner/$step/$level") { draw(part, corner, step, level) }

    fun png(part: Glyphs.GlowPart, corner: Glyphs.Corner, step: Int, level: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(image(part, corner, step, level), "PNG", out)
        return out.toByteArray()
    }

    /** What the client will advance the pen by: the width of what is actually drawn, plus one. */
    fun advance(part: Glyphs.GlowPart, corner: Glyphs.Corner, step: Int, level: Int): Int {
        val image = image(part, corner, step, level)
        for (x in image.width - 1 downTo 0) {
            for (y in 0 until image.height) {
                if (image.getRGB(x, y) ushr 24 != 0) return x + 2
            }
        }
        return 1
    }

    private fun draw(part: Glyphs.GlowPart, corner: Glyphs.Corner, step: Int, level: Int): BufferedImage {
        val spread = Glyphs.GLOW_SPREAD
        val width = if (part == Glyphs.GlowPart.HORIZONTAL) step else spread
        val height = if (part == Glyphs.GlowPart.VERTICAL) step else spread
        val alpha = level.toDouble() / Glyphs.ALPHA_LEVELS
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) for (x in 0 until width) {
            // How far this pixel is from the edge of whatever casts the halo.
            val distance = when (part) {
                Glyphs.GlowPart.HORIZONTAL ->
                    if (corner == Glyphs.Corner.TOP_LEFT) spread - y - 0.5 else y + 0.5

                Glyphs.GlowPart.VERTICAL ->
                    if (corner == Glyphs.Corner.TOP_LEFT) spread - x - 0.5 else x + 0.5

                Glyphs.GlowPart.CORNER -> {
                    val cx = if (corner == Glyphs.Corner.TOP_LEFT || corner == Glyphs.Corner.BOTTOM_LEFT) {
                        spread.toDouble()
                    } else {
                        0.0
                    }
                    val cy = if (corner == Glyphs.Corner.TOP_LEFT || corner == Glyphs.Corner.TOP_RIGHT) {
                        spread.toDouble()
                    } else {
                        0.0
                    }
                    Math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                }
            }
            // Squared falloff reads as light rather than as a grey band — the shape a blur
            // leaves behind, without blurring.
            val fade = (1.0 - distance / spread).coerceIn(0.0, 1.0)
            val value = Math.round(fade * fade * alpha * 255).toInt()
            image.setRGB(x, y, (value shl 24) or 0xFFFFFF)
        }
        return image
    }
}
