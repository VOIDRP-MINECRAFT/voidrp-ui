package ru.voidrp.ui.input

import com.github.retrooper.packetevents.PacketEvents
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Which version of the game a player is running.
 *
 * It decides which of the two packs they are sent: Mojang moved the text shader between
 * 26.1.2 and 26.2, and a pack can only carry one of the two layouts at a time.
 *
 * The server itself never learns this — the number arrives in the handshake and Paper
 * keeps it to itself — so it is read from PacketEvents when that is installed. Without it
 * the answer is simply unknown, and the pack is chosen by trying one and watching what
 * the client does with it.
 */
class ClientProtocol {

    // Asked each time rather than remembered: this plugin may well have been constructed
    // before PacketEvents finished enabling.
    private val available: Boolean
        get() = Bukkit.getPluginManager().getPlugin("packetevents")?.isEnabled == true

    /** The protocol number this client connected with, or null if there is no telling. */
    fun of(player: Player): Int? {
        if (!available) return null
        return runCatching {
            PacketEvents.getAPI().playerManager.getClientVersion(player).protocolVersion
        }.getOrNull()?.takeIf { it > 0 }
    }
}
