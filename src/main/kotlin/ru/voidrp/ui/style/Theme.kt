package ru.voidrp.ui.style

import org.bukkit.configuration.ConfigurationSection
import ru.voidrp.ui.pack.TextFonts

/**
 * The design tokens of the VoidRP look, so an in-game page and the website are recognisably
 * the same product: a very dark blue-black ground, cool off-white text, violet as the one
 * accent that carries emphasis, and a radius scale small enough to read at Minecraft's GUI
 * scale. The values mirror the site's own stylesheet.
 *
 * Every one of them can be overridden in `theme.yml`, because a plugin meant for other
 * people's servers cannot insist on someone else's brand colours. Change a colour and the
 * styles built from it change with it — [reload] rebuilds them.
 */
object Theme {

    // Ground and ink
    var BG = 0x05060D
    var SURFACE = 0x0F1526
    var INK = 0xEAF0FF
    var INK_SOFT = 0xAEB9D6
    var INK_DIM = 0x6B779A

    /** The cool grey every hairline and separator is tinted with. */
    var LINE = 0x96A8DC

    // Accents
    var VIOLET = 0x8B7BFF
    var VIOLET_SOFT = 0xA78BFA
    var FUCHSIA = 0xD946EF
    var GREEN = 0x34D399
    var GOLD = 0xFBBF24
    var RED = 0xFB7185

    /** Letter-spacing for the small uppercase labels the site puts above everything. */
    var TRACKING = 2

    // Type scale, in canvas units — the same numbers the site's stylesheet uses
    var TEXT_CAPTION = 12
    var TEXT_BODY = 14
    var TEXT_LEAD = 16
    var TEXT_H3 = 20
    var TEXT_H2 = 28
    var TEXT_H1 = 40

    // Radius scale
    var R_SM = 8
    var R_MD = 12
    var R_LG = 16
    var R_XL = 20

    // Spacing scale, in canvas units
    var SPACE_1 = 4
    var SPACE_2 = 8
    var SPACE_3 = 12
    var SPACE_4 = 16
    var SPACE_5 = 20
    var SPACE_6 = 24
    var SPACE_8 = 32

    /** The dimmed backdrop a full-screen page sits on. */
    var scrim = Style()

    /** The main surface of a page: a large, nearly opaque sheet. */
    var page = Style()

    /**
     * A panel inside a page — what the site calls a card.
     *
     * Surfaces are built the way the site builds them: a pale tint at low opacity over a
     * dark ground, not a dark colour of their own. That is also what survives the trip —
     * a colour travels in ten bits, so near-black tones all collapse into the same black,
     * while opacity is exact.
     */
    var card = Style()

    /** A card that is the one thing on the screen worth looking at. */
    var cardAccent = Style()

    /** A card that is chosen, or is the current one: a violet edge, tint and halo. */
    var cardSelected = Style()

    var buttonPrimary = Style()
    var buttonGhost = Style()

    /** A pill: the little tag the site marks a version or a mode with. */
    var chip = Style()

    /** The same, in the accent colour, for the one thing that matters on a card. */
    var chipAccent = Style()

    /** A hairline separator: a one-pixel box with nothing but a background. */
    var divider = Style()

    /** The wash the site puts behind anything it wants looked at. Small things only. */
    var accentWash = Gradient(Paint(VIOLET, 0.26), Paint(VIOLET, 0.12))

    /**
     * The accent itself: a filled bar or a primary button.
     *
     * Flat, on purpose. The site has a violet-to-fuchsia ramp here, and it is the one thing
     * that does not survive the trip — ten bits of colour leave three or four steps between
     * those two, and on a bar twelve pixels tall the stripes read as a comb.
     */
    var accentBar: Fill = Paint(VIOLET, 0.95)

    init {
        rebuild()
    }

