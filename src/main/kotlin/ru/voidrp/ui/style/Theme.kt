package ru.voidrp.ui.style

/**
 * The design tokens of the VoidRP look, so an in-game page and the website are recognisably
 * the same product. The values mirror the site's own stylesheet (`gui-premium.css`): a very
 * dark blue-black ground, cool off-white text, violet as the one accent that carries
 * emphasis, and a radius scale that stays small enough to read at Minecraft's GUI scale.
 *
 * A server that wants its own look overrides these, and every page follows — which is the
 * point of having tokens rather than colours scattered through the pages.
 */
object Theme {

    // Ground and ink
    const val BG = 0x05060D
    const val SURFACE = 0x0F1526
    const val INK = 0xEAF0FF
    const val INK_SOFT = 0xAEB9D6
    const val INK_DIM = 0x6B779A

    /** The cool grey every hairline and separator is tinted with. */
    const val LINE = 0x96A8DC

    // Accents
    const val VIOLET = 0x8B7BFF
    const val VIOLET_SOFT = 0xA78BFA
    const val FUCHSIA = 0xD946EF
    const val GREEN = 0x34D399
    const val GOLD = 0xFBBF24
    const val RED = 0xFB7185

    // Radius scale
    const val R_SM = 8
    const val R_MD = 12
    const val R_LG = 16
    const val R_XL = 20

    // Spacing scale, in canvas pixels
    const val SPACE_1 = 4
    const val SPACE_2 = 8
    const val SPACE_3 = 12
    const val SPACE_4 = 16
    const val SPACE_5 = 20
    const val SPACE_6 = 24
    const val SPACE_8 = 32

    /**
     * The dimmed backdrop a full-screen page sits on.
     *
     * Surfaces are built the way the site builds them — a pale tint at low opacity over a
     * dark ground, not a dark colour of their own. That is also what survives the trip:
     * a colour travels in ten bits, so near-black tones all collapse into the same black,
     * while opacity is exact.
     */
    val scrim = Style(background = Paint(0x000000, 0.55))

    /** The main surface of a page: a large, nearly opaque sheet. */
    val page = Style(
        background = Paint(0x0B1224, 0.92),
        border = Border(1, Paint(LINE, 0.25)),
        radius = R_XL,
        padding = Insets.all(SPACE_6),
        shadow = Shadow(offsetY = 6, spread = 4, paint = Paint(0x000000, 0.2)),
    )

    /** A panel inside a page — what the site calls a card. */
    val card = Style(
        background = Paint(LINE, 0.07),
        border = Border(1, Paint(LINE, 0.14)),
        radius = R_MD,
        padding = Insets.all(SPACE_4),
        textColour = INK,
    )

    /** A card that is the one thing on the screen worth looking at. */
    val cardAccent = card.copy(
        background = Paint(VIOLET, 0.18),
        border = Border(1, Paint(VIOLET, 0.45)),
    )

    val buttonPrimary = Style(
        background = Paint(VIOLET, 0.9),
        radius = R_SM,
        padding = Insets.symmetric(SPACE_2, SPACE_4),
        textColour = 0x0B0A1F,
        textSize = 2,
    )

    val buttonGhost = Style(
        background = Paint(LINE, 0.1),
        border = Border(1, Paint(LINE, 0.22)),
        radius = R_SM,
        padding = Insets.symmetric(SPACE_2, SPACE_4),
        textColour = INK_SOFT,
        textSize = 2,
    )

    /** A hairline separator: a one-pixel box with nothing but a background. */
    val divider = Style(background = Paint(LINE, 0.18))
}
