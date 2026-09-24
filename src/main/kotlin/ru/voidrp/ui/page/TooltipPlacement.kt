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
