#!/usr/bin/env python3
"""Re-bakes the interface icon set from its 64-pixel masters.

Lucide draws on a 24-unit grid with a 2-unit stroke. Scaled to 20 or 16 pixels that
stroke lands on fractions of a pixel, and a smooth resample turns it into a grey smear —
which reads as a crooked icon rather than a soft one, because the eye is looking at a
straight line and getting two half-lit pixels.

So small sizes are resampled and then made binary: a pixel is either the stroke or it is
not. It is what the game does with its own interface, and it is legible at 12 pixels.
Bigger sizes keep their partial pixels, where a diagonal needs them.

    python3 tools/rebake-icons.py            # rewrites src/main/resources/icons/ui
    python3 tools/rebake-icons.py --check    # says what would change, writes nothing
"""

import sys
from pathlib import Path

from PIL import Image, ImageChops

ICONS = Path(__file__).resolve().parent.parent / "src/main/resources/icons/ui"
SIZES = [12, 16, 20, 24, 32, 48]
CRISP_UP_TO = 24


def symmetric(master: Image.Image) -> bool:
    """Whether this icon is drawn the same on both sides of its middle.

    Most of them are — a house, a gear, a shield — and for those the resample is the only
    thing that makes one slope of a roof a pixel thicker than the other. Averaging with the
    mirror image before the threshold takes that away. For the ones that are not symmetric,
    a cart or an arrow, the same trick folds the icon in half and ruins it, so it is asked
    rather than assumed.
    """
    alpha = master.split()[3]
    mirrored = alpha.transpose(Image.FLIP_LEFT_RIGHT)
    difference = ImageChops.difference(alpha, mirrored)
    pixels = list(difference.getdata())
    return sum(pixels) / len(pixels) < 4.0


def bake(master: Image.Image, size: int, mirror: bool) -> Image.Image:
    out = master.resize((size, size), Image.LANCZOS)
    alpha = out.split()[3]
    if mirror:
        # Half of each side, added together: the same picture, with both halves agreeing
        # on where the ink is.
        flipped = alpha.transpose(Image.FLIP_LEFT_RIGHT)
        alpha = ImageChops.add(alpha.point(lambda v: v // 2), flipped.point(lambda v: v // 2))
    if size <= CRISP_UP_TO:
        alpha = alpha.point(lambda v: 255 if v >= 128 else 0)
    else:
        alpha = alpha.point(lambda v: max(0, min(255, int((v - 24) * 1.35))))
    out.putalpha(alpha)
    return out


def main() -> int:
    check = "--check" in sys.argv
    masters = sorted(ICONS.glob("*_64.png"))
    if not masters:
        print("no masters in", ICONS)
        return 1
    changed = 0
    for master_path in masters:
        name = master_path.name[: -len("_64.png")]
        master = Image.open(master_path).convert("RGBA")
        mirror = symmetric(master)
        for size in SIZES:
            target = ICONS / f"{name}_{size}.png"
            baked = bake(master, size, mirror)
            before = Image.open(target).convert("RGBA").tobytes() if target.is_file() else None
            if baked.tobytes() != before:
                changed += 1
                if not check:
                    baked.save(target)
    print(f"{'would change' if check else 'rewrote'} {changed} of {len(masters) * len(SIZES)} files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
