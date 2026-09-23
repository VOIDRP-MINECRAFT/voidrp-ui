package ru.voidrp.ui.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.voidrp.ui.VoidRpUiPlugin
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.page.DemoPage
import ru.voidrp.ui.page.ShopPage
import ru.voidrp.ui.render.Box
import ru.voidrp.ui.render.CornerPiece
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.Painter
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.render.Sprite
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme

/**
 * The tools that were needed to build this, kept for the next person who has to find out
 * why something is a few pixels out.
 *
 * Behind `/vui debug` and its own permission, because a server owner installing an
 * interface library should never meet them by accident.
 */
class DebugCommand(private val plugin: VoidRpUiPlugin) {

    fun handle(sender: CommandSender, args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            // How long a page takes to lay out and encode — the two things done per redraw.
            "bench" -> {
                val rounds = args.getOrNull(1)?.toIntOrNull() ?: 200
                val page = ShopPage()
                var shapes = 0
                var characters = 0
                var building = 0L
                var laying = 0L
                var encoding = 0L
                repeat(rounds) {
                    var mark = System.nanoTime()
                    val view = page.view()
                    building += System.nanoTime() - mark

                    mark = System.nanoTime()
                    val canvas = ru.voidrp.ui.layout.Viewport.DEFAULT
                    val placement = Layout.centred(view, canvas.width, canvas.height)
                    laying += System.nanoTime() - mark

                    mark = System.nanoTime()
                    val line = GlyphEncoder.encode(placement.nodes)
                    encoding += System.nanoTime() - mark

                    shapes = placement.nodes.size
                    characters = PlainTextComponentSerializer.plainText().serialize(line).length
                }
                fun micros(total: Long) = total / rounds / 1000
                sender.sendMessage(
                    Component.text(
                        "Page: $shapes shapes, $characters characters. " +
                            "Build ${micros(building)} µs, layout ${micros(laying)} µs, " +
                            "encode ${micros(encoding)} µs — " +
                            "${micros(building + laying + encoding)} µs total ($rounds runs)",
                        NamedTextColor.AQUA,
                    )
                )
            }

            // Every shape a page turns into, in the server log.
            "stats" -> {
                val canvas = ru.voidrp.ui.layout.Viewport.DEFAULT
                val nodes = Layout.centred(DemoPage().view(), canvas.width, canvas.height).nodes
                val shapes = Painter.flatten(nodes)
                val length = PlainTextComponentSerializer.plainText()
                    .serialize(GlyphEncoder.encode(nodes)).length
                sender.sendMessage(
                    Component.text("${shapes.size} shapes, $length characters — the list is in the log.", NamedTextColor.AQUA)
                )
                shapes.forEach { node ->
                    plugin.logger.info(
                        when (node) {
                            is Rect -> "  rect ${node.width}x${node.height} @ ${node.x},${node.y} " +
                                "#%06X a%.2f".format(node.paint.rgb, node.paint.alpha)
                            is CornerPiece -> "  corner r${node.radius} ${node.corner} @ ${node.x},${node.y}"
                            is Label -> "  label \"${node.text}\" ${node.size} @ ${node.x},${node.y}"
                            is Sprite -> "  sprite @ ${node.x},${node.y}"
                            is Box -> "  box @ ${node.x},${node.y}"
                            is ru.voidrp.ui.render.GlowPiece -> "  halo ${node.part} @ ${node.x},${node.y}"
                        }
                    )
                }
            }

            // Measures how fast swings arrive, which is how a held button is told from clicks.
            "clicks" -> {
                plugin.pages.traceClicks = !plugin.pages.traceClicks
                sender.sendMessage(
                    Component.text(
                        if (plugin.pages.traceClicks) "Swing tracing on." else "Swing tracing off.",
                        NamedTextColor.AQUA,
                    )
                )
            }

            "sens" -> {
                args.getOrNull(1)?.toDoubleOrNull()?.let { plugin.pages.sensitivity = it.coerceIn(1.0, 200.0) }
                val degrees = ru.voidrp.ui.layout.Viewport.DEFAULT.width / plugin.pages.sensitivity
                sender.sendMessage(
                    Component.text(
                        "Sensitivity ${plugin.pages.sensitivity} — ${degrees.toInt()}° of turn across the screen.",
                        NamedTextColor.AQUA,
                    )
                )
            }

            // Lines the cursor's own boss bar up with the page's.
            "cursor" -> {
                args.getOrNull(1)?.toIntOrNull()?.let { plugin.pages.cursorBarOffset = it }
                sender.sendMessage(
                    Component.text("Cursor bar offset: ${plugin.pages.cursorBarOffset}", NamedTextColor.AQUA)
                )
                sender.sendMessage(
                    Component.text("Drawn ${plugin.pages.frameRate} times a second.", NamedTextColor.AQUA)
                )
                player(sender)?.let { player ->
                    plugin.pages.cursorTiming(player)?.let {
                        sender.sendMessage(Component.text(it, NamedTextColor.AQUA))
                    }
                }
            }

            // One rectangle, to check placement, opacity and rounding by eye.
            "shape" -> player(sender)?.let { player ->
                val x = args.getOrNull(1)?.toIntOrNull() ?: (plugin.screens.of(player).width - 64) / 2
                val y = args.getOrNull(2)?.toIntOrNull() ?: (Shaders.CANVAS_HEIGHT - 64) / 2
                val w = args.getOrNull(3)?.toIntOrNull() ?: 64
                val h = args.getOrNull(4)?.toIntOrNull() ?: 64
                val colour = args.getOrNull(5)?.removePrefix("#")?.toIntOrNull(16) ?: 0xFFFFFF
                val alpha = args.getOrNull(6)?.toDoubleOrNull() ?: 1.0
                val radius = args.getOrNull(7)?.toIntOrNull() ?: 0
                plugin.renderer.render(
                    player,
                    listOf(Box(x, y, w, h, Style(background = Paint(colour, alpha), radius = radius))),
                )
                sender.sendMessage(
                    Component.text("$w×$h at ($x, $y), #%06X, α $alpha, r $radius".format(colour), NamedTextColor.AQUA)
                )
            }

            "text" -> player(sender)?.let { player ->
                val size = args.getOrNull(1)?.toIntOrNull() ?: Theme.TEXT_LEAD
                val text = args.drop(2).joinToString(" ").ifBlank { "The quick brown fox jumps over the lazy dog" }
                val label = Label(0, Shaders.CANVAS_HEIGHT / 2, text, size)
                val across = plugin.screens.of(player).width
                plugin.renderer.render(
                    player,
                    listOf(label.copy(x = (across - label.width) / 2)),
                    across / 2,
                )
                sender.sendMessage(Component.text("Size $size, width ${label.width}.", NamedTextColor.AQUA))
            }

            // Walks a panel across the canvas so placement can be judged in motion.
            "sweep" -> player(sender)?.let { plugin.startSweep(it) }

            // A recording of the pointer, to look at rather than to feel.
            "trace" -> player(sender)?.let { player ->
                val seconds = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 60) ?: 10
                val file = java.io.File(plugin.dataFolder, "cursor-trace.csv")
                if (plugin.pages.traceCursor(player, seconds, file)) {
                    sender.sendMessage("§aRecording the pointer for ${seconds}s → §f${file.path}")
                } else {
                    sender.sendMessage("§cOpen a page first.")
                }
            }

