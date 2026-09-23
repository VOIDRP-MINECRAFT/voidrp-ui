package com.example.uidemo

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.voidrp.ui.api.VoidRpUi

/**
 * Пример плагина на VoidRP UI: одна команда, одна страница.
 *
 * Плагину не нужно ничего, кроме сервиса — ни ресурспака, ни шрифтов, ни рендера. Всё это
 * уже делает VoidRP UI; здесь только то, что показывать.
 */
class UiDemoPlugin : JavaPlugin() {

    override fun onEnable() {
        logger.info("Готово: /uidemo открывает страницу.")
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Команда только для игроков.")
            return true
        }
        // Сервис появляется, когда VoidRP UI включён. Если его нет, мы это переживаем.
        val ui = VoidRpUi.get() ?: run {
            player.sendMessage("VoidRP UI не установлен.")
            return true
        }
        // false означает, что у игрока ещё нет ресурспака — ему об этом уже сказали.
        ui.open(player, DemoPage(player.name))
        return true
    }
}
