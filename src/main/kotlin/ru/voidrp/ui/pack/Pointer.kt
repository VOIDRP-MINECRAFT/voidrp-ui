package ru.voidrp.ui.pack

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * The pointer.
 *
 * It is drawn here rather than shipped so that its ink can be measured: the client moves
 * the pen past a glyph by the width of what is actually drawn, and the pointer is an arrow
 * inside a square of mostly empty space. Claiming the whole square made the encoder think
 * the line was wider than it was, and the boss bar's centring turned the difference into a
 * sliver of daylight down the side of the page.
 */
object Pointer {

    /** The square the arrow is drawn in, in canvas units. */
    const val SIZE = 18

    /** How wide the arrow really is — measured, not assumed. */
    val INK_WIDTH: Int by lazy { inkWidth(image()) }

    fun png(alpha: Double): ByteArray {
        val image = image()
        if (alpha < 1.0) {
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                val argb = image.getRGB(x, y)
                val a = Math.round((argb ushr 24) * alpha).toInt()
                image.setRGB(x, y, (a shl 24) or (argb and 0xFFFFFF))
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", out)
        return out.toByteArray()
    }

    /** A white arrow with a dark edge, drawn with its own colours so no tint touches it. */
    private fun image(): BufferedImage {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val arrow = Path2D.Double()
        arrow.moveTo(1.5, 1.0)
        arrow.lineTo(1.5, 13.5)
        arrow.lineTo(5.0, 10.4)
        arrow.lineTo(7.4, 16.4)
        arrow.lineTo(10.0, 15.3)
        arrow.lineTo(7.7, 9.6)
        arrow.lineTo(12.2, 9.6)
        arrow.closePath()

        g.color = Color(0x10, 0x14, 0x26)
        g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(arrow)
        g.color = Color.WHITE
        g.fill(arrow)
        g.dispose()
        return image
    }

    private fun inkWidth(image: BufferedImage): Int {
        for (column in SIZE - 1 downTo 0) {
            for (row in 0 until SIZE) {
                if (image.getRGB(column, row) ushr 24 != 0) return column + 1
            }
        }
        return SIZE
    }
}
