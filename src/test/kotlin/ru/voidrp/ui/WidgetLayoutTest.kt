package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.RichText
import ru.voidrp.ui.layout.Scroll
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Span
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.page.Page
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.render.Sprite
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.button
import ru.voidrp.ui.widget.iconButton
import ru.voidrp.ui.widget.select

/**
 * What the components put where — the things a screenshot shows and a unit test usually
 * does not. Every one of these was a real defect first.
 */
class WidgetLayoutTest {

    /** A page is needed to build anything that answers the cursor; nothing here hovers. */
    private class Blank : Page() {
        override fun view(): View = Panel()
    }

    @Test
    fun `an icon button puts its icon in the middle`() {
        // It did not: the button's style carries the padding its text would need, and
        // inside a 42-unit square that leaves an inner box six units wide. The icon was
        // centred — in that box, which is the corner of the button.
        val page = Blank()
        val placement = Layout.place(page.iconButton("home", "b", size = 42), 0, 0, 42, 42)
        val icon = placement.nodes.filterIsInstance<Sprite>().single()
        assertEquals(9, icon.x, "the icon is not centred horizontally")
        assertEquals(9, icon.y, "the icon is not centred vertically")
    }

    @Test
    fun `a control with a height of its own centres its caption`() {
        // The style carries the padding its text would need, and a control that is also
        // given a height has no room for it: the caption ends up against the bottom edge.
        // On a 36-unit tab that was eighteen units above the word and five below.
        val page = Blank()
        listOf<Pair<String, View>>(
            "button" to page.button("Primary", "b", height = 44),
            "tab" to page.button("All", "t", Theme.buttonGhost, height = 36),
            "menu" to page.select("m", listOf("Survival"), 0, open = false, width = Size.Fixed(230)),
        ).forEach { (name, view) ->
            val height = if (name == "tab") 36 else if (name == "button") 44 else 40
            val label = Layout.place(view, 0, 0, 230, height).nodes.filterIsInstance<Label>().first()
            val cell = TextFonts.sheet(label.weight, label.size).cellHeight
            val above = label.y
            val below = height - (label.y + cell)
            assertTrue(
                Math.abs(above - below) <= 2,
                "$name: the caption is off centre — $above above, $below below",
            )
        }
    }

    @Test
    fun `pieces of one line sit on one baseline`() {
        // A smaller span left at the same top edge floats above the line it belongs to:
        // "12400 coins" had the word hanging off the top of the number.
        val rich = RichText(
            listOf(Span("12400", size = Theme.TEXT_H2), Span(" coins", size = Theme.TEXT_CAPTION)),
        )
        val labels = Layout.place(rich, 0, 0, 400, 60).nodes.filterIsInstance<Label>()
        assertEquals(2, labels.size)
        val bottoms = labels.map { it.y + TextFonts.sheet(it.weight, it.size).cellHeight }
        assertEquals(bottoms[0], bottoms[1], "pieces of the line sit on different baselines")
    }

    @Test
    fun `a list shows whole rows and nothing of the rest`() {
        // A row the window cut down to a few units used to stay as a line lying at the
        // edge, with the corners it was rounded with as two stray squares beside it.
        val rows = (1..6).map {
            Panel(style = Theme.card, width = Size.Fill, height = Size.Fixed(40), children = listOf(Text("Row $it")))
        }
        val scroll = Scroll(children = rows, offset = 0, gap = 8, height = Size.Fixed(100))
        val nodes = Layout.place(scroll, 0, 0, 300, 100).nodes

        val outside = nodes.filterIsInstance<Rect>().filter { it.y + it.height > 100 || it.y < 0 }
        assertTrue(outside.isEmpty(), "the list spills out of its window: ${outside.take(3)}")

        // Two whole rows fit in a hundred units; the third would show two of its forty.
        val labels = nodes.filterIsInstance<Label>().map { it.text }
        assertEquals(listOf("Row 1", "Row 2"), labels, "the window shows the wrong rows")
    }

    @Test
    fun `a chip keeps its word when the row runs out of room`() {
        // The row takes the difference out of everything in proportion, which turns a chip
        // into a word with an ellipsis. A chip says it would rather not.
        val page = Blank()
        val row = Panel(
            direction = ru.voidrp.ui.layout.Direction.ROW,
            width = Size.Fixed(300),
            gap = 8,
            children = listOf(
                Panel(width = Size.Fill, children = listOf(Text("A headline that would happily take the whole row"))),
                ru.voidrp.ui.widget.chip("MC 26.2"),
            ),
        )
        val wanted = Layout.measure(ru.voidrp.ui.widget.chip("MC 26.2"), 300, 60).width
        val placement = Layout.place(row, 0, 0, 300, 60)
        val chipLabel = placement.nodes.filterIsInstance<Label>().single { it.text == "MC 26.2" }
        assertTrue(
            chipLabel.x + TextFonts.sheet(chipLabel.weight, chipLabel.size).width("MC 26.2") <= 300,
            "the chip spills out of the row",
        )
        assertTrue(wanted > 0 && chipLabel.text == "MC 26.2", "the chip lost its text")
        assertEquals(page.hovered, null)
    }
}
