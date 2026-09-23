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
     * The same, for a glyph that drifts.
     *
     * A page is sent once and then sits still, which is right for a page and wrong for the
     * specks of light behind it: a background that moves has to move at the client's frame
     * rate, not at the server's. So a drifting glyph carries its own marker, and the shader
     * works out where it is from the time of day rather than from anything we send.
     *
     * 0xC for the same reason 0xB was chosen: the sixteen named colours have red 00, 55, AA
     * or FF, so none of them lands on it.
     */
    const val MARKER_DRIFT = 0xC

    /**
     * Whether the pack carries the drifting branch at all.
     *
     * It costs one import — the client's own globals, where the time of day lives — and a
     * server that would rather not have its text shader reach for anything extra can build
     * the pack without it. The encoder then sends the specks as ordinary static shapes.
     */
    var particles = false

    /**
     * How tall the canvas is: the height of the player's window, always.
     *
     * It is 1024 on purpose: the vertical position travels in 10 bits, and 1024 steps over
     * 1024 units is exactly one unit per step. An earlier 1080-tall canvas put the steps
     * 1.0557 units apart, so two pieces of the same panel could land a fraction of a pixel
     * apart — invisible while everything was opaque, and a bright seam or a hairline gap
     * as soon as anything became translucent.
     *
     * The width is not fixed: a unit is square, so how many of them fit across is the
     * shape of the player's window. See `Viewport`.
     */
    const val CANVAS_HEIGHT = 1024

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
        bool voidrp_decode(vec4 color, out float canvasY, out vec3 fill, out bool drifts) {
            int red = int(floor(color.r * 255.0 + 0.5));
            int mark = red >> 4;
            drifts = mark == ${MARKER_DRIFT};
            if (mark != ${MARKER} && !drifts) {
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
        //
        // The glyph's own extent arrives in GUI pixels and is read as canvas units, which
        // is what makes a page the same size at every GUI scale: a 64-unit tile is a
        // 64-pixel glyph, and 64 units is 1/16 of the window's height either way.
        // Where a speck of light is at this moment.
        //
        // Nothing about it is sent: its own place on the line is its seed, so every speck
        // drifts at its own pace and sways by its own amount, and the whole field is one
        // static page that never needs sending again.
        vec2 voidrp_drift(vec2 canvas, float seed, float time) {
            float pace = 0.35 + fract(seed * 7.13) * 0.9;
            float sway = 4.0 + fract(seed * 3.71) * 10.0;
            float y = mod(canvas.y + time * pace + seed * ${CANVAS_HEIGHT}.0, ${CANVAS_HEIGHT}.0);
            float x = canvas.x + sin(time * 0.012 + seed * 6.2831) * sway;
            return vec2(x, y);
        }

        vec4 voidrp_place(float canvasY, vec4 original, bool drifts) {
            vec2 ndc = original.xy / original.w;
            float penX = ndc.x / ProjMat[0][0];
            float fromTop = (1.0 - ndc.y) / -ProjMat[1][1];
            vec2 canvas = vec2(penX, canvasY + fromTop - ${LINE_TOP}.0);
        #ifdef VOIDRP_PARTICLES
            if (drifts) {
                // Ticks since the world began, near enough: GameTime runs 0…1 over twenty
                // minutes, which is all a drift needs.
                float time = GameTime * 24000.0;
                canvas = voidrp_drift(canvas, fract(sin(canvas.x * 12.9898) * 43758.5453), time);
            }
        #endif

            // A unit is square, and stays square.
            //
            // 1024 units is the height of the window, whatever the window is; the same
            // scale is used across, so how many units fit from edge to edge is simply the
            // shape of the screen. Nothing is stretched to make a page reach the sides —
            // instead the page is laid out knowing how wide the screen is, the way a web
            // page is laid out to the width of the browser.
            //
            // x arrives measured from the middle of the page, which is exactly where the
            // boss bar's centring leaves the pen, so no width has to be baked in here: the
            // same shader draws a page laid out for any screen.
            float aspect = (2.0 / ProjMat[0][0]) / (-2.0 / ProjMat[1][1]);
            vec2 target = vec2(canvas.x * 2.0 / (${CANVAS_HEIGHT}.0 * aspect),
                               1.0 - canvas.y * 2.0 / ${CANVAS_HEIGHT}.0);
            return vec4(target * original.w, original.z, original.w);
        }
    """.trimIndent()

    /**
     * The client's own fragment shader, with one line changed.
     *
     * It throws away every fragment fainter than a tenth. For text that is a tidy way to
     * skip the empty corners of a glyph; for an interface built out of glyphs it means a
     * surface laid on at a sixteenth of opacity is not dimmed but **discarded**, a rounded
     * corner loses the softness of its edge, and a shadow stops dead where its falloff
     * drops below the line. The game did all three, while every preview here showed the
     * page as it was meant to look.
     *
     * So the glyphs the vertex shader recognised are let through unless they are empty
     * altogether. The client's own text keeps the threshold it came with — that threshold
     * is also what stops the transparent corners of a letter writing depth.
     *
     * Patched by replacing a line rather than rewritten, so if a version changes the
     * shader underneath us the build fails loudly instead of shipping a broken pack.
     */
    val TEXT_FSH_MODERN: String
        get() {
            val vanilla = String(Shaders::class.java.getResourceAsStream("/vanilla/text.fsh")!!.readBytes())
            val declared = vanilla.replace(
                "in vec2 texCoord0;",
                "in vec2 texCoord0;\nin float voidrpShape;",
            )
            check(declared != vanilla) { "No texCoord0 in the vanilla fragment shader" }
            val patched = declared.replace(
                "if (color.a < 0.1) {",
                "if (color.a < (voidrpShape > 0.5 ? 0.004 : 0.1)) {",
            )
            check(patched != declared) { "No alpha cutoff in the vanilla fragment shader" }
            return patched
        }

    private fun withParticles(source: String): String =
        if (!particles) source
        else source.replaceFirst("#version 330", "#version 330\n#define VOIDRP_PARTICLES 1")
            .replaceFirst("#version 150", "#version 150\n#define VOIDRP_PARTICLES 1")

    val TEXT_VSH_MODERN: String get() = withParticles(MODERN_TEMPLATE)

    /** 26.2 and newer: a single `text.vsh` with variants behind #define. */
    private val MODERN_TEMPLATE = """
        #version 330

        #if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
        #moj_import <minecraft:fog.glsl>
        #moj_import <minecraft:sample_lightmap.glsl>
        #endif

        #moj_import <minecraft:dynamictransforms.glsl>
        #moj_import <minecraft:projection.glsl>
        #ifdef VOIDRP_PARTICLES
        #moj_import <minecraft:globals.glsl>
        #endif

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
        // 1 for our glyphs, 0 for the client's own text: the fragment shader throws away
        // anything fainter than a tenth, which is most of what an interface is made of.
        out float voidrpShape;

        //__VOIDRP_COMMON__

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            vec4 tint = Color;

            float canvasY;
            vec3 fill;
            bool drifts;
            voidrpShape = 0.0;
            if (voidrp_decode(Color, canvasY, fill, drifts)) {
                gl_Position = voidrp_place(canvasY, gl_Position, drifts);
                tint = vec4(fill, 1.0);
                voidrpShape = 1.0;
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

    val TEXT_VSH_LEGACY: String get() = withParticles(LEGACY_TEMPLATE)

    /** 1.21.6 … 26.1.2: the older `rendertype_text.vsh`, GLSL 150. */
    private val LEGACY_TEMPLATE = """
        #version 150

        #moj_import <minecraft:fog.glsl>
        #moj_import <minecraft:dynamictransforms.glsl>
        #moj_import <minecraft:projection.glsl>
        #ifdef VOIDRP_PARTICLES
        // 1.21.6 has no uniform blocks here: the time of day is a uniform of its own.
        uniform float GameTime;
        #endif

        in vec3 Position;
        in vec4 Color;
        in vec2 UV0;
        in ivec2 UV2;

        uniform sampler2D Sampler2;

        out float sphericalVertexDistance;
        out float cylindricalVertexDistance;
        out vec4 vertexColor;
        out vec2 texCoord0;
        // 1 for our glyphs, 0 for the client's own text: the fragment shader throws away
        // anything fainter than a tenth, which is most of what an interface is made of.
        out float voidrpShape;

        //__VOIDRP_COMMON__

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            vec4 tint = Color;

            float canvasY;
            vec3 fill;
            bool drifts;
            voidrpShape = 0.0;
            if (voidrp_decode(Color, canvasY, fill, drifts)) {
                gl_Position = voidrp_place(canvasY, gl_Position, drifts);
                tint = vec4(fill, 1.0);
                voidrpShape = 1.0;
            }

            sphericalVertexDistance = fog_spherical_distance(Position);
            cylindricalVertexDistance = fog_cylindrical_distance(Position);
            vertexColor = tint * texelFetch(Sampler2, UV2 / 16, 0);
            texCoord0 = UV0;
        }
    """.trimIndent().replace("//__VOIDRP_COMMON__", COMMON)
}
