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
        render(ru.voidrp.ui.page.HomePage().view(), File(out, "home.png"))
        render(ru.voidrp.ui.page.DemoPage().view(), File(out, "demo.png"))
        render(ru.voidrp.ui.page.ShopPage().view(), File(out, "shop.png"))
        println("Снимки: ${out.absolutePath}")
    }

    fun render(view: View, target: File) {
        val placement = Layout.centred(view, Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT)
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
        val image = BufferedImage(Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // A dull world behind the page, so opacity can be judged rather than guessed at.
        for (y in 0 until Shaders.CANVAS_HEIGHT) {
            g.color = Color(40 + y / 40, 46 + y / 48, 58 + y / 64)
            g.drawLine(0, y, Shaders.CANVAS_WIDTH, y)
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
                g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
                )
                g.drawImage(picture, node.x, node.y, size, size, null)
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
        fun channel(shift: Int, levels: Int): Int {
            val value = (rgb shr shift) and 0xFF
            return Math.round(Math.round(value * levels / 255.0) * 255.0 / levels).toInt()
        }
        return Color(channel(16, 7), channel(8, 15), channel(0, 7))
    }

    private fun colourOf(paint: Paint): Color {
        val base = quantise(paint.rgb)
        val steps = Glyphs.ALPHA_LEVELS
        val alpha = Math.round(paint.alpha * steps).toInt().coerceIn(0, steps).toDouble() / steps
        return Color(base.red, base.green, base.blue, Math.round(alpha * 255).toInt())
    }

    private fun blend(image: BufferedImage, x: Int, y: Int, colour: Color, alpha: Double) {
        if (x < 0 || y < 0 || x >= image.width || y >= image.height || alpha <= 0.0) return
        val under = Color(image.getRGB(x, y))
        fun mix(over: Int, below: Int) = (over * alpha + below * (1 - alpha)).toInt().coerceIn(0, 255)
        image.setRGB(
            x,
            y,
            Color(mix(colour.red, under.red), mix(colour.green, under.green), mix(colour.blue, under.blue)).rgb,
        )
    }
}
