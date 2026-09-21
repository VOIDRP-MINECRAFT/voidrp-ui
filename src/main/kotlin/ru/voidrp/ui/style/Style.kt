package ru.voidrp.ui.style

/**
 * How a box looks, in the terms a web page would use.
 *
 * A page says "this panel has a translucent background, a hairline border and 12 pixels of
 * rounding" and never thinks about glyphs: [ru.voidrp.ui.render.Painter] turns a style into
 * the rectangles and corner pieces the client can draw.
 */

/** A colour with opacity, like CSS `rgba()`. [rgb] is 0xRRGGBB, [alpha] is 0…1. */
data class Paint(val rgb: Int, val alpha: Double = 1.0) {

    fun alpha(value: Double): Paint = copy(alpha = value.coerceIn(0.0, 1.0))

    val visible: Boolean get() = alpha > 0.0
}

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
 * A soft drop shadow. Stacked translucent rectangles rather than a blur: a vertex shader
 * cannot blur anything, so depth comes from a couple of offset layers the way a paper
 * mock-up would fake it.
 */
data class Shadow(val offsetY: Int, val spread: Int, val paint: Paint)

/** Everything that can be said about a box. Unset parts simply are not drawn. */
data class Style(
    val background: Paint? = null,
    val border: Border? = null,
    val radius: Int = 0,
    val padding: Insets = Insets.NONE,
    val shadow: Shadow? = null,
    val textColour: Int = Theme.INK,
    val textSize: Int = 2,
) {
    fun background(paint: Paint) = copy(background = paint)
    fun border(width: Int, paint: Paint) = copy(border = Border(width, paint))
    fun radius(value: Int) = copy(radius = value)
    fun padding(value: Int) = copy(padding = Insets.all(value))
    fun padding(vertical: Int, horizontal: Int) = copy(padding = Insets.symmetric(vertical, horizontal))
    fun shadow(shadow: Shadow?) = copy(shadow = shadow)
    fun text(colour: Int = textColour, size: Int = textSize) = copy(textColour = colour, textSize = size)
}
