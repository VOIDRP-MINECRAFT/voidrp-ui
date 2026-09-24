#!/usr/bin/env python3
"""Item pictures the oldest supported client does not have.

The pack names every icon texture of the newest client, and a client that is missing one of
those files does not just go without that one picture: 1.21.6, given a font where a few
providers point at textures it has never heard of, drew every glyph of that font — the
icons and the spacers that place them — as the missing-glyph box, and each box moved the
pen by the wrong amount, so the rest of the page slid sideways. The legacy pack leaves
those providers out and puts a space of the same width in their place.

    python3 tools/legacy-absent.py ~/.minecraft/versions/1.21.6/1.21.6.jar \\
        > src/main/resources/icons/absent_legacy.txt
"""
import sys
import zipfile

old = {n for n in zipfile.ZipFile(sys.argv[1]).namelist() if n.startswith("assets/minecraft/textures/")}
table = [line.split()[0] for line in open("src/main/resources/icons/vanilla_items.txt") if line.strip()]
absent = [t for t in table if f"assets/minecraft/textures/{t}.png" not in old]
print("# Icon textures the oldest supported client (1.21.6) does not have. tools/legacy-absent.py")
print("\n".join(absent))
print(f"{len(absent)} of {len(table)} icons absent", file=sys.stderr)
