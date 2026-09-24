package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.voidrp.ui.pack.Icons

/**
 * The icon font for clients older than 26.2.
 *
 * Found on a live 1.21.6 client: the pack named every item texture of 26.2, and 1.21.6 —
 * short 193 of them — drew every glyph of that font as the missing-glyph box, the spacers
 * beside each icon included. Each box moved the pen by the wrong amount and everything drawn
 * after the first icon slid sideways. The older pack leaves those textures out and puts a
 * space of the same width where each one was.
 */
class LegacyIconFontTest {

    @Test
    fun `the older font names no texture the older client lacks`() {
        val legacy = Icons.fontJson(16, legacy = true)
        assertFalse("copper_axe.png" in legacy, "the legacy font still points at a 26.2 texture")
        assertTrue("item/diamond.png" in legacy, "the legacy font lost an item every client has")
    }

    @Test
    fun `and the modern font keeps them all`() {
        assertTrue("copper_axe.png" in Icons.fontJson(16))
    }
}
