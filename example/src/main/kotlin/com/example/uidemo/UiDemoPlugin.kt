package com.example.uidemo

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.voidrp.ui.api.VoidRpUi

/**
 * An example plugin on VoidRP UI: one command, one page.
 *
 * It needs nothing but the service — no resource pack, no fonts, no rendering. VoidRP UI does
 * all of that already; what is here is only what to show.
 */
class UiDemoPlugin : JavaPlugin() {

    override fun onEnable() {
        logger.info("Ready: /uidemo opens the page.")
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Players only.")
            return true
        }
        // The service exists once VoidRP UI is enabled. Without it, this plugin says so and carries on.
        val ui = VoidRpUi.get() ?: run {
            player.sendMessage("VoidRP UI is not installed.")
            return true
        }
        // false means the player does not have the resource pack yet — they have been told already.
        ui.open(player, DemoPage(player.name))
        return true
    }
}
