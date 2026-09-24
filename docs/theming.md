# Theming

The whole look is a dozen colours and four numbers in `plugins/VoidRpUI/theme.yml`. Nothing
in a page names a colour of its own, so changing that file changes every page, including
the ones other plugins wrote.

| | |
|---|---|
| ![Midnight](theme-midnight.png) | **midnight** — near-black with violet. The default, and what the screenshots are taken in. |
| ![Daylight](theme-daylight.png) | **daylight** — a light theme: pale page, dark ink, indigo accent. |
| ![Ember](theme-ember.png) | **ember** — warm dark: charcoal with an orange fire in it. |
| ![Grove](theme-grove.png) | **grove** — dark green, for a server that is mostly outdoors. |

The same page, the same code. Pick one with `theme: midnight` in `config.yml` before the
first run, or copy any of them out of the jar (`themes/*.yml`) into `theme.yml` and edit
from there.

## What the file holds

```yaml
# The page's surfaces and its text
bg: "#05060D"        # behind everything: what hides the world
surface: "#0F1526"   # the page itself; every card and tile is derived from it
ink: "#EAF0FF"       # the text you read
ink-soft: "#AEB9D6"  # secondary text
ink-dim: "#6B779A"   # labels, captions, anything quiet
line: "#96A8DC"      # the tint of every border and divider

# Accents
accent: "#8B7BFF"        # the one colour the interface belongs to
accent-soft: "#A78BFA"   # a lighter one, for text on the accent and for highlights
accent-second: "#D946EF" # the far end of a gradient, and anything that needs contrast
green: "#34D399"         # good news
gold: "#FBBF24"          # money, levels, rewards
red: "#FB7185"           # bad news

tracking: 2          # letter-spacing of the small caps labels

text:                # type sizes, in canvas units (the canvas is 1024 tall)
  micro: 10
  caption: 12
  body: 14
  lead: 16
  h3: 20
  h2: 28
  h1: 40

radius:              # rounding
  sm: 8
  md: 12
  lg: 16
  xl: 20
```

## How the surfaces are built

You give two colours — `surface` and `line` — and the theme derives the rest, each one
standing on the one below it:

```
world  →  black  →  bg  →  surface (the page)  →  card  →  tile
```

A card is `surface` carried a little towards `line` on a dark theme, and towards white on
a light one. The theme knows which it is from the luminance of `surface`, which also
decides whether cards get the lit top edge that gives them depth — invisible on a pale
surface, so it is left off.

Each surface is then **expressed** rather than named: colour travels to the client in ten
bits, and at the dark end the step between two entries is bigger than the difference
between a page and a card on it. So the engine picks a colour *and* an opacity whose result
over the surface below lands on what you asked for — the miss falls from twenty units to
one or two. That is why `theme.yml` means what it says. The details are in
[internals](internals.md).

## Writing your own

Start from the one nearest what you want, change `surface`, `line` and `accent`, and look
at it:

```bash
./gradlew preview        # build/preview/theme-*.png
```

Three things worth knowing while you do:

- **Pick `surface` first.** Everything else is derived from it. A page reads as one surface
  with things standing on it, so the difference between them is small on purpose.
- **`line` decides how much contrast the cards have.** It is the colour surfaces are
  carried towards, so a cool grey gives cool cards and a warm one warm cards, and something
  far from `surface` makes them stand out sharply.
- **One accent is enough.** `accent` carries the whole interface; `accent-second` exists so
  a gradient has somewhere to run to.

A page that builds a panel of its own should take its colours from the theme rather than
naming them, or it will stay dark on a light theme:

```kotlin
Panel(style = Theme.card)                                   // the surfaces themselves
Panel(style = Style(background = Theme.tileFill))           // the fills they are made of
Palette.express(0x1A2030, over = Theme.onCard)              // a colour of your own, over a card
```

`Theme.groundFill`, `pageFill`, `cardFill`, `tileFill` are the fills; `Theme.onGround`,
`onPage`, `onCard` are what they actually come out as, for expressing something over them.
`Theme.isDark` says which kind of theme is loaded.

## Something that moves

A page is sent once and then sits still, which is right for a page and wrong for what is
behind it. So a field of specks is drawn by the shader rather than by the server:

```kotlin
override fun view(): View = screen(children = listOf(
    Raw(Rect(0, 0, viewport.width, viewport.height, background)),
    Particles(count = 90, colour = Theme.VIOLET_SOFT, alpha = 0.5),
    content(),
))
```

Each speck carries a marker of its own, and the shader works out where it is from the time
of day — its place on the line is its seed, so every one of them drifts at its own pace and
sways by its own amount. The page is still sent once and never again: the motion costs no
frames, no packets and no server thread, and it runs at the client's frame rate rather than
at ours.

It asks the text shader for one thing more than it otherwise would — the client's own
time of day — and a client that would not give it would refuse the whole pack rather than
that one line. It is on by default since it was tried on real clients at both ends of the
supported range, 26.2 and 1.21.6, and moved on both. `effects.particles: false` turns it
off: the same `Particles` are then drawn as a still field and the shader is left exactly as
it was, so a page written with them works either way.

## Changing it while the server runs

`/vui reload`-style restarts are not needed: the theme is re-read with the config, and the
next page a player opens is drawn in it. The resource pack does not change — colours travel
with the page, not with the pack.
