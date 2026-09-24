package ru.voidrp.ui

import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import ru.voidrp.ui.pack.Icons

/**
 * Checks the item widths we ship against the textures they were measured from.
 *
 * Everything else in the suite asks whether the server and the client agree about the pen,
 * but for item pictures both sides read the same table — so if the table is wrong, they
 * agree and the page still slides. This is the one test that can tell, and it needs the
 * client's own jar, which is not ours to ship.
 *
 * Point `VOIDRP_CLIENT_JAR` at a Minecraft jar to run it; without one it is skipped, which
 * is the honest thing for a check that cannot be made portable.
 */
class IconWidthTest {

    @Test
    fun `shipped icon widths match the client's textures`() {
        val jar = System.getenv("VOIDRP_CLIENT_JAR")?.let(::File)?.takeIf { it.isFile }
        if (jar == null) {
            println("VOIDRP_CLIENT_JAR is not set — icon width check skipped")
            return
        }

        val wrong = mutableListOf<String>()
        var checked = 0
        ZipFile(jar).use { zip ->
            Icons.NAMES.forEach { name ->
                val entry = zip.getEntry("assets/minecraft/textures/$name.png") ?: return@forEach
                val image = zip.getInputStream(entry).use { ImageIO.read(it) }
                if (image.width != 16 || image.height != 16) {
                    wrong += "$name: a ${image.width}×${image.height} texture, yet it is in the table"
                    return@forEach
                }
                var ink = 0
                for (x in 15 downTo 0) {
                    if ((0 until 16).any { y -> image.getRGB(x, y) ushr 24 != 0 }) {
                        ink = x + 1
                        break
                    }
                }
                checked++
                // The table is stated at size 16, where one texture pixel is one unit.
                val ours = Icons.advance(name, 16) - 1
                if (ours != ink) wrong += "$name: ours $ours, the texture's $ink"
            }
        }

        assertTrue(checked > 100, "only $checked icons checked — is it the wrong jar?")
        assertTrue(wrong.isEmpty(), "widths disagree with the textures:\n" + wrong.take(10).joinToString("\n"))
    }
}
