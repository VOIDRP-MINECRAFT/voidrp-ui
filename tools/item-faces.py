#!/usr/bin/env python3
"""Which texture stands for each item in the inventory — worked out from the client's models.

The pack carries none of Mojang's artwork: an icon is a glyph that points at a texture the
client already has. For most items that texture is simply item/<name>. For a block it is
not — ancient_debris has no ancient_debris.png, only _side and _top; a glass pane's icon is
block/glass, not the thin edge in glass_pane_top. Guessing from file names gets those wrong,
so this reads what the client itself would draw: the item definition, its model, the
model's parents, and the texture the model puts on the face you see.

Only names come out of it. The client jar is read on the machine that builds the pack and
nothing of it is copied.

    python3 tools/item-faces.py ~/.minecraft/versions/26.2/26.2.jar \\
        > src/main/resources/icons/item_faces.txt

Each line is `<item> <texture> [#rrggbb]`, written only where the plain lookup would get
it wrong: the texture is not the one named after the item, or the client tints it. Leaves,
vines and lily pads are grey in the texture and green only because the client paints them;
the colour is read from the item's own definition — a constant, or the grass and foliage
colour maps at the temperature it asks for — and only the number is kept. A face is tinted
only if the model says so: a grass block's side already has its green, only the top is
painted, so the side is drawn as it is.
"""
import json
import sys
import zipfile

# The face a flat picture of a block should show, best first. `front` is the face with the
# recognisable part — a furnace's mouth, a crafting table's tools; `side` is what a column
# shows round its middle; `all` and `texture` are single-texture blocks.
FACE_PREFERENCE = ["front", "side", "all", "texture", "north", "south", "east", "west",
                   "wall", "pattern", "cross", "plant", "top", "end", "up", "particle"]


def strip(ref: str) -> str:
    return ref.split(":", 1)[-1]


class Models:
    def __init__(self, jar: zipfile.ZipFile):
        self.jar = jar
        self.cache: dict[str, dict] = {}

    def load(self, model_id: str) -> dict:
        key = strip(model_id)
        if key not in self.cache:
            try:
                self.cache[key] = json.loads(self.jar.read(f"assets/minecraft/models/{key}.json"))
            except KeyError:
                self.cache[key] = {}
        return self.cache[key]

    def textures(self, model_id: str) -> tuple[dict, list[str]]:
        """Every texture variable the model ends up with, and the chain of models behind it."""
        chain = []
        merged: dict[str, str] = {}
        current = model_id
        for _ in range(16):
            model = self.load(current)
            chain.append(strip(current))
            for name, value in model.get("textures", {}).items():
                merged.setdefault(name, value)      # the child's own wins
            parent = model.get("parent")
            if not parent:
                break
            current = parent
        # Resolve #references: "side": "#all"
        for _ in range(8):
            changed = False
            for name, value in merged.items():
                if isinstance(value, str) and value.startswith("#"):
                    target = merged.get(value[1:])
                    if target and target != value:
                        merged[name] = target
                        changed = True
            if not changed:
                break
        return merged, chain


def colormap(jar: zipfile.ZipFile, name: str, temperature: float, downfall: float) -> int | None:
    """What the client paints grass or foliage at a temperature and rainfall."""
    try:
        import io
        from PIL import Image
        image = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/colormap/{name}.png"))).convert("RGB")
    except Exception:
        return None
    t = min(max(temperature, 0.0), 1.0)
    d = min(max(downfall, 0.0), 1.0) * t
    x = int((1.0 - t) * 255)
    y = int((1.0 - d) * 255)
    r, g, b = image.getpixel((x, y))
    return (r << 16) | (g << 8) | b


def tint_colour(jar: zipfile.ZipFile, tint: dict) -> int | None:
    kind = strip(tint.get("type", ""))
    if kind == "constant":
        return tint.get("value", 0) & 0xFFFFFF
    if kind in ("grass", "foliage", "dry_foliage"):
        mapped = colormap(jar, kind, tint.get("temperature", 0.5), tint.get("downfall", 1.0))
        if mapped is not None:
            return mapped
    default = tint.get("default")
    return default & 0xFFFFFF if isinstance(default, int) else None


