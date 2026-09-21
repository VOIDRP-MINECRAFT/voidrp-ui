package ru.voidrp.ui

import java.io.File
import java.util.UUID
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import ru.voidrp.ui.command.UiCommand
import ru.voidrp.ui.pack.PackBuilder
import ru.voidrp.ui.render.BossBarRenderer
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.style.Paint

/**
 * Real interfaces on a vanilla client: no mods, no launcher of ours, nothing for the
 * player to install beyond accepting the server's resource pack.
 *
 * Free and open source (MIT) — https://void-rp.ru
 */
class VoidRpUiPlugin : JavaPlugin(), Listener {

    val renderer = BossBarRenderer(logger)
    private val sweeps = mutableMapOf<UUID, BukkitTask>()
    private lateinit var packFile: File
    private var packHash: String = ""

    override fun onEnable() {
        saveDefaultConfig()
        packFile = File(dataFolder, "voidrp-ui.zip")
        packHash = PackBuilder(
            shaderMode = config.getString("pack.shader-mode", "patched")!!,
            withOverlay = config.getBoolean("pack.legacy-overlay", true),
        ).build(packFile)
        logger.info("Ресурспак собран: ${packFile.name}, ${packFile.length() / 1024} КБ, sha1 $packHash")

        server.pluginManager.registerEvents(this, this)
        getCommand("vui")?.let {
            val handler = UiCommand(this)
            it.setExecutor(handler)
            it.tabCompleter = handler
        }
    }

    override fun onDisable() {
        sweeps.values.forEach { it.cancel() }
        sweeps.clear()
        renderer.clearAll()
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (config.getBoolean("pack.send-on-join", true)) {
            sendPack(event.player)
        }
    }

    /** Says in the log what the client did with the pack — the first thing to check when nothing is drawn. */
    @EventHandler
    fun onPackStatus(event: org.bukkit.event.player.PlayerResourcePackStatusEvent) {
        logger.info("Ресурспак у ${event.player.name}: ${event.status}")
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        stopSweep(event.player)
        renderer.clear(event.player)
    }

    /**
     * Sends the pack as its own request, which a 1.20.3+ client stacks on top of whatever
     * other packs the server already applies — so this coexists with a server's own pack
     * instead of replacing it.
     */
    fun sendPack(player: Player) {
        val url = config.getString("pack.url").orEmpty()
        if (url.isBlank()) {
            logger.warning("pack.url не задан — игрокам нечего скачивать. Укажите адрес, по которому раздаётся ${packFile.name}.")
            return
        }
        val info = ResourcePackInfo.resourcePackInfo()
            .id(PACK_ID)
            .uri(java.net.URI.create(url))
            .hash(packHash)
            .build()
        player.sendResourcePacks(
            ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(config.getBoolean("pack.required", false))
                .prompt(Component.text("Интерфейсы сервера. void-rp.ru", NamedTextColor.AQUA))
                .build()
        )
    }

    /** Moves a panel across the canvas so placement can be judged while it is in motion. */
    fun startSweep(player: Player) {
        stopSweep(player)
        var tick = 0
        sweeps[player.uniqueId] = server.scheduler.runTaskTimer(this, Runnable {
            if (!player.isOnline) {
                stopSweep(player)
                return@Runnable
            }
            val x = (tick * 16) % (1920 - 64)
            val y = 540 + (Math.sin(tick / 10.0) * 300).toInt()
            renderer.render(player, listOf(Rect(x, y, 64, 64, Paint(0xFFFFFF))))
            tick++
        }, 0L, 2L)
    }

    fun stopSweep(player: Player) {
        sweeps.remove(player.uniqueId)?.cancel()
    }

    companion object {
        private val PACK_ID: UUID = UUID.fromString("7f3a1c2e-9d44-4c6b-9a10-0f3b2c5d8e01")
    }
}
