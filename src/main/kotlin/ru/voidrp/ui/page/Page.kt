package ru.voidrp.ui.page

import org.bukkit.entity.Player
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.View

/** Which mouse button the player used. */
enum class Button { LEFT, RIGHT }

/**
 * A screen.
 *
 * A page says what it looks like right now, given whatever state it holds, and the runtime
 * draws that. When the state changes the page calls [refresh] and says so again — there is
 * no separate step where the interface is updated by hand, which is what keeps a page and
 * its data from drifting apart.
 *
 * The player points at named panels; [hovered] is the one under the cursor, so a page can
 * light it up simply by choosing a different style for it in [view].
 */
abstract class Page {

    internal var session: PageSession? = null

    /** The player this page is open for. */
    val player: Player get() = session?.player ?: error("Страница ещё не открыта")

    /** The id of the panel under the cursor, if any. */
    val hovered: String? get() = session?.hovered

    /** Where the cursor is on the canvas. */
    val cursorX: Int get() = session?.cursorX ?: 0
    val cursorY: Int get() = session?.cursorY ?: 0

    /** Where a named panel ended up when the page was last laid out. */
    fun region(id: String): Layout.Region? = session?.region(id)

    /** What the page looks like right now. Called again whenever something changes. */
    abstract fun view(): View

    /**
     * What to show next to the cursor, usually depending on [hovered].
     *
     * Tooltips ride the cursor's own boss bar, which is sent every frame anyway, so one
     * follows the mouse without the page being redrawn.
     */
    open fun tooltip(): View? = null

    /** The player clicked a named panel. */
    open fun onClick(id: String, button: Button) {}

    /**
     * The button is being held over a named panel — a drag.
     *
     * [x] and [y] are where the cursor is on the canvas, so a scrollbar or a slider can
     * work out what the player means by it.
     */
    open fun onDrag(id: String, x: Int, y: Int) {}

    /**
     * A number key, 1 to 9.
     *
     * Only reaches pages that ask for it with [usesKeys], because the game sends the same
     * packet for a number key and for the scroll wheel: a page has to say which it means.
     */
    open fun onKey(key: Int) {}

    /** Whether number keys reach [onKey] instead of being read as scrolling. */
    open val usesKeys: Boolean get() = false

    /** The player scrolled; [direction] is 1 down the list and −1 up. */
    open fun onScroll(direction: Int) {}

    /** The page is going away, whether the player closed it or something else did. */
    open fun onClose() {}

    /**
     * Asks the player to type something, then hands the answer back.
     *
     * The page stays on screen while the game's own text field is open, so this reads as
     * a field on the page rather than a detour through chat.
     */
    fun prompt(
        title: String,
        label: String,
        initial: String = "",
        hint: String? = null,
        maxLength: Int = 128,
        onSubmit: (String) -> Unit,
    ) {
        session?.prompt(title, label, initial, hint, maxLength, onSubmit)
    }

    /** Draw the page again, because something it shows has changed. */
    fun refresh() {
        session?.render()
    }

    /** Opens another page on top of this one. Crouching, or [back], returns here. */
    fun push(next: Page) {
        session?.push(next)
    }

    /** Goes back to the page this one was opened from, if there is one. */
    fun back(): Boolean = session?.back() ?: false

    /** Close this page. */
    fun close() {
        session?.close()
    }
}
