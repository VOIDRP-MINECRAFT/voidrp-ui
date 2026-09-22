package ru.voidrp.ui.page

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Grid
import ru.voidrp.ui.layout.Icon
import ru.voidrp.ui.layout.Image
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Raw
import ru.voidrp.ui.layout.RichText
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Span
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.Glyphs
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.render.Painter
import ru.voidrp.ui.render.Rect
import ru.voidrp.ui.style.Border
import ru.voidrp.ui.style.Insets
import ru.voidrp.ui.style.Gradient
import ru.voidrp.ui.style.GradientDirection
import ru.voidrp.ui.style.Palette
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.chip
import ru.voidrp.ui.widget.eyebrow

/**
 * The in-game home screen, rebuilt on a vanilla client.
 *
 * It is the screen the modded servers open with F6 — drawn there by a browser embedded in
 * the game — set here in glyphs instead, element for element: the starfield, the icon
 * rail, the top bar with its balances, the profile card, the welcome panel, the four
 * numbers, the stat tiles, the shortcuts and the achievements.
 *
 * The data is made up. The point of the page is the design.
 */
class HomePage : Page() {

    private val rail = listOf(
        "home", "tech", "treasury", "users", "quest", "trophy",
        "battlepass", "bell", "market", "globe", "user", "settings",
    )

    private val tiles = listOf(
        "tech" to "Технологии",
        "treasury" to "Казна",
        "quest" to "Квесты",
        "alliance" to "Альянс",
        "market" to "Рынок",
        "battlepass" to "Пропуск",
    )

    private val stats = listOf(
        Triple("target", "K/D", "3.86"),
        Triple("flame", "Лучший стрик", "14"),
        Triple("skull", "Мобов убито", "3 820"),
        Triple("quest", "Квестов", "57"),
        Triple("pickaxe", "Блоков добыто", "128 407"),
        Triple("package", "Блоков установлено", "96 233"),
    )

    private val achievements = listOf(
        "Первый алмаз" to true,
        "Сто убийств" to true,
        "Основатель" to true,
        "Ночь в аду" to true,
        "Древний страж" to true,
        "Золотая лихорадка" to true,
        "Сотня квестов" to false,
        "Мастер торговли" to false,
        "Повелитель бездны" to false,
        "Легенда сервера" to false,
    )

    /** Where the stars sit, as fractions of the canvas, so they scatter the same each time. */
    private val stars = listOf(
        0.04 to 0.08, 0.11 to 0.31, 0.07 to 0.62, 0.15 to 0.85, 0.21 to 0.17,
        0.26 to 0.55, 0.33 to 0.09, 0.38 to 0.74, 0.44 to 0.28, 0.49 to 0.91,
        0.55 to 0.13, 0.61 to 0.47, 0.66 to 0.80, 0.72 to 0.22, 0.77 to 0.63,
        0.83 to 0.36, 0.88 to 0.71, 0.93 to 0.12, 0.96 to 0.52, 0.99 to 0.88,
        0.18 to 0.44, 0.29 to 0.68, 0.41 to 0.05, 0.52 to 0.34, 0.69 to 0.95,
        0.86 to 0.26, 0.13 to 0.96, 0.58 to 0.71, 0.75 to 0.48, 0.35 to 0.39,
    )

    private var selected = "home"

    /** How wide the page itself is, whatever the window is. The site holds to the same. */
    private val BODY_WIDTH = 1278

    // The surfaces, as the site's stylesheet has them: a near-black page, cards a shade
    // above it, tiles a shade above those. Naming such colours outright does not survive
    // the trip — the palette's dark end is coarse enough that page and card land on the
    // same entry and the card disappears — so each is expressed as a colour and an opacity
    // over what is behind it, which lands within a unit or two. See [Palette.express].
    private val PAGE = 0x060711
    private val CARD = 0x090B16
    private val KPI = 0x0D0F19
    private val TILE = 0x11131E
    private val WELL = 0x191A28

    /** The well the player stands in, in the profile card. */
    private val WELL_WIDTH = 286
    private val WELL_HEIGHT = 300

