package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Grid
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.style.Insets
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style

/**
 * "As much room as you like", handed through a panel with padding.
 *
 * Found on a live client: a card in a grid, with a spacer that fills pushing its last line to
 * the foot. The grid measures its cells against no limit, the card took its padding off that,
 * and the spacer read what was left as a real height — 268 million units. Encoding a
 * rectangle that size ran out of stack and held the server thread for ten seconds.
 */
class UnboundedTest {

    @Test
    fun `a spacer in a padded card in a grid stays as tall as the card needs`() {
        val card = Panel(
            style = Style(padding = Insets.all(20)),
            width = Size.Fill,
            id = "card",
            children = listOf(Text("Telegram", 20), Panel(height = Size.Fill), Text("Link to chat", 12)),
        )
        val grid = Grid(columns = 3, gap = 12, width = Size.Fill, children = listOf(card, card, card))
        val placed = Layout.place(Panel(width = Size.Fixed(1200), children = listOf(grid)), 0, 0, 1200, 1024)
        val tallest = placed.regions.filter { it.id == "card" }.maxOf { it.height }
        assertTrue(tallest < 1024, "a card came out $tallest units tall")
    }

    @Test
    fun `a rectangle far bigger than the screen is still encoded`() {
        // Whatever the layout does, the encoder must not be the thing that brings the server down.
        GlyphEncoder.encode(listOf(Rect(0, 0, 1820, 268_000_000, Paint(0x223344))), 910)
        GlyphEncoder.encode(listOf(Rect(-5_000_000, -5_000_000, 10_000_000, 10_000_000, Paint(0x223344))), 910)
    }
}
