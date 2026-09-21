package ru.voidrp.ui.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.voidrp.ui.VoidRpUiPlugin
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.page.DemoPage
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.render.Box
import ru.voidrp.ui.render.CornerPiece
import ru.voidrp.ui.render.Painter
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.render.Sprite

/**
 * The proof the whole design rests on: put a panel at a spot on the canvas and move it.
 *
 * If a glyph lands where the command says at any window size and GUI scale, then position
 * and size really do travel to the client at runtime, and pages never have to be baked
 * into the resource pack.
 */
class UiCommand(private val plugin: VoidRpUiPlugin) : CommandExecutor, TabCompleter {

    /**
     * A page written the way pages are meant to be written: what it contains, not where
     * each piece sits. Every size here is the site's own — a 14 is a 14px label, a 24 is
     * `--gp-space-6` — and the layout does the arithmetic.
     */
    private fun demoPage(): List<Node> {
        val width = 760
        val inner = width - Theme.SPACE_6 * 2 - 2

        fun stat(caption: String, value: String, style: Style) = Panel(
            style = style,
            width = Size.Fill,
            gap = 2,
            children = listOf(
                Text(caption, Theme.TEXT_CAPTION, Theme.INK_DIM),
                Text(value, Theme.TEXT_H3, Theme.INK, TextFonts.Weight.SEMIBOLD),
            ),
        )

        fun button(caption: String, style: Style) = Panel(
            style = style,
            width = Size.Fixed(220),
            height = Size.Fixed(48),
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = listOf(Text(caption, style.textSize, style.textColour, style.textWeight)),
        )

        val page = Panel(
            style = Theme.page,
            width = Size.Fixed(width),
            gap = Theme.SPACE_4,
            children = listOf(
                Panel(
                    gap = 2,
                    children = listOf(
                        Text("VoidRP: Origins", Theme.TEXT_H2, Theme.INK, TextFonts.Weight.SEMIBOLD),
                        Text("Интерфейс рисует ванильный клиент", Theme.TEXT_BODY, Theme.INK_SOFT),
                    ),
                ),
                Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),
                stat("Игроков онлайн", "42 из 200", Theme.card),
                stat("Сезон пропуска", "15 уровень", Theme.cardAccent),
                // A progress bar is a track with a fill inside it.
                Panel(
                    style = Style(background = Paint(Theme.LINE, 0.14), radius = 6),
                    width = Size.Fill,
                    height = Size.Fixed(12),
                    children = listOf(
                        Panel(
                            style = Style(background = Paint(Theme.VIOLET, 0.95), radius = 6),
                            width = Size.Fixed(inner * 2 / 3),
                            height = Size.Fill,
                        )
                    ),
                ),
                Panel(
                    direction = Direction.ROW,
                    gap = Theme.SPACE_3,
                    width = Size.Fill,
                    children = listOf(
                        button("Продолжить", Theme.buttonPrimary),
                        button("Отмена", Theme.buttonGhost),
                    ),
                ),
                Text("void-rp.ru", Theme.TEXT_CAPTION, Theme.INK_DIM),
            ),
        )