    private val pageTint = Palette.express(PAGE, 0x000000)
    private val cardFill = Palette.express(CARD, PAGE)
    private val kpiFill = Palette.express(KPI, PAGE)
    private val tileFill = Palette.express(TILE, CARD)
    private val wellFill = Palette.express(WELL, CARD)

    override fun view(): View = Panel(
        width = Size.Fixed(Shaders.CANVAS_WIDTH),
        height = Size.Fixed(Shaders.CANVAS_HEIGHT),
        // Not a window floating over the world: the screen belongs to the interface, the
        // way it does when a browser is drawing it.
        style = Style(background = Paint(0x000000, 0.97)),
        direction = Direction.ROW,
        // The tint and the stars are placed on the canvas directly and take no room in the
        // row, so the rail and the page lay out as if they were not there.
        children = listOf(Raw(Rect(0, 0, Shaders.CANVAS_WIDTH, Shaders.CANVAS_HEIGHT, pageTint))) +
            starNodes() + listOf(iconRail(), content()),
    )

    /** A sky behind the page. Each star is one unit of nothing much, and they add up. */
    private fun starNodes(): List<View> = (stars + stars.map { (x, y) -> (x + 0.037) % 1.0 to (y + 0.41) % 1.0 })
        .mapIndexed { index, (x, y) ->
        val size = if (index % 5 == 0) 2 else 1
        Raw(
            Rect(
                (x * Shaders.CANVAS_WIDTH).toInt(),
                (y * Shaders.CANVAS_HEIGHT).toInt(),
                size,
                size,
                Paint(if (index % 3 == 0) Theme.VIOLET_SOFT else Theme.INK, if (index % 2 == 0) 0.5 else 0.28),
            )
        )
    }

    /** The rail: icons only, no panel around them, the current one filled violet. */
    private fun iconRail() = Panel(
        width = Size.Fixed(68),
        height = Size.Fill,
        style = Style(padding = Insets.symmetric(Theme.SPACE_4, Theme.SPACE_3)),
        gap = 6,
        align = Align.CENTER,
        children = buildList {
            add(
                Panel(
                    style = Style(background = Paint(Theme.VIOLET, 0.9), radius = 10),
                    width = Size.Fixed(34),
                    height = Size.Fixed(34),
                    justify = Justify.CENTER,
                    align = Align.CENTER,
                    children = listOf(Icon("voidcoin", 16, 0x0B0A1F)),
                )
            )
            add(Panel(height = Size.Fixed(Theme.SPACE_3)))
            rail.forEach { name ->
                val id = "rail:$name"
                val active = selected == name
                add(
                    Panel(
                        style = when {
                            active -> Style(background = Paint(Theme.VIOLET, 0.9), radius = 12)
                            hovered == id -> Style(background = Paint(Theme.LINE, 0.1), radius = 12)
                            else -> Style(radius = 12)
                        },
                        width = Size.Fixed(42),
                        height = Size.Fixed(42),
                        justify = Justify.CENTER,
                        align = Align.CENTER,
                        id = id,
                        children = listOf(Icon(name, 20, if (active) 0x140F2E else Theme.INK_DIM)),
                    )
                )
            }
            add(Panel(height = Size.Fill))
            add(
                Panel(
                    style = Style(radius = 12),
                    width = Size.Fixed(42),
                    height = Size.Fixed(42),
                    justify = Justify.CENTER,
                    align = Align.CENTER,
                    id = "logout",
                    children = listOf(Icon("logout", 20, Theme.INK_DIM)),
                )
            )
        },
    )

    private fun content() = Panel(
        width = Size.Fill,
        height = Size.Fill,
        style = Style(padding = Insets(0, Theme.SPACE_6, Theme.SPACE_5, 0)),
        gap = Theme.SPACE_4,
        // The bar runs the width of the window; the page under it sits in a column of its
        // own, centred, the way the site holds its content to a readable width instead of
        // letting it stretch to whatever the window happens to be.
        align = Align.CENTER,
        children = listOf(
            topBar(),
            Panel(
                direction = Direction.ROW,
                width = Size.Fixed(BODY_WIDTH),
                height = Size.Fill,
                gap = Theme.SPACE_4,
                align = Align.START,
                children = listOf(profile(), rightColumn()),
            ),
        ),
    )

