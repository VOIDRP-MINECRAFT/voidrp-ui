package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Gap
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Scroll
import ru.voidrp.ui.widget.scrolled

/**
 * The wheel against a list, at both ends.
 *
 * Found on a live client: the shop stopped its offset at the top and let it run on past the
 * bottom. The picture stopped moving, because the layout only draws what fits, but the
 * number kept growing — and then turning the wheel back did nothing for as many notches as
 * had been spent past the end.
 */
class ScrollTest {

    private val width = 700
    private val height = 300
    private fun list(offset: Int = 0) = Scroll(children = List(12) { Gap(size = 70) }, offset = offset, gap = 8)

    @Test
    fun `spinning past the bottom stops at the bottom`() {
        val limit = Layout.maxOffset(list(), width, height)
        assertTrue(limit > 0, "the list is taller than its window")
        var offset = 0
        repeat(100) { offset = scrolled(list(offset), 1, width, height) }
        assertEquals(limit, offset)
    }

    @Test
    fun `and the first notch back moves it`() {
        var offset = 0
        repeat(100) { offset = scrolled(list(offset), 1, width, height) }
        val back = scrolled(list(offset), -1, width, height)
        assertTrue(back < offset, "a notch up after spinning past the end did nothing")
    }

    @Test
    fun `the top holds too`() {
        assertEquals(0, scrolled(list(0), -1, width, height))
    }

    @Test
    fun `a list that fits does not move at all`() {
        val short = Scroll(children = List(2) { Gap(size = 70) }, gap = 8)
        assertEquals(0, scrolled(short, 1, width, height))
    }

    @Test
    fun `every stop on the way down is the top of a row`() {
        // A list stopped between rows cuts a card through its middle, and a glyph is drawn
        // whole or not at all — so what survives is a description with no title over it.
        val limit = Layout.maxOffset(list(), width, height)
        val starts = Layout.rowStarts(list(), width).toSet()
        var offset = 0
        val stops = mutableListOf<Int>()
        while (offset < limit) {
            offset = scrolled(list(offset), 1, width, height)
            stops += offset
        }
        stops.dropLast(1).forEach { assert(it in starts) { "stopped at $it, between rows" } }
        assertEquals(limit, stops.last(), "the bottom is reachable")
    }

    @Test
    fun `and back up the same way`() {
        val starts = Layout.rowStarts(list(), width).toSet()
        var offset = Layout.maxOffset(list(), width, height)
        while (offset > 0) {
            offset = scrolled(list(offset), -1, width, height)
            assert(offset in starts) { "stopped at $offset on the way up, between rows" }
        }
    }

    @Test
    fun `a fixed step is still there for a list that wants it`() {
        assertEquals(20, scrolled(list(0), 1, width, height, step = 20))
    }
}
