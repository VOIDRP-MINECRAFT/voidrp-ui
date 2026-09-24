# How it works inside

For anyone who wants to understand the trick or repeat it. The rakes are listed too — each
one cost an evening, and nearly all of them look like "nothing is drawn" or "the page has
slid sideways".

## The transport: a line in a boss bar

A page is **one line of text** in the title of an invisible boss bar. The choice is not
arbitrary:

- A boss bar's title is a text component — an ordinary `Component`, with no hand-written
  packets and nothing tied to a protocol version.
- Client shader packs (Iris, OptiFine) replace world rendering but leave the interface —
  boss bar, chat, scoreboard — on the vanilla core shaders. A display entity would give
  3D, but shader packs hide it.
- The bar itself is hidden with transparent `boss_bar/white_background.png` and
  `white_progress.png`; the white bar colour is reserved for the interface.

### Several bars, not one

A title is replaced whole, so everything on a bar travels again whenever any of it
changes. A page is therefore spread over **two bars**; the third carries the highlight
and tooltip for what the pointer is over, and the fourth the pointer itself.

The highlight and tooltip used to ride the pointer's bar, so every frame of movement over a
shop row sent them again: 3.4 KB, 85 times a second, where the pointer alone is 130 bytes.
On their own bar they go when the pointer crosses onto something else, and the tooltip
stays where it appeared instead of following the pointer.

The page is split like this:

- The layout marks where a scrolling list and whatever stands above the page (a menu)
  begin and end in the list of shapes. The page is cut there, so a scroll sends the list
  again and not the rest of it — for the shop that is 11 KB instead of 17 (the list and the
  footer under it, which share a bar).
- Pieces are always runs of the page in its own order. Bars are drawn one after another,
  so a piece stitched together from two places would put something on top of what it was
  meant to be under.
- Bars left over halve the heaviest piece, which halves what a hover there costs.
- A piece that comes out the same as last time is not sent.

Why four: the client stops drawing boss bars at a third of the screen's height, each takes
19 GUI units starting at 12, and no GUI scale leaves the screen shorter than 240 units — so
bars at 12, 31, 50 and 69 always fit and a fifth does not.

Every bar below the first draws its line 19 units lower, so its shapes are carried 19 units
higher per bar above it. Near the top of the screen that goes below zero, where 10 bits of y
cannot follow — so a second pair of markers, `0xD` (and `0xE` for drifting specks), means
"take 64 off the y you read". Before it, the pointer could not be drawn over the top 19
units of the screen.

## The shader

The pack carries a replaced text vertex shader. It looks at the vertex colour: if the high
nibble of red is the marker `0xB` (or `0xC`…`0xE`, see above), this glyph is ours, and the shader moves it to the
position encoded in the remaining bits.

Two file layouts, both needed:

| version | file | GLSL |
|---|---|---|
| 1.21.6 … 26.1.2 | `shaders/core/rendertype_text.vsh` | 150 |
| 26.2+ | `shaders/core/text.vsh` | 330, variants behind `#define IS_GUI / IS_SEE_THROUGH / IS_GRAYSCALE` |

They ship as two packs, chosen by the client's version.

### What the shader sees per vertex

`Position` (screen coordinates for the interface), `Color`, `UV0` (which glyph in the
atlas), `UV2` (light). The position is `ProjMat * ModelViewMat * Position`, and it can be
overridden by computing it from the colour.

### The channels

| what | where it travels |
|---|---|
| x position | the width of the spacers before the glyph — laid out by the **client** |
| y position | 10 bits in the glyph's colour |
| fill colour | 10 bits in the glyph's colour (RGB 3-4-3) |
| shape and size | which glyph — an alphabet of shapes is baked into the font |
| opacity | which font — the alphabet is baked sixteen times, one per step |

**Disproved:** `shadow_color` does not give a second channel on the same glyph. The shadow
is drawn as separate vertices, and the shader handles each one independently and cannot
tie it to the original. So "48 bits per element" is not possible.

## Horizontal layout

The font has invisible spacer glyphs in steps of ±1, ±2, ±4 … ±512 (the `space` provider).
The pen is moved with them to the pixel, and x is laid out by the client — the server only
predicts where the pen will end up.

**The line is brought back to zero width**: a spacer at the end returns the pen to 0. The
boss bar centres its title, and a zero-width line starts exactly at the centre of the
screen. The shader reads x back out of the final NDC: `penX = ndc.x / ProjMat[0][0]`.

Hence the rule that governs everything: **any error in predicting a width moves the whole
page by half of that error**. Nearly every "the page has slid" comes from here.

## The canvas

