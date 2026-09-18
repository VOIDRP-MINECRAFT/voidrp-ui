package ru.voidrp.ui.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.voidrp.ui.VoidRpUiPlugin
import ru.voidrp.ui.render.Rect

/**
 * The proof the whole design rests on: put a panel at a spot on the canvas and move it.
 *
 * If a glyph lands where the command says at any window size and GUI scale, then position
 * and size really do travel to the client at runtime, and pages never have to be baked
 * into the resource pack.
 */
class UiCommand(private val plugin: VoidRpUiPlugin) : CommandExecutor, TabCompleter {

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
                plugin.renderer.render(sender, listOf(Rect(x, y, w, h, colour)))
                sender.sendMessage(
                    Component.text("Прямоугольник $w×$h в ($x, $y), цвет #%06X.".format(colour), NamedTextColor.AQUA)
                )
            }

            "sweep" -> {
                // Walks a panel across the canvas so placement can be judged in motion.
                plugin.startSweep(sender)
                sender.sendMessage(Component.text("Панель поехала по экрану. /vui clear — убрать.", NamedTextColor.AQUA))
            }

            "demo" -> {
                // A window: dark body, accent header, two buttons and a progress bar —
                // enough to judge sizes, colours and layering at once.
                plugin.renderer.render(sender, listOf(
                    Rect(560, 240, 800, 600, 0x0B1220),
                    Rect(560, 240, 800, 72, 0x7DA2D4),
                    Rect(600, 360, 720, 12, 0x223044),
                    Rect(600, 360, 480, 12, 0x5FD38D),
                    Rect(600, 720, 340, 80, 0x5FD38D),
                    Rect(980, 720, 340, 80, 0xE05555),
                ))
                sender.sendMessage(Component.text("Демо-окно. /vui clear — убрать.", NamedTextColor.AQUA))
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
                Component.text("/vui pack | test <x> <y> <ш> <в> <#цвет> | demo | sweep | debug | clear", NamedTextColor.YELLOW)
            )
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> = if (args.size == 1) listOf("pack", "test", "demo", "sweep", "debug", "clear") else emptyList()
}
