package ru.voidrp.ui.page

import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Grid
import ru.voidrp.ui.layout.Icon
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.RichText
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Span
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.Shaders
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.style.Border
import ru.voidrp.ui.style.Insets
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.button
import ru.voidrp.ui.widget.chip
import ru.voidrp.ui.widget.eyebrow

/**
 * The in-game home screen, rebuilt on a vanilla client.
 *
 * It is the same screen the modded servers open with F6 — the one drawn by a browser
 * embedded in the game — set here in glyphs instead: the icon rail, the profile card, the
 * welcome panel, the four numbers, the stat grid, the shortcuts and the leaderboard.
 * Nothing is installed on the client; the data here is made up, because the point of this
 * page is the design.
 */
class HomePage : Page() {

    private val rail = listOf(
        "home" to "Главная",
        "tech" to "Технологии",
        "treasury" to "Казна",
        "users" to "Государство",
        "quest" to "Квесты",
        "trophy" to "Рейтинги",
        "battlepass" to "Пропуск",
        "bell" to "Уведомления",
        "market" to "Рынок",
        "globe" to "Карта",
        "user" to "Профиль",
        "settings" to "Настройки",
    )

    private val tiles = listOf(
        "market" to "Рынок",
        "quest" to "Квесты",
        "battlepass" to "Пропуск",
        "treasury" to "Казна",
        "tech" to "Технологии",
        "trophy" to "Рейтинги",
    )

    private val stats = listOf(
        Triple("target", "K/D", "2.41"),
        Triple("flame", "Серия", "14"),
        Triple("skull", "Мобов", "3 820"),
        Triple("quest", "Квестов", "57"),
        Triple("pickaxe", "Сломано", "128 407"),
        Triple("package", "Поставлено", "96 233"),
    )

    private val nations = listOf(
        Triple(1, "VLD" to "Валдария", "412 900"),
        Triple(2, "NRD" to "Нордхейм", "355 120"),
        Triple(3, "ARC" to "Аркадия", "298 640"),
    )

    /** Minutes played each day, as a share of the best day. */
    private val minutes = listOf(0.35, 0.52, 0.28, 0.74, 0.61, 0.88, 1.0, 0.44, 0.39, 0.67, 0.82, 0.55, 0.71, 0.48)

    private val achievements = listOf(
        "trophy" to true, "star" to true, "crown" to true, "flame" to true, "zap" to true, "gift" to true,
        "target" to false, "pickaxe" to false, "swords" to false, "shield" to false, "map" to false, "lock" to false,
    )

    private var selected = "home"

    override fun view(): View {
        val window = Panel(
            style = Theme.page.copy(padding = Insets.NONE, radius = Theme.R_XL),
            width = Size.Fixed(1420),
            height = Size.Fixed(860),
            direction = Direction.ROW,
            children = listOf(iconRail(), content()),
        )

        return Panel(
            width = Size.Fixed(Shaders.CANVAS_WIDTH),
            height = Size.Fixed(Shaders.CANVAS_HEIGHT),
            style = Theme.scrim,
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = listOf(window),
        )
    }

    /** The narrow column of icons down the left, as the WebGUI has it. */
    private fun iconRail() = Panel(
        style = Style(
            background = Paint(0x05060D, 0.55),
            border = Border(1, Paint(Theme.LINE, 0.1)),
            radius = Theme.R_XL,
            padding = Insets.symmetric(Theme.SPACE_3, Theme.SPACE_2),
        ),
        width = Size.Fixed(64),
        height = Size.Fill,
        gap = 6,
        align = Align.CENTER,
        children = rail.map { (name, _) ->
            val id = "rail:$name"
            val active = selected == name
            Panel(
                style = when {
                    active -> Style(background = Paint(Theme.VIOLET, 0.22), radius = 10)
                    hovered == id -> Style(background = Paint(Theme.LINE, 0.1), radius = 10)
                    else -> Style(radius = 10)
                },
                width = Size.Fixed(40),
                height = Size.Fixed(40),
                justify = Justify.CENTER,
                align = Align.CENTER,
                id = id,
                children = listOf(
                    Icon(name, 20, if (active) Theme.INK else Theme.INK_DIM)
                ),
            )
        },
    )

    private fun content() = Panel(
        width = Size.Fill,
        height = Size.Fill,
        style = Style(padding = Insets.all(Theme.SPACE_5)),
        gap = Theme.SPACE_4,
        children = listOf(
            topBar(),
            Panel(
                direction = Direction.ROW,
                width = Size.Fill,
                gap = Theme.SPACE_4,
                align = Align.START,
                children = listOf(profile(), rightColumn()),
            ),
        ),
    )