    private fun topBar() = Panel(
        direction = Direction.ROW,
        width = Size.Fill,
        height = Size.Fixed(58),
        align = Align.CENTER,
        gap = Theme.SPACE_3,
        children = listOf(
            RichText(
                spans = listOf(
                    Span("VOID", Theme.INK, TextFonts.Weight.BOLD),
                    Span("RP", Theme.VIOLET_SOFT, TextFonts.Weight.BOLD),
                ),
                size = Theme.TEXT_LEAD,
            ),
            Text("/", Theme.TEXT_LEAD, Theme.INK_DIM, wrap = false),
            eyebrow("Главная"),
            Panel(width = Size.Fill),
            balanceChip("voidcoin", "120", Theme.VIOLET_SOFT),
            balanceChip("coins", "184 200", Theme.GOLD),
            Panel(
                style = Style(
                    background = Paint(Theme.LINE, 0.06),
                    border = Border(1, Paint(Theme.LINE, 0.16)),
                    radius = Theme.R_MD,
                    padding = Insets.symmetric(6, 10),
                ),
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                align = Align.CENTER,
                children = listOf(
                    Panel(
                        style = Style(background = Paint(Theme.VIOLET, 0.25), radius = 6),
                        width = Size.Fixed(24),
                        height = Size.Fixed(24),
                        justify = Justify.CENTER,
                        align = Align.CENTER,
                        children = listOf(Icon("user", 12, Theme.VIOLET_SOFT)),
                    ),
                    eyebrow("lvl 37"),
                ),
            ),
            Panel(
                style = if (hovered == "close") {
                    Style(background = Paint(Theme.RED, 0.2), border = Border(1, Paint(Theme.RED, 0.4)), radius = Theme.R_MD)
                } else {
                    Style(background = Paint(Theme.LINE, 0.06), border = Border(1, Paint(Theme.LINE, 0.16)), radius = Theme.R_MD)
                },
                width = Size.Fixed(36),
                height = Size.Fixed(36),
                justify = Justify.CENTER,
                align = Align.CENTER,
                id = "close",
                children = listOf(Icon("x", 16, Theme.INK_SOFT)),
            ),
        ),
    )

    private fun balanceChip(icon: String, value: String, colour: Int) = Panel(
        style = Style(
            background = Paint(colour, 0.12),
            border = Border(1, Paint(colour, 0.3)),
            radius = Theme.R_MD,
            padding = Insets.symmetric(7, 12),
        ),
        direction = Direction.ROW,
        gap = 6,
        align = Align.CENTER,
        children = listOf(
            Icon(icon, 14, colour),
            Text(value, Theme.TEXT_BODY, colour, TextFonts.Weight.BOLD, wrap = false),
        ),
    )

