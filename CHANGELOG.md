# Changelog

Versions follow [semver](https://semver.org/). While the major is zero, breaking changes
arrive with a minor bump and are named here outright.

## 0.3.3

**The pointer, measured rather than guessed at.** A recording of a live one
(`/vui debug trace`) showed three things at once, and none of them was the thing the
prediction added in 0.3.2 was meant to fix:

- **It came to rest twelve units from the aim and stayed there.** A stop is *silence* — a
  client sends its aim only when the aim has changed — so with the readings ended, nothing
  pulled the reckoning onto the last one. It was clamped into a window around it and left
  at the edge: a pointer that settles beside the button it is pointed at. It comes home to
  the aim now.
- **It sailed 45 units past a hand that stopped**, and took 110 ms to even start back. A
  stop was noticed by a threshold at 120 ms, which is longer than two gaps between
  readings, so the pointer was thrown forward by a speed the hand no longer had. Belief in
  the reckoning now fades with the age of the last reading instead of falling off a cliff,
  and the speed the lead is taken from drops the moment a reading shows a drop while a rise
  has to be shown three times.
- **It assumed the readings arrive a tick apart.** On the client they were measured landing
  66 to 110 ms apart. Everything counted in gaps — how long a reading stays fresh, how far
  the reckoning may run, half the wait for the next one — is counted in the measured gap
  now.

And one thing the recording could not have shown, because the hands it was made with do
not exist: **a single reading showing a huge step is ambiguous** — a hand moving very fast,
or one jump — and thrown forward by the speed it implies, the pointer leaves the screen.
There is a ceiling on the throw now, 32 units, about three per cent of the height of the
screen.

Against the numbers before them, over six hands and four connections in simulation: the
worst overshoot down 61%, the worst jolt between two frames down 39%, the resting error
gone, and a fast sweep about 20% further behind the hand, which is the price. Measured for
real instead — the same scripted hand, the same client, one build against the other —
resting error 12.2 units to none, overshoot down 15%, the worst jolt between two frames
down 22%. The simulation is kinder than the rig because the rig's mouse teleports, which is
the one thing no ceiling and no filter can follow gracefully and no hand ever does.

The lead a far-away player is given is capped at 75 ms rather than 140: below about 80 ms
of ping the cap decides nothing, and above it a pointer that bounces reads as broken where
one that trails only reads as slow.

The arithmetic moved out of the session into `ru.voidrp.ui.input.Pointer`, where it can be
— and is — tested against a hand that sweeps, flicks, eases, arcs, creeps onto a small
button, and merely rests on the mouse.

**Also**
- The plugin no longer says it has no PacketEvents on a server that has it. The listeners
  were installed while the plugin was being constructed, which is before its dependencies
  are enabled; they are installed when it starts now, and `softdepend` names PacketEvents
  so it is loaded first.
- `/vui debug trace <seconds>` writes `cursor-trace.csv` — the moment, the aim, the
  reckoning and the drawn position, every frame. "The mouse feels bad" cannot be acted on;
  four columns can.

## 0.3.2

**The pointer is drawn where the player will be looking, not where they were.** Every link
in the chain costs time: the client reports its aim twenty times a second (25 ms on average
before a turn is even sent), the packet takes half a round trip, our frame takes up to one
frame to go out, and the answer takes the other half of the round trip to be drawn. Drawn
at the last reading, a pointer lags by all of it at once. It is now carried forward by that
whole chain, measured — the round trip comes from the player's own ping — so for a hand
moving steadily the lag cancels out.

- A reading on the other side of where the tracker was heading means the speed it believed
  in was wrong rather than short. It is dropped instead of carried on, which is what used
  to sail the pointer past the thing it was aimed at.
- How far the reckoning may run ahead of the last reading was a flat thirty units, which
  held a fast sweep back and let a slow one drift. It is what the speed covers in the gap
  between readings now.
- The smoothing over the top is lighter (0.45 → 0.72): with the prediction under it there
  is less jitter left to hide, and hiding it was costing another thirty milliseconds.
- The pointer is drawn 85 times a second rather than 62, configurable with
  `input.frame-rate`.
- `/vui debug cursor` prints the numbers behind all of this: ping, the lead it works out
  from it, how long ago the last reading landed, and the speed the tracker believes in.

## 0.3.1

**Fixes a page that came out as a dark rectangle with nothing in it.** The invisible
characters that move the pen had been put in the Syriac block, which holds a format
character, a combining mark and an unassigned code point within twenty of its start. A
renderer drops or zero-widths all three, so the pen stopped moving part way through a page
and everything after the first few shapes was thrown off the screen. They live among plain
letters now, and a test holds every spacer to being one.

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
