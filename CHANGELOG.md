# Changelog

Versions follow [semver](https://semver.org/). While the major is zero, breaking changes
arrive with a minor bump and are named here outright.

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
