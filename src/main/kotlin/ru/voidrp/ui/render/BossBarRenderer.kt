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
class BossBarRenderer(private val log: Logger? = null) {

    companion object {
        /**
         * Past this many characters a page is worth complaining about: a title travels in
         * one packet, so a page that keeps growing eventually stops arriving at all.
         */
        private const val BUSY_PAGE = 20_000
    }

    private val pages = mutableMapOf<UUID, BossBar>()
    private val cursors = mutableMapOf<UUID, BossBar>()

    fun render(player: Player, nodes: List<Node>, centre: Int = 0) =
        render(player, GlyphEncoder.encode(nodes, centre))

    /**
     * Sends the page.
     *
     * A boss bar's title is replaced whole, so everything drawn on this one travels again
     * every time any of it changes. That is fine for a page, which changes when the player
     * does something — and it is why the cursor lives on a bar of its own.
     */
    fun render(player: Player, title: net.kyori.adventure.text.Component) {
        val length = PlainTextComponentSerializer.plainText().serialize(title).length
        if (length > BUSY_PAGE) {
            log?.warning("The page for ${player.name} is $length characters, which is near the packet limit.")
        }
        bar(pages, player).name(title)
    }

    /**
     * Sends the cursor, which moves sixty times a second.
     *
     * On its own bar it is a few dozen bytes each time instead of the whole page: with
     * both on one bar a rich page went out in full every frame, which was tens of
     * kilobytes a second per player and felt exactly like a laggy mouse.
     */
    fun cursor(player: Player, title: net.kyori.adventure.text.Component) {
        // The page's bar is made sure of first. Bars stack in the order they appear, and a
        // page that finds itself second is drawn a line lower than every y it carries —
        // which the player sees as a strip of the world along the top of the screen.
        bar(pages, player)
        bar(cursors, player).name(title)
    }

    private fun bar(store: MutableMap<UUID, BossBar>, player: Player): BossBar =
        store.getOrPut(player.uniqueId) {
            // Progress 0 keeps the bar itself invisible; only the title glyphs are drawn.
            BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
                .also { player.showBossBar(it) }
        }

    fun clear(player: Player) {
        pages.remove(player.uniqueId)?.let { runCatching { player.hideBossBar(it) } }
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
            try {
                org.bukkit.Bukkit.getPlayer(entry.key)?.hideBossBar(entry.value)
            } catch (ignored: Throwable) {
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
        val ours = pages[player.uniqueId]
        val cursor = cursors[player.uniqueId]
        for (bar in player.activeBossBars()) {
            if (bar === ours || bar === cursor) continue
            if (bar.color() != BossBar.Color.WHITE || bar.progress() != 0f) continue
            try {
                player.hideBossBar(bar)
            } catch (ignored: Throwable) {
            }
        }
    }
}
