package ru.voidrp.ui

import java.io.File
import ru.voidrp.ui.layout.Viewport

/**
 * Draws the pages that ship with the plugin: `./gradlew preview`.
 *
 * The drawing itself lives in the engine ([ru.voidrp.ui.preview.Preview]), so anyone
 * writing their own pages can do the same from their own project. This is only the list of
 * what to draw and in which states — the ones a still of a fresh page never shows.
 */
object Preview {

    private fun render(view: ru.voidrp.ui.layout.View, target: File, viewport: Viewport = Viewport.DEFAULT) =
        ru.voidrp.ui.preview.Preview.render(view, target, viewport)

    private fun render(page: ru.voidrp.ui.page.Page, target: File, viewport: Viewport = Viewport.DEFAULT) =
        ru.voidrp.ui.preview.Preview.render(page, target, viewport)

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.firstOrNull() ?: "build/preview").apply { mkdirs() }
        // Point these at a live server's folders to draw its real logo and faces:
        // VOIDRP_IMAGES=.../plugins/VoidRpUI/images ./gradlew preview
        System.getenv("VOIDRP_IMAGES")?.let { ru.voidrp.ui.pack.ServerImages.load(File(it)) }
        System.getenv("VOIDRP_HEADS")?.let { ru.voidrp.ui.pack.PlayerHeads.load(File(it)) }
        render(ru.voidrp.ui.page.HomePage().view(), File(out, "home.png"))
        render(ru.voidrp.ui.page.DemoPage().view(), File(out, "demo.png"))
        render(ru.voidrp.ui.page.ShopPage().view(), File(out, "shop.png"))

        // The states a still picture of a fresh page never shows, and where layout bugs
        // hide: a dropdown standing over the card below it, and a list part way down.
        render(
            ru.voidrp.ui.page.DemoPage().also { it.onClick("mode", ru.voidrp.ui.page.Button.LEFT) }.view(),
            File(out, "demo-open.png"),
        )
        render(
            ru.voidrp.ui.page.ShopPage().also { page -> repeat(3) { page.onScroll(1) } }.view(),
            File(out, "shop-scrolled.png"),
        )
        // The tooltip never appears in a still of a page — it rides on the cursor — so it
        // is drawn here on its own, over a card, to be looked at.
        render(
            ru.voidrp.ui.layout.Panel(
                style = ru.voidrp.ui.style.Theme.card,
                width = ru.voidrp.ui.layout.Size.Fixed(520),
                height = ru.voidrp.ui.layout.Size.Fixed(260),
                justify = ru.voidrp.ui.layout.Justify.CENTER,
                align = ru.voidrp.ui.layout.Align.CENTER,
                children = listOf(
                    ru.voidrp.ui.widget.tooltipPanel(
                        "Diamond",
                        listOf("Price: 120 coins", "In stock: 12", "Good for tools and armour."),
                    ),
                ),
            ),
            File(out, "tooltip.png"),
        )
        // A bordered, rounded panel on its own: the corner where a border meets its own
        // rounding is where this kind of thing goes wrong.
        render(
            ru.voidrp.ui.layout.Panel(
                style = ru.voidrp.ui.style.Theme.card,
                width = ru.voidrp.ui.layout.Size.Fixed(420),
                height = ru.voidrp.ui.layout.Size.Fixed(220),
                justify = ru.voidrp.ui.layout.Justify.CENTER,
                align = ru.voidrp.ui.layout.Align.CENTER,
                children = listOf(
                    ru.voidrp.ui.layout.Panel(
                        style = ru.voidrp.ui.style.Style(
                            background = ru.voidrp.ui.style.Paint(ru.voidrp.ui.style.Theme.VIOLET, 0.12),
                            border = ru.voidrp.ui.style.Border(
                                1,
                                ru.voidrp.ui.style.Paint(ru.voidrp.ui.style.Theme.VIOLET, 0.4),
                            ),
                            radius = ru.voidrp.ui.style.Theme.R_MD,
                        ),
                        width = ru.voidrp.ui.layout.Size.Fixed(131),
                        height = ru.voidrp.ui.layout.Size.Fixed(78),
                    ),
                ),
            ),
            File(out, "border.png"),
        )
        // Hovered states. A page only knows what the cursor is over through its session,
        // so here it is simply told — these are the styles a still picture never shows and
        // the outline the session draws over them.
        render(
            object : ru.voidrp.ui.page.HomePage() {
                override val hovered = "tile:alliance"
            }.view(),
            File(out, "home-hover.png"),
        )
        render(
            object : ru.voidrp.ui.page.ShopPage() {
                override val hovered = "item:1"
            }.view(),
            File(out, "shop-hover.png"),
        )
        // The same page on every screen anyone plays on. A layout that only ever gets
        // looked at on one shape of monitor is a layout that breaks on the next one.
        val shapes = listOf("5:4", "4:3", "16:10", "16:9", "21:9")
        shapes.forEach { shape ->
            val viewport = Viewport.parse(shape)!!
            render(ru.voidrp.ui.page.HomePage(), File(out, "home-${shape.replace(':', 'x')}.png"), viewport)
        }

        // The one page a player sees before any other, if the server did not guess their
        // screen right: the frame they line up with their own edges.
        render(ru.voidrp.ui.page.ScreenPage(choose = {}), File(out, "screen.png"), Viewport.parse("5:4")!!)

        // The same page in every look the jar ships, so a server owner can see what they
        // are choosing between — and so a theme that breaks on a light background is
        // caught here rather than by whoever installs it.
        listOf("midnight", "daylight", "ember", "grove").forEach { name ->
            val file = File("src/main/resources/themes/$name.yml")
            if (!file.isFile) return@forEach
            ru.voidrp.ui.style.Theme.reload(
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file),
            )
            render(ru.voidrp.ui.page.HomePage(), File(out, "theme-$name.png"))
        }
        ru.voidrp.ui.style.Theme.reload(
            org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(File("src/main/resources/themes/midnight.yml")),
        )

        render(StatesSheet().view(), File(out, "states.png"))
        render(StatesSheet(part = 2).view(), File(out, "states-2.png"))
        render(StatesSheet(hover = "hover:button").view(), File(out, "states-hover.png"))
        println("Drawn into ${out.absolutePath}")
    }

}
