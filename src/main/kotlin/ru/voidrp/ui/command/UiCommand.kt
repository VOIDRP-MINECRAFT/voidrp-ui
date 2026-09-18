package ru.voidrp.ui.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.voidrp.ui.VoidRpUiPlugin
import ru.voidrp.ui.render.Element

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
                val x = args.getOrNull(1)?.toIntOrNull() ?: 960
                val y = args.getOrNull(2)?.toIntOrNull() ?: 540
                plugin.renderer.render(sender, listOf(Element(x, y)))
                sender.sendMessage(
                    Component.text("Панель: x=$x y=$y (холст 1920×1080).", NamedTextColor.AQUA)
                )
            }

            "sweep" -> {
                // Walks a panel across the canvas so placement can be judged in motion.
                plugin.startSweep(sender)
                sender.sendMessage(Component.text("Панель поехала по экрану. /vui clear — убрать.", NamedTextColor.AQUA))
            }

            "debug" -> {
                // Same glyph straight into chat: if the pack is live it is a white square,
                // and the reported colour says whether the marker survived the trip.
                val element = Element(960, 540)
                val component = ru.voidrp.ui.render.GlyphEncoder.encode(element)
                sender.sendMessage(Component.text("Глиф в чате → ").append(component))
                sender.sendMessage(
                    Component.text(
                        "цвет=#%06X шрифт=voidrp:ui символ=U+E000".format(
                            (component.color()?.value() ?: 0)
                        ),
                        NamedTextColor.GRAY,
                    )
                )
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
                Component.text("/vui pack | test <x> <y> | sweep | debug | clear", NamedTextColor.YELLOW)
            )
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> = if (args.size == 1) listOf("pack", "test", "sweep", "debug", "clear") else emptyList()
}
