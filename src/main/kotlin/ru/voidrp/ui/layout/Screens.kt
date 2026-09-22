package ru.voidrp.ui.layout

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

/**
 * What shape each player's screen is.
 *
 * The one thing a page needs that the game will not tell us. A vanilla client sends its
 * language, its view distance and which hand it holds a sword in, but never the size of
 * its window, and there is no packet to ask with — so the player says once, and it is
 * remembered for good.
 *
 * Until they do, they get [Viewport.DEFAULT], which is right for most people and wrong
 * without breaking anything: a page keeps what matters inside the safe band, so a screen
 * that turns out to be narrower loses decoration rather than buttons.
 */
class Screens(private val file: File, private val fallback: () -> Viewport) {

    private val chosen = ConcurrentHashMap<UUID, Int>()

    fun load() {
        if (!file.isFile) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getKeys(false).forEach { key ->
            val id = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
            val width = yaml.getInt("$key.width", 0)
            if (width in 640..4096) chosen[id] = width
        }
    }

    /** This player's canvas: what they chose, or what the server assumes for everyone. */
    fun of(player: Player): Viewport = chosen[player.uniqueId]?.let { Viewport(it) } ?: fallback()

    /** Whether this player has said what their screen looks like. */
    fun isSet(player: Player): Boolean = chosen.containsKey(player.uniqueId)

    fun set(player: Player, viewport: Viewport) {
        chosen[player.uniqueId] = viewport.width
        save()
    }

    /** Back to the server's assumption. */
    fun clear(player: Player) {
        chosen.remove(player.uniqueId)
        save()
    }

    private fun save() {
        val yaml = YamlConfiguration()
        chosen.forEach { (id, width) -> yaml.set("$id.width", width) }
        runCatching {
            file.parentFile?.mkdirs()
            yaml.save(file)
        }
    }
}
