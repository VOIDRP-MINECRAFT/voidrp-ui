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
import ru.voidrp.ui.pack.PackServer
import ru.voidrp.ui.page.PageManager
import ru.voidrp.ui.pack.Shaders
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
    val pages = PageManager(this, renderer)
    private val sweeps = mutableMapOf<UUID, BukkitTask>()
    private lateinit var packFile: File
    private var packHash: String = ""
    private var packServer: PackServer? = null

    /** The address each player typed to get here; the one to hand them the pack from. */
    private val hostnames = mutableMapOf<UUID, String>()

    override fun onEnable() {
        saveDefaultConfig()
        packFile = File(dataFolder, "voidrp-ui.zip")
        packHash = PackBuilder(
            shaderMode = config.getString("pack.shader-mode", "patched")!!,
            withOverlay = config.getBoolean("pack.legacy-overlay", true),
        ).build(packFile)
        logger.info("Ресурспак собран: ${packFile.name}, ${packFile.length() / 1024} КБ, sha1 $packHash")

        // Serving the pack ourselves is what makes this plugin drop-in: no zip to host,
        // nothing to keep in step with the build.
        if (config.getString("pack.url").isNullOrBlank() && config.getBoolean("pack.serve.enabled", true)) {
            val port = config.getInt("pack.serve.port", 8123)
            packServer = PackServer(packFile, port, logger).takeIf { it.start() }
        }

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(pages, this)
        // The cursor follows the player's aim, so it is read every tick.
        server.scheduler.runTaskTimer(this, Runnable { pages.tick() }, 1L, 1L)
        pages.start()
        getCommand("vui")?.let {
            val handler = UiCommand(this)
            it.setExecutor(handler)
            it.tabCompleter = handler
        }
    }

    override fun onDisable() {
        packServer?.stop()
        pages.shutdown()
        sweeps.values.forEach { it.cancel() }
        sweeps.clear()
        renderer.clearAll()
    }

    /** The hostname is only known at login, and it is what the pack link is built from. */
    @EventHandler
    fun onLogin(event: org.bukkit.event.player.PlayerLoginEvent) {
        hostnames[event.player.uniqueId] = event.hostname
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
        hostnames.remove(event.player.uniqueId)
        stopSweep(event.player)
        renderer.clear(event.player)
    }

    /**
     * Sends the pack as its own request, which a 1.20.3+ client stacks on top of whatever
     * other packs the server already applies — so this coexists with a server's own pack
     * instead of replacing it.
     */
    fun sendPack(player: Player) {
        val url = packUrl(player)
        if (url.isBlank()) {
            logger.warning("Пак негде взять: укажите pack.url или включите pack.serve.enabled.")
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

    /**
     * Where this player should fetch the pack.
     *
     * Players reach a server by whatever name they typed, which is often not the name the
     * machine knows itself by, so the link is built from that — it then works the same for
     * someone on the same network and someone on the other side of the internet, with
     * nothing to configure. A server behind a proxy, or one that would rather host the zip
     * elsewhere, sets pack.url and none of this applies.
     */
    private fun packUrl(player: Player): String {
        config.getString("pack.url")?.takeIf { it.isNotBlank() }?.let { return it }
        val serving = packServer ?: return ""
        val configured = config.getString("pack.serve.host").orEmpty()
        val host = when {
            configured.isNotBlank() -> configured
            else -> hostnames[player.uniqueId]?.substringBefore(':')?.takeIf { it.isNotBlank() }
                ?: player.address?.address?.hostAddress
                ?: "127.0.0.1"
        }
        return serving.urlFor(host)
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
            val x = (tick * 16) % (Shaders.CANVAS_WIDTH - 64)
            val y = Shaders.CANVAS_HEIGHT / 2 + (Math.sin(tick / 10.0) * 280).toInt()
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
