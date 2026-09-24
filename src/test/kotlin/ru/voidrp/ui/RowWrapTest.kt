package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.Painter

/**
 * An icon beside a column of text that wraps — the commonest row there is, and it came out
 * wrong on a live client: the text was measured as one line against the whole row, drawn as
 * two in the room it was actually given, and the second line lay over the heading. The icon
 * next to it was squeezed as well, because the column claimed its whole text's width.
 */
class RowWrapTest {

    private fun row() = Panel(
        direction = Direction.ROW,
        gap = 12,
        align = Align.CENTER,
        width = Size.Fill,
        children = listOf(
            Panel(width = Size.Fixed(40), height = Size.Fixed(40), id = "icon"),
            Panel(
                gap = 2,
                width = Size.Fill,
                id = "column",
                children = listOf(
                    Text("Запомни дом", 16),
                    Text("/sethome — запомнить место, /home — вернуться. Домов можно три, у каждого своё имя.", 14),
                ),
            ),
        ),
    )

    @Test
    fun `the icon keeps its size and the text its lines`() {
        val page = Panel(width = Size.Fixed(730), gap = 12, children = listOf(row(), Panel(width = Size.Fill, height = Size.Fixed(10), id = "next")))
        val placed = Layout.place(page, 0, 0, 730, 400)
        val icon = placed.regions.single { it.id == "icon" }
        val column = placed.regions.single { it.id == "column" }
        val next = placed.regions.single { it.id == "next" }
        assertEquals(40, icon.width)

        val labels = Painter.flatten(placed.nodes).filterIsInstance<Label>().sortedBy { it.y }
        assertEquals(3, labels.size, "a heading and two lines of text")
        // Each line starts below the one before it ends.
        labels.zipWithNext { above, below ->
            assertTrue(below.y >= above.y + above.size, "'${below.text}' at ${below.y} overlaps '${above.text}' at ${above.y}")
        }
        // And whatever follows the row starts below the whole of it.
        assertTrue(next.y >= column.y + column.height, "the next row starts at ${next.y}, inside the text")
        assertTrue(column.height >= labels.last().y + labels.last().size - column.y)
    }
}
