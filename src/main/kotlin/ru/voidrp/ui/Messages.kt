package ru.voidrp.ui

import java.io.File
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin

/**
 * Everything the player is told, in a file they can edit.
 *
 * A plugin meant to be installed by other people cannot have its wording baked into the
 * code — not the language, not the tone, not the server's own name for things. The strings
 * live in `messages.yml`, written in MiniMessage, and anything missing falls back to what
 * ships with the jar, so a half-edited file never leaves a blank where a sentence should be.
 */
class Messages(private val plugin: Plugin) {

    private var file = YamlConfiguration()
    private var defaults = YamlConfiguration()

    init {
        reload()
    }

    fun reload() {
        runCatching { plugin.saveResource("messages.yml", false) }
        val target = File(plugin.dataFolder, "messages.yml")
        if (target.isFile) file = YamlConfiguration.loadConfiguration(target)
        plugin.getResource("messages.yml")?.bufferedReader()?.use {
            defaults = YamlConfiguration.loadConfiguration(it)
        }
    }

    /** One message, with optional `<name>` placeholders. */
    fun get(key: String, vararg placeholders: Pair<String, String>): Component {
        val raw = file.getString(key) ?: defaults.getString(key) ?: key
        val resolvers = placeholders.map { (name, value) -> Placeholder.unparsed(name, value) }
        return MiniMessage.miniMessage().deserialize(raw, *resolvers.toTypedArray())
    }
}
