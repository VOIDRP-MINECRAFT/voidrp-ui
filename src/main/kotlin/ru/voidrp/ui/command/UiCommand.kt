package ru.voidrp.ui.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import ru.voidrp.ui.VoidRpUiPlugin
import ru.voidrp.ui.page.DemoPage
import ru.voidrp.ui.layout.Viewport
import ru.voidrp.ui.page.HomePage

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
                if (!plugin.pages.open(player, HomePage())) return@withPlayer
                player.sendMessage(plugin.messages.get("page.opened"))
            }

            "close" -> withPlayer(sender) { player -> plugin.pages.close(player) }

            // The old demo, kept because it shows every control in one place.
            "demo" -> withPlayer(sender) { player -> plugin.pages.open(player, DemoPage()) }

            "pack" -> withPlayer(sender) { player ->
                plugin.sendPack(player)
                player.sendMessage(plugin.messages.get("pack.sent"))
            }

            // The one thing the game never tells the server: what shape the window is.
            // Asked for here rather than guessed, and remembered for good.
            "screen", "экран" -> withPlayer(sender) { player -> screen(player, args.getOrNull(1)) }

            "help", null -> sender.sendMessage(plugin.messages.get("command.usage"))

            else -> sender.sendMessage(plugin.messages.get("command.unknown"))
        }
        return true
    }

    private fun screen(player: Player, choice: String?) {
        val screens = plugin.screens
        if (choice == null) {
            // Shown rather than described: the page draws a frame at the width the server
            // believes in, and the player picks until it sits on the edges of their screen.
            val opened = plugin.pages.open(
                player,
                ru.voidrp.ui.page.ScreenPage { chosen ->
                    if (chosen == null) screens.clear(player) else screens.set(player, chosen)
                },
            )
            if (!opened) {
                player.sendMessage(
                    plugin.messages.get("screen.current", "screen" to Viewport.name(screens.of(player))),
                )
                player.sendMessage(
                    plugin.messages.get("screen.list", "list" to Viewport.PRESETS.keys.joinToString(" · ")),
                )
            }
            return
        }
        if (choice.equals("auto", ignoreCase = true) || choice.equals("сброс", ignoreCase = true)) {
            screens.clear(player)
            player.sendMessage(
                plugin.messages.get("screen.auto", "screen" to Viewport.name(screens.of(player))),
            )
            plugin.pages.refresh(player)
            return
        }
        val chosen = Viewport.parse(choice)
        if (chosen == null) {
            player.sendMessage(plugin.messages.get("screen.unknown"))
            return
        }
        screens.set(player, chosen)
        player.sendMessage(plugin.messages.get("screen.set", "screen" to Viewport.name(chosen)))
        plugin.pages.refresh(player)
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
        args.size <= 1 -> listOf("open", "demo", "close", "screen", "pack", "help").let {
            if (sender.hasPermission("voidrp.ui.debug")) it + "debug" else it
        }.filter { it.startsWith(args.firstOrNull().orEmpty(), ignoreCase = true) }

        args[0].equals("debug", ignoreCase = true) -> debug.complete(args.drop(1))

        args[0].equals("screen", ignoreCase = true) && args.size == 2 ->
            (Viewport.PRESETS.keys + "auto").filter { it.startsWith(args[1], ignoreCase = true) }

        else -> emptyList()
    }
}
