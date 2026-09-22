package ru.voidrp.ui.pack

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * The rounded corners: a filled quarter disc for a panel, an arc for its border.
 *
 * Both are drawn here rather than described by a formula, because the pen has to be
 * predicted from what is actually drawn. A corner is antialiased, so its outermost column
 * is barely lit — and once that faint column is multiplied by a low opacity it rounds away
 * to nothing, taking a unit of width with it. Assuming "radius plus one" was right for
 * solid corners and wrong for every faint one, which is the kind of error that moves a
 * whole page sideways.
 */
object Corners {

    private val cache = mutableMapOf<String, BufferedImage>()

    fun image(radius: Int, corner: Glyphs.Corner, ring: Boolean, level: Int): BufferedImage =
        cache.getOrPut("$radius/$corner/$ring/$level") { draw(radius, corner, ring, level) }

    fun png(radius: Int, corner: Glyphs.Corner, ring: Boolean, level: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(image(radius, corner, ring, level), "PNG", out)
        return out.toByteArray()
    }

    /** What the client will advance the pen by: the width of the ink, plus one. */
    fun advance(radius: Int, corner: Glyphs.Corner, ring: Boolean, level: Int): Int {
        val image = image(radius, corner, ring, level)
        for (x in image.width - 1 downTo 0) {
            for (y in 0 until image.height) {
                if (image.getRGB(x, y) ushr 24 != 0) return x + 2
            }
        }
        return 1
    }

    private fun draw(radius: Int, corner: Glyphs.Corner, ring: Boolean, level: Int): BufferedImage {
        val alpha = level.toDouble() / Glyphs.ALPHA_LEVELS
        // The centre of the circle is the inner corner of the piece — the one that touches
        // the rest of the panel.
        val cx = if (corner == Glyphs.Corner.TOP_LEFT || corner == Glyphs.Corner.BOTTOM_LEFT) radius.toDouble() else 0.0
        val cy = if (corner == Glyphs.Corner.TOP_LEFT || corner == Glyphs.Corner.TOP_RIGHT) radius.toDouble() else 0.0
        val image = BufferedImage(radius, radius, BufferedImage.TYPE_INT_ARGB)
        val steps = 4
        val inner = radius - 1.0

        for (y in 0 until radius) for (x in 0 until radius) {
            var covered = 0
            for (sx in 0 until steps) for (sy in 0 until steps) {
                val dx = x + (sx + 0.5) / steps - cx
                val dy = y + (sy + 0.5) / steps - cy
                val distance = dx * dx + dy * dy
                val within = distance <= radius.toDouble() * radius
                // A ring is the outermost unit of the disc and nothing else.
                if (if (ring) within && distance > inner * inner else within) covered++
            }
            val value = Math.round(covered.toDouble() / (steps * steps) * alpha * 255).toInt()
            image.setRGB(x, y, (value shl 24) or 0xFFFFFF)
        }
        return image
    }
}
