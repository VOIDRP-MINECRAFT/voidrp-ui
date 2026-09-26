package ru.voidrp.ui

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bukkit.configuration.file.YamlConfiguration
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.GlyphEncoder
import ru.voidrp.ui.render.Label

/**
 * A face of a server's own, named in `theme.yml`.
 *
 * The face is global — the pack carries its sheets — so every test here puts Inter back
 * when it is done, or the tests after it would measure the wrong letters.
 */
class FaceTest {

    @AfterTest
    fun backToInter() = TextFonts.use(TextFonts.Face.INTER)

    /** Inter out of the jar as a file, standing in for a face a server drops into `fonts/`. */
    private fun interFile(folder: File): File {
        val file = File(folder, "Face-Regular.ttf")
        TextFonts::class.java.getResourceAsStream("/font/Inter-Regular.ttf")!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }

    private fun section(yaml: String) = YamlConfiguration().apply { loadFromString(yaml) }.getConfigurationSection("font")

    @Test
    fun `a pixel face has no half-lit pixels`() {
        val folder = Files.createTempDirectory("voidrp-fonts").toFile()
        TextFonts.use(TextFonts.Face(mapOf(TextFonts.Weight.REGULAR to interFile(folder)), pixel = true, sizes = listOf(12, 24)))

        assertEquals(listOf(12, 24), TextFonts.SIZES, "the sizes baked are the face's own")
        TextFonts.all().forEach { sheet ->
            val image = ImageIO.read(ByteArrayInputStream(sheet.png))
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr 24
                assertTrue(alpha == 0 || alpha == 255, "size ${sheet.size}/${sheet.weight}: a smoothed pixel at $x,$y")
            }
        }
    }

    @Test
    fun `text in a face of the server's own still balances`() {
        val folder = Files.createTempDirectory("voidrp-fonts").toFile()
        TextFonts.use(TextFonts.Face(mapOf(TextFonts.Weight.REGULAR to interFile(folder)), pixel = true, sizes = listOf(8, 16, 24)))
        val client = ClientSimulator.build()

        TextFonts.SIZES.forEach { size ->
            TextFonts.Weight.entries.forEach { weight ->
                val line = GlyphEncoder.encode(listOf(Label(40, 40, "Händler „Größe“ 1234", size, weight = weight)))
                assertTrue(client.missingGlyphs(line).isEmpty(), "size $size/$weight: the pack has no glyphs for it")
                assertEquals(0, client.width(line), "size $size/$weight: the line is not zero wide")
                assertEquals(40, client.penBeforeFirstDrawn(line), "size $size/$weight: the label will not land on its x")
            }
        }
    }

    @Test
    fun `a page asking for a size in between gets the nearest one baked`() {
        TextFonts.use(TextFonts.Face(pixel = true, sizes = listOf(8, 16, 24)))
        assertEquals(16, TextFonts.nearestSize(14))
        assertEquals(24, TextFonts.nearestSize(40))
        assertEquals(8, TextFonts.nearestSize(10))
    }

    @Test
    fun `no font section keeps Inter`() {
        val warnings = mutableListOf<String>()
        assertEquals(TextFonts.Face.INTER, TextFonts.Face.read(null, File("."), warnings::add))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `the font section names files, pixel mode and sizes`() {
        val folder = Files.createTempDirectory("voidrp-fonts").toFile()
        val regular = interFile(folder)
        val face = TextFonts.Face.read(
            section("font:\n  regular: \"Face-Regular.ttf\"\n  bold: \"\"\n  pixel: true\n  sizes: [24, 8, 16, 8]\n"),
            folder,
        ) { error("no warning expected: $it") }

        assertEquals(mapOf(TextFonts.Weight.REGULAR to regular), face.files)
        assertTrue(face.pixel)
        assertEquals(listOf(8, 16, 24), face.sizes, "sizes are sorted and each baked once")
    }

    @Test
    fun `a missing file is reported and the face stays Inter`() {
        val folder = Files.createTempDirectory("voidrp-fonts").toFile()
        val warnings = mutableListOf<String>()
        val face = TextFonts.Face.read(section("font:\n  regular: \"Nowhere.ttf\"\n  pixel: true\n"), folder, warnings::add)

        assertEquals(TextFonts.Face.INTER, face)
        assertEquals(1, warnings.size, "the server owner should hear why their face is not used")
    }

    @Test
    fun `a bold file without a regular one is refused`() {
        val folder = Files.createTempDirectory("voidrp-fonts").toFile()
        interFile(folder)
        val warnings = mutableListOf<String>()
        val face = TextFonts.Face.read(section("font:\n  bold: \"Face-Regular.ttf\"\n"), folder, warnings::add)

        assertEquals(TextFonts.Face.INTER, face)
        assertEquals(1, warnings.size)
    }
}
