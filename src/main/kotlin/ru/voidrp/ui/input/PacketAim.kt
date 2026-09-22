package ru.voidrp.ui.input

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit

/**
 * Where the player is looking, taken the moment the client says so.
 *
 * A look packet arrives and then waits: the server reads its inbound queue once a tick, so
 * by the time `player.location` knows about a turn, up to a twentieth of a second has
 * passed. For a pointer driven by that look, it is half of all the lag there is.
 *
 * PacketEvents hands us the packet on the network thread instead, which is as early as the
 * information exists on this side. It is optional — a server without it loses nothing but
 * those milliseconds — so everything here is behind a check that the plugin is installed,
 * and the class is never touched if it is not.
 */
class PacketAim {

    private val looks = ConcurrentHashMap<UUID, FloatArray>()

    /** Registers the listener, and says whether there was anything to register it with. */
    fun install(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("packetevents")?.isEnabled != true) return false
        return runCatching {
            PacketEvents.getAPI().eventManager.registerListener(
                object : PacketListenerAbstract(PacketListenerPriority.MONITOR) {
                    override fun onPacketReceive(event: PacketReceiveEvent) {
                        when (event.packetType) {
                            PacketType.Play.Client.PLAYER_ROTATION -> {
                                val look = WrapperPlayClientPlayerRotation(event)
                                remember(event.user.uuid, look.yaw, look.pitch)
                            }

                            PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION -> {
                                val look = WrapperPlayClientPlayerPositionAndRotation(event)
                                remember(event.user.uuid, look.yaw, look.pitch)
                            }

                            else -> Unit
                        }
                    }
                },
            )
            true
        }.getOrDefault(false)
    }

    private fun remember(player: UUID?, yaw: Float, pitch: Float) {
        if (player == null) return
        looks[player] = floatArrayOf(yaw, pitch)
    }

    /** The newest look this player sent, or null if none has arrived yet. */
    fun look(player: UUID): FloatArray? = looks[player]

    fun forget(player: UUID) {
        looks.remove(player)
    }
}