            // Design without logging in twice: whatever the player is looking at, drawn to
            // a PNG next to the plugin. The same renderer the tests and the docs use.
            "shot" -> player(sender)?.let { player ->
                val page = plugin.pages.current(player)
                if (page == null) {
                    sender.sendMessage("§cOpen a page first.")
                    return@let
                }
                val shapes = args.drop(1).mapNotNull { ru.voidrp.ui.layout.Viewport.parse(it) }
                    .ifEmpty { listOf(plugin.pages.viewportOf(player)) }
                val folder = java.io.File(plugin.dataFolder, "preview").apply { mkdirs() }
                val name = page.javaClass.simpleName.removeSuffix("Page").lowercase()
                shapes.forEach { screen ->
                    val file = java.io.File(
                        folder,
                        if (shapes.size == 1) "$name.png" else "$name-${screen.width}.png",
                    )
                    runCatching { ru.voidrp.ui.preview.Preview.render(page, file, screen) }
                        .onSuccess { sender.sendMessage("§aSnapshot: §f${file.path}") }
                        .onFailure { sender.sendMessage("§cFailed: ${it.message}") }
                }
            }

            "clear" -> player(sender)?.let { player ->
                plugin.stopSweep(player)
                plugin.pages.close(player)
                plugin.renderer.clear(player)
            }

            else -> sender.sendMessage(plugin.messages.get("command.usage-debug"))
        }
    }

    fun complete(args: List<String>): List<String> = if (args.size <= 1) {
        listOf("bench", "stats", "clicks", "sens", "cursor", "trace", "shape", "text", "shot", "sweep", "clear")
            .filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) }
    } else {
        emptyList()
    }

    private fun player(sender: CommandSender): Player? {
        if (sender is Player) return sender
        sender.sendMessage(plugin.messages.get("command.players-only"))
        return null
    }
}
