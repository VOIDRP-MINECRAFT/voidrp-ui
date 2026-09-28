package ru.voidrp.ui.input

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.configuration.file.YamlConfiguration

/**
 * How each player likes their pointer: how fast it goes, whether their client moves it by
 * itself, and how far their client's clock runs ahead of ours.
 *
 * All three depend on the player rather than the server — the mouse and its sensitivity,
 * the graphics card and its frame rate, the connection — so each player sets them for
 * themselves (`/vui cursor`, or a server's own settings page) and they are kept for good.
 * Anything a player has not set follows the server's `input` section.
 */
class CursorPrefs(private val file: File) {

    data class Prefs(
        val sensitivity: Double? = null,
        val motion: Boolean? = null,
        val clockOffset: Double? = null,
        /** The page in the world, pointed at with the middle of the view, rather than on the screen. */
        val world: Boolean? = null,
    )

    private val chosen = ConcurrentHashMap<UUID, Prefs>()

    fun load() {
        if (!file.isFile) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getKeys(false).forEach { key ->
            val id = runCatching { UUID.fromString(key) }.getOrNull() ?: return@forEach
            val section = yaml.getConfigurationSection(key) ?: return@forEach
            chosen[id] = Prefs(
                sensitivity = if (section.isSet("sensitivity")) section.getDouble("sensitivity").coerceIn(SENSITIVITY_MIN, SENSITIVITY_MAX) else null,
                motion = if (section.isSet("motion")) section.getBoolean("motion") else null,
                clockOffset = if (section.isSet("clock-offset")) section.getDouble("clock-offset").coerceIn(OFFSET_MIN, OFFSET_MAX) else null,
                world = if (section.isSet("world")) section.getBoolean("world") else null,
            )
        }
    }

    fun of(id: UUID): Prefs = chosen[id] ?: Prefs()

    fun update(id: UUID, change: (Prefs) -> Prefs) {
        val next = change(of(id))
        if (next == Prefs()) chosen.remove(id) else chosen[id] = next
        save()
    }

    /** Back to the server's settings. */
    fun reset(id: UUID) {
        chosen.remove(id)
        save()
    }

    @Synchronized
    private fun save() {
        val yaml = YamlConfiguration()
        chosen.forEach { (id, p) ->
            p.sensitivity?.let { yaml.set("$id.sensitivity", it) }
            p.motion?.let { yaml.set("$id.motion", it) }
            p.clockOffset?.let { yaml.set("$id.clock-offset", it) }
            p.world?.let { yaml.set("$id.world", it) }
        }
        runCatching {
            file.parentFile?.mkdirs()
            yaml.save(file)
        }
    }

    companion object {
        const val SENSITIVITY_MIN = 2.0
        const val SENSITIVITY_MAX = 40.0
        const val OFFSET_MIN = -2.0
        const val OFFSET_MAX = 4.0
    }
}
