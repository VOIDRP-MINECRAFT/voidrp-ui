package ru.voidrp.ui.pack

/**
 * The patched text shaders that make a vanilla client draw our interface.
 *
 * Minecraft draws chat, boss bars and the rest of the GUI through one vertex shader for
 * text. We replace it with a version that recognises our own glyphs and puts them where
 * the server says, so a page travels as ordinary text the client already knows how to
 * render. Every other glyph is left exactly as vanilla drew it.
 *
 * ### What a vertex shader can and cannot know
 *
 * It sees one vertex at a time and cannot look at its neighbours, so everything about a
 * glyph must travel in that vertex's own attributes. Two are usable:
 *
 *  - **Colour** — 24 bits. Red is the marker, green and blue the position (8 bits each,
 *    ~7.5 × 4.2 canvas pixels per step; finer placement is a later refinement).
 *  - **gl_VertexID** — every glyph is four vertices in a fixed order, so the index
 *    modulo 4 tells each vertex *which corner of the quad it is*. That is what lets the
 *    shader rebuild the quad at an arbitrary size: corner × size + position. (UV0 looks
 *    like it would do, but glyphs live in a shared font atlas — verified on a real
 *    client: using UV shrank the quad to a dot.)
 *
 * Note it cannot be done with the vertex's own position: the four corners arrive at
 * different places and nothing in them says where the glyph started, so a shift computed
 * per vertex tears the quad apart.
 *
 * ### The canvas
 *
 * Pages are authored against a fixed canvas stretched over the whole window. The window
 * size in GUI units is never sent to the shader, but the projection matrix encodes it:
 * an orthographic GUI projection maps x from 0..width onto -1..1, so width is
 * 2/ProjMat[0][0]. That holds at any resolution and any GUI scale.
 */
object Shaders {

    /**
     * Marks a glyph as ours: the whole red channel equals this value.
     *
     * Chosen so no standard Minecraft colour can hit it. The first version used the top
     * four bits (0xA) and turned out to match GRAY (#AAAAAA) — every grey word on screen
     * was mistaken for an element and flung across the canvas. 0xB5 (181) is not the red
     * of any of the 16 named colours (00, 55, AA, FF), so only a hand-picked RGB colour
     * with exactly this red can collide.
     */
    const val MARKER = 0xB5

    const val CANVAS_WIDTH = 1920
    const val CANVAS_HEIGHT = 1080

    /** Bits per axis: green carries x, blue carries y, 8 bits each. */
    const val POSITION_BITS = 8

    /** Element size in canvas pixels for this first cut — one fixed size until the ladder lands. */
    const val FIXED_SIZE = 64

    private const val POSITION_STEPS = 1 shl POSITION_BITS

    /** Shared between both shader layouts: decode, and map canvas pixels to GUI units. */
    private val COMMON = """
        // Unpacks the marker and position from the vertex colour.
        // Returns false for ordinary text, which is then drawn untouched.
        // (Not named "packed": that is a reserved word in GLSL, and strict drivers
        // reject the whole shader over it while lenient compilers let it pass.)
        bool voidrp_decode(vec4 color, out vec2 canvasPos) {
            int red = int(floor(color.r * 255.0 + 0.5));
            if (red != ${MARKER}) {
                return false;
            }
            float qx = floor(color.g * 255.0 + 0.5);
            float qy = floor(color.b * 255.0 + 0.5);
            canvasPos = vec2(
                qx * ${CANVAS_WIDTH}.0 / ${POSITION_STEPS - 1}.0,
                qy * ${CANVAS_HEIGHT}.0 / ${POSITION_STEPS - 1}.0
            );
            return true;
        }

        // Canvas pixels straight to normalised device coordinates. Deliberately bypasses
        // ProjMat and ModelViewMat: the boss bar (and every other GUI element) carries its
        // own translation in ModelViewMat, so going through the matrices would drag the
        // glyph along with whatever it happens to be attached to. NDC is the whole window,
        // at any resolution and any GUI scale.
        vec2 voidrp_ndc(vec2 canvasPos) {
            return vec2(canvasPos.x / ${CANVAS_WIDTH}.0 * 2.0 - 1.0,
                        1.0 - canvasPos.y / ${CANVAS_HEIGHT}.0 * 2.0);
        }

        // Which corner of its quad this vertex is. Every glyph is exactly four vertices,
        // emitted top-left, bottom-left, bottom-right, top-right, so the vertex index
        // says it. (UV0 cannot: glyphs are packed into a shared font atlas, so their
        // texture coordinates are a tiny slice of it, not 0..1.)
        vec2 voidrp_corner() {
            int i = gl_VertexID % 4;
            if (i == 0) return vec2(0.0, 0.0);
            if (i == 1) return vec2(0.0, 1.0);
            if (i == 2) return vec2(1.0, 1.0);
            return vec2(1.0, 0.0);
        }

        // Rebuilds the quad from the corner. Keeps the original depth
        // so the glyph still layers like the text it came from.
        vec4 voidrp_place(vec2 canvasPos, vec2 corner, vec4 original) {
            vec2 origin = voidrp_ndc(canvasPos);
            vec2 size = vec2(${FIXED_SIZE}.0 / ${CANVAS_WIDTH}.0 * 2.0,
                             -${FIXED_SIZE}.0 / ${CANVAS_HEIGHT}.0 * 2.0);
            vec2 ndc = origin + corner * size;
            return vec4(ndc * original.w, original.z, original.w);
        }
    """.trimIndent()

    /** 26.2 and newer: a single `text.vsh` with variants behind #define. */
    val TEXT_VSH_MODERN: String get() = MODERN_TEMPLATE

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
            vec3 pos = Position;
            vec4 tint = Color;

            gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

            vec2 canvasPos;
            if (voidrp_decode(Color, canvasPos)) {
                gl_Position = voidrp_place(canvasPos, voidrp_corner(), gl_Position);
                tint = vec4(1.0, 1.0, 1.0, 1.0);
            }

        #if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
            sphericalVertexDistance = fog_spherical_distance(pos);
            cylindricalVertexDistance = fog_cylindrical_distance(pos);
            vertexColor = tint * sample_lightmap(Sampler2, UV2);
        #else
            vertexColor = tint;
        #endif
            texCoord0 = UV0;
        }
    """.trimIndent().replace("//__VOIDRP_COMMON__", COMMON)

    /** 1.21.6 … 26.1.2: the older `rendertype_text.vsh`, GLSL 150, one file per variant. */
    val TEXT_VSH_LEGACY: String get() = LEGACY_TEMPLATE

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
            vec3 pos = Position;
            vec4 tint = Color;

            gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

            vec2 canvasPos;
            if (voidrp_decode(Color, canvasPos)) {
                gl_Position = voidrp_place(canvasPos, voidrp_corner(), gl_Position);
                tint = vec4(1.0, 1.0, 1.0, 1.0);
            }

            sphericalVertexDistance = fog_spherical_distance(pos);
            cylindricalVertexDistance = fog_cylindrical_distance(pos);
            vertexColor = tint * texelFetch(Sampler2, UV2 / 16, 0);
            texCoord0 = UV0;
        }
    """.trimIndent().replace("//__VOIDRP_COMMON__", COMMON)
}
