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

    /** Kept so the listener can be taken off again; PacketEvents outlives this plugin. */
    private var listener: PacketListenerAbstract? = null

    /** Registers the listener, and says whether there was anything to register it with. */
    fun install(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("packetevents")?.isEnabled != true) return false
        return runCatching {
            listener = PacketEvents.getAPI().eventManager.registerListener(
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
            ).let { it as? PacketListenerAbstract }
            true
        }.getOrDefault(false)
    }

    /**
     * Takes the listener off again.
     *
     * PacketEvents is a plugin of its own and goes on running when this one stops. A
     * listener left behind belongs to a class loader that has been closed, so the next
     * packet it sees throws "zip file closed" on the network thread — every packet, for as
     * long as the server is up. Reloading this plugin is exactly when that happens.
     */
    fun uninstall() {
        val registered = listener ?: return
        listener = null
        runCatching { PacketEvents.getAPI().eventManager.unregisterListener(registered) }
        looks.clear()
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
