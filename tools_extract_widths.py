import json
from PIL import Image

NUL = chr(0)
d = json.load(open('assets/minecraft/font/include/default.json'))
widths = {}
providers = []
for p in d['providers']:
    if p['file'] not in ('minecraft:font/ascii.png', 'minecraft:font/nonlatin_european.png', 'minecraft:font/accented.png'):
        continue
    im = Image.open('assets/minecraft/textures/' + p['file'].split(':')[1]).convert('RGBA')
    rows = p['chars']
    cw = im.size[0] // len(rows[0])
    ch = im.size[1] // len(rows)
    for r, row in enumerate(rows):
        for c, char in enumerate(row):
            if char in (NUL, ' ') or char in widths:
                continue
            # Minecraft: glyph width = rightmost non-transparent column + 1
            width = 0
            for x in range(cw - 1, -1, -1):
                if any(im.getpixel((c * cw + x, r * ch + y))[3] != 0 for y in range(ch)):
                    width = x + 1
                    break
            widths[char] = width
    providers.append({'file': p['file'], 'ascent': p['ascent'], 'height': p.get('height', 8), 'cell_height': ch, 'chars': rows})

out = {'cell': 8, 'space': 4, 'providers': providers, 'widths': widths}
json.dump(out, open('vanilla_glyphs.json', 'w', encoding='utf8'), ensure_ascii=True)

ru = ''.join(chr(c) for c in range(0x0410, 0x0450)) + chr(0x0401) + chr(0x0451)
missing = [c for c in ru if c not in widths]
print('symbols with width:', len(widths))
print('cyrillic missing:', missing or 'none')
print('samples:', {c: widths[c] for c in 'AiIl!Wm.' if c in widths},
      {hex(ord(c)): widths[c] for c in (chr(0x041F), chr(0x0440), chr(0x0448)) if c in widths})
