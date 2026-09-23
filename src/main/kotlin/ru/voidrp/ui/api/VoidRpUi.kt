package ru.voidrp.ui.api

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import ru.voidrp.ui.page.Page

/**
 * What another plugin talks to.
 *
 * This is the whole surface: open a page for a player, close it, ask what they have open.
 * Everything else — how a page is written, what it may contain — lives in [Page] and the
 * layout, which any plugin can use directly.
 *
 * ```kotlin
 * val ui = VoidRpUi.get() ?: return   // VoidRpUI not installed
 * ui.open(player, MyShopPage())
 * ```
 *
 * The implementation registers itself with Bukkit's service manager, so nothing has to be
 * cast to our plugin class and nothing breaks when this plugin is reloaded underneath.
 * Depend on it softly (`softdepend: [VoidRpUI]` in plugin.yml) and check for null: a server
 * without the interface installed should lose the screens, not the plugin.
 */
interface VoidRpUi {

    /**
     * Opens [page] for [player], replacing whatever they had open.
     *
     * Returns false when the client has no interface pack and therefore nothing to draw
     * with; the player is told so and sent the pack again.
     */
    fun open(player: Player, page: Page): Boolean

    /** Closes whatever [player] has open, if anything. */
    fun close(player: Player)

    /** Whether this player is looking at a page right now. */
    fun isOpen(player: Player): Boolean

    /** The page on screen for this player, or null. */
    fun current(player: Player): Page?

    /** Sends this player the resource pack again — useful after they rejected it. */
    fun sendPack(player: Player)

    /**
     * The canvas this player's pages are drawn on: 1024 units tall, as wide as the shape
     * of their window.
     *
     * Read it in a page through `viewport`; this is for everything outside one.
     */
    fun viewport(player: Player): ru.voidrp.ui.layout.Viewport

    /**
     * Sets what shape this player's screen is, or clears it back to the server's own
     * setting with null. Their open page is drawn again at once.
     */
    fun setViewport(player: Player, viewport: ru.voidrp.ui.layout.Viewport?)

    /**
     * Shows the screen question: a frame on the edge of the canvas that the player lines
     * up with their own screen, and then carries on to [then].
     *
     * The plugin asks this by itself before a player's first page ever. This is for
     * putting it behind a button of your own — a settings page, a gear in a rail.
     */
    fun askScreen(player: Player, then: Page? = null): Boolean

    /** Canvas units per degree of turn. */
    var sensitivity: Double

    companion object {

        /** The running interface, or null when this plugin is not installed. */
        @JvmStatic
        fun get(): VoidRpUi? = Bukkit.getServicesManager().load(VoidRpUi::class.java)
    }
}
