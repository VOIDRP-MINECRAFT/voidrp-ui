package ru.voidrp.ui.pack

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The site's own typeface, baked into the pack.
 *
 * Pages should look like the rest of VoidRP, and nothing gives a design away faster than
 * its lettering — so the interface is set in Inter, the face the website and the in-game
 * WebGUI already use, rather than Minecraft's pixel font. Inter is rendered here into
 * bitmap sheets at the sizes pages actually use, which keeps every letter crisp (the
 * client never has to scale one) and keeps the server and the client in perfect agreement
 * about how wide a word is.
 *
 * ### Why the widths matter so much
 *
 * A page is one line of text laid out by the client, and the server has to predict the pen
 * exactly or the page slides sideways. The client advances a bitmap glyph by the width of
 * its ink plus one pixel — not by the typographic advance, which also counts the air on
 * either side of a letter. Both numbers are measured here while the sheet is drawn, and
 * the encoder makes up the difference with spacer glyphs.
 *
 * Inter is used under the SIL Open Font License; see `font/Inter-OFL.txt` in the jar.
 */
object TextFonts {

    /** Weights, named the way a stylesheet names them. */
    enum class Weight(val id: String, val resource: String) {
        REGULAR("regular", "Inter-Regular.ttf"),
        SEMIBOLD("semibold", "Inter-SemiBold.ttf"),
    }

    /** The type scale, in canvas units — which are the site's pixels, near enough. */
    val SIZES = listOf(12, 14, 16, 20, 28, 40)

    /** A column of air at the left of every cell, so a leaning letter keeps its tail. */
    private const val LEFT_PAD = 1

    /** Padding character for the tail of the last row; the client is never asked to draw it. */
    private const val BLANK = '\u0000'

    /** Everything a Russian interface needs, plus the punctuation a design reaches for. */
    private val CHARSET: List<Char> = buildList {
        for (code in 0x21..0x7E) add(code.toChar())
        for (code in 0x410..0x44F) add(code.toChar())
        add('Ё') // Ё
        add('ё') // ё
        "«»—–…·×°№→←•₽©".forEach { add(it) }
    }.distinct()

    /** How the client and the encoder each see one character. */
    data class Metric(
        /** What the client will advance the pen by: the width of the ink, plus one. */
        val clientAdvance: Int,
        /** What the text should actually advance by, from the typeface's own metrics. */
        val advance: Int,
    )

    /** One baked sheet: the texture, its grid, and the metrics for everything on it. */
    class Sheet(
        val weight: Weight,
        val size: Int,
        val png: ByteArray,
        val cellHeight: Int,
        val rows: List<String>,
        val metrics: Map<Char, Metric>,
        val spaceAdvance: Int,
    ) {
        val fontName: String get() = fontName(weight, size)
        val textureName: String get() = "font/${weight.id}_$size.png"

        /** How far the pen moves past one character in this sheet. */
        fun advance(char: Char): Int =
            if (char == ' ') spaceAdvance else metrics[char]?.advance ?: spaceAdvance

        fun width(text: String): Int {
            var total = 0
            for (index in text.indices) total += advance(text[index])
            return total
        }
    }

    fun fontName(weight: Weight, size: Int): String = "inter_${weight.id}_$size"

    /** The baked size closest to what a page asked for. */
    fun nearestSize(size: Int): Int = SIZES[sizeIndex(size)]

    /**
     * Sheets by weight and size, in a plain array.
     *
     * Widths are asked for a character at a time, several times over while a page is laid
     * out, so this is one of the hottest paths there is: a map keyed by a pair of values
     * allocated a pair on every letter, which cost more than the measuring did.
     */
    private val sheets: Array<Array<Sheet>> by lazy {
        Array(Weight.entries.size) { weight ->
            Array(SIZES.size) { index -> bake(Weight.entries[weight], SIZES[index]) }
        }
    }

    fun all(): List<Sheet> = sheets.flatMap { it.asList() }

    fun sheet(weight: Weight, size: Int): Sheet = sheets[weight.ordinal][sizeIndex(size)]

