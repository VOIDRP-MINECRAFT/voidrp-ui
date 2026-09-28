package ru.voidrp.ui

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.voidrp.ui.pack.PackBuilder

/** The server's own pack files, carried in the same archive as everything the plugin draws. */
class PackExtraTest {

    private fun lege(root: File, path: String, bytes: ByteArray) {
        val file = File(root, path)
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
    }

    @Test
    fun `extra files travel in the pack and never replace the plugin's own`() {
        val root = Files.createTempDirectory("voidrp-extra").toFile()
        val inventory = byteArrayOf(1, 2, 3, 4)
        lege(root, "assets/minecraft/textures/gui/container/inventory.png", inventory)
        lege(root, "assets/minecraft/textures/gui/sprites/boss_bar/white_background.png", byteArrayOf(9))
        lege(root, "pack.mcmeta", "{}".toByteArray())

        val target = File.createTempFile("voidrp-extra", ".zip").apply { deleteOnExit() }
        val builder = PackBuilder(extra = root)
        builder.build(target)

        ZipFile(target).use { zip ->
            val entry = zip.getEntry("assets/minecraft/textures/gui/container/inventory.png")
            assertContentEquals(inventory, zip.getInputStream(entry).readBytes())
            val bar = zip.getEntry("assets/minecraft/textures/gui/sprites/boss_bar/white_background.png")
            assertTrue(zip.getInputStream(bar).readBytes().size > 1, "the plugin's own boss bar stays")
        }
        assertEquals(1, builder.extraCount)
        assertEquals(
            listOf("assets/minecraft/textures/gui/sprites/boss_bar/white_background.png", "pack.mcmeta"),
            builder.skipped,
        )
    }

    @Test
    fun `without a folder nothing changes`() {
        val target = File.createTempFile("voidrp-extra", ".zip").apply { deleteOnExit() }
        val builder = PackBuilder(extra = File("does-not-exist"))
        builder.build(target)
        assertEquals(0, builder.extraCount)
        assertTrue(builder.skipped.isEmpty())
    }
}
