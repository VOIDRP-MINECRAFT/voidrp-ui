package ru.voidrp.ui.pack

/**
 * The patched text shaders that make a vanilla client draw our interface.
 *
 * Minecraft draws chat, boss bars and the rest of the GUI through one vertex shader for
 * text. We replace it with a version that recognises our own glyphs and moves them, so a
 * page travels as ordinary text the client already knows how to render. Every other glyph
 * is left exactly as vanilla drew it.
 *
 * ### Who decides what
 *
 * A vertex shader sees one vertex at a time and nothing else, so the work is split:
 *
 *  - **The client lays out x.** A page is one line of text. Invisible spacer characters
 *    of known width move the pen, so the client itself puts every glyph at the right
 *    horizontal offset, to the pixel. The shader reads that offset back out of where the
 *    vertex ended up.
 *  - **The colour carries y and the fill colour.** 24 bits: a 4-bit marker, 10 bits of y,
 *    10 bits of colour (RGB 3-4-3).
 *  - **The font carries the shape.** Rectangles of power-of-two sides are baked into the
 *    font, so the client draws the quad at the right size; letters are ordinary glyphs.
 *    Nothing about a page is baked into the pack — only this alphabet.
 *
 * ### Why the line is zero wide
 *
 * A boss bar centres its title. On 26.2 that centring is baked straight into the vertex
 * positions (verified on a live client: a long line shifted everything left by half its
 * width). The encoder ends every line by returning the pen to zero, so the line has zero
 * width and starts at the centre of the screen; the shader measures x from there, in the
 * final NDC. Canvas units map straight to NDC, stretching the canvas over the whole
 * window at any resolution and GUI scale. Depth comes from the original transform.
 */
object Shaders {

    /**
     * Marks a glyph as ours: the high nibble of the red channel equals this value.
     *
     * The 16 named Minecraft colours have red 00, 55, AA or FF — nibbles 0, 5, A, F — so
     * none of them can hit 0xB. (An earlier marker of 0xA matched GRAY #AAAAAA: every grey
     * word on screen was mistaken for an element and flung across the canvas.)
     */
    const val MARKER = 0xB

    /**
     * The canvas every page is drawn on, stretched over the whole window.
     *
     * It is 1024 tall on purpose: the vertical position travels in 10 bits, and 1024 steps
     * over 1024 units is exactly one unit per step. An earlier 1080-tall canvas put the
     * steps 1.0557 units apart, so two pieces of the same panel could land a fraction of a
     * pixel apart — invisible while everything was opaque, and a bright seam or a hairline
     * gap as soon as anything became translucent.
     *
     * The width follows from 16:9, so a unit is as wide as it is tall and a square is
     * square. The shader uses the exact ratio; this rounded value is what pages count in.
     */
    const val CANVAS_WIDTH = 1820
    const val CANVAS_HEIGHT = 1024

    /** 16:9 against the canvas height, to the precision the shader needs. */
    private const val CANVAS_WIDTH_EXACT = "1820.444"

    /** Vertical position bits: one step per canvas unit. */
    const val Y_BITS = 10

    /** Colour bits: red 3, green 4, blue 3. */
    const val COLOUR_BITS = 10

    private const val Y_MAX = (1 shl Y_BITS) - 1

    /**
     * How far below the top of the screen the first boss bar's text line puts a glyph with
     * ascent 0, in GUI units. Measured on a live 26.2 client (a rectangle meant for y=100
     * landed ~10 units low). Holds while our bar is the first one on screen.
     */
    const val LINE_TOP = 10