    private fun topBar() = Panel(
        direction = Direction.ROW,
        width = Size.Fill,
        align = Align.CENTER,
        gap = Theme.SPACE_3,
        children = listOf(
            RichText(
                spans = listOf(
                    Span("VOID", Theme.INK, TextFonts.Weight.BOLD),
                    Span("RP", Theme.VIOLET_SOFT, TextFonts.Weight.BOLD),
                ),
                size = Theme.TEXT_H3,
            ),
            Text("/", Theme.TEXT_H3, Theme.INK_DIM, wrap = false),
            eyebrow("Главная"),
            Panel(width = Size.Fill),
            Panel(
                style = if (hovered == "close") Theme.cardAccent else Theme.card.copy(padding = Insets.all(8)),
                width = Size.Fixed(36),
                height = Size.Fixed(36),
                justify = Justify.CENTER,
                align = Align.CENTER,
                id = "close",
                children = listOf(Icon("x", 16, Theme.INK_SOFT)),
            ),
        ),
    )

    /** The player card: a portrait, a name, the nation they belong to, their pass. */
    private fun profile() = Panel(
        style = Theme.card.copy(radius = Theme.R_LG),
        width = Size.Fixed(300),
        height = Size.Fill,
        gap = Theme.SPACE_3,
        align = Align.CENTER,
        children = listOf(
            Panel(
                style = Style(
                    background = Paint(Theme.VIOLET, 0.1),
                    border = Border(1, Paint(Theme.VIOLET, 0.3)),
                    radius = Theme.R_LG,
                ),
                width = Size.Fill,
                height = Size.Fixed(180),
                justify = Justify.CENTER,
                align = Align.CENTER,
                children = listOf(Icon("user", 64, Theme.VIOLET_SOFT)),
            ),
            Text("mironoouv", Theme.TEXT_H3, Theme.VIOLET_SOFT, TextFonts.Weight.BOLD, wrap = false),
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                align = Align.CENTER,
                children = listOf(
                    chip("VLD", Theme.chipAccent),
                    Text("Валдария", Theme.TEXT_BODY, Theme.INK_SOFT, wrap = false),
                ),
            ),
            Panel(
                direction = Direction.ROW,
                gap = 6,
                align = Align.CENTER,
                children = listOf(
                    Icon("shield", 12, Theme.INK_DIM),
                    Text("Лидер · Основатель", Theme.TEXT_CAPTION, Theme.INK_DIM, wrap = false),
                ),
            ),
            Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),
            Panel(
                width = Size.Fill,
                gap = 6,
                children = listOf(
                    Panel(
                        direction = Direction.ROW,
                        width = Size.Fill,
                        align = Align.CENTER,
                        gap = 6,
                        children = listOf(
                            Icon("battlepass", 14, Theme.INK_SOFT),
                            Text("Уровень 37", Theme.TEXT_BODY, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false),
                            Panel(width = Size.Fill),
                            chip("Premium", Theme.chip.copy(textColour = Theme.GOLD)),
                        ),
                    ),
                    Panel(
                        style = Style(background = Paint(Theme.LINE, 0.12), radius = 4),
                        width = Size.Fill,
                        height = Size.Fixed(8),
                        direction = Direction.ROW,
                        children = listOf(
                            Panel(
                                style = Style(background = Paint(Theme.GOLD, 0.95), radius = 4),
                                width = Size.Percent(0.64),
                                height = Size.Fill,
                            )
                        ),
                    ),
                ),
            ),
            Panel(style = Theme.divider, width = Size.Fill, height = Size.Fixed(1)),
            Panel(
                width = Size.Fill,
                gap = Theme.SPACE_2,
                children = listOf(
                    Panel(
                        direction = Direction.ROW,
                        width = Size.Fill,
                        align = Align.CENTER,
                        children = listOf(
                            eyebrow("Достижения"),
                            Panel(width = Size.Fill),
                            Text("12 / 40", Theme.TEXT_CAPTION, Theme.INK_SOFT, TextFonts.Weight.SEMIBOLD, wrap = false),
                        ),
                    ),
                    Grid(
                        columns = 6,
                        gap = 6,
                        rowGap = 6,
                        width = Size.Fill,
                        children = achievements.map { (icon, unlocked) ->
                            Panel(
                                style = Style(
                                    background = Paint(if (unlocked) Theme.VIOLET else Theme.LINE, if (unlocked) 0.16 else 0.05),
                                    border = Border(1, Paint(if (unlocked) Theme.VIOLET else Theme.LINE, if (unlocked) 0.4 else 0.1)),
                                    radius = 8,
                                ),
                                width = Size.Fill,
                                height = Size.Fixed(36),
                                justify = Justify.CENTER,
                                align = Align.CENTER,
                                children = listOf(
                                    Icon(icon, 16, if (unlocked) Theme.VIOLET_SOFT else Theme.INK_DIM)
                                ),
                            )
                        },
                    ),
                ),
            ),
            Panel(height = Size.Fill),
            Panel(
                direction = Direction.ROW,
                width = Size.Fill,
                align = Align.CENTER,
                gap = Theme.SPACE_2,
                children = listOf(
                    Panel(
                        style = Style(background = Paint(Theme.GREEN, 0.9), radius = 4),
                        width = Size.Fixed(6),
                        height = Size.Fixed(6),
                    ),
                    Text("48 ms", Theme.TEXT_CAPTION, Theme.INK_SOFT, wrap = false),
                    Panel(width = Size.Fill),
                    Text("с 12 мар 2026", Theme.TEXT_CAPTION, Theme.INK_DIM, wrap = false),
                ),
            ),
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
                align = Align.START,
                children = listOf(statsPanel(), tilesPanel()),
            ),
            topNations(),
            activity(),
        ),
    )

    /**
     * Fourteen days of play, as bars.
     *
     * A chart is the one thing this medium draws as easily as a browser does: a bar is a
     * rectangle, and rectangles are what everything here is made of.
     */
    private fun activity() = Panel(
        style = Theme.card.copy(radius = Theme.R_LG),
        width = Size.Fill,
        // A definite height, because bars are a share of something: "fill what is left"
        // inside a column that is itself as tall as its contents leaves nothing to share.
        height = Size.Fixed(168),
        gap = Theme.SPACE_3,
        children = listOf(
            Panel(
                direction = Direction.ROW,
                width = Size.Fill,
                align = Align.CENTER,
                gap = Theme.SPACE_2,
                children = listOf(
                    Icon("activity", 16, Theme.INK_SOFT),
                    Text("Активность", Theme.TEXT_BODY, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false),
                    Panel(width = Size.Fill),
                    eyebrow("14 дней"),
                ),
            ),
            Panel(
                direction = Direction.ROW,
                width = Size.Fill,
                height = Size.Fixed(96),
                gap = 6,
                align = Align.END,
                children = minutes.mapIndexed { index, value ->
                    val id = "day:$index"
                    Panel(
                        width = Size.Fill,
                        height = Size.Percent(value),
                        style = Style(
                            background = Paint(
                                if (hovered == id) Theme.VIOLET_SOFT else Theme.VIOLET,
                                if (hovered == id) 0.95 else 0.6,
                            ),
                            radius = 4,
                        ),
                        id = id,
                    )
                },
            ),
        ),
    )

    /** The welcome panel: the one place on the page allowed to be loud. */
    private fun welcome() = Panel(
        style = Style(
            background = Paint(0x221840, 0.88),
            border = Border(1, Paint(Theme.VIOLET, 0.26)),
            radius = Theme.R_XL,
            padding = Insets.all(Theme.SPACE_5),
        ),
        width = Size.Fill,
        height = Size.Fixed(212),
        gap = Theme.SPACE_2,
        children = listOf(
            eyebrow("VoidRP · сезон 3", Theme.VIOLET_SOFT),
            RichText(
                spans = listOf(
                    Span("Добро пожаловать в ", Theme.INK),
                    Span("VOID", Theme.INK, TextFonts.Weight.BOLD),
                    Span("RP", Theme.VIOLET_SOFT, TextFonts.Weight.BOLD),
                ),
                size = Theme.TEXT_H2,
                weight = TextFonts.Weight.BOLD,
            ),
            Text(
                "Государства, экономика и рейтинги — всё здесь, не выходя из игры.",
                Theme.TEXT_BODY,
                Theme.INK_SOFT,
            ),
            Panel(height = Size.Fill),
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                align = Align.CENTER,
                children = listOf(button("Играть", "play", Theme.buttonPrimary, Size.Fixed(160))),
            ),
        ),
    )

    private fun kpis() = Grid(
        columns = 4,
        gap = Theme.SPACE_3,
        width = Size.Fill,
        children = listOf(
            kpi("wallet", "Баланс", "184 200 ₽", Theme.GOLD),
            kpi("swords", "Убийств", "1 204", Theme.INK),
            kpi("skull", "Смертей", "312", Theme.INK),
            kpi("clock", "В игре", "268 ч", Theme.INK),
        ),
    )

    private fun kpi(icon: String, caption: String, value: String, colour: Int) = Panel(
        style = Theme.card.copy(radius = Theme.R_LG, padding = Insets.symmetric(15, 16)),
        width = Size.Fill,
        gap = 3,
        children = listOf(
            Panel(
                direction = Direction.ROW,
                gap = Theme.SPACE_2,
                align = Align.CENTER,
                children = listOf(
                    Icon(icon, 16, if (colour == Theme.GOLD) Theme.GOLD else Theme.INK_DIM),
                    eyebrow(caption, if (colour == Theme.GOLD) Theme.GOLD else Theme.INK_DIM),
                ),
            ),
            Text(value, Theme.TEXT_H3, colour, TextFonts.Weight.BOLD, wrap = false),
        ),
    )

    private fun statsPanel() = Panel(
        style = Theme.card.copy(radius = Theme.R_LG),
        width = Size.Fill,
        gap = Theme.SPACE_3,
        children = listOf(
            panelHead("activity", "Статистика"),
            Grid(
                columns = 2,
                gap = Theme.SPACE_2,
                rowGap = Theme.SPACE_2,
                width = Size.Fill,
                children = stats.map { (icon, label, value) ->
                    Panel(
                        style = Style(background = Paint(Theme.LINE, 0.05), radius = 10, padding = Insets.symmetric(8, 10)),
                        width = Size.Fill,
                        direction = Direction.ROW,
                        gap = Theme.SPACE_2,
                        align = Align.CENTER,
                        children = listOf(
                            Icon(icon, 16, Theme.VIOLET_SOFT),
                            Text(label, Theme.TEXT_CAPTION, Theme.INK_SOFT, wrap = false),
                            Panel(width = Size.Fill),
                            Text(value, Theme.TEXT_BODY, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false),
                        ),
                    )
                },
            ),
        ),
    )

    private fun tilesPanel() = Panel(
        style = Theme.card.copy(radius = Theme.R_LG),
        width = Size.Fixed(330),
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
                            Style(background = Paint(Theme.VIOLET, 0.12), border = Border(1, Paint(Theme.VIOLET, 0.4)), radius = 13)
                        } else {
                            Style(background = Paint(Theme.LINE, 0.04), border = Border(1, Paint(Theme.LINE, 0.12)), radius = 13)
                        },
                        width = Size.Fill,
                        height = Size.Fixed(72),
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

    private fun topNations() = Panel(
        style = Theme.card.copy(radius = Theme.R_LG),
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
                    Text("Топ государств", Theme.TEXT_BODY, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false),
                    Panel(width = Size.Fill),
                    Panel(
                        direction = Direction.ROW,
                        gap = 4,
                        align = Align.CENTER,
                        id = "all-nations",
                        children = listOf(
                            eyebrow("Все", if (hovered == "all-nations") Theme.INK else Theme.INK_DIM),
                            Icon("chevronRight", 12, Theme.INK_DIM),
                        ),
                    ),
                ),
            ),
            Panel(
                width = Size.Fill,
                gap = 6,
                children = nations.map { (rank, nation, value) ->
                    val (tag, name) = nation
                    val id = "nation:$tag"
                    Panel(
                        style = Style(
                            background = Paint(Theme.LINE, if (hovered == id) 0.09 else 0.03),
                            border = Border(1, Paint(Theme.LINE, if (hovered == id) 0.22 else 0.12)),
                            radius = Theme.R_MD,
                            padding = Insets.symmetric(9, 12),
                        ),
                        width = Size.Fill,
                        direction = Direction.ROW,
                        gap = 11,
                        align = Align.CENTER,
                        id = id,
                        children = listOf(
                            Panel(
                                style = Style(
                                    background = Paint(rankColour(rank), if (rank <= 3) 0.9 else 0.2),
                                    radius = 7,
                                ),
                                width = Size.Fixed(24),
                                height = Size.Fixed(24),
                                justify = Justify.CENTER,
                                align = Align.CENTER,
                                children = listOf(
                                    Text(
                                        rank.toString(),
                                        Theme.TEXT_CAPTION,
                                        if (rank <= 3) 0x1A1200 else Theme.INK_DIM,
                                        TextFonts.Weight.BOLD,
                                        wrap = false,
                                    )
                                ),
                            ),
                            chip(tag, Theme.chipAccent),
                            Text(name, Theme.TEXT_BODY, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false),
                            Panel(width = Size.Fill),
                            Icon("coins", 14, Theme.GOLD),
                            Text(value, Theme.TEXT_BODY, Theme.GOLD, TextFonts.Weight.BOLD, wrap = false),
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
            Icon(icon, 16, Theme.INK_SOFT),
            Text(title, Theme.TEXT_BODY, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false),
        ),
    )

    private fun rankColour(rank: Int) = when (rank) {
        1 -> 0xFCD34D
        2 -> 0xE5E7EB
        3 -> 0xD9A066
        else -> Theme.LINE
    }

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
