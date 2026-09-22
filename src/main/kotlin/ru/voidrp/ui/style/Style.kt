package ru.voidrp.ui.style

import ru.voidrp.ui.pack.TextFonts

/**
 * How a box looks, in the terms a web page would use.
 *
 * A page says "this panel has a translucent background, a hairline border and 12 pixels of
 * rounding" and never thinks about glyphs: [ru.voidrp.ui.render.Painter] turns a style into
 * the rectangles and corner pieces the client can draw.
 */

/** Anything that can fill a shape: a flat colour or a gradient between two. */
sealed interface Fill

/** A colour with opacity, like CSS `rgba()`. [rgb] is 0xRRGGBB, [alpha] is 0…1. */
data class Paint(val rgb: Int, val alpha: Double = 1.0) : Fill {

    fun alpha(value: Double): Paint = copy(alpha = value.coerceIn(0.0, 1.0))

    val visible: Boolean get() = alpha > 0.0
}

/**
 * A gradient between two colours, drawn as bands.
 *
 * Nothing in the trip to the client can express a smooth ramp — a glyph has one colour —
 * so the ramp is made of stripes, each a flat colour of its own.
 *
 * Be sparing with these. Colour travels in ten bits, which between two given colours leaves
 * only a handful of distinct steps, and opacity has sixteen: a gentle wash over a large
 * area passes, a strong transition across a small one shows its stripes whatever is done
 * about it. Where a gradient does not look right, a flat fill usually does.
 */
data class Gradient(
    val from: Paint,
    val to: Paint,
    val direction: GradientDirection = GradientDirection.VERTICAL,
    /** How many stripes; left alone, one every few pixels. More is smoother and costs more. */
    val steps: Int? = null,
    /**
     * Whether neighbouring stripes are sent to neighbouring palette entries to fake the
     * colours in between. It helps a long, gentle transition and hurts a short, strong one,
     * where the pattern itself becomes the thing you see.
     */
    val dither: Boolean = true,
    /**
     * What is behind this, if it is known.
     *
     * Told that, a wash is drawn a different way entirely: each stripe is given the colour
     * and the opacity whose result over that backdrop looks nearest to what the stripe
     * wants, and a flat layer underneath brings the whole range within reach. It is the
     * difference between a fade in bands and a fade you cannot pick the steps out of, so
     * say it wherever the surface underneath is known.
     */
    val over: Int? = null,
    /**
     * Where the fade begins and ends, as fractions of the side it runs along.
     *
     * A wash rarely runs corner to corner: the site's welcome panel is done fading two
     * fifths of the way across and flat from there. Same idea as the positions on a
     * stylesheet's colour stops.
     */
    val start: Double = 0.0,
    val stop: Double = 1.0,
) : Fill {

    /** How far between the two colours a point at [position] along the side is. */
    fun at(position: Double): Double = when {
        stop <= start -> if (position < start) 0.0 else 1.0
        else -> ((position - start) / (stop - start)).coerceIn(0.0, 1.0)
    }
}

enum class GradientDirection { VERTICAL, HORIZONTAL }

/** Space inside a box, between its edge and its content. */
data class Insets(val top: Int, val right: Int, val bottom: Int, val left: Int) {
    companion object {
        val NONE = Insets(0, 0, 0, 0)
        fun all(value: Int) = Insets(value, value, value, value)
        fun symmetric(vertical: Int, horizontal: Int) = Insets(vertical, horizontal, vertical, horizontal)
    }

    val horizontal: Int get() = left + right
    val vertical: Int get() = top + bottom
}

/** A line drawn around the outside of a box, inside its bounds, like `border` in CSS. */
data class Border(val width: Int, val paint: Paint)

/**
 * A soft drop shadow: a halo of baked, fading tiles sitting [offsetY] units lower than
 * what casts it. [spread] is kept for pages that set it, but the reach is the one the
 * tiles are baked at.
 */
data class Shadow(val offsetY: Int, val spread: Int = 0, val paint: Paint)

/** Everything that can be said about a box. Unset parts simply are not drawn. */
data class Style(
    val background: Fill? = null,
    /**
     * A second wash over the first, in the same shape.
     *
     * One fade runs in one direction, and light in a real interface rarely does: the site's
     * welcome panel is brightest at the bottom left and falls away both upwards and to the
     * right. Two crossed washes say that, where one cannot.
     */
    val overlay: Fill? = null,
    val border: Border? = null,
    val radius: Int = 0,
    val padding: Insets = Insets.NONE,
    val shadow: Shadow? = null,
    /** A halo in a colour, sitting square behind the box — light rather than depth. */
    val glow: Paint? = null,
    /**
     * A single lit line just inside the top edge.
     *
     * The site gives every card a pale gradient from its top; a gradient across a whole
     * panel bands badly here, but the part of it the eye actually reads is that first
     * line of light, and one line costs one rectangle.
     */
    val highlight: Paint? = null,
    val textColour: Int = Theme.INK,
    val textSize: Int = Theme.TEXT_BODY,
    val textWeight: TextFonts.Weight = TextFonts.Weight.REGULAR,
) {
    fun background(fill: Fill) = copy(background = fill)
    fun border(width: Int, paint: Paint) = copy(border = Border(width, paint))
    fun radius(value: Int) = copy(radius = value)
    fun padding(value: Int) = copy(padding = Insets.all(value))
    fun padding(vertical: Int, horizontal: Int) = copy(padding = Insets.symmetric(vertical, horizontal))
    fun shadow(shadow: Shadow?) = copy(shadow = shadow)
    fun glow(paint: Paint?) = copy(glow = paint)
    fun text(
        colour: Int = textColour,
        size: Int = textSize,
        weight: TextFonts.Weight = textWeight,
    ) = copy(textColour = colour, textSize = size, textWeight = weight)
}
