# Layout

A page describes **what** is on it, not where each piece goes. The layout works out the
sizes and the coordinates, the way a browser does. This is everything a page is built from.

All of it is plain data classes in `ru.voidrp.ui.layout`. They are immutable: a page builds
its description afresh every time, and the engine turns that into shapes.

## The panel

`Panel` is the only container. It is a flex container from the web: it stacks its children
in a row or a column, with a gap and rules for lining them up.

```kotlin
Panel(
    direction = Direction.ROW,      // ROW | COLUMN (COLUMN by default)
    gap = Theme.SPACE_3,            // space between children
    justify = Justify.SPACE_BETWEEN,// along the direction
    align = Align.CENTER,           // across it
    width = Size.Fill,
    height = Size.Fixed(58),
    maxWidth = 1278,                // optional limit, plus automatic centring
    style = Theme.card,
    id = "top-bar",                 // the panel becomes something the player can click
    children = listOf(/* … */),
)
```

| Field | What it does |
|---|---|
| `direction` | `ROW` for a row, `COLUMN` for a column |
| `gap` | space between children, in canvas units |
| `justify` | `START`, `CENTER`, `END`, `SPACE_BETWEEN` — along the direction |
| `align` | `START`, `CENTER`, `END`, `STRETCH` — across it |
| `width` / `height` | see **Sizes** |
| `minWidth` / `maxWidth` / `minHeight` / `maxHeight` | limits; past one, the panel takes the middle of the room it was given (`margin: 0 auto`) |
| `shrink` | `false` keeps this panel's size when the row it is in has too little room, and the rest of the row gives it up instead — what a chip wants, because squeezed it becomes a word with an ellipsis |
| `wrap` / `lineGap` | a row that carries on underneath when it runs out of width |
| `style` | background, border, rounding, padding, shadow, glow — see the theme |
| `id` | names a region: hovering and clicks arrive under this name |

Padding inside a panel is `style.padding`, not a field of the panel:
`Style(padding = Insets.all(16))` or `Insets.symmetric(vertical, horizontal)`.

## Sizes

```kotlin
Size.Auto              // as big as the content needs (the default)
Size.Fill              // everything the parent can spare
Size.Fixed(240)        // exactly this many units
Size.Percent(0.66)     // a fraction of what the parent offers
```

Several `Fill` children in one panel share the spare room equally. A `Fill` inside an
`Auto` parent behaves as `Auto`: there is nothing to share out while the parent's own size
still depends on its children.

## The root of a page

```kotlin
override fun view(): View = screen(
    style = Theme.scrim,
    align = Align.CENTER,
    children = listOf(card()),
)
```

`screen()` (from `ru.voidrp.ui.widget`) is a panel exactly the size of the player's screen:
1024 units tall and as many across as the shape of their window allows. See
[responsive](responsive.md).

## Text

```kotlin
Text("Welcome", size = Theme.TEXT_H2, colour = Theme.INK, weight = Weight.BOLD)
Text(long, wrap = true, maxLines = 2, lineHeight = 20, align = TextAlign.CENTER)
Text("ACHIEVEMENTS", size = Theme.TEXT_CAPTION, tracking = 2)   // tracked out, as small caps
```

Text measures itself, so a panel around it fits it exactly. A long line wraps at spaces;
`maxLines` cuts with an ellipsis; `wrap = false` forbids wrapping, which is what a row of
labels that must not fold needs.

A line made of differently styled pieces is `RichText`:

```kotlin
RichText(listOf(
    Span("VOID", Theme.INK, Weight.BOLD),
    Span("RP", Theme.VIOLET_SOFT, Weight.BOLD),
), size = Theme.TEXT_LEAD)
```

## Pictures

```kotlin
Icon("wallet", size = 16, colour = Theme.INK_SOFT)  // an interface icon, takes the colour
Image("diamond", size = 32)                          // an item, from the client's textures
Picture("logo", height = 64)                         // the server's own PNG, own colours
Head("mironoouv", size = 128)                        // a player's face from their skin
```

## Grid

```kotlin
Grid(
    columns = 3,
    gap = Theme.SPACE_2,
    rowGap = Theme.SPACE_2,
    grow = true,        // rows share out the height their panel has left over
    children = tiles,
)
```

Every cell is the same size — that of the largest — so the grid lines up. The number of
columns can come from the screen:
`viewport.columns(ideal = 260, min = 2, max = 5, gap = Theme.SPACE_3)`.

## A row that wraps

```kotlin
Panel(
    direction = Direction.ROW,
    wrap = true,
    gap = Theme.SPACE_2,
    lineGap = Theme.SPACE_2,   // between lines; the panel's gap when not given
    children = tags.map { chip(it) },
)
```

`flex-wrap`, for what a grid does not cover: things of different widths — chips, tags, a
hand of items — that fill the line and start another. A grid puts everything in cells of
one size, which is right for tiles and wrong for words. Only a row wraps; in a column the
flag is ignored.

## Scrolling

```kotlin
Scroll(children = rows, offset = scroll, gap = 8, bar = true, id = "list")
```

The page keeps `offset`: the wheel changes a number and the page draws itself again. A
glyph cannot be cut in half on the way to the client — it is drawn whole or not at all — so
rectangles are cut at the edge of the window and words and icons are shown only while they
fit whole.

The bar can be dragged: `scrollFromBar(scroll, id, viewport.width, viewport.height)` from
`ru.voidrp.ui.widget` turns the cursor's position into the new `offset`.

## Above everything

```kotlin
Overlay(dropdown())   // takes its place as an ordinary child, but is painted last
```

A dropdown in the ordinary flow pushes the panel apart as it opens and ends up underneath
whatever comes after it. Inside an `Overlay` it moves nothing and is painted on top.

## Placing by hand

```kotlin
Raw(Rect(0, 0, viewport.width, viewport.height, tint))   // coordinates from the parent's corner
```

`Raw` takes no room and moves nothing — for stars on a background, a glow inside a card,
and anything else the layout has no word for yet.

## Emptiness

```kotlin
Gap(size = 12)          // just empty space
Gap(grow = true)        // a spring: pushes its neighbours apart
```

## Regions, hovering, clicks

A panel with an `id` is remembered during layout, and everything after that happens on the
server:

```kotlin
Panel(
    id = "tile:market",
    style = if (hovered == "tile:market") Theme.card.hover() else Theme.card,
    children = listOf(Icon("cart"), Text("Market")),
)

override fun onClick(id: String, button: Button) {
    if (id == "tile:market") push(MarketPage())
}
```

More about events in [pages](page.md).

## What the layout does not have

- **Absolute positioning** — beyond `Raw`, and deliberately so: a page typed out in
  coordinates breaks on the first screen of another shape.
- **Clipping to an arbitrary shape** — a glyph is drawn whole; cutting happens with
  rectangles only, which is what `Scroll` does.
