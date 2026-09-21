package ru.voidrp.ui.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.voidrp.ui.VoidRpUiPlugin
import ru.voidrp.ui.page.DemoPage

/**
 * What a player or an operator types.
 *
 * Deliberately small: opening the demo, asking for the pack again, and help. Everything
 * that was useful while building this — sweeping panels across the screen, printing the
 * shapes of a page, timing the encoder — now lives behind `/vui debug` and its own
 * permission, where it cannot confuse someone who just installed the plugin.
 */
class UiCommand(private val plugin: VoidRpUiPlugin) : CommandExecutor, TabCompleter {

    private val debug = DebugCommand(plugin)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "debug" -> {
                if (!sender.hasPermission("voidrp.ui.debug")) {
                    sender.sendMessage(plugin.messages.get("command.no-permission"))
                    return true
                }
                debug.handle(sender, args.drop(1))
            }

            "open" -> withPlayer(sender) { player ->
                if (!plugin.pages.open(player, DemoPage())) return@withPlayer
                player.sendMessage(plugin.messages.get("page.opened"))
            }

            "close" -> withPlayer(sender) { player -> plugin.pages.close(player) }

            "pack" -> withPlayer(sender) { player ->
                plugin.sendPack(player)
                player.sendMessage(plugin.messages.get("pack.sent"))
            }

            "help", null -> sender.sendMessage(plugin.messages.get("command.usage"))

            else -> sender.sendMessage(plugin.messages.get("command.unknown"))
        }
        return true
    }

    private inline fun withPlayer(sender: CommandSender, action: (Player) -> Unit) {
        if (sender is Player) action(sender) else sender.sendMessage(plugin.messages.get("command.players-only"))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): List<String> = when {
        args.size <= 1 -> listOf("open", "close", "pack", "help").let {
            if (sender.hasPermission("voidrp.ui.debug")) it + "debug" else it
        }.filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) }

        args[0].equals("debug", ignoreCase = true) -> debug.complete(args.drop(1))

        else -> emptyList()
    }
}