        return listOf(Box(0, 0, Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT, Theme.scrim)) +
            Layout.centred(page, Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT).nodes
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Runs from the console too: it draws nothing, it only reports what a page would
        // turn into, which is exactly what is needed when something lands in the wrong place.
        if (args.firstOrNull()?.lowercase() == "stats") {
            val shapes = Painter.flatten(demoPage())
            sender.sendMessage("Демо-страница: ${shapes.size} фигур — список в логе сервера.")
            plugin.logger.info("Демо-страница: ${shapes.size} фигур")
            shapes.forEach { node ->
                plugin.logger.info(
                    when (node) {
                        is Rect -> "  rect ${node.width}x${node.height} @ ${node.x},${node.y} #%06X a%.2f".format(node.paint.rgb, node.paint.alpha)
                        is CornerPiece -> "  corner r${node.radius} ${node.corner} @ ${node.x},${node.y} a%.2f".format(node.paint.alpha)
                        is Label -> "  label \"${node.text}\" size ${node.size} @ ${node.x},${node.y}"
                        is Box -> "  box (не развёрнут)"
                        is Sprite -> "  sprite @ ${node.x},${node.y}"
                    }
                )
            }
            return true
        }
        if (sender !is Player) {
            sender.sendMessage("Команда только для игроков.")
            return true
        }
        when (args.firstOrNull()?.lowercase()) {
            "pack" -> {
                plugin.sendPack(sender)
                sender.sendMessage(Component.text("Отправил ресурспак — примите его в клиенте.", NamedTextColor.GREEN))
            }

            "test" -> {
                val x = args.getOrNull(1)?.toIntOrNull() ?: (Shaders.CANVAS_WIDTH - 64) / 2
                val y = args.getOrNull(2)?.toIntOrNull() ?: (Shaders.CANVAS_HEIGHT - 64) / 2
                val w = args.getOrNull(3)?.toIntOrNull() ?: 64
                val h = args.getOrNull(4)?.toIntOrNull() ?: 64
                val colour = args.getOrNull(5)?.removePrefix("#")?.toIntOrNull(16) ?: 0xFFFFFF
                val alpha = args.getOrNull(6)?.toDoubleOrNull() ?: 1.0
                val radius = args.getOrNull(7)?.toIntOrNull() ?: 0
                plugin.renderer.render(
                    sender,
                    listOf(Box(x, y, w, h, Style(background = Paint(colour, alpha), radius = radius))),
                )
                sender.sendMessage(
                    Component.text(
                        "Прямоугольник $w×$h в ($x, $y), цвет #%06X, прозрачность $alpha, скругление $radius.".format(colour),
                        NamedTextColor.AQUA,
                    )
                )
            }

            "sweep" -> {
                // Walks a panel across the canvas so placement can be judged in motion.
                plugin.startSweep(sender)
                sender.sendMessage(Component.text("Панель поехала по экрану. /vui clear — убрать.", NamedTextColor.AQUA))
            }

            // The interactive one: a cursor, hover and clicks, all answered on the server.
            "open" -> {
                plugin.pages.open(sender, DemoPage())
                sender.sendMessage(
                    Component.text("Страница открыта. Наводите прицелом, ЛКМ — нажать, Shift — закрыть.", NamedTextColor.AQUA)
                )
            }

            "demo" -> {
                plugin.renderer.render(sender, demoPage())
                sender.sendMessage(Component.text("Демо-окно. /vui clear — убрать.", NamedTextColor.AQUA))
            }

            // One styled box, large, so anything wrong with an edge or a corner is
            // impossible to miss.
            "style" -> {
                val name = args.getOrNull(1)?.lowercase() ?: "card"
                val style = when (name) {
                    "page" -> Theme.page
                    "accent" -> Theme.cardAccent
                    "button" -> Theme.buttonPrimary
                    "ghost" -> Theme.buttonGhost
                    else -> Theme.card
                }
                val w = 700
                val h = 300
                plugin.renderer.render(sender, listOf(
                    Box(0, 0, Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT, Theme.scrim),
                    Box(
                        (Shaders.CANVAS_WIDTH - w) / 2, (Shaders.CANVAS_HEIGHT - h) / 2, w, h, style,
                        listOf(Label(0, 0, name, Theme.TEXT_H3, style.textColour, style.textWeight)),
                    ),
                ))
                sender.sendMessage(Component.text("Стиль «$name», блок $w×$h.", NamedTextColor.AQUA))
            }

            "text" -> {
                val size = args.getOrNull(1)?.toIntOrNull() ?: Theme.TEXT_LEAD
                val text = args.drop(2).joinToString(" ").ifBlank { "Съешь ещё этих булок, ABC 123" }
                val label = Label(0, Shaders.CANVAS_HEIGHT / 2, text, size, 0xFFFFFF)
                plugin.renderer.render(sender, listOf(label.copy(x = (Shaders.CANVAS_WIDTH - label.width) / 2)))
                sender.sendMessage(
                    Component.text("Надпись размера $size, ширина ${label.width} точек холста.", NamedTextColor.AQUA)
                )
            }

            "debug" -> {
                // Same glyph straight into chat: if the pack is live it is a white square,
                // and the reported colour says whether the marker survived the trip.
                val component = ru.voidrp.ui.render.GlyphEncoder.encode(listOf(Rect(0, 0, 16, 16)))
                sender.sendMessage(Component.text("Глиф в чате → ").append(component))
                sender.sendMessage(
                    Component.text("Если это квадратик-заглушка — пак не применился. F3+T перезагружает ресурсы.", NamedTextColor.GRAY)
                )
            }

            "clear" -> {
                plugin.pages.close(sender)
                plugin.stopSweep(sender)
                plugin.renderer.clear(sender)
                sender.sendMessage(Component.text("Убрал.", NamedTextColor.GRAY))
            }

            else -> sender.sendMessage(
                Component.text("/vui pack | open | test <x> <y> <ш> <в> <#цвет> <прозр> <скругл> | text <размер> <текст> | demo | style <имя> | sweep | stats | clear", NamedTextColor.YELLOW)
            )
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> = if (args.size == 1) listOf("pack", "open", "test", "text", "demo", "style", "sweep", "debug", "stats", "clear") else emptyList()
}
