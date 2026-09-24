package ru.voidrp.ui.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A tooltip stays where it appeared.
 *
 * It rides a boss bar of its own, and that bar is sent again whenever the tooltip moves — so
 * a tooltip that followed the pointer cost a packet every few units of movement.
 */
class TooltipPinTest {

    @Test
    fun `moving along a row leaves the tooltip where it is`() {
        val pin = TooltipPin()
        assertTrue(pin.update("diamond", 64, "tip", 400, 300))
        for (x in 400..1000 step 7) assertFalse(pin.update("diamond", 64, "tip", x, 305))
        assertEquals(400 to 300, pin.x to pin.y)
    }

    @Test
    fun `something new under the pointer pins it again`() {
        val pin = TooltipPin()
        pin.update("diamond", 64, "tip", 400, 300)
        assertTrue(pin.update("emerald", 64, "tip", 420, 370))
        assertEquals(420 to 370, pin.x to pin.y)
        // And so does the same thing saying something else.
        assertTrue(pin.update("emerald", 64, "other", 430, 370))
    }

    @Test
    fun `over a tall panel it catches up once left behind`() {
        val pin = TooltipPin()
        pin.update("map", 600, "tip", 100, 100)
        assertFalse(pin.update("map", 600, "tip", 180, 100))
        assertTrue(pin.update("map", 600, "tip", 260, 100))
        assertEquals(260, pin.x)
    }
}
