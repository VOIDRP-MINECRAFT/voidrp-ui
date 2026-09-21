package ru.voidrp.ui.page

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Asking the player to type something.
 *
 * Text is the one thing a page cannot do for itself: the pointer is built out of where a
 * player is looking, and nothing in that gives us letters. The game has had a proper text
 * field since 1.21.6 — the dialogs the login screen is already built from — so a page hands
 * the keyboard over to one of those and takes the answer back.
 *
 * The page stays on screen behind the dialog, and because the dialog holds the mouse while
 * it is open, the cursor waits politely where it was left.
 */
object Prompt {

    private const val FIELD = "value"

    fun show(
        plugin: Plugin,
        player: Player,
        title: String,
        label: String,
        initial: String,
        hint: String?,
        maxLength: Int,
        onSubmit: (String) -> Unit,
    ) {
        val input = DialogInput.text(FIELD, Component.text(label))
            .width(300)
            .maxLength(maxLength)
            .initial(initial)
            .build()

        val submit = ActionButton.builder(Component.text("Готово"))
            .width(150)
            .action(
                DialogAction.customClick(
                    { response, _ ->
                        val value = response.getText(FIELD).orEmpty()
                        // Back to the server thread: what a page does with an answer is
                        // usually anything but thread-safe.
                        plugin.server.scheduler.runTask(plugin, Runnable { onSubmit(value) })
                    },
                    ClickCallback.Options.builder().uses(1).build(),
                )
            )
            .build()

        val cancel = ActionButton.builder(Component.text("Отмена")).width(150).build()

        val dialog = Dialog.create { builder ->
            builder.empty()
                .base(
                    DialogBase.builder(Component.text(title))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(
                            hint?.let {
                                listOf(DialogBody.plainMessage(Component.text(it, NamedTextColor.GRAY), 320))
                            } ?: emptyList()
                        )
                        .inputs(listOf(input))
                        .build()
                )
                .type(DialogType.confirmation(submit, cancel))
        }
        player.showDialog(dialog)
    }
}
