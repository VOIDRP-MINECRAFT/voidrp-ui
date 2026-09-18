package ru.voidrp.ui.render

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import ru.voidrp.ui.pack.Shaders

/**
 * One thing on screen, in canvas coordinates (1920×1080 stretched over the window).
 *
 * This is the whole vocabulary the renderer knows. Pages are built from these; layout,
 * data binding and reactivity all end up producing a list of them.
 */
data class Element(
    val x: Int,
    val y: Int,
)

/**
 * Turns elements into the text the client draws.
 *
 * The position travels in the glyph's colour and the size in which character it is, so
 * one element is one glyph — nothing about it is baked into the resource pack, which is
 * what lets a page be built from data at the moment it opens.
 */
object GlyphEncoder {

    private val FONT: Key = Key.key("voidrp", "ui")

    private const val MAX_STEP = (1 shl Shaders.POSITION_BITS) - 1

    /** The single glyph of the pack: private-use area, never collides with real text. */
    private val GLYPH = String(Character.toChars(0xE000))

    /**
     * Packs the marker and the position into a text colour: red is the marker, green
     * the x step and blue the y step, rounded to the nearest of 256 per axis.
     */
    fun encode(element: Element): Component {
        val qx = Math.round(element.x.toDouble() * MAX_STEP / Shaders.CANVAS_WIDTH).toInt().coerceIn(0, MAX_STEP)
        val qy = Math.round(element.y.toDouble() * MAX_STEP / Shaders.CANVAS_HEIGHT).toInt().coerceIn(0, MAX_STEP)
        val colour = (Shaders.MARKER shl 16) or (qx shl 8) or qy

        // The shadow is drawn as separate vertices whose colour is a darkened copy, so the
        // shader cannot recognise it and it would stay stranded where the text sat. A
        // transparent shadow colour (1.21.4+) keeps it from being drawn at all.
        return Component.text(GLYPH)
            .font(FONT)
            .color(TextColor.color(colour))
            .shadowColor(ShadowColor.none())
    }

    fun encodeAll(elements: List<Element>): Component {
        if (elements.isEmpty()) return Component.empty()
        var result = Component.empty()
        elements.forEach { result = result.append(encode(it)) }
        return result
    }
}
