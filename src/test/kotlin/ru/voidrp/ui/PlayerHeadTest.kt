package ru.voidrp.ui

import java.awt.image.BufferedImage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.layout.Head
import ru.voidrp.ui.layout.Layout
import ru.voidrp.ui.layout.Viewport
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.pack.PlayerHeads
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.render.GlyphEncoder

/** A face out of a skin, which the pack carries like any other picture. */
class PlayerHeadTest {

    /** A skin whose face is one colour and whose hat is another, so both can be told apart. */
    private fun skin(tall: Boolean): BufferedImage {
        val image = BufferedImage(64, if (tall) 64 else 32, BufferedImage.TYPE_INT_ARGB)
        for (y in 8 until 16) for (x in 8 until 16) image.setRGB(x, y, 0xFF3366CC.toInt())
        for (y in 8 until 16) for (x in 40 until 44) image.setRGB(x, y, 0xFFCC3333.toInt())
        return image
    }

    @AfterTest
    fun forget() = PlayerHeads.load(null)

    @Test
    fun `a face is taken from either shape of skin`() {
        listOf(true, false).forEach { tall ->
            PlayerHeads.loadForTest("steve", skin(tall))
            assertTrue(PlayerHeads.has("steve"), "лицо не вырезано из скина ${if (tall) "64×64" else "64×32"}")
        }
    }

    @Test
    fun `a head is as wide as the client will make it`() {
        PlayerHeads.loadForTest("steve", skin(true))
        val client = ClientSimulator.build()
        val wrong = mutableListOf<String>()
        PlayerHeads.SIZES.forEach { size ->
            val ours = PlayerHeads.advance("steve", size)
            val theirs = client.advanceOf(PlayerHeads.fontName(size), PlayerHeads.glyph("steve")!!)
            if (ours != theirs) wrong += "на размере $size: у нас $ours, у клиента $theirs"
        }
        assertTrue(wrong.isEmpty(), "голова разъезжается:\n" + wrong.joinToString("\n"))
    }

    @Test
    fun `a page with a head on it balances`() {
        PlayerHeads.loadForTest("steve", skin(true))
        val client = ClientSimulator.build()
        val nodes = Layout.centred(
            Panel(width = Size.Fixed(300), children = listOf(Head("steve", 32))),
            Viewport.DEFAULT.width,
            Viewport.HEIGHT,
        ).nodes
        assertEquals(0, client.width(GlyphEncoder.encode(nodes)), "строка с головой не сошлась")
    }
}
