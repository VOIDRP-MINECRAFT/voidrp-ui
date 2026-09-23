package ru.voidrp.ui

import java.io.File
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin

/**
 * Everything the player is told, in a file they can edit.
 *
 * A plugin meant to be installed by other people cannot have its wording baked into the
 * code — not the language, not the tone, not the server's own name for things. The strings
 * live in `messages.yml`, written in MiniMessage, and anything missing falls back to what
 * ships with the jar, so a half-edited file never leaves a blank where a sentence should be.
 *
 * The jar carries a set per language under `lang/`. Which one is written out on a server's
 * first run is decided by `language` in the config; after that the file belongs to the
 * server, and changing the setting will not overwrite it.
 */
class Messages(private val plugin: Plugin) {

    private var file = YamlConfiguration()
    private var defaults = YamlConfiguration()

    init {
        reload()
    }

    fun reload() {
        val language = plugin.config.getString("language", FALLBACK)?.lowercase()?.takeIf { it.isNotBlank() }
            ?: FALLBACK
        val bundled = read("lang/$language.yml") ?: read("lang/$FALLBACK.yml")
        defaults = bundled ?: YamlConfiguration()

        val target = File(plugin.dataFolder, "messages.yml")
        if (!target.isFile) {
            // Written out once, from the language the server asked for. Never overwritten:
            // the moment it exists it is the server's own wording, not ours.
            runCatching {
                plugin.dataFolder.mkdirs()
                plugin.getResource("lang/$language.yml")?.use { source ->
                    target.outputStream().use { source.copyTo(it) }
                }
            }
        }
        if (target.isFile) file = YamlConfiguration.loadConfiguration(target)
    }

    /** One message, with optional `<name>` placeholders. */
    fun get(key: String, vararg placeholders: Pair<String, String>): Component {
        val raw = file.getString(key) ?: defaults.getString(key) ?: key
        val resolvers = placeholders.map { (name, value) -> Placeholder.unparsed(name, value) }
        return MiniMessage.miniMessage().deserialize(raw, *resolvers.toTypedArray())
    }

    /**
     * The same message as plain text, for a page.
     *
     * A page is drawn out of glyphs and has no idea what a colour tag is: it needs the
     * words. Formatting in the file is honoured where it can be — in chat — and stripped
     * here, so the same string serves both.
     */
    fun text(key: String, vararg placeholders: Pair<String, String>): String =
        PlainTextComponentSerializer.plainText().serialize(get(key, *placeholders))

    private fun read(path: String): YamlConfiguration? =
        plugin.getResource(path)?.bufferedReader()?.use { YamlConfiguration.loadConfiguration(it) }

    companion object {
        /** English, because a plugin other people install should speak to them first. */
        const val FALLBACK = "en"

        private val bundled: YamlConfiguration by lazy {
            Messages::class.java.getResourceAsStream("/lang/$FALLBACK.yml")
                ?.bufferedReader()
                ?.use { YamlConfiguration.loadConfiguration(it) }
                ?: YamlConfiguration()
        }

        /**
         * The English wording that ships in the jar, for whoever has no server to ask.
         *
         * A page drawn outside a running plugin — a preview, a test — would otherwise put
         * the key itself on the screen, which is how `screen-page.title` ended up in the
         * documentation's own screenshots.
         */
        @JvmStatic
        fun bundled(key: String): String {
            val raw = bundled.getString(key) ?: return key
            return PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(raw))
        }
    }
}
