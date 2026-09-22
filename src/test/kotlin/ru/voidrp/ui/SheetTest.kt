package ru.voidrp.ui

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import ru.voidrp.ui.pack.Corners
import ru.voidrp.ui.pack.Glow
import ru.voidrp.ui.pack.Glyphs

/**
 * Pictures that hold several glyphs each, cell by cell.
 *
 * Shipping one file per corner of every radius at every opacity made the zip's own table of
 * contents a third of the pack, so they travel in grids now. The widths are checked
 * elsewhere against the client's own reckoning; this checks the thing a width cannot see —
 * that each cell holds the glyph it is supposed to, and not its neighbour.
 */
class SheetTest {

    private fun cellsMatch(
        sheet: ByteArray,
        columns: Int,
        rows: Int,
        expected: (Int, Int) -> java.awt.image.BufferedImage,
    ): String? {
        val image = ImageIO.read(ByteArrayInputStream(sheet)) ?: return "картинка не читается"
        val cellWidth = image.width / columns
        val cellHeight = image.height / rows
        for (row in 0 until rows) for (column in 0 until columns) {
            val want = expected(column, row)
            for (y in 0 until cellHeight) for (x in 0 until cellWidth) {
                val got = image.getRGB(column * cellWidth + x, row * cellHeight + y)
                val ours = if (x < want.width && y < want.height) want.getRGB(x, y) else 0
                if (got != ours) return "ячейка $column,$row расходится в точке $x,$y"
            }
        }
        return null
    }

    @Test
    fun `every corner sits in its own cell`() {
        val wrong = mutableListOf<String>()
        Glyphs.RADII.forEach { radius ->
            listOf(Glyphs.MIN_ALPHA_LEVEL, 8, Glyphs.ALPHA_LEVELS).forEach { level ->
                cellsMatch(Corners.sheet(radius, level), Glyphs.Corner.entries.size, 2) { column, row ->
                    Corners.image(radius, Glyphs.Corner.entries[column], row == 1, level)
                }?.let { wrong += "радиус $radius, ступень $level: $it" }
            }
        }
        assertTrue(wrong.isEmpty(), "лист уголков собран неверно:\n" + wrong.take(3).joinToString("\n"))
    }

    @Test
    fun `every icon sits in its own cell`() {
        val wrong = mutableListOf<String>()
        ru.voidrp.ui.pack.UiIcons.SIZES.forEach { size ->
            val sheet = ru.voidrp.ui.pack.UiIcons.sheet(size) ?: return@forEach
            val image = ImageIO.read(ByteArrayInputStream(sheet)) ?: return@forEach
            val columns = image.width / size
            ru.voidrp.ui.pack.UiIcons.NAMES.forEachIndexed { index, name ->
                val bytes = ru.voidrp.ui.pack.UiIcons.png(name, size) ?: return@forEachIndexed
                val want = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@forEachIndexed
                val atX = (index % columns) * size
                val atY = (index / columns) * size
                for (y in 0 until size) for (x in 0 until size) {
                    val ours = if (x < want.width && y < want.height) want.getRGB(x, y) else 0
                    if (image.getRGB(atX + x, atY + y) != ours) {
                        wrong += "$name на размере $size: расходится в точке $x,$y"
                        return@forEachIndexed
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), "лист иконок собран неверно:\n" + wrong.take(3).joinToString("\n"))
    }

    @Test
    fun `every halo tile sits in its own cell`() {
        val wrong = mutableListOf<String>()
        val level = 8
        Glyphs.GLOW_RADII.forEach { radius ->
            cellsMatch(Glow.cornerSheet(radius, level), Glyphs.Corner.entries.size, 1) { column, _ ->
                Glow.image(Glyphs.GlowPart.CORNER, Glyphs.Corner.entries[column], 1, level, radius)
            }?.let { wrong += "углы ореола радиуса $radius: $it" }
        }
        listOf(
            Glyphs.GlowPart.HORIZONTAL to Glyphs.Corner.TOP_LEFT,
            Glyphs.GlowPart.HORIZONTAL to Glyphs.Corner.BOTTOM_LEFT,
            Glyphs.GlowPart.VERTICAL to Glyphs.Corner.TOP_LEFT,
            Glyphs.GlowPart.VERTICAL to Glyphs.Corner.TOP_RIGHT,
        ).forEach { (part, corner) ->
            val horizontal = part == Glyphs.GlowPart.HORIZONTAL
            val columns = if (horizontal) 1 else Glyphs.GLOW_STEPS.size
            val rows = if (horizontal) Glyphs.GLOW_STEPS.size else 1
            cellsMatch(Glow.sideSheet(part, corner, level), columns, rows) { column, row ->
                Glow.image(part, corner, Glyphs.GLOW_STEPS[if (horizontal) row else column], level)
            }?.let { wrong += "стороны $part/$corner: $it" }
        }
        assertTrue(wrong.isEmpty(), "лист теней собран неверно:\n" + wrong.take(3).joinToString("\n"))
    }
}