def item_model(definition: dict):
    """The model an item definition draws in the inventory, and whether it is tinted."""
    node = definition.get("model", {})
    for _ in range(8):
        kind = strip(node.get("type", ""))
        if kind == "model":
            return node.get("model"), node.get("tints") or []
        # select / condition / range_dispatch: the inventory shows the plain case.
        for key in ("fallback", "on_false", "base"):
            if isinstance(node.get(key), dict):
                node = node[key]
                break
        else:
            cases = node.get("cases") or node.get("entries") or []
            if cases and isinstance(cases[0], dict) and isinstance(cases[0].get("model"), dict):
                node = cases[0]["model"]
            else:
                return None, []
    return None, []


def elements(models: "Models", chain: list[str]) -> list:
    for model_id in chain:
        found = models.load(model_id).get("elements")
        if found:
            return found
    return []


def face_is_tinted(models: "Models", chain: list[str], textures: dict, texture: str) -> bool:
    """Whether the face that shows this texture is one the client paints."""
    if "item/generated" in chain or "item/handheld" in chain:
        return True     # layer0 is tint index 0
    for element in elements(models, chain):
        for face in element.get("faces", {}).values():
            ref = face.get("texture", "")
            resolved = textures.get(ref[1:], ref) if ref.startswith("#") else ref
            if isinstance(resolved, str) and strip(resolved) == texture and "tintindex" in face:
                return True
    return False


def pick(textures: dict, chain: list[str], item: str = "") -> str | None:
    if "item/generated" in chain or "item/handheld" in chain:
        layer = textures.get("layer0")
        return strip(layer) if isinstance(layer, str) and not layer.startswith("#") else None
    # A texture named after the item is the one that says what it is: a beacon's model also
    # carries glass and obsidian, and the glass is what its particle falls back to, which
    # drew a beacon as an empty pane.
    for value in textures.values():
        if isinstance(value, str) and strip(value) in (f"block/{item}", f"item/{item}"):
            return strip(value)
    for key in FACE_PREFERENCE:
        value = textures.get(key)
        if isinstance(value, str) and not value.startswith("#"):
            return strip(value)
    return None


def main() -> None:
    jar = zipfile.ZipFile(sys.argv[1])
    models = Models(jar)
    known = set()
    for line in open(sys.argv[2] if len(sys.argv) > 2 else "src/main/resources/icons/vanilla_items.txt"):
        if line.strip():
            known.add(line.split()[0])

    prefix = "assets/minecraft/items/"
    out = []
    for path in sorted(jar.namelist()):
        if not path.startswith(prefix) or not path.endswith(".json"):
            continue
        item = path[len(prefix):-len(".json")]
        model_id, tints = item_model(json.loads(jar.read(path)))
        if not model_id:
            continue
        textures, chain = models.textures(model_id)
        texture = pick(textures, chain, item)
        # A texture the client cannot give us as one flat 16×16 glyph is no better than
        # the placeholder: animated, oversized, or simply not a picture we measured.
        if not texture or texture not in known:
            continue
        tint = None
        if tints and face_is_tinted(models, chain, textures, texture):
            tint = tint_colour(jar, tints[0])
        # What the plain lookup would find without this file: item/<name>, then block/<name>.
        plain = f"item/{item}" if f"item/{item}" in known else (f"block/{item}" if f"block/{item}" in known else None)
        if texture == plain and tint is None:
            continue
        out.append(f"{item} {texture}" + (f" #{tint:06x}" if tint is not None else ""))

    print("\n".join(out))
    print(f"{len(out)} items the plain lookup would draw wrongly or not at all", file=sys.stderr)


if __name__ == "__main__":
    main()