    /** Reads whatever `theme.yml` overrides and builds the styles from it. */
    fun reload(section: ConfigurationSection?) {
        section?.let { config ->
            BG = colour(config, "bg", BG)
            SURFACE = colour(config, "surface", SURFACE)
            INK = colour(config, "ink", INK)
            INK_SOFT = colour(config, "ink-soft", INK_SOFT)
            INK_DIM = colour(config, "ink-dim", INK_DIM)
            LINE = colour(config, "line", LINE)
            VIOLET = colour(config, "accent", VIOLET)
            VIOLET_SOFT = colour(config, "accent-soft", VIOLET_SOFT)
            FUCHSIA = colour(config, "accent-second", FUCHSIA)
            GREEN = colour(config, "green", GREEN)
            GOLD = colour(config, "gold", GOLD)
            RED = colour(config, "red", RED)

            TRACKING = config.getInt("tracking", TRACKING)
            TEXT_CAPTION = config.getInt("text.caption", TEXT_CAPTION)
            TEXT_BODY = config.getInt("text.body", TEXT_BODY)
            TEXT_LEAD = config.getInt("text.lead", TEXT_LEAD)
            TEXT_H3 = config.getInt("text.h3", TEXT_H3)
            TEXT_H2 = config.getInt("text.h2", TEXT_H2)
            TEXT_H1 = config.getInt("text.h1", TEXT_H1)

            R_SM = config.getInt("radius.sm", R_SM)
            R_MD = config.getInt("radius.md", R_MD)
            R_LG = config.getInt("radius.lg", R_LG)
            R_XL = config.getInt("radius.xl", R_XL)
        }
        rebuild()
    }

    private fun rebuild() {
        scrim = Style(background = Paint(BG, 0.93))

        page = Style(
            background = Paint(0x0B1224, 0.93),
            border = Border(1, Paint(LINE, 0.25)),
            radius = R_XL,
            padding = Insets.all(SPACE_6),
            shadow = Shadow(offsetY = 8, paint = Paint(0x000000, 0.45)),
            highlight = Paint(0xFFFFFF, 0.06),
            textColour = INK,
            textSize = TEXT_BODY,
        )

        card = Style(
            background = Paint(LINE, 0.07),
            border = Border(1, Paint(LINE, 0.14)),
            radius = R_MD,
            padding = Insets.all(SPACE_4),
            highlight = Paint(0xFFFFFF, 0.05),
            textColour = INK,
            textSize = TEXT_BODY,
        )

        cardAccent = card.copy(
            background = Paint(VIOLET, 0.18),
            border = Border(1, Paint(VIOLET, 0.45)),
        )

        cardSelected = card.copy(
            background = Paint(VIOLET, 0.12),
            border = Border(1, Paint(VIOLET, 0.55)),
            glow = Paint(VIOLET, 0.3),
        )

        buttonPrimary = Style(
            background = Paint(VIOLET, 0.9),
            glow = Paint(VIOLET, 0.28),
            highlight = Paint(0xFFFFFF, 0.18),
            radius = R_SM,
            padding = Insets.symmetric(SPACE_2, SPACE_4),
            textColour = 0x0B0A1F,
            textSize = TEXT_BODY,
            textWeight = TextFonts.Weight.SEMIBOLD,
        )

        buttonGhost = Style(
            background = Paint(LINE, 0.1),
            border = Border(1, Paint(LINE, 0.22)),
            radius = R_SM,
            padding = Insets.symmetric(SPACE_2, SPACE_4),
            textColour = INK_SOFT,
            textSize = TEXT_BODY,
        )

        chip = Style(
            background = Paint(LINE, 0.1),
            border = Border(1, Paint(LINE, 0.16)),
            radius = R_SM,
            padding = Insets.symmetric(4, SPACE_2),
            textColour = INK_SOFT,
            textSize = TEXT_CAPTION,
            textWeight = TextFonts.Weight.SEMIBOLD,
        )

        chipAccent = chip.copy(
            background = Paint(VIOLET, 0.22),
            border = Border(1, Paint(VIOLET, 0.45)),
            textColour = INK,
        )

        divider = Style(background = Paint(LINE, 0.18))
        accentWash = Gradient(Paint(VIOLET, 0.26), Paint(VIOLET, 0.12))
        accentBar = Paint(VIOLET, 0.95)
    }

    /** `#8b7bff`, `8b7bff` or a plain number — whichever the server owner typed. */
    private fun colour(config: ConfigurationSection, key: String, fallback: Int): Int {
        val raw = config.getString(key)?.trim()?.removePrefix("#") ?: return fallback
        return raw.toIntOrNull(16) ?: fallback
    }
}
