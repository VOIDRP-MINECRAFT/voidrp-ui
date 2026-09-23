# Changelog

Versions follow [semver](https://semver.org/). While the major is zero, breaking changes
arrive with a minor bump and are named here outright.

## 0.3.0

**Something that moves.** A page is sent once and then sits still, which is right for a page
and wrong for what is behind it. `Particles` is a field of specks that the **shader** draws:
each carries a marker of its own, and where it is comes from the time of day rather than
from anything the server sends. Its place on the line is its seed, so every speck drifts at
its own pace and sways by its own amount. The page is still sent once and never again — the
motion costs no frames, no packets and no server thread, and it runs at the client's frame
rate rather than at ours.

It asks the text shader for one thing more than it otherwise would, the client's own
globals, and a client that will not have those refuses the whole pack rather than that one
line. So it ships off: `effects.particles: true`, open a page, and see. With it off the same
`Particles` are drawn as a still field and the shader is untouched, so a page written with
them works either way.

## 0.2.5

- Something that takes no room no longer earns a gap either. A dropdown's open list is an
  overlay, so a panel holding one came out four units taller than it draws — and in a row of
  centred cells the open list sat two units higher than the closed one beside it.

## 0.2.4

- The closed dropdown centres its text too — it had the same fight between a height of its
  own and the padding its style carries for words.
- A test holds every control that has a height of its own to putting its caption in the
  middle of it.

## 0.2.3

- A button's caption sat low in it. The height is given, so the style's vertical padding had
  nothing left to do but fight it: the inner box came out shorter than the line of text and
  the caption was pushed against the bottom edge. Buttons keep only the padding that still
  means something — the one that decides how wide they are.
- The sheet of components says what it is, its cells line up on their middles rather than
  their tops, and its open dropdown no longer hangs off the bottom of the card.
- An empty state is a little less tall, which is what let the second sheet fit a 5:4 screen
  again.

## 0.2.2

- The pieces of one line sit on one baseline. A smaller span left at the same top edge
  floats above the line it belongs to, so "12400 coins" had the word hanging off the top of
  the number.
- A boss bar another plugin owns no longer throws on every packet: the guard built its copy
  of the flags with `EnumSet.copyOf`, which refuses an empty collection — and most bars have
  no flags. The listener is also wrapped, so nothing here can break someone else's packet.
- Four tests for the defects of the day: an icon button centring its icon, spans sharing a
  baseline, a list showing whole rows and nothing of the rest, and a chip keeping its word
  when the row runs out of room.

## 0.2.1

- An icon in an icon button sat in the corner of it. The button's style carries the padding
  its text would need, and inside a square that left an inner box a few units wide, so the
  icon was placed in the corner of that box rather than in the middle of the button. Icon
  buttons drop the padding.
- Icons are re-baked: a symmetric one is folded onto its own mirror before the threshold, so
  both sides of a roof agree on where the ink is, and icons inside buttons are drawn at 24 —
  the grid the set is drawn on, where a stroke lands on whole pixels.
- `Panel(shrink = false)` for something that would rather overflow than be squeezed. Chips
  use it: squeezed, a chip does not become narrower, it becomes a word with an ellipsis.
- Spacer glyphs moved to a two-byte range: the home page went from 94 to 90 KB on the wire.
- The screen setup page falls back to the English that ships in the jar rather than showing
  the key when it is drawn outside a running plugin.
- A scrolling list stops leaving pieces of a card at the edge of its window: a child the
  window shows a few units of is not drawn at all, rather than as a line with the corners it
  was rounded with lying beside it, and a card the window really does cut gets a square
  corner there instead of a notch.
- A tile's number no longer sits low: the line box of a big number carries room for a
  descender there is none of, so the boxes were centred exactly while the ink was not.

## 0.2.0

**A page is laid out for the player's screen.** It used to live on a fixed 1820×1024 board
that was stretched over whatever window it landed in — on a 4:3 monitor that is a 1.4×
squash: circles go oval and type goes narrow. Now a unit is always square: 1024 units is
the whole height of the window at any resolution and GUI scale, and the canvas is as wide
as the shape of the screen (1280 on 5:4, 1820 on 16:9, 2389 on 21:9). A page answers to
that width the way a web page answers to the width of the browser.

- `Viewport` with breakpoints: `viewport.by(compact, regular, wide)`, `columns(...)`,
  `safeWidth`.
- `Panel` gained `maxWidth` / `minWidth` / `maxHeight` / `minHeight` — `max-width` with
  automatic centring, which is `margin: 0 auto`.
- `Page.bleed` — fills painted wider than the canvas, so no strip of the world shows along
  an edge.
- A test lays every page out on seven screen shapes and fails if anything falls outside.

**The screen shape is asked of the player.** A vanilla client never sends the size of its
window and there is no packet to ask with. So before their very first page a player sees a
frame on the edge of the canvas and lines it up with their screen — by shape, or eight
units at a time with the narrower/wider pair. The answer lives in `screens.yml`. Turned off
with `display.ask-screen: false`; `display.keep-proportions` is gone, because nothing is
ever distorted any more.

**Clients 1.21.6–26.1.2.** Mojang renamed the text shader files in 26.2 and a pack names
them outright, so one archive cannot cover both. The plugin builds two packs and hands each
player the one their client reads: the version comes from PacketEvents, and without it the
modern pack is tried and the older one follows for anyone who could not load it.

**Components.** `card`, `tabs`, `statTile`, `iconButton`, `toggle`, `divider`,
`emptyState`, `notice`, `dialog`, `screen` — and a sheet that shows them all.

**A preview without the game.** `Preview.render(MyPage(), File("page.png"),
Viewport.parse("4:3")!!)` from any plugin: the same drawing that travels to a player. In
game, `/vui debug shot`.

**English, and a language setting.** The repository, the docs and the shipped defaults are
in English; `language: ru` in the config writes the Russian set into `messages.yml` on the
first run. The screen setup page takes its words from that file too, so it can be
translated like everything else.

**Documentation.** [Layout](docs/layout.md), [Pages](docs/page.md),
[Components](docs/components.md), [Responsive](docs/responsive.md), and an
[example plugin](example/) that can be copied and built.

**Fixed**
- A page sometimes opened 19 units too low — a race in which the cursor's bar was created
  before the page's.
- A page that closed itself stayed in the session list, and the player could not break
  blocks until they crouched.
- The PacketEvents listener was not unregistered when the plugin reloaded and threw
  `zip file closed` on every packet afterwards.
- The highlight on the top row of buttons (the cursor's bar cannot reach that high, so the
  page draws those itself).
- A jar named after the new version with the old one written inside it: the version was not
  declared an input of `processResources`.

## 0.1.1

- The pack halved: glyph sheets instead of a file per glyph (2095 → 1173 KB, 2968 → 617
  files).
- A page on the wire went to a third: 295 → 94 KB (colour and font are inherited, identical
  runs are merged).
- The server's own pictures (`images/`) and players' faces from skins (`heads/`).
- Tiles reach the bottom of their card (`Grid(grow = true)`).

## 0.1.0

The first version: real interfaces on a vanilla client, through a patched text shader and
an invisible boss bar.
