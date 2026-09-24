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
import org.bukkit.plugin.ServicePriority
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import ru.voidrp.ui.Messages
import ru.voidrp.ui.api.VoidRpUi
import ru.voidrp.ui.pack.PackBuilder
import ru.voidrp.ui.style.Theme
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

    val messages = Messages(this)
    val sounds = Sounds(this)
    /** Watches boss bars so the page can stay on the first of them. */
    val bars = ru.voidrp.ui.input.BossBarGuard()

    val renderer = BossBarRenderer(logger, bars::expectOwn)

    /**
     * What shape each player's screen is — the one thing the game never tells the server.
     *
     * A page is laid out against this width, so it is what makes the interface fit the
     * window instead of being squashed into it.
     */
    val screens = ru.voidrp.ui.layout.Screens(File(dataFolder, "screens.yml")) { serverScreen }

    /** The screen shape assumed for a player who has not said what theirs is. */
    private var serverScreen = ru.voidrp.ui.layout.Viewport.DEFAULT

    val pages = PageManager(
        this,
        renderer,
        messages,
        sounds,
        ::sendPack,
        ::packReady,
        screens,
        { config.getBoolean("display.ask-screen", true) },
        bars,
    )
    private val sweeps = mutableMapOf<UUID, BukkitTask>()
    private lateinit var packFile: File
    private var packHash: String = ""

    /**
     * The same pack built for clients older than 26.2.
     *
     * Mojang renamed the text shader between 26.1.2 and 26.2, and a pack names its shader
     * files outright, so one pack cannot serve both. Rather than pick a side, the plugin
     * builds both and hands each player the one their client can read.
     */
    private var legacyFile: File? = null
    private var legacyHash: String = ""
    private var packServer: PackServer? = null

    /** Which client each player is on, when PacketEvents is there to say. */
    private val clients = ru.voidrp.ui.input.ClientProtocol()

    /**
     * Players already sent the other pack after the first one would not load.
     *
     * Without PacketEvents the client's version is unknown, so the pack is chosen by
     * trying the modern one and watching: a client that cannot read it says so, and is
     * handed the legacy one. This remembers who has had that second chance, so a pack that
     * is broken for some other reason is not sent round and round.
     */
    private val retried = mutableSetOf<UUID>()

    /** The address each player typed to get here; the one to hand them the pack from. */
    private val hostnames = mutableMapOf<UUID, String>()

    /**
     * What each player did with our pack.
     *
     * Without it the interface is drawn in glyphs the client does not have, which looks
     * like a row of broken squares — worse than nothing. A page is refused until the pack
     * is in, and the player is told why.
     */
    private val packStatus = mutableMapOf<UUID, PlayerResourcePackStatusEvent.Status>()

    /**
     * Which build of the pack each player was last sent.
     *
     * Accepting a pack once is not enough: the plugin rebuilds it whenever it changes, and
     * a client still holding yesterday's copy has none of today's glyphs. It draws the
     * missing ones as empty squares and, because their widths are not what the server
     * predicted, the rest of the page slides off across the screen. So a page is only
     * opened for a player whose copy is the current one.
     */
    private val sentHash = mutableMapOf<UUID, String>()

    override fun onEnable() {
        saveDefaultConfig()
        // The look is a server's own: colours, type scale and rounding come from theme.yml.
        // The jar carries a few to start from, and `theme` in the config says which one is
        // written out on the first run. After that the file belongs to the server.
        installTheme()
        Theme.reload(
            org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(File(dataFolder, "theme.yml"))
        )
        // Anything a server drops in here is in the pack next time it is built.
        val images = File(dataFolder, "images").apply { mkdirs() }
        ru.voidrp.ui.pack.ServerImages.load(images)
        if (ru.voidrp.ui.pack.ServerImages.names.isNotEmpty()) {
            logger.info("Server pictures: ${ru.voidrp.ui.pack.ServerImages.names.joinToString(", ")}")
        }

        val skins = File(dataFolder, "heads").apply { mkdirs() }
        ru.voidrp.ui.pack.PlayerHeads.load(skins)
        if (ru.voidrp.ui.pack.PlayerHeads.names.isNotEmpty()) {
            logger.info("Faces: ${ru.voidrp.ui.pack.PlayerHeads.names.joinToString(", ")}")
        }

        // What to lay out for until a player says what their own screen looks like.
        serverScreen = config.getString("display.screen")?.takeIf { it.isNotBlank() }
            ?.let { ru.voidrp.ui.layout.Viewport.parse(it) }
            ?: ru.voidrp.ui.layout.Viewport.DEFAULT
        screens.load()
        logger.info(
            "Assumed screen: ${ru.voidrp.ui.layout.Viewport.name(serverScreen)} " +
                "(${serverScreen.width}×${serverScreen.height}). A player sets their own with /vui screen."
        )

        // Baked into the shader, so it is decided before the pack is built.
        ru.voidrp.ui.pack.Shaders.particles = config.getBoolean("effects.particles", false)

        packFile = File(dataFolder, "voidrp-ui.zip")
        packHash = PackBuilder(
            shaderMode = config.getString("pack.shader-mode", "patched")!!,
            withOverlay = config.getBoolean("pack.legacy-overlay", false),
        ).build(packFile)
        logger.info("Pack built: ${packFile.name}, ${packFile.length() / 1024} KB, sha1 $packHash")

        if (config.getBoolean("pack.legacy", true)) {
            val older = File(dataFolder, "voidrp-ui-legacy.zip")
            legacyHash = PackBuilder(
                shaderMode = config.getString("pack.shader-mode", "patched")!!,
                legacy = true,
            ).build(older)
            legacyFile = older
            logger.info(
                "Pack for clients older than 26.2 built: ${older.name}, ${older.length() / 1024} KB, sha1 $legacyHash"
            )
            if (!config.getString("pack.url").isNullOrBlank() &&
                config.getString("pack.legacy-url").isNullOrBlank()
            ) {
                logger.warning(
                    "The pack is served from pack.url but there is no address for the build for clients " +
                        "older than 26.2: host ${older.name} beside it and set pack.legacy-url, or " +
                        "those players get no interface at all."
                )
            }
        }

        // Serving the pack ourselves is what makes this plugin drop-in: no zip to host,
        // nothing to keep in step with the build.
        if (config.getString("pack.url").isNullOrBlank() && config.getBoolean("pack.serve.enabled", true)) {
            val port = config.getInt("pack.serve.port", 8123)
            packServer = PackServer(packFile, port, logger, legacyFile).takeIf { it.start() }
        }

        // Whoever is already online is holding the previous build; hand them this one.
        server.onlinePlayers.forEach { player ->
            if (config.getBoolean("pack.send-on-join", true)) sendPack(player)
        }

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(pages, this)
        // Registered as a service so another plugin never has to cast anything to this
        // class — and so a reload underneath it does not leave stale references around.
        server.servicesManager.register(VoidRpUi::class.java, pages, this, ServicePriority.Normal)
        // The cursor follows the player's aim, so it is read every tick.
        server.scheduler.runTaskTimer(this, Runnable { pages.tick() }, 1L, 1L)
        pages.start()
        if (!pages.ordersBars) {
            logger.info(
                "No PacketEvents: if another plugin shows a boss bar, a page opened after it " +
                    "sits 19 units lower. Hide that bar while pages are open, or install PacketEvents."
            )
        }
        logger.info(
            if (pages.readsPackets) {
                "Aim is read straight off the packets — no extra lag on the cursor."
            } else {
                "No PacketEvents: aim is read once a tick, so the cursor lags by up to 50 ms."
            },
        )
        getCommand("vui")?.let {
            val handler = UiCommand(this)
            it.setExecutor(handler)
            it.tabCompleter = handler
        }
    }

    override fun onDisable() {
        server.servicesManager.unregister(VoidRpUi::class.java, pages)
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
        if (config.getBoolean("heads.collect", true)) collectSkin(event.player)
    }

    /** Says in the log what the client did with the pack — the first thing to check when nothing is drawn. */
    @EventHandler
    fun onPackStatus(event: PlayerResourcePackStatusEvent) {
        if (event.id != PACK_ID) return
        packStatus[event.player.uniqueId] = event.status
        logger.info("Pack for ${event.player.name}: ${event.status}")

        // A client that downloaded the pack and then could not load it is usually one that
        // reads the old shader names — which is exactly what the other pack is for. Only
        // worth trying when nobody told us the version; with PacketEvents the choice was
        // already made on facts.
        // FAILED_RELOAD only: the pack arrived and the client could not read it, which is
        // what an old client does with the new shader names. A failed download is a hash
        // or a network problem, and sending a different archive would only paper over it.
        if (event.status != PlayerResourcePackStatusEvent.Status.FAILED_RELOAD) return
        if (ru.voidrp.ui.pack.Shaders.particles) {
            logger.warning(
                "The pack would not load and effects.particles is on. That setting asks the " +
                    "text shader for the client's globals; if this client will not have them it " +
                    "refuses the whole pack. Turn it off and restart to be sure."
            )
        }
        val player = event.player
        if (!canSendLegacy(player)) return
        if (sentHash[player.uniqueId] == legacyHash) return
        if (!retried.add(player.uniqueId)) return
        logger.info("The pack would not load for ${player.name} — sending the build for clients older than 26.2.")
        sendPack(player, older = true)
    }

    /**
     * Whether this player can be shown a page.
     *
     * A server that applies the pack some other way — through server.properties, or a
     * merged pack of its own — can turn the check off and take responsibility for it.
     */
    fun packReady(player: Player): Boolean {
        if (!config.getBoolean("pack.require-accepted", true)) return true
        val current = sentHash[player.uniqueId]
        if (current != packHash && current != legacyHash) return false
        return packStatus[player.uniqueId] == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pages.forget(event.player)
        hostnames.remove(event.player.uniqueId)
        packStatus.remove(event.player.uniqueId)
        sentHash.remove(event.player.uniqueId)
        retried.remove(event.player.uniqueId)
        stopSweep(event.player)
        renderer.clear(event.player)
    }

    /**
     * Sends the pack as its own request, which a 1.20.3+ client stacks on top of whatever
     * other packs the server already applies — so this coexists with a server's own pack
     * instead of replacing it.
     */
    /**
     * Keeps a copy of a player's skin, so their face can be drawn on a page.
     *
     * The skin is not fetched from anywhere: the server already holds it. Every player
     * carries a signed "textures" property on their profile with the address their skin is
     * served from — Mojang's own for an online-mode server, whatever the skin plugin set
     * for any other — and that address is what is read here. Nothing is asked of Mojang and
     * no third-party service is involved, which is also why it works on a server with its
     * own skins.
     *
     * The picture only enters the pack the next time the pack is built, and players fetch
     * the pack when it changes, so a face appears after a restart rather than the moment
     * its owner walks in. That is the price of drawing anything on a vanilla client.
     */
    private fun collectSkin(player: Player) {
        val folder = File(dataFolder, "heads")
        val file = File(folder, "${player.name.lowercase()}.png")
        if (file.exists()) return
        server.scheduler.runTaskAsynchronously(
            this,
            Runnable {
                runCatching {
                    val textures = player.playerProfile.properties.firstOrNull { it.name == "textures" } ?: return@Runnable
                    val json = String(java.util.Base64.getDecoder().decode(textures.value))
                    // The property is a small JSON blob; the skin's address is the one
                    // field we want out of it.
                    val url = Regex("\"SKIN\"[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"")
                        .find(json)?.groupValues?.get(1)?.replace("\\/", "/") ?: return@Runnable
                    val bytes = java.net.URI.create(url).toURL().openStream().use { it.readBytes() }
                    folder.mkdirs()
                    file.writeBytes(bytes)
                    logger.info("Saved ${player.name}'s skin — the face enters the pack the next time it is built.")
                }.onFailure {
                    logger.fine("Could not fetch ${player.name}'s skin: ${it.message}")
                }
            },
        )
    }

    /**
     * Writes a theme out on the first run, from the set the jar carries.
     *
     * Only once: the moment `theme.yml` exists it is the server's own look, and changing
     * the setting afterwards will not paint over it.
     */
    private fun installTheme() {
        val target = File(dataFolder, "theme.yml")
        if (target.isFile) return
        val name = config.getString("theme", "midnight")?.lowercase()?.takeIf { it.isNotBlank() } ?: "midnight"
        val source = getResource("themes/$name.yml") ?: getResource("themes/midnight.yml")
        if (source == null) {
            logger.warning("No theme named $name in the jar; the built-in look is used.")
            return
        }
        runCatching {
            dataFolder.mkdirs()
            source.use { input -> target.outputStream().use { input.copyTo(it) } }
            logger.info("Theme $name written to theme.yml — edit it there, it is yours now.")
        }
    }

    fun sendPack(player: Player) = sendPack(player, older = wantsLegacy(player))

    /**
     * Whether this player's client reads the old shader file names.
     *
     * Known outright when PacketEvents is installed. Without it the honest answer is that
     * we do not know, and the modern pack is tried first — the great majority of players
     * are on a current client, and the few who are not are caught by [onPackStatus].
     */
    private fun wantsLegacy(player: Player): Boolean {
        if (!canSendLegacy(player)) return false
        val protocol = clients.of(player) ?: return false
        return protocol < PackBuilder.MODERN_PROTOCOL
    }

    /** Whether there is an older pack, and somewhere for this player to fetch it from. */
    private fun canSendLegacy(player: Player): Boolean = packUrl(player, older = true).isNotBlank()

    private fun sendPack(player: Player, older: Boolean) {
        val url = packUrl(player, older)
        if (url.isBlank()) {
            logger.warning("Nowhere to fetch the pack from: set pack.url or enable pack.serve.enabled.")
            player.sendMessage(messages.get("pack.unavailable"))
            return
        }
        val hash = if (older) legacyHash else packHash
        sentHash[player.uniqueId] = hash
        packStatus.remove(player.uniqueId)
        val info = ResourcePackInfo.resourcePackInfo()
            .id(PACK_ID)
            .uri(java.net.URI.create(url))
            .hash(hash)
            .build()
        player.sendResourcePacks(
            ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(config.getBoolean("pack.required", false))
                .prompt(messages.get("pack.prompt"))
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
    private fun packUrl(player: Player, older: Boolean = false): String {
        val configured = if (older) "pack.legacy-url" else "pack.url"
        config.getString(configured)?.takeIf { it.isNotBlank() }?.let { return it }
        // A server hosting the zip itself but saying nothing about older clients gets the
        // one address it gave, which is the right answer when everyone is on one version.
        if (!older) config.getString("pack.url")?.takeIf { it.isNotBlank() }?.let { return it }
        // pack.legacy off means no older pack was built, and the server has no page for it:
        // handing out its address anyway sent old clients to a 404 and gave them nothing.
        if (older && legacyFile == null) return ""
        val serving = packServer ?: return ""
        val named = config.getString("pack.serve.host").orEmpty()
        val host = when {
            named.isNotBlank() -> named
            else -> hostnames[player.uniqueId]?.substringBefore(':')?.takeIf { it.isNotBlank() }
                ?: player.address?.address?.hostAddress
                ?: "127.0.0.1"
        }
        return if (older) serving.legacyUrlFor(host) else serving.urlFor(host)
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
            val x = (tick * 16) % (screens.of(player).width - 64)
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
