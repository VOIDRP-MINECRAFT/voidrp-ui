# Responsive

A page is laid out for the player's screen, not for one invented size. Here is how that
works, what to write in a page, and why one piece of it has to be asked of the player.

## The canvas

The canvas is always **1024 units tall** — the whole height of the player's window, at any
resolution and any GUI scale. A unit is square, so how many fit across is the shape of the
screen:

| Screen | Canvas width |
|---|---|
| 5:4 (1280×1024) | 1280 |
| 4:3 | 1365 |
| 16:10 | 1638 |
| 16:9 | 1820 |
| 21:9 | 2389 |

Nothing is stretched: a circle stays a circle and type stays type. It is the same model a
web page uses, with the height of the window fixed and the layout answering to the width.

```kotlin
class ProfilePage : Page() {
    override fun view(): View = screen(          // a panel the size of this player's window
        align = Align.CENTER,
        children = listOf(/* … */),
    )
}
```

`viewport.width` and `viewport.height` are there when a page needs the numbers themselves.

## Breakpoints

`viewport.size` gives three classes, the way a stylesheet's media queries do:

| Class | Width | Who that is |
|---|---|---|
| `COMPACT` | < 1500 | 5:4, 4:3 |
| `REGULAR` | 1500…2100 | 16:10, 16:9 |
| `WIDE` | ≥ 2100 | 21:9 and wider |

```kotlin
Grid(columns = viewport.by(compact = 2, regular = 3, wide = 4), children = tiles)

val gutter = viewport.by(compact = Theme.SPACE_3, regular = Theme.SPACE_5)
```

Columns can also be counted from a card's ideal width:

```kotlin
Grid(columns = viewport.columns(ideal = 260, min = 2, max = 5, gap = Theme.SPACE_3))
```

**The rule that matters:** the canvas height is constant, so on a narrow screen content
must **not** wrap into extra rows — the height is spoken for already. Narrow means
*narrower cards*, not *more rows*. Add rows only when you take something else away.

## Holding a width

A column is kept readable the way it is on the web — `max-width`, centred:

```kotlin
Panel(
    width = Size.Fill,   // take everything there is
    maxWidth = 1278,     // but no more than this; the rest becomes margin
    children = listOf(profile(), rightColumn()),
)
```

`maxWidth` / `maxHeight` / `minWidth` / `minHeight` are on every `Panel`. Given more room
than its limit, a panel takes the middle of what it was given — `margin: 0 auto`.

## The safe band

The server does not know the shape of the screen for certain (see below), so there is a
convention: everything a player reads or clicks fits inside the central
**`viewport.safeWidth`** — the 4:3 band, 1365 units. Only backgrounds, glows and decoration
go outside it.

Then a wrong screen shape costs looks rather than use.

## Bleeding past the canvas

No window is exactly a named format: a title bar and a task bar take a slice out of the
height, so a maximised 1920×1080 window is nearer 1.89 than 1.78. The difference shows as a
strip of the world down the edge.

So a page's background is painted wider than the canvas — by `Viewport.BLEED` units on each
side:

```kotlin
class HomePage : Page() {
    // Painted first and running past the edges; content never goes out there.
    override val bleed = listOf(Paint(0x000000, 0.97), pageTint)
}
```

The engine lays those fills under the page as it draws. Only colour belongs there: no text
and no buttons outside the canvas.

## Where the screen shape comes from

A vanilla client never sends it. Its settings packet carries the language, the view
distance and which hand the player holds a sword in — the size of the window is not in
there, and there is nothing to ask with. So:

1. `display.screen` in `config.yml` is what is assumed (`16:9` by default).
2. The player sets their own with **`/vui screen`** — a page opens with a frame on the edge
   of the canvas, and they press shapes until it sits on the edges of their screen, seeing
   the result immediately.
3. The same page has fine tuning — «narrower» and «wider», eight units a press. A window
   rarely matches a named shape exactly, and two or three presses put the frame on the edge.
4. The answer lives in `plugins/VoidRpUI/screens.yml`, for good.

The question is asked once by itself, before a player's very first page, and can be turned
off with `display.ask-screen: false`.

From your own code:

```kotlin
val ui = VoidRpUi.get() ?: return
ui.viewport(player)                                 // this player's canvas
ui.setViewport(player, Viewport.parse("4:3"))       // set it; null goes back to the server's
ui.askScreen(player, then = MyPage())               // show the setup, then carry on
```

## Checking without the game

`./gradlew preview` draws the pages into PNGs, including the home page in **every** screen
shape: `build/preview/home-5x4.png`, `home-4x3.png`, `home-16x9.png`, `home-21x9.png`.
Beside each picture is a `.txt` with the exact geometry — what ended up where, and how big.

The tests carry the guard that catches the commonest mistake in responsive layout:

```
./gradlew test --tests '*hangs off any screen*'
```

It lays every page out on every screen shape and fails if a single shape falls outside.
Your own page is added to it in one line.

## Under the hood

The vertex shader gets nothing from the client but the pen's position and the projection
matrix. From the matrix it works out the shape of the window, takes the window's height to
be 1024 units and uses the same scale across. The x coordinate arrives as a distance from
the middle of the page — which is exactly where the boss bar's centring leaves the pen — so
no width is baked into the shader: the same shader draws a page laid out for any screen.
