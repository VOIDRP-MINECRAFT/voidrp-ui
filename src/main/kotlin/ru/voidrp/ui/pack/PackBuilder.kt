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
    /**
     * Whether to carry the older client's shader in an overlay.
     *
     * Off, because a 26.2 client rejects the whole pack when it is there — measured, not
     * guessed: the same pack loads with `SUCCESSFULLY_LOADED` without the overlay and
     * `FAILED_RELOAD` with it, and nothing else about it differs. The overlay's format
     * range does not include 26.2, so the client is reading what it should be skipping.
     *
     * Older clients are therefore not served by this pack at all. Doing it properly means
     * a second pack with the old shader at the root, handed out by the player's version —
     * which is worth doing and is not this.
     */
    private val withOverlay: Boolean = false,
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
                    zip.put("assets/minecraft/shaders/core/text.fsh", Shaders.TEXT_FSH_MODERN)
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

            // One shape font per opacity step, and one text font per size. The faintest
            // step is skipped: the client throws away anything under a tenth of opacity, so
            // nothing is ever drawn at a sixteenth and baking it is a sixteenth of the
            // alphabet nobody will ever see.
            for (level in Glyphs.MIN_ALPHA_LEVEL..Glyphs.ALPHA_LEVELS) {
                zip.put("assets/voidrp/font/${Glyphs.fontName(level)}.json", fontDefinition(level))
                shapeTextures(level).forEach { (name, png) ->
                    zip.put("assets/voidrp/textures/gui/a$level/$name.png", png)
                }
            }
            // The interface's own icons travel with us — they are ours, and the client has
            // nothing like them.
            // A server's own pictures: one texture each, offered at every height.
            ServerImages.textures().forEach { (name, png) ->
                zip.put("assets/voidrp/textures/$name", png)
            }
            if (ServerImages.names.isNotEmpty()) {
                ServerImages.HEIGHTS.forEach { height ->
                    zip.put("assets/voidrp/font/${ServerImages.fontName(height)}.json", ServerImages.fontJson(height))
                }
            }

            // Faces, if a server keeps any skins about.
            PlayerHeads.textures().forEach { (name, png) ->
                zip.put("assets/voidrp/textures/$name", png)
            }
            if (PlayerHeads.names.isNotEmpty()) {
                PlayerHeads.SIZES.forEach { size ->
                    zip.put("assets/voidrp/font/${PlayerHeads.fontName(size)}.json", PlayerHeads.fontJson(size))
                }
            }

            UiIcons.SIZES.forEach { size ->
                zip.put("assets/voidrp/font/${UiIcons.fontName(size)}.json", UiIcons.fontJson(size))
                UiIcons.sheet(size)?.let { sheet ->
                    zip.put("assets/voidrp/textures/${UiIcons.sheetName(size)}", sheet)
                }
            }
            // Item pictures: names only, because the client already has the textures.
            Icons.SIZES.forEach { size ->
                zip.put("assets/voidrp/font/${Icons.fontName(size)}.json", Icons.fontJson(size))
            }
            // Inter, the face the site is set in, baked at each size pages use.
            TextFonts.all().forEach { sheet ->
                zip.put("assets/voidrp/font/${sheet.fontName}.json", TextFonts.fontJson(sheet))
                zip.put("assets/voidrp/textures/${sheet.textureName}", sheet.png)
            }
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
            "supported_formats": { "min_inclusive": ${if (withOverlay) FORMAT_OLDEST else FORMAT_MODERN_MIN}, "max_inclusive": $FORMAT_NEWEST },
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
     *  - **Corners** — a quarter disc per radius on the design system's scale, which is
     *    what turns four rectangles into a rounded panel.
     *  - **Spacers** that move the pen by ±1, ±2, ±4 … ±1024 without drawing, which is how
     *    a page places every glyph horizontally to the pixel.
     *
     * Ascent 0 puts a shape's top on the line's baseline, so the y carried in its colour
     * is exactly where its top edge lands.
     *
     * The same alphabet is written once per opacity step, each pointing at textures baked
     * at that opacity — see [Glyphs] for why opacity cannot travel with the element.
     */
    private fun fontDefinition(level: Int): String {
        val dir = "voidrp:gui/a$level"
        val providers = mutableListOf<String>()
        for (w in 0..Glyphs.MAX_EXP) {
            for (h in 0..Glyphs.MAX_EXP) {
                if (!Glyphs.hasRect(w, h)) continue
                providers += """
                    {"type": "bitmap", "file": "$dir/${Glyphs.textureName(w, h)}.png",
                     "ascent": 0, "height": ${1 shl h}, "chars": ["${Glyphs.rect(w, h).escaped()}"]}
                """.trimIndent()
            }
        }
        // One picture per radius: the filled corners on the first row, the rings on the
        // second. A grid in one file instead of eight files — see Corners.sheet.
        for (radius in Glyphs.RADII) {
            providers += """
                {"type": "bitmap", "file": "$dir/${Glyphs.cornerSheetName(radius)}.png",
                 "ascent": 0, "height": $radius, "chars": [
                   "${Glyphs.Corner.entries.joinToString("") { Glyphs.corner(radius, it) }.escaped()}",
                   "${Glyphs.Corner.entries.joinToString("") { Glyphs.ringCorner(radius, it) }.escaped()}"
                 ]}
            """.trimIndent()
        }
        providers += """
            {"type": "bitmap", "file": "$dir/cursor.png",
             "ascent": 0, "height": ${Glyphs.CURSOR_SIZE}, "chars": ["${Glyphs.cursor().escaped()}"]}
        """.trimIndent()
        if (Glyphs.bakesHalo(level)) {
            // The four corner tiles of a radius share a picture, as the rounded corners do.
            Glyphs.GLOW_RADII.forEach { radius ->
                providers += """
                    {"type": "bitmap", "file": "$dir/${Glyphs.glowCornerSheetName(radius)}.png",
                     "ascent": 0, "height": ${Glyphs.GLOW_SPREAD + radius}, "chars": [
                       "${Glyphs.Corner.entries.joinToString("") {
                    Glyphs.glow(Glyphs.GlowPart.CORNER, it, 1, radius)
                }.escaped()}"
                     ]}
                """.trimIndent()
            }
            // And the sides: eight lengths to a picture, stacked so it stays square.
            listOf(
                Glyphs.GlowPart.HORIZONTAL to Glyphs.Corner.TOP_LEFT,
                Glyphs.GlowPart.HORIZONTAL to Glyphs.Corner.BOTTOM_LEFT,
                Glyphs.GlowPart.VERTICAL to Glyphs.Corner.TOP_LEFT,
                Glyphs.GlowPart.VERTICAL to Glyphs.Corner.TOP_RIGHT,
            ).forEach { (part, corner) ->
                val horizontal = part == Glyphs.GlowPart.HORIZONTAL
                val cells = Glyphs.GLOW_STEPS.map { Glyphs.glow(part, corner, it, 0) }
                val rows = if (horizontal) {
                    cells.joinToString(", ") { "\"${it.escaped()}\"" }
                } else {
                    "\"${cells.joinToString("").escaped()}\""
                }
                providers += """
                    {"type": "bitmap", "file": "$dir/${Glyphs.glowSideSheetName(part, corner)}.png",
                     "ascent": 0, "height": ${if (horizontal) Glyphs.GLOW_SPREAD else Glyphs.GLOW_STEPS.max()},
                     "chars": [$rows]}
                """.trimIndent()
            }
        }
        val advances = Glyphs.spacers().entries.joinToString(", ") { (char, advance) ->
            "\"${char.escaped()}\": $advance"
        }
        providers += """{"type": "space", "advances": { $advances }}"""
        return Fonts.compact("""{ "providers": [ ${providers.joinToString(", ")} ] }""")
    }

    /**
     * The textures behind the alphabet at one opacity step.
     *
     * One white texture per aspect ratio: the bitmap provider scales a texture to the
     * glyph height and keeps its proportions, so a W×H rectangle needs a W:H texture —
     * kept to the smallest pixels that express the ratio (at most 1024×1).
     *
     * Corners are drawn at their real size, one pixel per canvas pixel, with the curve
     * antialiased the way a browser would draw `border-radius`.
     */
    private fun shapeTextures(level: Int): Map<String, ByteArray> {
        val alpha = level.toDouble() / Glyphs.ALPHA_LEVELS
        val out = mutableMapOf<String, ByteArray>()
        for (w in 0..Glyphs.MAX_EXP) for (h in 0..Glyphs.MAX_EXP) {
            if (!Glyphs.hasRect(w, h)) continue
            val name = Glyphs.textureName(w, h)
            if (name in out) continue
            val tw = if (w >= h) 1 shl (w - h) else 1
            val th = if (h > w) 1 shl (h - w) else 1
            val image = BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB)
            val argb = (Math.round(alpha * 255).toInt() shl 24) or 0xFFFFFF
            for (x in 0 until tw) for (y in 0 until th) image.setRGB(x, y, argb)
            out[name] = image.toPng()
        }
        Glyphs.RADII.forEach { radius ->
            out[Glyphs.cornerSheetName(radius)] = Corners.sheet(radius, level)
        }
        out["cursor"] = Pointer.png(alpha)
        if (Glyphs.bakesHalo(level)) {
            Glyphs.GLOW_RADII.forEach { radius ->
                out[Glyphs.glowCornerSheetName(radius)] = Glow.cornerSheet(radius, level)
            }
            listOf(
                Glyphs.GlowPart.HORIZONTAL to Glyphs.Corner.TOP_LEFT,
                Glyphs.GlowPart.HORIZONTAL to Glyphs.Corner.BOTTOM_LEFT,
                Glyphs.GlowPart.VERTICAL to Glyphs.Corner.TOP_LEFT,
                Glyphs.GlowPart.VERTICAL to Glyphs.Corner.TOP_RIGHT,
            ).forEach { (part, corner) ->
                out[Glyphs.glowSideSheetName(part, corner)] = Glow.sideSheet(part, corner, level)
            }
        }
        return out
    }

    private fun String.escaped(): String = Fonts.escapeJson(this)

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
