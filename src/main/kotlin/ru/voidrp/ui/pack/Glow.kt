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

    fun image(
        part: Glyphs.GlowPart,
        corner: Glyphs.Corner,
        step: Int,
        level: Int,
        radius: Int = 0,
    ): BufferedImage = cache.getOrPut("$part/$corner/$step/$level/$radius") { draw(part, corner, step, level, radius) }

    fun png(part: Glyphs.GlowPart, corner: Glyphs.Corner, step: Int, level: Int, radius: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(image(part, corner, step, level, radius), "PNG", out)
        return out.toByteArray()
    }

    /** What the client will advance the pen by: the width of what is actually drawn, plus one. */
    fun advance(part: Glyphs.GlowPart, corner: Glyphs.Corner, step: Int, level: Int, radius: Int = 0): Int {
        val image = image(part, corner, step, level, radius)
        for (x in image.width - 1 downTo 0) {
            for (y in 0 until image.height) {
                if (image.getRGB(x, y) ushr 24 != 0) return x + 2
            }
        }
        return 1
    }

    private fun draw(
        part: Glyphs.GlowPart,
        corner: Glyphs.Corner,
        step: Int,
        level: Int,
        radius: Int,
    ): BufferedImage {
        val spread = Glyphs.GLOW_SPREAD
        // A corner tile reaches past the edge by the spread and inwards by the radius, so
        // that the notch the rounding leaves is lit rather than left as a dark wedge.
        val reach = spread + radius
        val width = when (part) {
            Glyphs.GlowPart.HORIZONTAL -> step
            Glyphs.GlowPart.CORNER -> reach
            else -> spread
        }
        val height = when (part) {
            Glyphs.GlowPart.VERTICAL -> step
            Glyphs.GlowPart.CORNER -> reach
            else -> spread
        }
        val alpha = level.toDouble() / Glyphs.ALPHA_LEVELS
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) for (x in 0 until width) {
            // How far this pixel is from the edge of whatever casts the halo.
            val distance = when (part) {
                Glyphs.GlowPart.HORIZONTAL ->
                    if (corner == Glyphs.Corner.TOP_LEFT) spread - y - 0.5 else y + 0.5

                Glyphs.GlowPart.VERTICAL ->
                    if (corner == Glyphs.Corner.TOP_LEFT) spread - x - 0.5 else x + 0.5

                // Distance to the arc, not to the square corner: the halo follows the
                // rounding. Inside the arc is where the panel itself will be drawn, so
                // nothing is put there — a shadow at full strength under a translucent
                // card would show through it.
                Glyphs.GlowPart.CORNER -> {
                    val cx = if (corner == Glyphs.Corner.TOP_LEFT || corner == Glyphs.Corner.BOTTOM_LEFT) {
                        reach.toDouble()
                    } else {
                        0.0
                    }
                    val cy = if (corner == Glyphs.Corner.TOP_LEFT || corner == Glyphs.Corner.TOP_RIGHT) {
                        reach.toDouble()
                    } else {
                        0.0
                    }
                    // Stopping exactly at the arc leaves a hairline of background between
                    // the panel's antialiased edge and the halo. It tucks a unit
                    // under the edge instead, the way a box-shadow sits under a border box.
                    val toCentre = Math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                    val under = radius - 1.0
                    if (toCentre <= under) -1.0 else (toCentre - radius).coerceAtLeast(0.0)
                }
            }
            // Squared falloff reads as light rather than as a grey band — the shape a blur
            // leaves behind, without blurring.
            val fade = if (distance < 0.0) 0.0 else (1.0 - distance / spread).coerceIn(0.0, 1.0)
            val value = Math.round(fade * fade * alpha * 255).toInt()
            image.setRGB(x, y, (value shl 24) or 0xFFFFFF)
        }
        return image
    }
}
