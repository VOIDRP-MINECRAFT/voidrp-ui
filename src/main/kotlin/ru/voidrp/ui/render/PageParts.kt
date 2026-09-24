package ru.voidrp.ui.render

/**
 * How a page is shared out between the boss bars it is sent on.
 *
 * A bar's title is replaced whole, so the smaller the piece that changed, the less goes
 * over the wire. The page is cut where the layout says something changes on its own — a
 * scrolling list, a menu above the page — so that scrolling sends the list and nothing
 * else. Whatever bars are left over halve the biggest piece, which halves what a hover
 * somewhere on it costs.
 *
 * The pieces are always runs of the page in its own order: bars are drawn one after
 * another, so a piece that took shapes from two places would put one of them on top of
 * something it was meant to be under.
 */
object PageParts {

    /** Below this many shapes a piece is not worth halving. */
    const val SMALLEST_HALF = 48

    /**
     * Splits `0 until weights.size` into at most [count] runs, at the given [cuts] where it
     * can. [weights] is how many shapes each node draws — a panel is one node and a
     * hundred shapes — so that halving halves what is sent, not how many nodes there are.
     *
     * More cuts than bars: the two neighbours that are lightest together are joined, which
     * keeps a big list apart from the page around it. Fewer: the heaviest piece is halved.
     */
    fun split(weights: List<Int>, cuts: List<Int>, count: Int): List<IntRange> {
        val size = weights.size
        if (size <= 0 || count <= 0) return emptyList()
        val sums = IntArray(size + 1)
        for (i in 0 until size) sums[i + 1] = sums[i] + weights[i]
        fun weight(from: Int, until: Int) = sums[until] - sums[from]

        val bounds = (listOf(0) + cuts.filter { it in 1 until size }.distinct().sorted() + size).toMutableList()
        // bounds[i] until bounds[i + 1] is piece i.
        while (bounds.size - 1 > count) {
            var best = 1
            var bestWeight = Int.MAX_VALUE
            for (i in 1 until bounds.size - 1) {
                val joined = weight(bounds[i - 1], bounds[i + 1])
                if (joined < bestWeight) {
                    bestWeight = joined
                    best = i
                }
            }
            bounds.removeAt(best)
        }
        while (bounds.size - 1 < count) {
            var heaviest = 0
            for (i in 0 until bounds.size - 1) {
                if (weight(bounds[i], bounds[i + 1]) > weight(bounds[heaviest], bounds[heaviest + 1])) heaviest = i
            }
            val from = bounds[heaviest]
            val until = bounds[heaviest + 1]
            val total = weight(from, until)
            if (total < SMALLEST_HALF * 2 || until - from < 2) break
            // The first node past the middle of the weight, kept strictly inside the piece.
            var middle = from + 1
            while (middle < until - 1 && weight(from, middle) * 2 < total) middle++
            bounds.add(heaviest + 1, middle)
        }
        return (0 until bounds.size - 1).map { bounds[it] until bounds[it + 1] }
    }

    /** The same, for nodes that each draw one shape. */
    fun split(size: Int, cuts: List<Int>, count: Int): List<IntRange> = split(List(size) { 1 }, cuts, count)

    /** How many shapes a node draws once every panel in it is taken apart. */
    fun weight(node: Node): Int = Painter.flatten(listOf(node)).size
}
