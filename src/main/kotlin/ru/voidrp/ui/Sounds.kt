package ru.voidrp.ui

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * The little noises an interface makes.
 *
 * A button that answers with a sound feels like a button; one that does not feels like a
 * picture of one. They are named in the config rather than chosen here, because what suits
 * a server is not for a library to decide — and an empty name turns one off.
 */
class Sounds(private val plugin: Plugin) {

    private fun play(player: Player, key: String, pitch: Float) {
        val name = plugin.config.getString("sound.$key").orEmpty()
        if (name.isBlank()) return
        val volume = plugin.config.getDouble("sound.volume", 0.35).toFloat()
        player.playSound(player.location, name, volume, pitch)
    }

    fun click(player: Player) = play(player, "click", 1.6f)

    fun hover(player: Player) = play(player, "hover", 2.0f)

    fun open(player: Player) = play(player, "open", 1.2f)

    fun close(player: Player) = play(player, "close", 0.9f)
}