1024 units tall, always the height of the window. It is 1024 because the position travels
in 10 bits: 1024 steps over 1024 units is exactly one unit per step. At a height of 1080
the step was 1.0557 units, and two pieces of one panel could land a fraction of a pixel
apart — invisible while opaque, a bright seam or a hairline gap as soon as anything was
translucent.

The width is not fixed: a unit is square, so how many fit across is the shape of the
player's window. See [responsive](responsive.md).

## Rakes

All of them verified live, on a 26.2 client (NVIDIA).

**The shader fails to compile and the client silently rejects the whole pack.** How to
tell: the pack status in `PlayerResourcePackStatusEvent`; `FAILED_RELOAD` means downloaded
and refused.

- `packed` is a **reserved word in GLSL**. The NVIDIA driver rejects the whole shader over
  it while glslang lets it pass. Do not name variables after reserved words.
- Kotlin's `trimIndent()` does not strip the indentation when the template contains an
  interpolation at zero indent — `#version` ends up away from the start of the line. Splice
  the shared block in by a marker, after `trimIndent()`.

**Compute the position straight in NDC, around the matrices.** A boss bar carries its own
offset in `ModelViewMat`, and going through the matrices throws the quad off the screen. On
26.2 the bar's centring is baked into the vertices outright.

**Take the corner of the quad from `gl_VertexID % 4`, not from `UV0`.** Glyphs live in a
shared font atlas, and their UVs are a tiny piece of it.

**The marker must not collide with an ordinary colour.** The first marker, `0xA`, collided
with grey `#AAAAAA`: grey words in chat were taken for elements and flew across the screen
as white shapes. The 16 standard colours have red 00/55/AA/FF — nibbles 0, 5, A, F — so
`0xB` is free.

**A glyph texture must not be longer than ~128–256 pixels on a side.** The client puts it
into the font atlas at its own resolution, and a 512×1 ribbon (which a one-pixel border
wants) does not fit: the glyph silently disappears, its width is not what the encoder
predicted, and the page slides. Break shapes up so that the sides do not differ by more
than 2⁷.

**A glyph taller than ~512 units is not drawn at all.** A full-screen fill made of
1024×1024 pieces never appeared.

**A rectangle has to be split on both axes at once.** Rows first and columns after leaves a
4×1024 column at the edge — proportions the alphabet does not have.

**Put the `space` provider first in the font's JSON** — an earlier provider wins (vanilla's
`default.json` is built the same way). Otherwise the space comes from the empty glyph in
`ascii.png` with a width of 1: words run together and the pen parts company with the
client.

**Write the font JSON in UTF-8 rather than escaping everything as `\uXXXX`.**
`nonlatin_european.png` contains characters outside the BMP (Gothic, 0x10330), and a
five-digit escape breaks the table row — not a single letter is found and all the text
turns into squares.

**The client moves the pen by the glyph's ink plus one pixel**, not by the typographic
advance. So:
- both values are measured when the font is baked, and the difference is made up with
  spacers;
- every letter is drawn **in its own cell**: drawn straight onto a shared sheet, a letter
  that overhangs to the left (`j` in Inter) reaches into its neighbour's cell, the client
  measures that neighbour wider, and a gap appears after every `i`;
- item icons have the same story: the texture is 16×16 and the drawing inside does not fill
  it (a diamond is 14, a door 13). The widths were measured once and live as a table of
  numbers.

**The shader does not recognise a glyph's shadow** (separate vertices, a darkened colour) —
turn it off with `shadowColor(none())` or it stays hanging where the glyph was laid out.

## Shadows and glows

There is no blur here and there cannot be: the client can only place a ready-made picture.
So a soft halo is **baked** — four corner tiles and one tile per side, with a quadratic
falloff, for every blur step and every opacity step. A shadow and a glow are the same tile;
only the colour and the offset differ.

Two things came out of that which you would not expect.

**Measure the very picture you ship.** A glyph's width to the client is ink plus one pixel,
and a halo's edge fades to nothing gradually: exactly where the "visible" pixel ends is not
something to guess by eye. While the pack generator and the encoder each computed the width
their own way, pages slid. Now one piece of code draws the tiles (`pack/Glow.kt`,
`pack/Corners.kt`) and reports the width — taken from the finished image, at the rightmost
non-transparent column.

**Side tiles are mirrored, and it matters.** The left side of a halo is not the same
picture as the right: the falloff runs the other way and the ink ends at a different
column. While both sides took one glyph, a page came out 3 to 38 units short depending on
how many shadows it happened to have. Each side now has its own baked variant — the one
nearest the corner and the one furthest from it.

**A rounded corner is a ring, not a fill.** A border drawn with a filled corner is blended
under a translucent fill a second time, and light brackets light up at the panel's corners.
Border corners are baked as a separate ring alphabet.

