package ru.voidrp.ui.style

import ru.voidrp.ui.pack.TextFonts

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

    /** Letter-spacing for the small uppercase labels the site puts above everything. */
    const val TRACKING = 2

    // Type scale, in canvas units — the same numbers the site's stylesheet uses
    const val TEXT_CAPTION = 12
    const val TEXT_BODY = 14
    const val TEXT_LEAD = 16
    const val TEXT_H3 = 20
    const val TEXT_H2 = 28
    const val TEXT_H1 = 40

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

    /**
     * The wash the site puts behind anything it wants looked at.
     *
     * Kept for small things. A gradient is made of stripes of the nearest colour the ten
     * bits can express, and over a large, nearly black surface the nearest colours are far
     * enough apart that the stripes show. Big surfaces get a flat fill instead.
     */
    val accentWash = Gradient(Paint(VIOLET, 0.26), Paint(VIOLET, 0.12))

    /**
     * The accent itself: a filled bar or a primary button.
     *
     * Flat, on purpose. The site has a violet-to-fuchsia ramp here, and it is the one thing
     * that does not survive the trip — ten bits of colour leave three or four steps between
     * those two, and on a bar twelve pixels tall the stripes read as a comb. A flat accent
     * looks deliberate; a bad gradient looks broken.
     */
    val accentBar = Paint(VIOLET, 0.95)

    /** The main surface of a page: a large, nearly opaque sheet, lit from the top. */
    val page = Style(
        background = Paint(0x0B1224, 0.93),
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
        textSize = TEXT_BODY,
        textWeight = TextFonts.Weight.SEMIBOLD,
    )

    val buttonGhost = Style(
        background = Paint(LINE, 0.1),
        border = Border(1, Paint(LINE, 0.22)),
        radius = R_SM,
        padding = Insets.symmetric(SPACE_2, SPACE_4),
        textColour = INK_SOFT,
        textSize = TEXT_BODY,
    )

    /** A pill: the little tag the site marks a version or a mode with. */
    val chip = Style(
        background = Paint(LINE, 0.1),
        border = Border(1, Paint(LINE, 0.16)),
        radius = 8,
        padding = Insets.symmetric(4, SPACE_2),
        textColour = INK_SOFT,
        textSize = TEXT_CAPTION,
        textWeight = TextFonts.Weight.SEMIBOLD,
    )

    /** The same, in the accent colour, for the one thing that matters on a card. */
    val chipAccent = chip.copy(
        background = Paint(VIOLET, 0.22),
        border = Border(1, Paint(VIOLET, 0.45)),
        textColour = INK,
    )

    /**
     * A card that is chosen, or is the current one: a violet edge and a violet tint.
     *
     * No glow. A shadow here is a couple of larger copies underneath, which at this size
     * reads as a second, blurry border rather than light — the effect a stylesheet gets
     * from a blur radius is one of the few things that does not survive.
     */
    val cardSelected = card.copy(
        background = Paint(VIOLET, 0.12),
        border = Border(1, Paint(VIOLET, 0.55)),
    )

    /** A hairline separator: a one-pixel box with nothing but a background. */
    val divider = Style(background = Paint(LINE, 0.18))
}
