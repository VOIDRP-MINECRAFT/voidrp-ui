package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.SpriteMotion

/**
 * The shader refuses every pure grey — the client's own debug screen and chat box are
 * #E0E0E0, right on a marker — so nothing the encoder sends may be one.
 */
class GreyColourTest {

    @Test
    fun `no static or drifting glyph is a grey`() {
        for (y in -64..1023) {
            for (fill in 0 until 1024) {
                assertFalse(GlyphEncoder.isGrey(GlyphEncoder.pack(y, fill)), "y=$y fill=$fill")
                assertFalse(GlyphEncoder.isGrey(GlyphEncoder.pack(y, fill, drift = true)), "drift y=$y fill=$fill")
            }
        }
    }

    @Test
    fun `no pointer glyph is a grey`() {
        for (y in -200..1100 step 3) {
            for (tick in 0L until 4L) {
                for (vx in listOf(-50, -1, 0, 1, 7, 50)) {
                    for (vy in listOf(-50, -1, 0, 1, 7, 50)) {
                        val colour = GlyphEncoder.packMotion(y, SpriteMotion(tick, vx, vy))
                        assertFalse(GlyphEncoder.isGrey(colour), "y=$y tick=$tick v=$vx,$vy")
                    }
                }
            }
        }
    }

    @Test
    fun `the client's own greys are greys`() {
        for (c in listOf(0xE0E0E0, 0x808080, 0x707070, 0xA0A0A0)) {
            kotlin.test.assertTrue(GlyphEncoder.isGrey(c))
        }
    }
}
