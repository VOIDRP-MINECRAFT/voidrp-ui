package ru.voidrp.ui

import java.awt.image.BufferedImage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Viewport
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Picture
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.pack.ServerImages
import ru.voidrp.ui.render.GlyphEncoder

/**
 * A picture a server drops into its own folder.
 *
 * It is the one thing in the pack whose proportions are not ours to choose, so the two
 * sides have to agree about how wide it comes out: the client scales the texture to the
 * height its font gives, and the pen moves by the ink that leaves, plus one.
 */
class ServerImageTest {

    private val client by lazy { ClientSimulator.build() }

    private fun banner(): BufferedImage {
        // Wider than tall, with a margin of nothing on the right — so a width taken from
        // the file rather than from its ink would be wrong by six.
        val image = BufferedImage(96, 32, BufferedImage.TYPE_INT_ARGB)
        for (y in 4 until 28) for (x in 2 until 90) image.setRGB(x, y, 0xFF8B7BFF.toInt())
        return image
    }

    @AfterTest
    fun forget() = ServerImages.load(null)

    @Test
    fun `a picture is as wide as the client will make it`() {
        ServerImages.loadForTest("banner", banner())
        val simulator = ClientSimulator.build()
        val wrong = mutableListOf<String>()
        ServerImages.HEIGHTS.forEach { height ->
            val ours = ServerImages.advance("banner", height)
            val theirs = simulator.advanceOf(ServerImages.fontName(height), ServerImages.glyph("banner")!!)
            if (ours != theirs) wrong += "на высоте $height: у нас $ours, у клиента $theirs"
        }
        assertTrue(wrong.isEmpty(), "картинка сервера разъезжается:\n" + wrong.joinToString("\n"))
    }

    @Test
    fun `a picture keeps its proportions`() {
        ServerImages.loadForTest("banner", banner())
        // 96 by 32 asked for at 64 tall is 192 wide, whatever its ink says.
        assertEquals(192, ServerImages.width("banner", 64))
    }

    @Test
    fun `a page with a picture on it balances`() {
        ServerImages.loadForTest("banner", banner())
        val simulator = ClientSimulator.build()
        val nodes = Layout.centred(
            Panel(
                width = Size.Fixed(400),
                align = Align.CENTER,
                children = listOf(Picture("banner", 48)),
            ),
            Viewport.DEFAULT.width,
            Viewport.HEIGHT,
        ).nodes
        assertEquals(0, simulator.width(GlyphEncoder.encode(nodes)), "строка с картинкой не сошлась")
    }
}
