# Components

![Components: buttons, tick boxes, sliders, dropdowns](states.png)
![Components: tabs, switches, tiles, notices, dialogs](states-2.png)

All of it is ordinary functions in `ru.voidrp.ui.widget` that return a `View`. None of them
hold any state: the page holds the state, and a component only shows it and reports that it
was pressed. So there is nothing here that can drift out of step with the data.

The ones that answer the cursor are declared as extensions of `Page`, because they need to
know what is hovered:

```kotlin
import ru.voidrp.ui.widget.*

override fun view(): View = screen(style = Theme.scrim, align = Align.CENTER, children = listOf(
    card("Profile", icon = "user", children = listOf(
        statTile("Balance", "184 200", "coins", accent = Theme.GOLD),
        button("Open the market", "market", Theme.buttonPrimary),
    )),
))
```

## Structure

| | |
|---|---|
| `screen(style, direction, justify, align, gap, children)` | the root of a page: a panel the size of the player's screen |
| `card(title, children, icon, trailing, style, width, height, gap)` | a card with a heading; `trailing` goes on the right of it |
| `divider(width, paint)` | a hairline |
| `eyebrow(text, colour, size, tracking)` | the small tracked-out caps above things |
| `chip(text, style)` | a pill: a version, a mode, a state |
| `emptyState(title, hint, icon)` | nothing to show — an invitation, not an apology |
| `notice(text, tone, width)` | a line with a colour: `Tone.INFO`, `GOOD`, `WARN`, `BAD` |
| `skeleton(width, height, radius)` | a dim bar standing in for something still loading — see "Data that arrives later" in [page.md](page.md) |

## Things to press

| | Reports |
|---|---|
| `button(caption, id, style, width, height)` | `id` |
| `iconButton(icon, id, size, style, selected, iconSize)` | `id` |
| `tabs(id, tabs, selected, height)` | `"<id>:<key>"` |
| `checkbox(label, id, checked, style, width)` | `id` |
| `toggle(id, on, label)` | `id` |
| `stepper(id, value, style)` | `"<id>:-"` and `"<id>:+"` |
| `select(id, options, selected, open, width)` | `id` when closed, `"<id>:option:<index>"` for an option |
| `slider(id, value, width, height)` | `id`; where exactly is `sliderValue(id)` |
| `dialog(id, title, text, yes, no, tone, width)` | `"<id>:yes"` and `"<id>:no"` |

Everything pressable highlights itself: it knows `hovered` and takes a brighter style.

## Things that show

| | |
|---|---|
| `statTile(label, value, icon, style, width, accent)` | a number with a word under it — what a dashboard is made of |
| `progress(caption, value, valueText, colour, width)` | a bar with a caption and a value |
| `tooltipPanel(title, lines, width)` | a tooltip — return it from `tooltip()`; it goes under the hovered row or tile |

## What it looks like in a page

```kotlin
class MarketPage : Page() {

    private var tab = "all"
    private var confirming: String? = null

    override fun view(): View = screen(style = Theme.scrim, align = Align.CENTER, children = listOf(
        card(
            "Market",
            icon = "market",
            trailing = tabs("tab", listOf("all" to "All", "arms" to "Weapons"), tab),
            children = listOf(
                if (items.isEmpty()) emptyState("Nothing is for sale yet")
                else Grid(columns = viewport.by(compact = 3, regular = 4), children = items.map { tile(it) }),
            ),
        ),
        // The dialog stands over the page, so it goes in an Overlay.
        confirming?.let { Overlay(dialog("sell", "Sell for 120?", "This cannot be undone.", tone = Tone.BAD)) }
            ?: Gap(0),
    ))

    override fun onClick(id: String, button: Button) = when {
        id.startsWith("tab:") -> { tab = id.removePrefix("tab:"); refresh() }
        id == "sell:yes" -> { sell(confirming!!); confirming = null; refresh() }
        id == "sell:no" -> { confirming = null; refresh() }
        else -> Unit
    }
}
```

## Your own component

No API is needed for this: a component is a function that returns a `View`.

```kotlin
fun Page.priceTag(item: String, price: Int): View = Panel(
    style = if (hovered == "buy:$item") Theme.cardAccent else Theme.card,
    direction = Direction.ROW,
    gap = Theme.SPACE_2,
    align = Align.CENTER,
    id = "buy:$item",
    children = listOf(
        Image(item, 24),
        Text(name(item), Theme.TEXT_BODY, Theme.INK, wrap = false),
        Panel(width = Size.Fill),
        Text("$price", Theme.TEXT_BODY, Theme.GOLD, TextFonts.Weight.BOLD, wrap = false),
    ),
)
```

If it turns out to be useful to everyone, send it in — it belongs here.