    /** The player card: the portrait stage, the name, the nation, the pass. */
    private fun profile() = Panel(
        style = panel(),
        width = Size.Fixed(318),
        gap = Theme.SPACE_3,
        align = Align.CENTER,
        children = listOf(
            Panel(
                style = Style(
                    // A pale violet at low opacity, the way a stylesheet would tint a dark
                    // surface. Naming the dark colour outright does not work at this end of
                    // the palette — red and blue have two steps each down here, and #141033
                    // lands on a warm grey, which is how the well used to come out brown.
                    // The site fades this one from the middle out; a fade across something
                    // this small has three opacity steps to work with and shows every one,
                    // so it is left flat.
                    background = wellFill,
                    border = Border(1, Paint(Theme.LINE, 0.1)),
                    radius = Theme.R_LG,
                ),
                width = Size.Fill,
                height = Size.Fixed(WELL_HEIGHT),
                justify = Justify.CENTER,
                align = Align.CENTER,
                children = wellGlow() + listOf(Icon("user", 64, Paint(Theme.VIOLET_SOFT, 0.55).rgb)),
            ),
            Text(
                "mironoouv",
                Theme.TEXT_H2,
                Theme.VIOLET_SOFT,
                TextFonts.Weight.BOLD,
                wrap = false,
                glow = Paint(Theme.VIOLET, 0.35),
            ),
            chip("VLD", Theme.chipAccent),
            Panel(
                direction = Direction.ROW,
                gap = 6,
                align = Align.CENTER,
                children = listOf(
                    Icon("shield", 12, Theme.INK_DIM),
                    Text("Глава · Валдария", Theme.TEXT_BODY, Theme.INK_SOFT, wrap = false),
                ),
            ),
            Panel(
                style = Style(
                    background = Paint(Theme.LINE, 0.05),
                    border = Border(1, Paint(Theme.LINE, 0.1)),
                    radius = Theme.R_MD,
                    padding = Insets.all(Theme.SPACE_3),
                ),
                width = Size.Fill,
                gap = Theme.SPACE_2,
                children = listOf(
                    Panel(
                        direction = Direction.ROW,
                        width = Size.Fill,
                        align = Align.CENTER,
                        gap = 6,
                        children = listOf(
                            Icon("battlepass", 14, Theme.INK_SOFT),
                            Text(
                                "Уровень 37",
                                Theme.TEXT_BODY,
                                Theme.VIOLET_SOFT,
                                TextFonts.Weight.SEMIBOLD,
                                wrap = false,
                            ),
                            Panel(width = Size.Fill),
                            Panel(
                                style = Style(
                                    background = Paint(Theme.GOLD, 0.16),
                                    border = Border(1, Paint(Theme.GOLD, 0.35)),
                                    radius = 7,
                                    padding = Insets.symmetric(3, 8),
                                ),
                                direction = Direction.ROW,
                                gap = 4,
                                align = Align.CENTER,
                                children = listOf(
                                    Icon("crown", 12, Theme.GOLD),
                                    Text("Premium", Theme.TEXT_CAPTION, Theme.GOLD, TextFonts.Weight.SEMIBOLD, wrap = false),
                                ),
                            ),
                        ),
                    ),
                    Panel(
                        // The site's track is all but black, so the filled part carries the
                        // whole bar; a track this visible reads as a second bar.
                        style = Style(background = Paint(0x000000, 0.45), radius = 3),
                        width = Size.Fill,
                        height = Size.Fixed(6),
                        direction = Direction.ROW,
                        children = listOf(
                            Panel(
                                style = Style(background = Paint(Theme.GOLD, 0.95), radius = 3),
                                width = Size.Percent(0.08),
                                height = Size.Fill,
                            )
                        ),
                    ),
                ),
            ),
            Text("с 12 мар. 2026 г.", Theme.TEXT_CAPTION, Theme.INK_DIM, wrap = false),
        ),
    )

    private fun rightColumn() = Panel(
        width = Size.Fill,
        gap = Theme.SPACE_4,
        children = listOf(
            welcome(),
            kpis(),
            Panel(
                direction = Direction.ROW,
                width = Size.Fill,
                gap = Theme.SPACE_4,
                // Both cards end on the same line, as they do on the site — one stopping
                // short of the other is the sort of thing you notice without knowing why.
                align = Align.STRETCH,
                children = listOf(statsPanel(), tilesPanel()),
            ),
            achievementsPanel(),
        ),
    )

