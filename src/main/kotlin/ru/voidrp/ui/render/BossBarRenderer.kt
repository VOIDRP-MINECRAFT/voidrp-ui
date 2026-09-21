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

    fun render(player: Player, nodes: List<Node>) = render(player, GlyphEncoder.encode(nodes))

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
            log?.warning("Страница для ${player.name} — $length символов; это близко к пределу пакета.")
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
        bar(cursors, player).name(title)
    }

    private fun bar(store: MutableMap<UUID, BossBar>, player: Player): BossBar =
        store.getOrPut(player.uniqueId) {
            // Progress 0 keeps the bar itself invisible; only the title glyphs are drawn.
            BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
                .also { player.showBossBar(it) }
        }

    fun clear(player: Player) {
        pages.remove(player.uniqueId)?.let { player.hideBossBar(it) }
        cursors.remove(player.uniqueId)?.let { player.hideBossBar(it) }
    }

    fun clearAll() {
        (pages.keys + cursors.keys).toSet().forEach { id ->
            org.bukkit.Bukkit.getPlayer(id)?.let { clear(it) }
        }
        pages.clear()
        cursors.clear()
    }
}