    private fun sizeIndex(size: Int): Int {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        SIZES.forEachIndexed { index, candidate ->
            val distance = Math.abs(candidate - size)
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    /** How far the pen moves for one character. */
    fun advance(char: Char, weight: Weight, size: Int): Int = sheet(weight, size).advance(char)

    fun width(text: String, weight: Weight, size: Int): Int = sheet(weight, size).width(text)

    fun known(char: Char, weight: Weight, size: Int): Boolean =
        char == ' ' || sheet(weight, size).metrics.containsKey(char)

    /**
     * The font file for one sheet. Ascent 0 puts the top of a cell on the line's baseline,
     * so a label's y is the top of its line, exactly as a rectangle's y is its top edge.
     */
    fun fontJson(sheet: Sheet): String {
        val chars = sheet.rows.joinToString(", ") { "\"${Fonts.escapeJson(it)}\"" }
        val advances = buildList {
            add("\" \": ${sheet.spaceAdvance}")
            Glyphs.spacers().forEach { (char, advance) -> add("\"${Fonts.escapeJson(char)}\": $advance") }
        }
        return """{"providers": [{"type": "space", "advances": {${advances.joinToString(", ")}}},
            {"type": "bitmap", "file": "voidrp:${sheet.textureName}", "height": ${sheet.cellHeight},
             "ascent": 0, "chars": [$chars]}]}"""
    }

    /** Draws one weight at one size into a grid of cells and measures every letter. */
    private fun bake(weight: Weight, size: Int): Sheet {
        val base = Font.createFont(
            Font.TRUETYPE_FONT,
            TextFonts::class.java.getResourceAsStream("/font/${weight.resource}")
                ?: error("В плагине нет шрифта ${weight.resource}"),
        )
        val font = base.deriveFont(size.toFloat())

        val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        probe.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        probe.font = font
        val frc = probe.fontRenderContext
        val lineMetrics = font.getLineMetrics("Hg", frc)
        val ascent = ceil(lineMetrics.ascent.toDouble()).toInt()
        val cellHeight = ascent + ceil(lineMetrics.descent.toDouble()).toInt()

        val advances = CHARSET.associateWith { char ->
            font.createGlyphVector(frc, char.toString()).getGlyphMetrics(0).advanceX.toDouble()
        }
        // Every cell is the same size, so the widest letter sets the column width; the
        // extra column keeps antialiasing from bleeding into its neighbour.
        val cellWidth = ceil(advances.values.maxOrNull() ?: 8.0).toInt() + LEFT_PAD + 2
        val spaceAdvance = font.createGlyphVector(frc, " ").getGlyphMetrics(0).advanceX.roundToInt()
        probe.dispose()

        val columns = 16
        val rowCount = (CHARSET.size + columns - 1) / columns
        val image = BufferedImage(cellWidth * columns, cellHeight * rowCount, BufferedImage.TYPE_INT_ARGB)
        val sheet = image.createGraphics()

        val metrics = mutableMapOf<Char, Metric>()
        val rows = mutableListOf<String>()
        CHARSET.chunked(columns).forEachIndexed { row, chunk ->
            val line = StringBuilder()
            chunk.forEachIndexed { column, char ->
                // Each letter is drawn on its own before it joins the sheet. Drawing
                // straight onto the sheet let a letter with a left-leaning tail — Inter's
                // j, for one — reach back into its neighbour's cell, so the client measured
                // that neighbour as wider than the bake did and every word containing an i
                // came out with a gap after it.
                val cell = BufferedImage(cellWidth, cellHeight, BufferedImage.TYPE_INT_ARGB)
                val g = cell.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
                g.font = font
                g.color = Color.WHITE
                g.drawString(char.toString(), LEFT_PAD.toFloat(), ascent.toFloat())
                g.dispose()

                sheet.drawImage(cell, column * cellWidth, row * cellHeight, null)
                line.append(char)
                metrics[char] = Metric(
                    clientAdvance = inkWidth(cell) + 1,
                    advance = advances.getValue(char).roundToInt() + LEFT_PAD,
                )
            }
            while (line.length < columns) line.append(BLANK)
            rows += line.toString()
        }
        sheet.dispose()

        return Sheet(
            weight = weight,
            size = size,
            png = image.toPng(),
            cellHeight = cellHeight,
            rows = rows,
            metrics = metrics,
            spaceAdvance = spaceAdvance,
        )
    }

    /** The client measures a glyph by its rightmost lit column, and so do we. */
    private fun inkWidth(cell: BufferedImage): Int {
        for (column in cell.width - 1 downTo 0) {
            for (row in 0 until cell.height) {
                if (cell.getRGB(column, row) ushr 24 != 0) return column + 1
            }
        }
        return 0
    }

    private fun BufferedImage.toPng(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(this, "PNG", out)
        return out.toByteArray()
    }
}
