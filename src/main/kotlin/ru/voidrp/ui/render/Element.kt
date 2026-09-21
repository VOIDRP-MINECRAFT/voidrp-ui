package ru.voidrp.ui.render

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import ru.voidrp.ui.pack.Fonts
import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme

/** Anything a page can draw, in canvas units (1820×1024 stretched over the window). */
sealed interface Node {
    val x: Int
    val y: Int
}

/** A filled rectangle. The colour is quantised to RGB 3-4-3, the opacity to eighths. */
data class Rect(
    override val x: Int,
    override val y: Int,
    val width: Int,
    val height: Int,
    val paint: Paint = Paint(0xFFFFFF),
) : Node

/** One rounded corner of a box: a quarter disc filling the inside of that corner. */
data class CornerPiece(
    override val x: Int,
    override val y: Int,
    val radius: Int,
    val corner: Glyphs.Corner,
    val paint: Paint,
) : Node

/**
 * A line of text. [size] is a multiple of the 8-pixel cell, so 2 is 16 canvas pixels tall.
 * [y] is the top of the line, like a rectangle's top edge. Text has no opacity of its own —
 * it is drawn from the client's own glyphs — so use a dimmer colour instead.
 */
data class Label(
    override val x: Int,
    override val y: Int,
    val text: String,
    val size: Int = 2,
    val colour: Int = Theme.INK,
) : Node {
    val width: Int get() = Fonts.width(text, size)
}

/**
 * A styled container — the thing pages are actually written with.
 *
 * It owns a rectangle of the canvas and a [Style]; [Painter] turns the two into the shapes
 * the client draws. Children are placed relative to the inside of the padding, so moving a
 * box moves everything in it.
 */
data class Box(
    override val x: Int,
    override val y: Int,
    val width: Int,
    val height: Int,
    val style: Style = Theme.card,
    val children: List<Node> = emptyList(),
) : Node

/**
 * Turns nodes into the single line of text the client draws.
 *
 * The client lays the line out left to right, so horizontal placement is spacer glyphs
 * that move its pen; rectangles are split into power-of-two pieces baked into the font;
 * and every glyph's colour carries its y and fill for the shader to read. Opacity is the
 * one thing not in the colour: it picks which of the shape fonts the run is written in.
 */
object GlyphEncoder {

    private const val Y_MAX = (1 shl Shaders.Y_BITS) - 1

    fun encode(nodes: List<Node>): Component {
        val line = Component.text()
        var pen = 0

        for (node in Painter.flatten(nodes)) {
            pen = when (node) {
                is Rect -> appendRect(line, node, pen)
                is CornerPiece -> appendCorner(line, node, pen)
                is Label -> appendLabel(line, node, pen)
                is Box -> pen // Painter has already expanded every box.
            }
        }
        // Bring the pen back to zero so the whole line is zero wide: the boss bar centres
        // its title, and a zero-width line starts exactly at the centre of the screen,
        // which is what the shader measures x from.
        line.append(shapes(Glyphs.moveBy(-pen), Glyphs.ALPHA_LEVELS))
        return line.build()
    }

    private fun appendRect(line: TextComponent.Builder, rect: Rect, penIn: Int): Int {
        val level = Glyphs.alphaLevel(rect.paint.alpha)
        if (rect.width <= 0 || rect.height <= 0 || level == 0) return penIn
        var pen = penIn
        val fill = quantise(rect.paint.rgb)
        var top = rect.y

        // Rows from the largest piece down, pieces left to right within each row.
        for (h in powersOfTwo(rect.height)) {
            val colour = TextColor.color(pack(top, fill))
            var left = rect.x
            for (w in powersOfTwo(rect.width)) {
                line.append(shapes(Glyphs.moveBy(left - pen) + Glyphs.rect(w, h), level).color(colour))
                pen = left + Glyphs.rectAdvance(w)
                left += 1 shl w
            }
            top += 1 shl h
        }
        return pen
    }

    private fun appendCorner(line: TextComponent.Builder, piece: CornerPiece, penIn: Int): Int {
        val level = Glyphs.alphaLevel(piece.paint.alpha)
        if (piece.radius !in Glyphs.RADII || level == 0) return penIn
        val colour = TextColor.color(pack(piece.y, quantise(piece.paint.rgb)))
        val glyph = Glyphs.moveBy(piece.x - penIn) + Glyphs.corner(piece.radius, piece.corner)
        line.append(shapes(glyph, level).color(colour))
        return piece.x + Glyphs.cornerAdvance(piece.radius)
    }

    /**
     * A label is one run in one font: the letters themselves plus spacer characters
     * between them, so large text keeps its letter spacing proportional.
     */
    private fun appendLabel(line: TextComponent.Builder, label: Label, penIn: Int): Int {
        val size = label.size.coerceIn(Fonts.SIZES.first(), Fonts.SIZES.last())
        val colour = TextColor.color(pack(label.y, quantise(label.colour)))
        val font = Key.key("voidrp", Fonts.fontName(size))
        var pen = penIn

        val run = StringBuilder(Glyphs.moveBy(label.x - pen))
        pen = label.x
        for (char in label.text) {
            if (!Fonts.known(char)) continue
            run.append(char)
            // A bitmap glyph always advances one extra pixel; the rest of the gap is ours.
            if (char != ' ' && size > 1) run.append(Glyphs.moveBy(size - 1))
            pen += Fonts.advance(char, size)
        }

        line.append(
            Component.text(run.toString())
                .font(font)
                .color(colour)
                .shadowColor(ShadowColor.none())
        )
        return pen
    }

    /** Exponents whose powers of two sum to [value], largest first (600 → 9, 6, 4, 3). */
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

    private fun shapes(text: String, level: Int): TextComponent =
        Component.text(text)
            .font(Key.key("voidrp", Glyphs.fontName(level)))
            // The shadow is separate vertices in a darkened colour the shader cannot
            // recognise; left alone it would sit stranded where the text was laid out.
            .shadowColor(ShadowColor.none())

    /** 0xRRGGBB → RGB 3-4-3. Rounded: truncation turned dark navy #0B1220 into green-black. */
    private fun quantise(rgb: Int): Int {
        val r = Math.round((rgb shr 16 and 0xFF) * 7 / 255.0).toInt()
        val g = Math.round((rgb shr 8 and 0xFF) * 15 / 255.0).toInt()
        val b = Math.round((rgb and 0xFF) * 7 / 255.0).toInt()
        return (r shl 7) or (g shl 3) or b
    }

    /**
     * Marker nibble, then y (10 bits), then fill (10 bits). One step is one canvas unit,
     * so a y survives the trip untouched and pieces of the same panel always meet exactly.
     */
    private fun pack(y: Int, fill: Int): Int {
        val qy = y.coerceIn(0, Y_MAX)
        return (Shaders.MARKER shl 20) or (qy shl Shaders.COLOUR_BITS) or fill
    }
}
