package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.style.Theme

/**
 * Where a paragraph breaks.
 *
 * Found on a live client: a note typed as "Meet at spawn at 8" came out as a full line with
 * an "8" alone under it, at the start of a card.
 */
class WrapTest {

    private fun lines(value: String, width: Int): List<String> {
        val view = Text(value, Theme.TEXT_LEAD, Theme.INK, wrap = true)
        return Layout.place(view, 0, 0, width, 1024).nodes
            .filterIsInstance<Label>()
            .sortedBy { it.y }
            .map { it.text }
    }

    @Test
    fun `a short last word is not left on a line by itself`() {
        val sheet = TextFonts.sheet(TextFonts.Weight.REGULAR, Theme.TEXT_LEAD)
        val text = "Meet at spawn at 8"
        // Every width at which the sentence breaks in two, not just the one it was seen at.
        (60..600 step 2).forEach { width ->
            val broken = lines(text, width)
            if (broken.size < 2) return@forEach
            val last = broken.last()
            val above = broken[broken.size - 2]
            val lonely = ' ' !in last && sheet.width(last) * 3 <= width
            val couldMove = above.contains(' ') &&
                sheet.width(above.substringAfterLast(' ') + " " + last) <= width
            assertTrue(!(lonely && couldMove), "at $width: \"$above\" / \"$last\"")
        }
    }

    @Test
    fun `and nothing is lost or reordered doing it`() {
        (60..600 step 7).forEach { width ->
            val text = "Meet at spawn at 8"
            assertEquals(text, lines(text, width).joinToString(" "), "at $width")
        }
    }

    @Test
    fun `a line that fits is left alone`() {
        assertEquals(listOf("Meet at spawn at 8"), lines("Meet at spawn at 8", 1000))
    }
}
