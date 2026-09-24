package ru.voidrp.ui.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a tooltip goes.
 *
 * Found on a live client: put beside the pointer, down and to the right, a shop's tooltip
 * lay over the price of the very row it described.
 */
class TooltipPlacementTest {

    private val canvas = 1024
    private val tipHeight = 90

    @Test
    fun `it goes under a row, clear of it`() {
        val row = 400 to 70
        val top = TooltipPlacement.top(row, pointerY = 430, height = tipHeight, canvasHeight = canvas)
        assertTrue(top >= row.first + row.second, "the tooltip starts at $top, inside the row")
    }

    @Test
    fun `and over it when there is no room below`() {
        val row = 900 to 70
        val top = TooltipPlacement.top(row, pointerY = 930, height = tipHeight, canvasHeight = canvas)
        assertTrue(top + tipHeight <= row.first, "the tooltip ends at ${top + tipHeight}, inside the row")
    }

    @Test
    fun `across a tall panel it follows the pointer`() {
        val panel = 100 to 700
        assertEquals(
            500 + TooltipPlacement.OFFSET,
            TooltipPlacement.top(panel, pointerY = 500, height = tipHeight, canvasHeight = canvas),
        )
    }

    @Test
    fun `and it never leaves the screen`() {
        (0..1000 step 25).forEach { y ->
            val top = TooltipPlacement.top(y to 60, pointerY = y + 30, height = tipHeight, canvasHeight = canvas)
            assertTrue(top >= 0 && top + tipHeight <= canvas, "at row $y the tooltip spans $top..${top + tipHeight}")
        }
    }
}