    /** The one loud thing on the page, with a few blocks thrown behind the words. */
    private fun welcome() = Panel(
        style = Style(
            // Violet at the words, the page's own dark under the artwork. The fade is in
            // the opacity rather than in the colour: opacity has sixteen steps against the
            // palette's eight of blue, so a wash this wide stays smooth where interpolating
            // the colour would band into stripes.
            // The light sits in the bottom left corner and falls away two ways: across,
            // which the wash does, and upwards, which the shade over it does.
            background = Gradient(
                Paint(0x32295F),
                Paint(0x16112C),
                direction = GradientDirection.HORIZONTAL,
                over = PAGE,
                stop = 0.45,
            ),
            overlay = Gradient(Paint(0x000000, 0.25), Paint(0x000000, 0.0)),
            border = Border(1, Paint(Theme.VIOLET, 0.26)),
            radius = Theme.R_XL,
            highlight = Paint(0xFFFFFF, 0.08),
            glow = Paint(Theme.VIOLET, 0.22),
            shadow = ru.voidrp.ui.style.Shadow(offsetY = 8, paint = Paint(0x000000, 0.4)),
        ),
        width = Size.Fill,
        height = Size.Fixed(198),
        direction = Direction.ROW,
        align = Align.CENTER,
        children = listOf(
            Panel(
                style = Style(padding = Insets.all(Theme.SPACE_5)),
                width = Size.Fill,
                gap = Theme.SPACE_2,
                children = listOf(
                    eyebrow("VoidRP · Главная", Theme.VIOLET_SOFT),
                    RichText(
                        spans = listOf(
                            Span("Добро пожаловать в ", Theme.INK),
                            Span("VOID", Theme.INK, TextFonts.Weight.BOLD),
                            Span("RP", Theme.VIOLET_SOFT, TextFonts.Weight.BOLD),
                        ),
                        size = Theme.TEXT_H2,
                        weight = TextFonts.Weight.BOLD,
                    ),
                    // Held to the width the site holds it to, so it breaks after the same
                    // words rather than running the length of the panel.
                    Panel(
                        width = Size.Fixed(540),
                        children = listOf(
                            Text(
                                "Развивайся, сражайся и стань легендой своей империи. " +
                                    "Лучший Minecraft RPG-сервер с уникальными возможностями.",
                                Theme.TEXT_BODY,
                                Theme.INK_SOFT,
                            ),
                        ),
                    ),
                    Panel(height = Size.Fixed(Theme.SPACE_2)),
                    Panel(
                        style = Style(
                            background = Paint(Theme.VIOLET, 0.92),
                            radius = Theme.R_SM,
                            padding = Insets.symmetric(10, 18),
                        ),
                        direction = Direction.ROW,
                        gap = Theme.SPACE_2,
                        align = Align.CENTER,
                        id = "play",
                        children = listOf(
                            Icon("play", 16, 0x140F2E),
                            Text("Начать игру", Theme.TEXT_BODY, 0x140F2E, TextFonts.Weight.SEMIBOLD, wrap = false),
                        ),
                    ),
                ),
            ),
            // The artwork the WebGUI scatters behind the welcome: a few blocks, no more.
            Panel(
                style = Style(padding = Insets.symmetric(0, Theme.SPACE_6)),
                direction = Direction.ROW,
                gap = Theme.SPACE_3,
                align = Align.CENTER,
                // Items rather than blocks: a block texture drawn flat is a grey square,
                // while an item is a picture of itself.
                // Three of a size in a row reads as buttons. The site puts blocks back there
                // at different sizes, so these differ too — the middle one largest, as the
                // eye expects of a group.
                children = listOf(
                    "diamond" to 52,
                    "nether_star" to 64,
                    "netherite_ingot" to 48,
                ).map { (item, size) ->
                    Panel(
                        style = Style(
                            background = Paint(Theme.VIOLET, 0.16),
                            border = Border(1, Paint(Theme.VIOLET, 0.3)),
                            radius = Theme.R_MD,
                            // The site lights this corner from behind the artwork, which is
                            // why the panel is not darkest where it is furthest from the
                            // words.
                            glow = Paint(Theme.VIOLET, 0.16),
                        ),
                        width = Size.Fixed(size),
                        height = Size.Fixed(size),
                        justify = Justify.CENTER,
                        align = Align.CENTER,
                        children = listOf(Image(item, size * 4 / 7)),
                    )
                },
            ),
        ),
    )

    private fun kpis() = Grid(
        columns = 4,
        gap = Theme.SPACE_3,
        width = Size.Fill,
        children = listOf(
            kpi("wallet", "Баланс", "184 200", Theme.GOLD),
            kpi("swords", "PVP убийств", "1 204", Theme.INK),
            kpi("skull", "Смертей", "312", Theme.INK),
            kpi("clock", "Наиграно", "11д 4ч", Theme.INK),
        ),
    )

