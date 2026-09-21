package ru.voidrp.ui.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.voidrp.ui.VoidRpUiPlugin
import ru.voidrp.ui.render.Box
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.render.Rect

/**
 * The proof the whole design rests on: put a panel at a spot on the canvas and move it.
 *
 * If a glyph lands where the command says at any window size and GUI scale, then position
 * and size really do travel to the client at runtime, and pages never have to be baked
 * into the resource pack.
 */
class UiCommand(private val plugin: VoidRpUiPlugin) : CommandExecutor, TabCompleter {

    /**
     * A page written the way pages are meant to be written: boxes with styles from the
     * theme, nothing positioned by hand except within its own container.
     */
    private fun demoPage(): List<Node> {
        val width = 800
        val inner = width - Theme.SPACE_6 * 2 - 2
        fun centred(text: String, size: Int, colour: Int, boxWidth: Int, top: Int): Label {
            val label = Label(0, top, text, size, colour)
            return label.copy(x = (boxWidth - label.width) / 2)
        }
        fun stat(top: Int, caption: String, value: String, style: Style) = Box(
            0, top, inner, 92, style,
            listOf(
                Label(0, 0, caption, 2, Theme.INK_DIM),
                Label(0, 28, value, 3, Theme.INK),
            ),
        )

        return listOf(
            Box(0, 0, 1920, 1080, Theme.scrim),
            Box(
                560, 200, width, 620, Theme.page,
                listOf(
                    Label(0, 0, "VoidRP: Origins", 3, Theme.INK),
                    Label(0, 34, "Интерфейс рисует ванильный клиент", 2, Theme.INK_SOFT),
                    Box(0, 74, inner, 1, Theme.divider),
                    stat(94, "Игроков онлайн", "42 из 200", Theme.card),
                    stat(202, "Сезон пропуска", "15 уровень", Theme.cardAccent),
                    // A progress bar is two rounded boxes: the track and the fill.
                    Box(0, 318, inner, 12, Style(background = Paint(Theme.LINE, 0.14), radius = 6)),
                    Box(0, 318, inner * 2 / 3, 12, Style(background = Paint(Theme.VIOLET, 0.95), radius = 6)),
                    Box(
                        0, 366, 240, 56, Theme.buttonPrimary,
                        listOf(centred("Продолжить", 2, Theme.buttonPrimary.textColour, 240 - Theme.SPACE_4 * 2, 4)),
                    ),
                    Box(
                        260, 366, 240, 56, Theme.buttonGhost,
                        listOf(centred("Отмена", 2, Theme.buttonGhost.textColour, 240 - Theme.SPACE_4 * 2 - 2, 4)),
                    ),
                    Label(0, 452, "void-rp.ru", 1, Theme.INK_DIM),
                ),
            ),
        )
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
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
                val x = args.getOrNull(1)?.toIntOrNull() ?: 928
                val y = args.getOrNull(2)?.toIntOrNull() ?: 508
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

            "demo" -> {
                plugin.renderer.render(sender, demoPage())
                sender.sendMessage(Component.text("Демо-окно. /vui clear — убрать.", NamedTextColor.AQUA))
            }

            "text" -> {
                val size = args.getOrNull(1)?.toIntOrNull() ?: 2
                val text = args.drop(2).joinToString(" ").ifBlank { "Съешь ещё этих булок, ABC 123" }
                val label = Label(0, 540, text, size, 0xFFFFFF)
                plugin.renderer.render(sender, listOf(label.copy(x = 960 - label.width / 2)))
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
                plugin.stopSweep(sender)
                plugin.renderer.clear(sender)
                sender.sendMessage(Component.text("Убрал.", NamedTextColor.GRAY))
            }

            else -> sender.sendMessage(
                Component.text("/vui pack | test <x> <y> <ш> <в> <#цвет> | text <размер> <текст> | demo | sweep | debug | clear", NamedTextColor.YELLOW)
            )
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> = if (args.size == 1) listOf("pack", "test", "text", "demo", "sweep", "debug", "clear") else emptyList()
}
