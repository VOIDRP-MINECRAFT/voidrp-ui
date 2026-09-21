package ru.voidrp.ui.render

import java.util.UUID
import java.util.logging.Logger
import net.kyori.adventure.bossbar.BossBar
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

    private val bars = mutableMapOf<UUID, BossBar>()

    fun render(player: Player, nodes: List<Node>) = render(player, GlyphEncoder.encode(nodes))

    /**
     * Sends a page that is already encoded.
     *
     * Every run the encoder produces returns the pen to where it started, so runs can be
     * put one after another and the line stays as wide as nothing — which is what lets the
     * page be encoded once and only the cursor redone each tick.
     */
    fun render(player: Player, title: net.kyori.adventure.text.Component) {
        val length = PlainTextComponentSerializer.plainText().serialize(title).length
        if (length > BUSY_PAGE) {
            log?.warning("Страница для ${player.name} — $length символов; это близко к пределу пакета.")
        }
        val bar = bars.getOrPut(player.uniqueId) {
            BossBar.bossBar(title, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS).also { player.showBossBar(it) }
        }
        // Progress 0 keeps the bar itself invisible; only the title glyphs are drawn.
        bar.name(title)
    }

    fun clear(player: Player) {
        bars.remove(player.uniqueId)?.let { player.hideBossBar(it) }
    }

    fun clearAll() {
        bars.keys.toList().forEach { id ->
            val bar = bars.remove(id) ?: return@forEach
            org.bukkit.Bukkit.getPlayer(id)?.hideBossBar(bar)
        }
    }
}