    /**
     * The soft violet light the site has behind the player in this well.
     *
     * There is no radial gradient to be had here — a glyph is one flat colour — so it is
     * built the way a photographer would: a few squares of light, each the faintest
     * opacity the alphabet has, laid one inside the other. Four of them stack up to about
     * the brightness the site's centre has, and because each is rounded and each edge is
     * only five units of colour, what the eye gets is a blob rather than a set of boxes.
     */
    private fun wellGlow(): List<View> = buildList {
        // The faintest opacity the alphabet has is a sixteenth, which of the theme's violet
        // is a step you can see. A violet half as bright, at that same sixteenth, is a step
        // you cannot — so the light is built from more layers of less.
        //
        // Red has eight steps to blue's eight and green's sixteen, and none of them sits
        // where this violet wants to be: one is too grey, the next too pink. The layers
        // alternate between them, and what the eye adds up is the colour in between.
        val cool = Paint(0x2E3070, 1.0 / Glyphs.ALPHA_LEVELS)
        val warm = Paint(0x4A3470, 1.0 / Glyphs.ALPHA_LEVELS)
        listOf(272, 248, 224, 200, 176, 152, 128, 104, 82, 60).forEachIndexed { index, size ->
            val light = if (index % 2 == 0) cool else warm
            val shape = mutableListOf<ru.voidrp.ui.render.Node>()
            Painter.rounded(
                (WELL_WIDTH - size) / 2,
                (WELL_HEIGHT - size) / 2,
                size,
                size,
                Glyphs.nearestRadius(24, size / 2),
                light,
                shape,
            )
            shape.forEach { add(Raw(it)) }
        }
    }

