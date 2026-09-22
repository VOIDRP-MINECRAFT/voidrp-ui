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
    /** For labels that have to fit a narrow tile, where a caption would be cut short. */
    var TEXT_MICRO = 10
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
    /**
     * What a menu standing over the page is made of.
     *
     * Every other surface here is a tint at some opacity over a surface whose colour is
     * known, which is how a dark interface gets colours the palette cannot name. A menu
     * does not know what it will be standing on — that is the point of it — so this one is
     * opaque: the nearest colour the palette has, at full strength.
     */
    var menu = Style()

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
            TEXT_MICRO = config.getInt("text.micro", TEXT_MICRO)
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

    /**
     * A surface a little above the one under it, the way a stylesheet mixes two colours.
     *
     * Every surface in the theme is [SURFACE] carried some of the way towards [LINE], the
     * cool grey the hairlines are tinted with: a page, a card on it, a tile in the card.
     */
    private fun lift(amount: Double): Int {
        fun channel(shift: Int): Int {
            val from = (SURFACE shr shift) and 0xFF
            val to = (LINE shr shift) and 0xFF
            return Math.round(from + (to - from) * amount).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun rebuild() {
        // Every surface stands on the one below it, so the chain is built from the bottom:
        // black hides the world, a tint over it makes the ground the theme's own colour,
        // and each surface above is expressed against what the one below actually came out
        // as. A colour named outright does not survive the trip through ten bits; a colour
        // and an opacity chosen together land on it.
        val ground = Palette.express(BG, 0x000000)
        scrim = Style(background = Paint(0x000000, 0.93), overlay = ground)

        val onGround = Palette.composite(ground, 0x000000)
        val pageFill = Palette.express(SURFACE, onGround)
        val onPage = Palette.composite(pageFill, onGround)
        val cardFill = Palette.express(lift(0.06), onPage)
        val onCard = Palette.composite(cardFill, onPage)
        val tileFill = Palette.express(lift(0.12), onCard)

        page = Style(
            background = pageFill,
            border = Border(1, Paint(LINE, 0.25)),
            radius = R_XL,
            padding = Insets.all(SPACE_6),
            shadow = Shadow(offsetY = 8, paint = Paint(0x000000, 0.45)),
            highlight = Paint(0xFFFFFF, 0.06),
            textColour = INK,
            textSize = TEXT_BODY,
        )

        card = Style(
            background = cardFill,
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
            // Plain white on the accent. Dark ink on a violet button reads as switched
            // off, and the theme's ink is a blue-white that shows against a bright violet.
            textColour = 0xFFFFFF,
            textSize = TEXT_BODY,
            textWeight = TextFonts.Weight.SEMIBOLD,
        )

        buttonGhost = Style(
            background = tileFill,
            border = Border(1, Paint(LINE, 0.22)),
            radius = R_SM,
            padding = Insets.symmetric(SPACE_2, SPACE_4),
            textColour = INK_SOFT,
            textSize = TEXT_BODY,
        )

        menu = Style(
            background = Paint(Palette.nearest(lift(0.10)), 1.0),
            border = Border(1, Paint(LINE, 0.22)),
            radius = R_MD,
            padding = Insets.all(4),
            shadow = Shadow(offsetY = 6, paint = Paint(0x000000, 0.5)),
            textColour = INK,
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
