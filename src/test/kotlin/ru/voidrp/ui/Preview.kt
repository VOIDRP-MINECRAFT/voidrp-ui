package ru.voidrp.ui

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Viewport
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.pack.Icons
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Box
import ru.voidrp.ui.render.CornerPiece
import ru.voidrp.ui.render.Label
import ru.voidrp.ui.render.Node
import ru.voidrp.ui.render.Painter
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.render.Sprite
import ru.voidrp.ui.style.Paint

/**
 * Draws a page to a PNG, the way the client would.
 *
 * Designing an interface by asking someone to log in, open it and send a screenshot is a
 * slow way to move a button four pixels. Everything needed to draw the page already exists
 * on this side — the layout produces exact geometry, the font sheets are the same ones the
 * client is given — so the same page can be rendered here and looked at directly.
 *
 * It is deliberately faithful rather than pretty: colours are quantised to the ten bits
 * that survive the trip and opacity to its sixteen steps, so what shows up here is what
 * shows up in the game. Item pictures come from the client's own jar when
 * `VOIDRP_CLIENT_JAR` points at one, since we do not ship them.
 */
object Preview {

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
                        "Алмаз",
                        listOf("Цена: 120 ₽", "В наличии: 12", "Годится на инструменты и броню."),
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

        render(StatesSheet().view(), File(out, "states.png"))
        render(StatesSheet(hover = "hover:button").view(), File(out, "states-hover.png"))
        println("Снимки: ${out.absolutePath}")
    }

    /**
     * Draws a page for one screen shape.
     *
     * The page is told what it is being drawn on first, the same way a session tells it,
     * so whatever it decides by the width of the screen — how many columns, how wide the
     * rail — is what ends up in the picture.
     */
    fun render(page: ru.voidrp.ui.page.Page, target: File, viewport: Viewport = Viewport.DEFAULT) {
        page.viewportHint = viewport
        render(page.view(), target, viewport)
    }

    fun render(view: View, target: File, viewport: Viewport = Viewport.DEFAULT) {
        val placement = Layout.centred(view, viewport.width, viewport.height)
        // A list of what ended up where, beside the picture: measuring a layout by eye on a
        // screenshot is how a row sixty-four units tall gets mistaken for ninety.
        File(target.parentFile, target.nameWithoutExtension + ".txt").writeText(
            buildString {
                placement.regions.forEach { appendLine("region ${it.id}: ${it.width}×${it.height} @ ${it.x},${it.y}") }
                placement.nodes.forEach { node ->
                    when (node) {
                        is Box -> appendLine("box ${node.width}×${node.height} @ ${node.x},${node.y}")
                        is Label -> appendLine("text \"${node.text}\" ${node.size} @ ${node.x},${node.y}")
                        else -> Unit
                    }
                }
            }
        )
        val nodes = Painter.flatten(placement.nodes)
        val image = BufferedImage(viewport.width, viewport.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // A dull world behind the page, so opacity can be judged rather than guessed at.
        for (y in 0 until viewport.height) {
            g.color = Color(40 + y / 40, 46 + y / 48, 58 + y / 64)
            g.drawLine(0, y, viewport.width, y)
        }

        nodes.forEach { node -> draw(g, image, node) }
        g.dispose()
        ImageIO.write(image, "PNG", target)
    }

    private fun draw(g: java.awt.Graphics2D, image: BufferedImage, node: Node) {
        when (node) {
            is Rect -> {
                g.composite = AlphaComposite.SrcOver
                g.color = colourOf(node.paint)
                g.fillRect(node.x, node.y, node.width, node.height)
            }

            is CornerPiece -> {
                // The very picture the pack ships, tinted by the glyph's colour.
                val level = Glyphs.alphaLevel(node.paint.alpha)
                if (level > 0) {
                    val tile = ru.voidrp.ui.pack.Corners.image(node.radius, node.corner, node.ring, level)
                    val colour = quantise(node.paint.rgb)
                    for (y in 0 until tile.height) for (x in 0 until tile.width) {
                        val alpha = (tile.getRGB(x, y) ushr 24) / 255.0
                        if (alpha > 0.0) blend(image, node.x + x, node.y + y, colour, alpha)
                    }
                }
            }

            is Label -> drawText(image, node)

            is Sprite -> icon(node)?.let { picture ->
                val size = node.font?.substringAfterLast('_')?.toIntOrNull() ?: 16
                // A server's own picture keeps its proportions; everything else here is
                // square by construction.
                val width = if (node.font?.startsWith("ui_images_") == true) {
                    Math.round(picture.width.toDouble() * size / picture.height).toInt()
                } else {
                    size
                }
                g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
                )
                g.drawImage(picture, node.x, node.y, width, size, null)
            }

            // A halo tile is the very picture the pack ships, tinted by the glyph's colour.
            is ru.voidrp.ui.render.GlowPiece -> {
                val level = Glyphs.haloLevel(node.paint.alpha)
                if (level > 0) {
                    val tile = ru.voidrp.ui.pack.Glow.image(node.part, node.corner, node.step, level, node.radius)
                    val colour = quantise(node.paint.rgb)
                    for (y in 0 until tile.height) for (x in 0 until tile.width) {
                        val alpha = (tile.getRGB(x, y) ushr 24) / 255.0
                        if (alpha > 0.0) blend(image, node.x + x, node.y + y, colour, alpha)
                    }
                }
            }

            is Box -> Unit
        }
    }

    /** Letters are blitted out of the very sheets the client is sent. */
    private fun drawText(image: BufferedImage, label: Label) {
        val sheet = TextFonts.sheet(label.weight, label.size)
        val atlas = ImageIO.read(ByteArrayInputStream(sheet.png))
        val columns = 16
        val cellWidth = atlas.width / columns
        val cellHeight = sheet.cellHeight
        val colour = quantise(label.colour)
        var pen = label.x

        label.text.forEach { char ->
            if (char == ' ') {
                pen += sheet.spaceAdvance + label.tracking
                return@forEach
            }
            val index = sheet.rows.withIndex().firstNotNullOfOrNull { (row, line) ->
                line.indexOf(char).takeIf { it >= 0 }?.let { row to it }
            } ?: return@forEach
            val (row, column) = index
            for (y in 0 until cellHeight) for (x in 0 until cellWidth) {
                val argb = atlas.getRGB(column * cellWidth + x, row * cellHeight + y)
                val alpha = (argb ushr 24) / 255.0
                if (alpha <= 0.0) continue
                blend(image, pen + x, label.y + y, colour, alpha)
            }
            pen += (sheet.metrics[char]?.advance ?: sheet.spaceAdvance) + label.tracking
        }
    }

    private val jar: ZipFile? by lazy {
        System.getenv("VOIDRP_CLIENT_JAR")?.let(::File)?.takeIf { it.isFile }?.let(::ZipFile)
    }

    private val pictures = mutableMapOf<String, BufferedImage?>()

    private fun icon(sprite: Sprite): BufferedImage? {
        val font = sprite.font ?: return null
        // Our own icons are shipped white and tinted by the glyph's colour, exactly as the
        // client does it.
        if (font.startsWith("ui_icons_")) {
            val size = font.removePrefix("ui_icons_").toIntOrNull() ?: return null
            val name = ru.voidrp.ui.pack.UiIcons.NAMES.getOrNull(sprite.glyph.codePointAt(0) - 0xEA00) ?: return null
            val bytes = ru.voidrp.ui.pack.UiIcons.png(name, size) ?: return null
            val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
            val tint = quantise(sprite.colour)
            val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until source.height) for (x in 0 until source.width) {
                val alpha = source.getRGB(x, y) ushr 24
                out.setRGB(x, y, (alpha shl 24) or (tint.rgb and 0xFFFFFF))
            }
            return out
        }
        // A server's own picture and a player's face go on in their own colours.
        if (font.startsWith("ui_images_")) {
            val name = ru.voidrp.ui.pack.ServerImages.names
                .getOrNull(sprite.glyph.codePointAt(0) - 0xEC00) ?: return null
            val bytes = ru.voidrp.ui.pack.ServerImages.png(name) ?: return null
            return ImageIO.read(ByteArrayInputStream(bytes))
        }
        if (font.startsWith("ui_heads_")) {
            val name = ru.voidrp.ui.pack.PlayerHeads.names
                .getOrNull(sprite.glyph.codePointAt(0) - 0xED00) ?: return null
            val bytes = ru.voidrp.ui.pack.PlayerHeads.textures()[
                ru.voidrp.ui.pack.PlayerHeads.textureName(name),
            ] ?: return null
            return ImageIO.read(ByteArrayInputStream(bytes))
        }
        if (!font.startsWith("icons_")) return null
        val code = sprite.glyph.codePointAt(0) - 0xF000
        val name = Icons.NAMES.getOrNull(code) ?: return null
        return pictures.getOrPut(name) {
            val zip = jar ?: return@getOrPut null
            val entry = zip.getEntry("assets/minecraft/textures/$name.png") ?: return@getOrPut null
            zip.getInputStream(entry).use { ImageIO.read(it) }
        }
    }

    /** What is left of a colour after the ten bits it has to travel in. */
    private fun quantise(rgb: Int): Color {
        val shown = ru.voidrp.ui.style.Palette.nearest(rgb)
        return Color(shown shr 16 and 0xFF, shown shr 8 and 0xFF, shown and 0xFF)
    }

    private fun colourOf(paint: Paint): Color {
        val base = quantise(paint.rgb)
        val steps = Glyphs.ALPHA_LEVELS
        val alpha = Math.round(paint.alpha * steps).toInt().coerceIn(0, steps).toDouble() / steps
        return Color(base.red, base.green, base.blue, Math.round(alpha * 255).toInt())
    }

    /**
     * What the client throws away.
     *
     * Minecraft's text shader discards a fragment fainter than a tenth, and every shape
     * here is a glyph of text — which is how a tint at a sixteenth of opacity looked right
     * in this preview and came out pure black in the game. The pack ships that shader with
     * one line changed, so our own glyphs are only dropped when they are empty; this is
     * the same line, and the two have to agree or the preview goes back to lying.
     */
    private const val DISCARD_BELOW = 0.004

    private fun blend(image: BufferedImage, x: Int, y: Int, colour: Color, alpha: Double) {
        if (x < 0 || y < 0 || x >= image.width || y >= image.height || alpha < DISCARD_BELOW) return
        val under = Color(image.getRGB(x, y))
        fun mix(over: Int, below: Int) = (over * alpha + below * (1 - alpha)).toInt().coerceIn(0, 255)
        image.setRGB(
            x,
            y,
            Color(mix(colour.red, under.red), mix(colour.green, under.green), mix(colour.blue, under.blue)).rgb,
        )
    }
}
