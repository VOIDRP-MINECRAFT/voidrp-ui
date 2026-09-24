package ru.voidrp.ui.render

import java.util.UUID
import java.util.logging.Logger
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player

/**
 * Puts a page on screen by riding invisible boss bars.
 *
 * Client shader packs (Iris, OptiFine) replace the pipeline the world is drawn in but
 * leave the GUI alone, so a boss bar keeps rendering through the vanilla core shaders we
 * patched. That is why the transport is a boss bar and not a display entity: the page
 * survives whatever the player has installed.
 *
 * A boss bar's title is a text component, and our glyphs are text, so a page is sent by
 * setting the title — no packets by hand and no version-specific plumbing.
 */
class BossBarRenderer(
    private val log: Logger? = null,
    /**
     * Told just before a bar of ours is created.
     *
     * Adventure never says what id it gave a bar, so whoever is watching the packets has
     * to be told to expect the next one — see [ru.voidrp.ui.input.BossBarGuard].
     */
    private val announcing: (Player) -> Unit = {},
) {

    companion object {
        /**
         * Past this many characters a page is worth complaining about: a title travels in
         * one packet, so a page that keeps growing eventually stops arriving at all.
         */
        private const val BUSY_PAGE = 20_000

        /**
         * How many bars a page is spread over; the pointer has one more of its own.
         *
         * Four is what every client draws: the boss bar list stops at a third of the
         * screen's height, each bar takes nineteen units of it starting at twelve, and no
         * GUI scale leaves the screen shorter than 240 — so bars at 12, 31, 50 and 69 always
         * fit, and a fifth at 88 does not on a large scale.
         */
        const val PAGE_BARS = 3
    }

    private val pages = mutableMapOf<UUID, List<BossBar>>()
    private val cursors = mutableMapOf<UUID, BossBar>()

    fun render(player: Player, nodes: List<Node>, centre: Int = 0) =
        render(player, GlyphEncoder.encode(nodes, centre))

    /** Sends a whole page on the first bar, and clears the others. For tests and tools. */
    fun render(player: Player, title: Component) {
        part(player, 0, title)
        for (index in 1 until PAGE_BARS) part(player, index, Component.empty())
    }

    /**
     * Sends one piece of the page.
     *
     * A boss bar's title is replaced whole, so everything drawn on a bar travels again
     * every time any of it changes. That is why a page is spread over [PAGE_BARS] of them —
     * see [PageParts] — and why the cursor lives on a bar of its own.
     */
    fun part(player: Player, index: Int, title: Component) {
        val length = PlainTextComponentSerializer.plainText().serialize(title).length
        if (length > BUSY_PAGE) {
            log?.warning("A piece of the page for ${player.name} is $length characters, which is near the packet limit.")
        }
        bars(player)[index].name(title)
    }

    /**
     * Sends the cursor, which moves sixty times a second.
     *
     * On its own bar it is a few dozen bytes each time instead of the whole page: with
     * both on one bar a rich page went out in full every frame, which was tens of
     * kilobytes a second per player and felt exactly like a laggy mouse.
     */
    fun cursor(player: Player, title: Component) {
        bars(player)
        cursors[player.uniqueId]!!.name(title)
    }

    /**
     * The page's bars, made the first time any of them is needed — all of them, and the
     * pointer's, in the order they are drawn.
     *
     * Bars stack in the order they appear and are drawn in that order too, so the order
     * they are made in is the page's order, top to bottom and back to front. A bar made
     * later would land below the pointer's, a line out and on top of it.
     */
    private fun bars(player: Player): List<BossBar> {
        pages[player.uniqueId]?.let { return it }
        val made = List(PAGE_BARS) { bar(player) }
        pages[player.uniqueId] = made
        cursors[player.uniqueId] = bar(player)
        return made
    }

    private fun bar(player: Player): BossBar {
        announcing(player)
        // Progress 0 keeps the bar itself invisible; only the title glyphs are drawn.
        return BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
            .also { player.showBossBar(it) }
    }

    fun clear(player: Player) {
        pages.remove(player.uniqueId)?.forEach { runCatching { player.hideBossBar(it) } }
        cursors.remove(player.uniqueId)?.let { runCatching { player.hideBossBar(it) } }
    }

    /**
     * Takes down every bar, on the way out.
     *
     * Written in the plainest way there is, and every step allowed to fail on its own:
     * this runs while the plugin is being unloaded, when a class the code has not touched
     * before may no longer be loadable. It once threw on a set union — a whole Kotlin
     * helper class that had never been needed until that moment — and the bars were left
     * hanging on the players. They are invisible, so nobody sees them; what they do is
     * push the next plugin's page down a line, which showed up as the page sitting too low
     * and the pointer drawing somewhere off the screen.
     */
    fun clearAll() {
        for (entry in pages.entries) {
            for (bar in entry.value) {
                try {
                    org.bukkit.Bukkit.getPlayer(entry.key)?.hideBossBar(bar)
                } catch (ignored: Throwable) {
                }
            }
        }
        for (entry in cursors.entries) {
            try {
                org.bukkit.Bukkit.getPlayer(entry.key)?.hideBossBar(entry.value)
            } catch (ignored: Throwable) {
            }
        }
        pages.clear()
        cursors.clear()
    }

    /**
     * Takes down bars left over from a previous life of this plugin.
     *
     * A page's vertical place on screen depends on how many bars are above it, so one
     * orphan is enough to put every page a line out. Ours are recognisable: white, and
     * empty of progress — which the pack also relies on, since it is the white bar's
     * texture that is made transparent.
     */
    fun clearOrphans(player: Player) {
        val ours = pages[player.uniqueId].orEmpty()
        val cursor = cursors[player.uniqueId]
        for (bar in player.activeBossBars()) {
            if (ours.any { it === bar } || bar === cursor) continue
            if (bar.color() != BossBar.Color.WHITE || bar.progress() != 0f) continue
            try {
                player.hideBossBar(bar)
            } catch (ignored: Throwable) {
            }
        }
    }
}
