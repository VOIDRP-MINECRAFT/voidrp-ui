# Pages: state and events

A page is a class that answers one question: **what do I look like right now**. Everything
else is the engine's business.

```kotlin
class ShopPage : Page() {

    private var tab = "weapons"
    private var scroll = 0

    override fun view(): View = screen(style = Theme.scrim, align = Align.CENTER, children = listOf(
        Panel(style = Theme.card, children = listOf(
            tabs("tab", listOf("weapons" to "Weapons", "food" to "Food"), tab),
            Scroll(children = items(), offset = scroll, id = "list"),
        )),
    ))

    override fun onClick(id: String, button: Button) {
        if (id.startsWith("tab:")) { tab = id.removePrefix("tab:"); refresh() }
    }

    override fun onScroll(direction: Int) { scroll += direction * 40; refresh() }
}
```

The page keeps its own state, in ordinary fields. Change a field, call `refresh()`, and the
engine builds the description again and sends it. There is no "update this one element",
and that is deliberate: what is on the screen cannot drift out of step with the data.

## What a page can ask

| | |
|---|---|
| `player` | the player this page is open for |
| `viewport` | this player's canvas: `width`, `height`, `size`, `by(...)` |
| `hovered` | the `id` of the region under the cursor, or `null` |
| `cursorX` / `cursorY` | the cursor, in canvas units |
| `region(id)` | where a named panel ended up after the last layout |

## Events

```kotlin
override fun onClick(id: String, button: Button)        // left/right click on a panel with an id
override fun onDrag(id: String, x: Int, y: Int)         // held and moved — a slider
override fun onScroll(direction: Int)                   // the wheel: 1 down, −1 up
override fun onKey(key: Int)                            // hotbar slot 1…9, if usesKeys = true
override fun onClose()                                  // the page went away, however it went
```

None of this needs a client mod: a swing of the arm is a left click, a use is a right
click, the wheel is a change of held slot, Shift is "back". While a page is open those
actions are swallowed, so a player does not break a block by pressing a button.

`onKey` only reaches pages with `usesKeys = true`, because the game sends the same packet
for a number key and for the wheel. Number keys suit tabs and modes: the hotbar really
moves and the player sees which slot is lit.

## Navigation

```kotlin
push(MarketPage())   // open on top; Shift or back() returns here
back()               // back, if there is anywhere to go
close()              // close for good
```

## Typing

```kotlin
prompt(title = "Price", label = "How much?", initial = "100") { answer ->
    price = answer.toIntOrNull() ?: price
    refresh()
}
```

The game's own text field opens (the page stays on screen) and the answer arrives in the
callback. It reads as a field on the page rather than a detour through chat.

## A tooltip beside the cursor

```kotlin
override fun tooltip(): View? = when (hovered) {
    "item:diamond" -> tooltipPanel("Diamond", listOf("Price: 120", "In stock: 12"))
    else -> null
}
```

The tooltip rides on the cursor's own boss bar, which is sent every frame anyway — so it
follows the mouse without the page being redrawn.

## Hovering without a redraw

The highlight under the cursor is drawn by the engine on the cursor's bar: the page stays
as it is. That is on purpose — a page travels whole (about 90 KB for the home page), and
redrawing it every time the cursor crosses a card would be felt as the cursor stuttering.

If a page really needs its **contents** to change on hover, rather than just be
highlighted — a preview panel that follows the list, say — it says so for itself:

```kotlin
override val redrawsOnHover = true
```

Then `hovered` drives that page's layout too, and every other page stays cheap.
(`input.redraw-on-hover: true` in the config turns it on for all of them.)

## Colour to the edges of the screen

```kotlin
override val bleed = listOf(Paint(0x000000, 0.97), pageTint)
```

The fills that must reach the edges of the window even when the screen shape was guessed a
little wrong. The engine paints them first and wider than the canvas. Only colour goes
there — no text, no buttons. Why it is needed at all is in [responsive](responsive.md).

## Opening a page from your own plugin

```kotlin
val ui = VoidRpUi.get() ?: return          // the plugin is not installed
ui.open(player, MyPage())
ui.isOpen(player); ui.current(player); ui.close(player)
```

And what the engine knows about the player's screen:

```kotlin
ui.viewport(player)                                   // the canvas: width, height, size
ui.setViewport(player, Viewport.parse("4:3"))         // set it; null goes back to the server's
ui.askScreen(player, then = MyPage())                 // show screen setup, then your page
```

`open` returns `false` when the player has no pack: they have already been told so and sent
it again. If the player has never set their screen up, the engine shows the screen setup
first and opens what you asked for when they are done.
