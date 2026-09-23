package ru.voidrp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import ru.voidrp.ui.page.DemoPage
import ru.voidrp.ui.page.HomePage
import ru.voidrp.ui.page.ShopPage

/**
 * A page that has not changed has to describe itself the same way twice.
 *
 * The whole picture travels as one boss bar title — ninety kilobytes for a rich page — so
 * before spending that, the session compares the description with the one it last drew.
 * That comparison only ever saves anything if an unchanged page really does come out equal:
 * a tree carrying a lambda, an object with identity equality, a timestamp or a fresh random
 * seed would differ on every call and the page would be sent again for nothing, for ever.
 * Nothing in the engine would break, which is exactly why it needs a test.
 */
class PageIdentityTest {

    @Test
    fun `an unchanged page describes itself identically`() {
        assertEquals(HomePage().view(), HomePage().view())
        assertEquals(ShopPage().view(), ShopPage().view())
        assertEquals(DemoPage().view(), DemoPage().view())
    }

    @Test
    fun `and the same instance asked twice agrees with itself`() {
        val page = ShopPage()
        assertEquals(page.view(), page.view())
    }

    @Test
    fun `a page that has changed says so`() {
        val page = ShopPage()
        val before = page.view()
        page.onScroll(1)
        assertNotEquals(before, page.view(), "a scrolled list draws the same picture")
    }
}
