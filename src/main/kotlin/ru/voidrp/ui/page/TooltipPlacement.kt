package ru.voidrp.ui.page

/**
 * Where a tooltip goes.
 *
 * Beside the pointer, down and to the right, it covered the very thing it was describing: in
 * a shop the pointer sits in the middle of a row, and the tooltip lay over that row's price.
 * So for anything the size of a row or a tile it goes under what is hovered — or over it,
 * when there is no room below — and it follows the pointer only across something so tall
 * that going round it would take the tooltip away from the pointer altogether.
 */
internal object TooltipPlacement {

    /** How far from the pointer a tooltip sits, so the pointer does not cover it. */
    const val OFFSET = 16

    /** How far a tooltip keeps from the edge of what it describes. */
    const val GAP = 6

    /** Taller than this, a hovered thing is a panel rather than a row. */
    const val AROUND_MAX = 160

    /**
     * Over a panel that tall, how far the pointer may go from where the tooltip appeared
     * before the tooltip is brought back to it. Anywhere smaller it stays put.
     */
    const val REPIN = 120

    /** The tooltip's top, in page units. [hovered] is the top and height of what is hovered. */
    fun top(hovered: Pair<Int, Int>?, pointerY: Int, height: Int, canvasHeight: Int): Int {
        val fallback = (pointerY + OFFSET).coerceAtMost(canvasHeight - height - 4).coerceAtLeast(4)
        val (top, tall) = hovered ?: return fallback
        if (tall > AROUND_MAX) return fallback
        val below = top + tall + GAP
        if (below + height <= canvasHeight - 4) return below
        val above = top - GAP - height
        if (above >= 4) return above
        return fallback
    }
}

/**
 * Where a tooltip is pinned: the pointer, as it was when the tooltip appeared.
 *
 * It stays there while the pointer is over the same thing and the tooltip says the same.
 * Following the pointer, a tooltip had to be sent again every few units of movement, and it
 * was harder to read for moving. Over something taller than [TooltipPlacement.AROUND_MAX] it
 * would be left behind, so there it is pinned again once the pointer has gone
 * [TooltipPlacement.REPIN] away from it.
 */
internal class TooltipPin {
    var x = 0
        private set
    var y = 0
        private set
    private var owner: Pair<String?, Any?>? = null

    /** Moves the pin if it has to, and says whether it did. */
    fun update(region: String?, regionHeight: Int, tooltip: Any?, pointerX: Int, pointerY: Int): Boolean {
        val next = region to tooltip
        val tall = region != null && regionHeight > TooltipPlacement.AROUND_MAX
        val drifted = tall && Math.hypot((pointerX - x).toDouble(), (pointerY - y).toDouble()) > TooltipPlacement.REPIN
        if (next == owner && !drifted) return false
        owner = next
        x = pointerX
        y = pointerY
        return true
    }
}
