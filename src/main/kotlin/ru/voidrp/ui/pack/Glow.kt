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

    /**
     * The four corner tiles of one radius on one sheet, side by side.
     *
     * Same reason as the rounded corners: a grid in one picture instead of four files, and
     * the zip's table of contents stops being a third of the pack. Each tile is drawn into
     * its own cell, because the client measures a cell's ink to place the pen.
     */
    fun cornerSheet(radius: Int, level: Int): ByteArray {
        val reach = Glyphs.GLOW_SPREAD + radius
        val image = BufferedImage(reach * Glyphs.Corner.entries.size, reach, BufferedImage.TYPE_INT_ARGB)
        Glyphs.Corner.entries.forEachIndexed { column, corner ->
            val tile = image(Glyphs.GlowPart.CORNER, corner, 1, level, radius)
            for (y in 0 until tile.height) for (x in 0 until tile.width) {
                image.setRGB(column * reach + x, y, tile.getRGB(x, y))
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", out)
        return out.toByteArray()
    }

    /**
     * The eight side tiles of one edge on one sheet.
     *
     * Kept square on purpose. A font atlas will not take a long thin strip — a 1024 by 16
     * ribbon is exactly the shape that once made a glyph vanish and a page slide sideways —
     * so the tiles are stacked rather than laid end to end: the horizontal ones as eight
     * rows of 128 by 16, the vertical ones as eight columns of 16 by 128. Either way the
     * picture is 128 square.
     *
     * A tile shorter than its cell sits at the cell's start; the rest is nothing, which is
     * what the client draws and what our own measurement of its ink already says.
     */
    fun sideSheet(part: Glyphs.GlowPart, corner: Glyphs.Corner, level: Int): ByteArray {
        val span = Glyphs.GLOW_STEPS.max()
        val across = Glyphs.GLOW_SPREAD
        val horizontal = part == Glyphs.GlowPart.HORIZONTAL
        val image = if (horizontal) {
            BufferedImage(span, across * Glyphs.GLOW_STEPS.size, BufferedImage.TYPE_INT_ARGB)
        } else {
            BufferedImage(across * Glyphs.GLOW_STEPS.size, span, BufferedImage.TYPE_INT_ARGB)
        }
        Glyphs.GLOW_STEPS.forEachIndexed { index, step ->
            val tile = image(part, corner, step, level)
            val atX = if (horizontal) 0 else index * across
            val atY = if (horizontal) index * across else 0
            for (y in 0 until tile.height) for (x in 0 until tile.width) {
                image.setRGB(atX + x, atY + y, tile.getRGB(x, y))
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", out)
        return out.toByteArray()
    }

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