The test `each thing a style can add balances on its own` turns on one style property at a
time — shadow, glow, highlight, border — and checks each one brings the line back to zero.
That is what pointed at the shadow when a page slid.

## Colour

Ten bits are given to colour: three red, four green, three blue. Rounding each channel on
its own is the obvious way into that grid and the wrong one at its dark end, where the step
is bigger than the colour itself. `#141033` has twenty of red and fifty-one of blue; round
each and red rises to 36, blue falls to 36, and a dark violet becomes a warm grey. The
numbers could not be closer, and the colour is gone.

So the choice is made over the colour as a whole, in Oklab, where plain distance agrees
with what the eye reports (`style/Palette.kt`). That does not save you where the shade is
simply not in the palette — and at the dark end it almost never is.

**There are two axes, not one.** Opacity has sixteen steps, and the eye does not see the
colour but its mix with whatever is underneath. When the backdrop is known, the reachable
results become 1024 × 16, and near a background of the same family they lie densely.
`Palette.express(target, over)` searches them and returns a colour and an opacity whose
result is nearest to what was asked for: the miss falls from twenty units to one or two.

The theme builds its surfaces in a chain from the bottom up: black hides the world, a tint
over it makes the ground the colour of the theme, and each further surface is expressed
over what the previous one **actually** came out as (`Palette.composite`). Which is why
`theme.yml` now means what it says.

## Gradients

A glyph has one colour, so a gradient is always stripes. The only question is which shades
are available for them.

**Not knowing the backdrop** there are few: along the line from violet to the page's dark
background the palette holds three or four, and the wash comes out in slabs. Fading by
opacity gives sixteen steps of one colour — a stripe every ten units of blue; dithering
between two such steps turns slabs into corduroy, because a jump of ten is not something
the eye blends. Both were tried on the welcome panel, and both are visible.

**Knowing the backdrop** (`Gradient(over = …)`) changes everything: a flat floor is laid
first in the colour of the middle of the fade, and each stripe is then expressed over that
floor — about forty reachable shades along the same line, with a step of one or two units.
Where two candidates are almost equal the one nearer the previous stripe wins: otherwise
neighbours pick pairs on opposite sides of the target and the lean shows up as a seam.
Identical stripes merge, so the whole wash is a few dozen rectangles.

`start` and `stop` say where the fade begins and ends, like colour-stop positions in CSS,
and `Style.overlay` lays a second wash over the first — light rarely falls away in one
direction only.

## Input

The cursor is the player's aim converted into canvas coordinates. The findings that matter:

- **Do not put the aim back every tick.** The client goes on sending its own rotation and
  the two of you pull at the blanket: the screen shakes. The cursor's position has to be a
  pure function of the current aim.
- **Pitch runs out at 90°.** Open a page while looking at your feet and the room to move
  down is already spent. Level the aim once, when the page opens — one packet, no fight.
- **The client reports its rotation 20 times a second — at best.** That is the ceiling on
  *knowing* the position, but not on drawing it: between readings the cursor is reckoned
  forward. Measured on a live client they arrived 66 to 110 ms apart, not the 50 the
  protocol suggests, so the tracker measures the gap rather than assuming it.
- **A hand at rest is silence, not a reading.** A client sends its aim only when the aim has
  changed. Anything that waits to be *told* the hand has stopped waits for ever: a tracker
  has to treat an overdue reading as the news. Getting this wrong left the pointer resting
  twelve units from the button it was pointed at, and that is not visible in any simulation
  that feeds it a steady stream of readings.
- **What matters is not only arriving, but how the frames of a gap divide the distance.**
  A reading says the hand covered so much ground since the last one; drawing that distance
  over that time, at one pace, is what makes a pointer move rather than twitch. A filter
  that corrects a fraction of its error per reading lunges instead: 45% of a gap's travel
  in its first frame, five times a second. Measured, not guessed — see
  `ru.voidrp.ui.input.Pointer` and the recorded hand it is tested against.
- **Never add the lead as a distance.** An offset on the position has to appear and
  disappear, and both show. Fold it into the pace instead: aim to arrive early.
- **`/vui debug trace <seconds>`** writes every frame of the pointer to a CSV: the aim, the
  reckoning and what was drawn. "The mouse feels bad" cannot be acted on; four columns can.
- **The game does not report a button press — it reports a swing of the arm.** Held down,
  the swing arrives **every tick, exactly 50 ms apart**; clicked by hand, no closer than
  140 ms. The ranges do not overlap, so a press is the first swing after a pause of 80 ms.
- Hovering and clicks are resolved **on the server** against the layout it just produced.
  The client is neither asked nor trusted.
- Text input uses the game's own dialogs (1.21.6+, `DialogInput.text`). The page stays on
  screen and the dialog borrows the mouse for a moment.