    private val COMMON = """
        // Our glyph: the high nibble of red is the marker. The remaining 20 bits are
        // y (10) followed by the fill colour (10, RGB 3-4-3).
        // (Nothing here may be named "packed" — a reserved word in GLSL that makes strict
        // drivers reject the whole shader while lenient compilers let it pass.)
        bool voidrp_decode(vec4 color, out float canvasY, out vec3 fill) {
            int red = int(floor(color.r * 255.0 + 0.5));
            if ((red >> 4) != ${MARKER}) {
                return false;
            }
            int bits = ((red & 15) << 16)
                     | (int(floor(color.g * 255.0 + 0.5)) << 8)
                     |  int(floor(color.b * 255.0 + 0.5));
            int qy = (bits >> ${COLOUR_BITS}) & ${Y_MAX};
            int c = bits & ${(1 shl COLOUR_BITS) - 1};
            canvasY = float(qy);
            fill = vec3(float((c >> 7) & 7) / 7.0,
                        float((c >> 3) & 15) / 15.0,
                        float(c & 7) / 7.0);
            return true;
        }

        // Where the client put this vertex -> where the page wants it, in NDC.
        //
        // The line is built with zero total width, so the boss bar's centring leaves its
        // start at the centre of the screen. Reading the pen offset back out of the final
        // NDC (not from Position) makes this independent of whether the client baked that
        // centring into the vertices or into ModelViewMat — on 26.2 it is baked in.
        //
        // Vertically, the boss bar's own text line sits LINE_TOP GUI units below the top
        // of the screen; subtracting it leaves just the glyph's own extent (0 at its top
        // edge, its height at the bottom), which is added to the y carried in the colour.
        vec4 voidrp_place(float canvasY, vec4 original) {
            vec2 ndc = original.xy / original.w;
            float penX = ndc.x / ProjMat[0][0];
            float fromTop = (1.0 - ndc.y) / -ProjMat[1][1];
            vec2 canvas = vec2(penX, canvasY + fromTop - ${LINE_TOP}.0);
            vec2 target = vec2(canvas.x / ${CANVAS_WIDTH_EXACT} * 2.0 - 1.0,
                               1.0 - canvas.y / ${CANVAS_HEIGHT}.0 * 2.0);
            return vec4(target * original.w, original.z, original.w);
        }
    """.trimIndent()

    val TEXT_VSH_MODERN: String get() = MODERN_TEMPLATE

    /** 26.2 and newer: a single `text.vsh` with variants behind #define. */
    private val MODERN_TEMPLATE = """
        #version 330

        #if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
        #moj_import <minecraft:fog.glsl>
        #moj_import <minecraft:sample_lightmap.glsl>
        #endif

        #moj_import <minecraft:dynamictransforms.glsl>
        #moj_import <minecraft:projection.glsl>

        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        #if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
        in ivec2 UV2;
        #endif

        #if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
        uniform sampler2D Sampler2;
        out float sphericalVertexDistance;
        out float cylindricalVertexDistance;
        #endif

        out vec4 vertexColor;
        out vec2 texCoord0;

        //__VOIDRP_COMMON__

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            vec4 tint = Color;

            float canvasY;
            vec3 fill;
            if (voidrp_decode(Color, canvasY, fill)) {
                gl_Position = voidrp_place(canvasY, gl_Position);
                tint = vec4(fill, 1.0);
            }

        #if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
            sphericalVertexDistance = fog_spherical_distance(Position);
            cylindricalVertexDistance = fog_cylindrical_distance(Position);
            vertexColor = tint * sample_lightmap(Sampler2, UV2);
        #else
            vertexColor = tint;
        #endif
            texCoord0 = UV0;
        }
    """.trimIndent().replace("//__VOIDRP_COMMON__", COMMON)

    val TEXT_VSH_LEGACY: String get() = LEGACY_TEMPLATE

    /** 1.21.6 … 26.1.2: the older `rendertype_text.vsh`, GLSL 150. */
    private val LEGACY_TEMPLATE = """
        #version 150

        #moj_import <minecraft:fog.glsl>
        #moj_import <minecraft:dynamictransforms.glsl>
        #moj_import <minecraft:projection.glsl>

        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        in ivec2 UV2;

        uniform sampler2D Sampler2;

        out float sphericalVertexDistance;
        out float cylindricalVertexDistance;
        out vec4 vertexColor;
        out vec2 texCoord0;

        //__VOIDRP_COMMON__

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            vec4 tint = Color;

            float canvasY;
            vec3 fill;
            if (voidrp_decode(Color, canvasY, fill)) {
                gl_Position = voidrp_place(canvasY, gl_Position);
                tint = vec4(fill, 1.0);
            }

            sphericalVertexDistance = fog_spherical_distance(Position);
            cylindricalVertexDistance = fog_cylindrical_distance(Position);
            vertexColor = tint * texelFetch(Sampler2, UV2 / 16, 0);
            texCoord0 = UV0;
        }
    """.trimIndent().replace("//__VOIDRP_COMMON__", COMMON)
}
