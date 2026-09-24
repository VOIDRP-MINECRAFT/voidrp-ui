# Changelog

Versions follow [semver](https://semver.org/). While the major is zero, breaking changes
arrive with a minor bump and are named here outright.

## 0.3.5

**A page is sent again only when it has changed.** The whole page travels as one boss bar
title — ninety kilobytes for a rich one — and it used to go out every time a page asked,
whether or not anything on it was different. The description a page gives is immutable
data, so it is compared with the last one first: finding out that nothing changed costs
0.08 ms against 2.3 ms to draw the home page again, and the ninety kilobytes stay put. A
test holds every page that ships to describing itself identically when it has not changed,
since a lambda or a timestamp in the tree would quietly defeat the whole thing.

**Several changes in one tick are drawn once.** A wheel spun hard puts several notches into
a single tick; the first is drawn at once and the rest fold into one more draw on the next.

`/vui debug cursor` reports how many times the page was really sent, how many asks came out
the same and how many were folded — the numbers behind both of those.

**Found by driving a real client, and fixed:**

- **Two rows lit at once while scrolling.** A page was described with what the pointer was
  over *before* the change, so a list scrolling under a still pointer lit the row that had
  just left it, while the pointer's own highlight marked the row that had arrived. When a
  change moves things under the pointer, the page is now asked once more with the answer
  the new layout gives.
- **A list that stuck after being spun past its end.** The shop stopped its offset at the
  top and let it run on past the bottom; the picture stopped moving, the number did not,
  and turning the wheel back did nothing for as many notches as were spent past the end.
- **Cards cut through the middle.** The wheel moved a list 48 units, rows are 76 tall, so
  it came to rest between rows — and since a glyph is drawn whole or not at all, the top
  card showed its description and the word "coins" with no title and no price.

All three are handled by `scrolled()`, which moves a list a row at a time and never past
either end, and which `docs/layout.md` now recommends for every list.

**Blocks have pictures.** Ancient debris was an empty square in the shop, because there is
no `ancient_debris.png` — only a side and a top. So were 103 other blocks, furnaces and
crafting tables among them. Which face stands for an item is now read from the client's own
models by `tools/item-faces.py` rather than guessed from file names, which would have given
a glass pane its thin edge instead of the glass. Only names and numbers come out of it.

**What the client paints is painted.** Leaves, vines, ferns and lily pads are grey in their
textures and green only because the client colours them; drawn white they were grey noise.
The colour comes from the item's own definition — a constant, or the grass and foliage maps
at the temperature it names — and rides the glyph's colour. A face is painted only if the
model paints it: a grass block's side already has its green.

432 items in all, which the plain lookup drew wrongly or not at all. A model that carries a
texture named after its item keeps it: a beacon is its core, not the glass its particle
falls back to.

**Clients older than 26.2 get their interface.** Tried at last on a real 1.21.6 client —
through ViaVersion, which is how most servers see one — and it had never worked:

- **Every item picture was the missing-glyph box, and the page slid sideways after it.** The
  pack named every item texture of 26.2, and 1.21.6, short 193 of them, did not just go
  without those: it drew every glyph of the icon font — the diamond, and the spacers that
  place each icon — as the box, and each box moved the pen by the wrong amount. The older
  pack now leaves those textures out and puts a space of the same width in each one's
  place: a brand-new item is simply not drawn, and nothing around it moves.
  `tools/legacy-absent.py` writes the list from the oldest supported client.
- **With `pack.legacy: false`, old clients were sent to a 404.** The built-in server handed
  out the address of a pack it had not built. There is no address now when there is no pack.

The screen setup frame sat exactly on the edges of the 1.21.6 window, which is what showed
the placement itself was right and the fault was in the fonts.

**Other plugins' boss bars stay out of the page.** With an event timer showing, the page kept
its place — the bar was replayed below ours — but a bar below ours is a line further down
the screen, and its title came out across the middle of the page. Now those bars are held
back while a page is open, the way the game's own menus cover the HUD: every packet for
them is kept, and when the last page closes they come back as they are by then. On a live
client, a timer renamed from "10 min left" to "2 min left" while the page was open came back
reading "2 min left". Going from one page to the next does not flash them in between.

Changes to other plugins' bars are tracked even when nothing is held back, so a replayed
bar is no longer a picture of how it looked when it was first sent.

**The background moves out of the box.** `effects.particles` shipped off in case a client
refused the shader it needs. Tried on real clients at both ends of the supported range —
26.2 and 1.21.6 — the pack loads on both and the specks drift on both, so it is on now.

- **A speck drawn as a line from the top of the screen to the bottom.** Everything that moves
  a speck was worked out per corner of its glyph: the wrap at the bottom of the screen took
  the top corners round before the bottom ones, and the seed came from each corner's own
  x, so the left and right edges drifted at different paces and specks became slanted
  streaks. Both come from the glyph's own row now, which all four corners share, and a
  speck moves as the square it is.

**No frame down the sides of a wide screen.** The strips painted past the canvas — so that a
window not quite the named shape shows no world at its edges — left out the screen's
second wash, and on a 21:9 window they came out darker than the page by a visible step
(6,7,15 against 9,11,27). `Page.bleedOf(style)` takes every layer off a style; all three
pages that ship use it, and the edges now differ from the page by one level in one channel.

**A text field opens empty.** The demo kept its placeholder in the same variable as the
value, so the game's dialog opened with "press to type" already typed and the player had to
delete it before writing anything. The placeholder is only drawn now, dimmed, while the
field is empty — and `prompt`'s documentation says `initial` is the value so far, since
this is the page people copy.

## 0.3.4

**The pointer walks; it no longer pounces.** A recording of a real hand — ten seconds of a
player moving the mouse fast and slow, kept as a fixture in `src/test/resources/hand.csv` —
showed what every version before this got wrong, and it was not the lag any of them were
tuned against:

- **45% of a gap's whole movement happened in its first frame**, where an even walk puts
  20%. A tracker corrects a fraction of its error per reading, so it lunges when one lands
  and coasts afterwards. Five times a second, that is a pointer that twitches rather than
  moves, and no smoothing on top could fix it: smoothing hid the twitch by adding lag, and
  the twitch came back the moment the lag was taken out.
- **The lead collapsed and came back twenty times a second.** It was a distance added to
  the position, worked out from the speed the filter believed in — and that speed jumped
  when a reading landed and fell to nothing when the filter ran out of distance. Thirty-two
  units, on and off, on top of everything else.

What a reading really says is that the hand covered a distance in the time since the last
one. So that distance is now drawn over that time, at one pace: every frame of a gap is the
same size, and there is nothing left to lunge. The lead is folded into the pace — the walk
aims to arrive half a round trip early, rather than being shoved forward by a number that
has to appear and disappear. Nothing is extrapolated past the last reading any more, which
means there is no speed to be wrong about, nothing to sail past a target with, and a stop is
simply a walk that has finished.

Measured on the recorded hand, this build against the one before it: the first frame of a
gap takes **25%** of its travel instead of 45%, and the worst jump between two frames is
**133 units instead of 308**. The pointer sits 104 units from the hand's own path rather
than 88 — that is the price, and it is a dial:

```yaml
input:
  smoothing: 1.5   # gaps allowed for the walk. 1.0 = closest to the hand, 2.0 = most even
```

`/vui debug smooth <n>` turns it with a page open, which is the only way to judge it. On a
slow connection the lead may spend only the slack the walk was given, never the walk itself
— otherwise a long round trip would eat the whole budget and put the jump straight back.

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
