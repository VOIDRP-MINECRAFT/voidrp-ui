package ru.voidrp.ui.pack

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Builds the resource pack the client needs: our patched text shaders, the font whose
 * glyphs the pages are drawn with, and the textures behind them.
 *
 * The pack is generated rather than shipped as a file so it always matches the plugin,
 * and so a server owner never has to open a zip to change anything.
 *
 * ### Why two copies of the shader
 *
 * Mojang reorganised the shader files between 26.1.2 and 26.2: up to pack format 84 the
 * file is `rendertype_text.vsh` (GLSL 150), from 26.2 (format 88) it is `text.vsh`
 * (GLSL 330) with variants behind #define. Both are verified against the real client
 * jars. The base pack carries the old layout and an overlay carries the new one, which
 * is what overlays exist for — one pack, every supported version.
 */
class PackBuilder(
    /** "patched" — our shader, "vanilla" — the untouched one, "none" — no shaders at all. */
    private val shaderMode: String = "patched",
    /** Older clients need the overlay; turning it off narrows down what a client dislikes. */
    private val withOverlay: Boolean = true,
) {

    companion object {
        /** 1.21.6 — the oldest version we support. */
        const val FORMAT_OLDEST = 63

        /** 26.1.2 — the last version with the old shader layout. */
        const val FORMAT_LEGACY_MAX = 84

        /** 26.2 — the first version with the new shader layout. */
        const val FORMAT_MODERN_MIN = 88

        /** Far enough ahead that a new release does not silently drop the pack. */
        const val FORMAT_NEWEST = 200

        private const val OVERLAY_DIR = "legacy_shaders"
    }

    /** Writes the pack to [target] and returns its SHA-1, which the client is told to expect. */
    fun build(target: File): String {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.put("pack.mcmeta", packMeta())
            zip.put("pack.png", packIcon())

            // Shaders can be left out to tell apart "the pack is rejected" from "the
            // shader does not compile" — a client refuses the whole pack either way.
            when (shaderMode) {
                "patched" -> {
                    zip.put("assets/minecraft/shaders/core/text.vsh", Shaders.TEXT_VSH_MODERN)
                    if (withOverlay) {
                        zip.put("$OVERLAY_DIR/assets/minecraft/shaders/core/rendertype_text.vsh", Shaders.TEXT_VSH_LEGACY)
                    }
                }
                // Diagnostic: the client's own shader, copied byte for byte. If even this
                // is rejected, the pack's shape is wrong, not the shader's code.
                "vanilla" -> {
                    val vanilla = javaClass.getResourceAsStream("/vanilla/text.vsh")!!.readBytes()
                    zip.put("assets/minecraft/shaders/core/text.vsh", vanilla)
                }
                // Diagnostic: the vanilla shader with one line changed — every glyph is
                // nudged down. Tells apart "the client dislikes edits" from "the client
                // dislikes something specific in our code".
                "shift" -> {
                    val vanilla = String(javaClass.getResourceAsStream("/vanilla/text.vsh")!!.readBytes())
                    val shifted = vanilla.replace(
                        "gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);",
                        "gl_Position = ProjMat * ModelViewMat * vec4(Position + vec3(0.0, 20.0, 0.0), 1.0);",
                    )
                    check(shifted != vanilla) { "Не нашёл строку gl_Position в ванильном шейдере" }
                    zip.put("assets/minecraft/shaders/core/text.vsh", shifted)
                }
            }

            // Pages ride a white boss bar; its bar is made transparent so only the glyphs
            // show. White is reserved for us — another plugin's white bar would vanish too.
            zip.put("assets/minecraft/textures/gui/sprites/boss_bar/white_background.png", transparent(182, 5))
            zip.put("assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png", transparent(182, 5))

            zip.put("assets/voidrp/font/ui.json", fontDefinition())
            rectTextures().forEach { (name, png) -> zip.put("assets/voidrp/textures/gui/$name.png", png) }
        }

        val data = bytes.toByteArray()
        target.parentFile?.mkdirs()
        target.writeBytes(data)
        return MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02x".format(it) }
    }

    /**
     * The pack claims every format we support, and the overlay takes over where the new
     * shader layout starts. A client outside the range ignores the pack instead of
     * warning the player about it.
     */
    private fun packMeta(): String = """
        {
          "pack": {
            "pack_format": $FORMAT_MODERN_MIN,
            "supported_formats": { "min_inclusive": $FORMAT_OLDEST, "max_inclusive": $FORMAT_NEWEST },
            "description": "VoidRP UI — интерфейсы сервера. void-rp.ru"
          }${if (shaderMode == "patched" && withOverlay) "," else ""}
          ${if (shaderMode == "patched" && withOverlay) """"overlays": {
            "entries": [
              {
                "formats": { "min_inclusive": $FORMAT_OLDEST, "max_inclusive": $FORMAT_LEGACY_MAX },
                "directory": "$OVERLAY_DIR"
              }
            ]
          }""" else ""}
        }
    """.trimIndent()

    /**
     * The alphabet every page is drawn with: nothing page-specific ever lands in the pack.
     *
     *  - **Rectangles** of every power-of-two width and height, 1…1024 each way. Any
     *    rectangle is a handful of these side by side, so the client draws quads of the
     *    exact size and the shader never has to scale anything.
     *  - **Spacers** that move the pen by ±1, ±2, ±4 … ±1024 without drawing, which is how
     *    a page places every glyph horizontally to the pixel.
     *
     * Ascent 0 puts a rectangle's top on the line's baseline, so the y carried in its
     * colour is exactly where its top edge lands.
     */
    private fun fontDefinition(): String {
        val providers = mutableListOf<String>()
        for (w in 0..Glyphs.MAX_EXP) {
            for (h in 0..Glyphs.MAX_EXP) {
                providers += """
                    {"type": "bitmap", "file": "voidrp:gui/${Glyphs.textureName(w, h)}.png",
                     "ascent": 0, "height": ${1 shl h}, "chars": ["${Glyphs.rect(w, h).escaped()}"]}
                """.trimIndent()
            }
        }
        val advances = Glyphs.spacers().entries.joinToString(", ") { (char, advance) ->
            "\"${char.escaped()}\": $advance"
        }
        providers += """{"type": "space", "advances": { $advances }}"""
        return """{ "providers": [ ${providers.joinToString(",\n")} ] }"""
    }

    /**
     * One white texture per aspect ratio. The bitmap provider scales a texture to the glyph
     * height and keeps its proportions, so a W×H rectangle needs a W:H texture — kept to
     * the smallest pixels that express the ratio (at most 1024×1).
     */
    private fun rectTextures(): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        for (w in 0..Glyphs.MAX_EXP) for (h in 0..Glyphs.MAX_EXP) {
            val name = Glyphs.textureName(w, h)
            if (name in out) continue
            val tw = if (w >= h) 1 shl (w - h) else 1
            val th = if (h > w) 1 shl (h - w) else 1
            val image = BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until tw) for (y in 0 until th) image.setRGB(x, y, 0xFFFFFFFF.toInt())
            out[name] = image.toPng()
        }
        return out
    }

    private fun String.escaped(): String = codePoints().toArray().joinToString("") { "\\u%04x".format(it) }

    /** A plain white square — pages tint it, so one texture serves every panel. */
    private fun panelTexture(): ByteArray {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 8) for (y in 0 until 8) image.setRGB(x, y, 0xFFFFFFFF.toInt())
        return image.toPng()
    }

    private fun transparent(width: Int, height: Int): ByteArray =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).toPng()

    private fun packIcon(): ByteArray {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 64) for (y in 0 until 64) {
            val edge = x < 3 || y < 3 || x > 60 || y > 60
            image.setRGB(x, y, if (edge) 0xFF7DA2D4.toInt() else 0xFF0B1220.toInt())
        }
        return image.toPng()
    }

    private fun BufferedImage.toPng(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(this, "PNG", out)
        return out.toByteArray()
    }

    private fun ZipOutputStream.put(path: String, content: String) = put(path, content.toByteArray())

    private fun ZipOutputStream.put(path: String, content: ByteArray) {
        // Fixed timestamps keep the archive byte-identical between restarts, so its SHA-1
        // stays the same and a copy published elsewhere does not go stale.
        val entry = ZipEntry(path).apply {
            time = 0
            creationTime = java.nio.file.attribute.FileTime.fromMillis(0)
            lastModifiedTime = java.nio.file.attribute.FileTime.fromMillis(0)
        }
        putNextEntry(entry)
        write(content)
        closeEntry()
    }

}
