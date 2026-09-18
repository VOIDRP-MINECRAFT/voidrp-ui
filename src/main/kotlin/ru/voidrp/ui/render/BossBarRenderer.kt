package ru.voidrp.ui.render

import java.util.UUID
import net.kyori.adventure.bossbar.BossBar
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
class BossBarRenderer {

    private val bars = mutableMapOf<UUID, BossBar>()

    fun render(player: Player, elements: List<Element>) {
        val title = GlyphEncoder.encodeAll(elements)
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
