package ru.voidrp.ui.render

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import ru.voidrp.ui.pack.TextFonts
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
    /** An outline rather than a filled quarter — what a border's corner is. */
    val ring: Boolean = false,
) : Node

/**
 * A picture from the pack, drawn at its own colours.
 *
 * The colour bits still carry the vertical position, so the fill has to be white: white
 * leaves a texture exactly as it was baked, which is how a pointer or an item icon keeps
 * its own colours while everything else is tinted.
 */
data class Sprite(
    override val x: Int,
    override val y: Int,
    val glyph: String,
    val advance: Int,
    val colour: Int = 0xFFFFFF,
    /** Which font holds the picture; the shape alphabet when nothing is named. */
    val font: String? = null,
) : Node

/**
 * A line of text, set in the site's typeface. [size] is in canvas units, which are the
 * site's pixels near enough, so a 14 here is a 14px label there. [y] is the top of the
 * line, like a rectangle's top edge.
 *
 * Text has no opacity of its own — a text component carries none — so use a dimmer colour
 * where a stylesheet would use a lower opacity.
 */
data class Label(
    override val x: Int,
    override val y: Int,
    val text: String,
    val size: Int = 16,
    val colour: Int = Theme.INK,
    val weight: TextFonts.Weight = TextFonts.Weight.REGULAR,
    /** Extra air after every letter — what a stylesheet calls letter-spacing. */
    val tracking: Int = 0,
) : Node {
    val width: Int get() = TextFonts.width(text, weight, size) + tracking * (text.length - 1).coerceAtLeast(0)
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
                is Sprite -> appendSprite(line, node, pen)
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

        val tiles = mutableListOf<Tile>()
        tile(rect.x, rect.y, rect.width, rect.height, tiles)
        for (piece in tiles) {
            val colour = TextColor.color(pack(piece.top, fill))
            line.append(shapes(Glyphs.moveBy(piece.left - pen) + Glyphs.rect(piece.w, piece.h), level).color(colour))
            pen = piece.left + Glyphs.rectAdvance(piece.w)
        }
        return pen
    }

    /** One rectangle of the alphabet: 2^[w] by 2^[h], with its top-left corner on the canvas. */
    private data class Tile(val left: Int, val top: Int, val w: Int, val h: Int)

    /**
     * Cuts a rectangle into pieces the alphabet actually has.
     *
     * Each step takes the largest piece that fits in the corner, narrowed until its sides
     * are within [Glyphs.MAX_ASPECT_EXP] of each other, then fills the strip to its right
     * and everything below it the same way. Doing both sides together matters: choosing
     * rows first and columns after left a four-pixel column a thousand pixels tall at the
     * edge of a full-screen fill — a shape with no glyph, which simply went missing.
     */
    private fun tile(x: Int, y: Int, width: Int, height: Int, out: MutableList<Tile>) {
        if (width <= 0 || height <= 0) return
        var w = minOf(highestPower(width), Glyphs.MAX_EXP)
        var h = minOf(highestPower(height), Glyphs.MAX_EXP)
        if (w - h > Glyphs.MAX_ASPECT_EXP) w = h + Glyphs.MAX_ASPECT_EXP
        if (h - w > Glyphs.MAX_ASPECT_EXP) h = w + Glyphs.MAX_ASPECT_EXP
        val pieceWidth = 1 shl w
        val pieceHeight = 1 shl h

        out += Tile(x, y, w, h)
        tile(x + pieceWidth, y, width - pieceWidth, pieceHeight, out)
        tile(x, y + pieceHeight, width, height - pieceHeight, out)
    }

    private fun appendCorner(line: TextComponent.Builder, piece: CornerPiece, penIn: Int): Int {
        val level = Glyphs.alphaLevel(piece.paint.alpha)
        if (piece.radius !in Glyphs.RADII || level == 0) return penIn
        val colour = TextColor.color(pack(piece.y, quantise(piece.paint.rgb)))
        val shape = if (piece.ring) {
            Glyphs.ringCorner(piece.radius, piece.corner)
        } else {
            Glyphs.corner(piece.radius, piece.corner)
        }
        val glyph = Glyphs.moveBy(piece.x - penIn) + shape
        line.append(shapes(glyph, level).color(colour))
        return piece.x + Glyphs.cornerAdvance(piece.radius)
    }

    private fun appendSprite(line: TextComponent.Builder, sprite: Sprite, penIn: Int): Int {
        val colour = TextColor.color(pack(sprite.y, quantise(sprite.colour)))
        val font = sprite.font ?: Glyphs.fontName(Glyphs.ALPHA_LEVELS)
        // The move to the right place is written in the shape alphabet, which every font
        // of ours carries, so the picture and the step before it are one run.
        line.append(
            Component.text(Glyphs.moveBy(sprite.x - penIn) + sprite.glyph)
                .font(Key.key("voidrp", font))
                .color(colour)
                .shadowColor(ShadowColor.none())
        )
        return sprite.x + sprite.advance
    }

    /**
     * A label is one run in one font: each letter followed by the spacer that makes up the
     * difference between the ink the client measures and the advance the typeface asks for.
     */
    private fun appendLabel(line: TextComponent.Builder, label: Label, penIn: Int): Int {
        val size = TextFonts.nearestSize(label.size)
        val sheet = TextFonts.sheet(label.weight, size)
        val colour = TextColor.color(pack(label.y, quantise(label.colour)))
        val font = Key.key("voidrp", sheet.fontName)
        var pen = penIn

        val run = StringBuilder(Glyphs.moveBy(label.x - pen))
        pen = label.x
        for (char in label.text) {
            if (char == ' ') {
                run.append(char)
                run.append(Glyphs.moveBy(label.tracking))
                pen += sheet.spaceAdvance + label.tracking
                continue
            }
            val metric = sheet.metrics[char] ?: continue
            run.append(char)
            run.append(Glyphs.moveBy(metric.advance - metric.clientAdvance + label.tracking))
            pen += metric.advance + label.tracking
        }

        line.append(
            Component.text(run.toString())
                .font(font)
                .color(colour)
                .shadowColor(ShadowColor.none())
        )
        return pen
    }

    /** The exponent of the largest power of two that fits in [value]. */
    private fun highestPower(value: Int): Int =
        if (value <= 0) 0 else 31 - Integer.numberOfLeadingZeros(value)

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