    private fun kpi(icon: String, caption: String, value: String, colour: Int) = Panel(
        style = panel(padding = Insets.symmetric(14, 16)).copy(background = kpiFill),
        width = Size.Fill,
        gap = 4,
        children = listOf(
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                align = Align.CENTER,
                children = listOf(
                    Icon(icon, 14, if (colour == Theme.GOLD) Theme.GOLD else Theme.INK_DIM),
                    eyebrow(caption, if (colour == Theme.GOLD) Theme.GOLD else Theme.INK_DIM),
                ),
            ),
            Text(value, Theme.TEXT_H3, colour, TextFonts.Weight.BOLD, wrap = false),
        ),
    )

    /** Statistics as tiles — an icon, a label under it, the number below that. */
    private fun statsPanel() = Panel(
        style = panel(),
        width = Size.Fill,
        gap = Theme.SPACE_3,
        children = listOf(
            panelHead("activity", "Статистика"),
            Grid(
                columns = 3,
                gap = Theme.SPACE_2,
                rowGap = Theme.SPACE_2,
                width = Size.Fill,
                children = stats.map { (icon, label, value) ->
                    Panel(
                        style = Style(
                            background = tileFill,
                            border = Border(1, Paint(Theme.LINE, 0.1)),
                            radius = Theme.R_MD,
                            padding = Insets.symmetric(12, 4),
                        ),
                        width = Size.Fill,
                        gap = 6,
                        justify = Justify.CENTER,
                        align = Align.CENTER,
                        children = listOf(
                            Icon(icon, 16, Theme.VIOLET_SOFT),
                            // «Блоков установлено» is eighteen letters in a tile a hundred
                            // and fifty wide, so it sets small and without the tracking the
                            // other labels carry. The site does the same.
                            eyebrow(label, size = Theme.TEXT_MICRO, tracking = 0),
                            Text(value, Theme.TEXT_LEAD, Theme.INK, TextFonts.Weight.BOLD, wrap = false),
                        ),
                    )
                },
            ),
        ),
    )

    private fun tilesPanel() = Panel(
        style = panel(),
        width = Size.Fixed(420),
        gap = Theme.SPACE_3,
        children = listOf(
            panelHead("grid", "Быстрый доступ"),
            Grid(
                columns = 3,
                gap = Theme.SPACE_2,
                rowGap = Theme.SPACE_2,
                width = Size.Fill,
                children = tiles.map { (icon, label) ->
                    val id = "tile:$icon"
                    Panel(
                        style = if (hovered == id) {
                            Style(
                                background = Paint(Theme.VIOLET, 0.12),
                                border = Border(1, Paint(Theme.VIOLET, 0.4)),
                                radius = Theme.R_MD,
                            )
                        } else {
                            Style(
                                background = tileFill,
                                border = Border(1, Paint(Theme.LINE, 0.1)),
                                radius = Theme.R_MD,
                            )
                        },
                        width = Size.Fill,
                        height = Size.Fixed(78),
                        gap = 7,
                        justify = Justify.CENTER,
                        align = Align.CENTER,
                        id = id,
                        children = listOf(
                            Icon(icon, 20, Theme.VIOLET_SOFT),
                            Text(label, Theme.TEXT_CAPTION, Theme.INK_SOFT, TextFonts.Weight.SEMIBOLD, wrap = false),
                        ),
                    )
                },
            ),
        ),
    )

    private fun achievementsPanel() = Panel(
        style = panel(),
        width = Size.Fill,
        gap = Theme.SPACE_3,
        children = listOf(
            Panel(
                direction = Direction.ROW,
                width = Size.Fill,
                align = Align.CENTER,
                gap = Theme.SPACE_2,
                children = listOf(
                    Icon("trophy", 16, Theme.INK_SOFT),
                    eyebrow("Достижения", Theme.INK),
                    Panel(width = Size.Fill),
                    Text("12/40", Theme.TEXT_CAPTION, Theme.GOLD, TextFonts.Weight.BOLD, wrap = false),
                ),
            ),
            Grid(
                columns = 2,
                gap = Theme.SPACE_2,
                rowGap = Theme.SPACE_2,
                width = Size.Fill,
                children = achievements.map { (name, unlocked) ->
                    Panel(
                        style = Style(
                            background = tileFill,
                            border = Border(1, Paint(if (unlocked) Theme.GOLD else Theme.LINE, if (unlocked) 0.2 else 0.1)),
                            radius = Theme.R_MD,
                            padding = Insets.symmetric(10, 12),
                        ),
                        width = Size.Fill,
                        direction = Direction.ROW,
                        gap = Theme.SPACE_3,
                        align = Align.CENTER,
                        children = listOf(
                            Panel(
                                style = Style(
                                    background = Paint(if (unlocked) Theme.GOLD else Theme.LINE, if (unlocked) 0.16 else 0.06),
                                    radius = 8,
                                ),
                                width = Size.Fixed(28),
                                height = Size.Fixed(28),
                                justify = Justify.CENTER,
                                align = Align.CENTER,
                                children = listOf(Icon("trophy", 14, if (unlocked) Theme.GOLD else Theme.INK_DIM)),
                            ),
                            Text(name, Theme.TEXT_BODY, if (unlocked) Theme.INK else Theme.INK_DIM, wrap = false),
                            Panel(width = Size.Fill),
                            eyebrow(if (unlocked) "получено" else "закрыто", if (unlocked) Theme.GREEN else Theme.INK_DIM),
                        ),
                    )
                },
            ),
        ),
    )

    private fun panelHead(icon: String, title: String) = Panel(
        direction = Direction.ROW,
        width = Size.Fill,
        gap = Theme.SPACE_2,
        align = Align.CENTER,
        children = listOf(
            Panel(
                style = Style(background = Paint(Theme.VIOLET, 0.14), radius = 8),
                width = Size.Fixed(28),
                height = Size.Fixed(28),
                justify = Justify.CENTER,
                align = Align.CENTER,
                children = listOf(Icon(icon, 14, Theme.VIOLET_SOFT)),
            ),
            eyebrow(title, Theme.INK),
        ),
    )

    private fun panel(padding: Insets = Insets.all(Theme.SPACE_4)) = Style(
        background = cardFill,
        border = Border(1, Paint(Theme.LINE, 0.12)),
        radius = Theme.R_XL,
        padding = padding,
        highlight = Paint(0xFFFFFF, 0.05),
        shadow = ru.voidrp.ui.style.Shadow(offsetY = 6, paint = Paint(0x000000, 0.35)),
    )

    override fun onClick(id: String, button: Button) {
        when {
            id == "close" || id == "play" -> {
                close()
                return
            }

            id.startsWith("rail:") -> selected = id.removePrefix("rail:")
        }
        refresh()
    }
}
