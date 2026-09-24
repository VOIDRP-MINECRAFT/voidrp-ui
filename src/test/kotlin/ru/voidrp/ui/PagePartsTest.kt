package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.page.ShopPage
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.PageParts

/**
 * A page spread over several boss bars, so that a change sends only the bar it is on.
 *
 * The point of it is the shop: its list scrolls, and the rest of the page — the heaviest
 * part — should not travel again every time it does.
 */
class PagePartsTest {

    @Test
    fun `pieces cover the page in order, with nothing missed or repeated`() {
        for (cuts in listOf(emptyList(), listOf(100), listOf(40, 300, 310, 900))) {
            val runs = PageParts.split(1000, cuts, 3)
            assertEquals(0, runs.first().first)
            assertEquals(999, runs.last().last)
            runs.zipWithNext { a, b -> assertEquals(a.last + 1, b.first) }
            assertTrue(runs.size <= 3)
        }
    }

    @Test
    fun `too many cuts keep the big pieces apart`() {
        // Page, list, footer, menu: the footer and the menu are the smallest pair.
        val runs = PageParts.split(1000, listOf(500, 900, 950), 3)
        assertEquals(listOf(0 until 500, 500 until 900, 900 until 1000), runs)
    }

    @Test
    fun `a page with nothing to cut at is halved`() {
        assertEquals(3, PageParts.split(1000, emptyList(), 3).size)
        // And a tiny one is left whole: halving it would save nothing.
        assertEquals(1, PageParts.split(20, emptyList(), 3).size)
    }

    @Test
    fun `halving goes by what is drawn, not by how many panels draw it`() {
        // One panel of a hundred shapes and ten small things after it: the cut lands right
        // after the panel, not in the middle of the list of nodes.
        val runs = PageParts.split(listOf(100) + List(10) { 1 }, emptyList(), 2)
        assertEquals(listOf(0 until 1, 1 until 11), runs)
    }

    @Test
    fun `scrolling the shop changes only the list`() {
        val page = ShopPage()
        fun pieces(): List<List<ru.voidrp.ui.render.Node>> {
            val placement = Layout.centred(page.view(), 1820, Shaders.CANVAS_HEIGHT)
            assertTrue(placement.cuts.isNotEmpty(), "the shop's list left no cut in the page")
            return PageParts.split(placement.nodes.map(PageParts::weight), placement.cuts, 3)
                .map { placement.nodes.subList(it.first, it.last + 1) }
        }
        val before = pieces()
        page.onScroll(1)
        val after = pieces()
        assertEquals(before.size, after.size)
        val changed = before.indices.filter { before[it] != after[it] }
        assertEquals(1, changed.size, "a scroll changed pieces $changed")
        // And that piece is not the page around the list.
        assertTrue(changed.single() != 0, "a scroll sent the page itself again")
    }

    @Test
    fun `a lifted bar can still reach the top of the screen`() {
        val top = GlyphEncoder.pack(-57, 0)
        assertEquals(Shaders.MARKER_SHIFTED, top shr 20)
        assertEquals(Shaders.SHIFT - 57, (top shr Shaders.COLOUR_BITS) and 1023)
        assertEquals(Shaders.MARKER, GlyphEncoder.pack(0, 0) shr 20)
        assertEquals(Shaders.MARKER_DRIFT_SHIFTED, GlyphEncoder.pack(-1, 0, drift = true) shr 20)
    }
}
