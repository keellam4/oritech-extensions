"""
Builds the extra paint palettes that Oritech itself doesn't have, from the main ones (generate_palettes.py):

  pipe_key.png / pipe_<colour>.png
      Oritech's item pipes (and transparent item pipes) are copper-coloured, a different set of shades from the
      orange used on machines, so the main palette doesn't touch them. Each copper shade gets the colour its
      nearest machine shade turns into, adjusted to keep the copper shade's brightness.

  extender_key.png / extender_<colour>.png
      Machine Addon Extender and Capacitor Addon Extender. Same as the main palette, plus their red trim
      (the corner brackets / edge trim) recoloured to an accent that suits each paint (ACCENTS below), and
      any leftover orange shades the main palette misses.

  structure_key.png / structure_<colour>.png
      Industrial Glass frames, reactor parts (rods, vents, ports, heat pipes) and particle accelerator parts. Works like the pipe palette:
      every trim shade follows the paint. Status colours stay as they are (pure red redstone indicators,
      pale light strips, bright yellow markers), so a painted reactor still reads the same.
      Reactor walls only use main-palette shades, so they need no palette of their own.

Usage: python3 -P derive_palettes.py <oritech textures/block dir or oritech.jar> <output assets/oritech_extensions dir>
"""
import colorsys, io, os, sys, zipfile
from PIL import Image

VARIANTS = ['camo', 'diamond', 'fluxite', 'white', 'industrial', 'netherite', 'redstone', 'sculk']

# Accent for the extenders' red trim, per paint: picked to contrast with (but suit) the paint colour.
ACCENTS = {
    'camo': (201, 176, 112),       # khaki
    'diamond': (40, 74, 168),      # deep blue (Oritech's code says DIAMOND; the paint is called "Adamant" in game)
    'fluxite': (122, 64, 190),     # violet
    'white': (78, 116, 158),       # steel blue
    'industrial': (52, 52, 58),    # charcoal (hazard stripes)
    'netherite': (226, 178, 58),   # gold
    'redstone': (176, 176, 182),   # stone grey
    'sculk': (38, 214, 224),       # sculk glow cyan
}

PIPE_TEXTURES = ['item_pipe', 'transparent_item_pipe']
EXTENDER_TEXTURES = ['machine_extender', 'machine_extender_off', 'capacitor_ctm/center', 'capacitor_ctm/horizontal',
                     'capacitor_ctm/vertical', 'capacitor_ctm/particle']
GLASS_TEXTURES = [f'industrial_glass_block/{part}' for part in ('center', 'empty', 'horizontal', 'vertical', 'particle')]
REACTOR_PART_TEXTURES = ['reactor_absorber_port', 'reactor_condenser', 'reactor_controller', 'reactor_energy_port',
                         'reactor_fuel_port', 'reactor_redstone_port', 'reactor_reflector', 'reactor_rod', 'reactor_vent']
ACCELERATOR_TEXTURES = ['accelerator_controller', 'accelerator_motor', 'accelerator_ring', 'accelerator_sensor',
                        'particle_collector_block']
# Heat pipes are yellow all over, so every shade of theirs follows the paint.
REACTOR_HEAT_PIPE = 'reactor_heat_pipe'
# Red trim on the extenders that should take the accent colour.
ACCENT_SHADES = [(198, 60, 51), (181, 57, 98), (171, 65, 58)]


def open_source(path):
    if path.endswith('.jar'):
        z = zipfile.ZipFile(path)
        return lambda n: Image.open(io.BytesIO(z.read(f'assets/oritech/textures/block/{n}.png'))).convert('RGBA')
    return lambda n: Image.open(os.path.join(path, f'{n}.png')).convert('RGBA')


def hls(c):
    return colorsys.rgb_to_hls(*[x / 255 for x in c])


def rgb(h, l, s):
    return tuple(max(0, min(255, round(x * 255))) for x in colorsys.hls_to_rgb(h, max(0, min(1, l)), s))


def warm(c):
    h, s, _ = colorsys.rgb_to_hsv(*[x / 255 for x in c])
    return (h * 360 <= 45 or h * 360 >= 330) and s >= 0.4


def trim(c):
    """Orange, brown and red-pink trim on glass frames and reactor parts. Leaves status colours alone."""
    h, s, v = colorsys.rgb_to_hsv(*[x / 255 for x in c])
    h *= 360
    if v < 0.3 or s < 0.55 or 40 < h < 325:
        return False
    return not (s >= 0.95 and (h <= 12 or h >= 350))  # pure red: redstone indicators


def nearest(shade, keys):
    """Closest key shade, weighing brightness and hue (hue wraps around)."""
    h, l, s = hls(shade)
    def dist(k):
        kh, kl, ks = hls(k)
        dh = min(abs(h - kh), 1 - abs(h - kh))
        return (l - kl) ** 2 * 4 + dh ** 2 * 6 + (s - ks) ** 2
    return min(keys, key=dist)


def follow(shade, ref, target):
    """`target`'s colour, shifted by how much brighter/darker `shade` is than `ref`."""
    th, tl, ts = hls(target)
    return rgb(th, tl + (hls(shade)[1] - hls(ref)[1]), ts)


def strip(colours, path):
    im = Image.new('RGBA', (len(colours), 1))
    im.putdata([c + (255,) for c in colours])
    im.save(path)


def shades(load, names):
    out = set()
    for n in names:
        for p in load(n).getdata():
            if p[3] > 0:
                out.add(p[:3])
    return out


def main():
    load = open_source(sys.argv[1])
    out = os.path.join(sys.argv[2], 'textures', 'colormap', 'paint')
    read = lambda n: [p[:3] for p in Image.open(os.path.join(out, f'{n}.png')).convert('RGBA').getdata()]
    key = read('key')
    paints = {v: dict(zip(key, read(v))) for v in VARIANTS}

    # Pipes: every warm (copper/orange/red) shade in the pipe textures.
    pipe_keys = sorted(s for s in shades(load, PIPE_TEXTURES) if warm(s))
    strip(pipe_keys, os.path.join(out, 'pipe_key.png'))
    for v in VARIANTS:
        strip([follow(s, k, paints[v][k]) for s in pipe_keys for k in [nearest(s, key)]],
              os.path.join(out, f'pipe_{v}.png'))

    # Extenders: main palette + accent trim + leftover warm shades.
    ext = shades(load, EXTENDER_TEXTURES)
    accents = sorted(s for s in ext if s in ACCENT_SHADES)
    extra = sorted(s for s in ext if warm(s) and s not in key and s not in ACCENT_SHADES)
    ext_keys = key + accents + extra
    strip(ext_keys, os.path.join(out, 'extender_key.png'))
    brightest = max(accents, key=lambda c: hls(c)[1]) if accents else None
    for v in VARIANTS:
        row = [paints[v][k] for k in key]
        row += [follow(a, brightest, ACCENTS[v]) for a in accents]
        row += [follow(s, k, paints[v][k]) for s in extra for k in [nearest(s, key)]]
        strip(row, os.path.join(out, f'extender_{v}.png'))
    # Glass frames, reactor parts and accelerator parts: trim shades (and every heat pipe shade) follow the paint.
    struct_keys = sorted({s for s in shades(load, GLASS_TEXTURES + REACTOR_PART_TEXTURES + ACCELERATOR_TEXTURES) if trim(s)}
                         | shades(load, [REACTOR_HEAT_PIPE]))
    strip(struct_keys, os.path.join(out, 'structure_key.png'))
    for v in VARIANTS:
        strip([follow(s, k, paints[v][k]) for s in struct_keys for k in [nearest(s, key)]],
              os.path.join(out, f'structure_{v}.png'))

    print(f'structure: {len(struct_keys)} shades')
    print(f'pipe: {len(pipe_keys)} shades; extender: {len(key)} + {len(accents)} accent + {len(extra)} extra -> {out}')


if __name__ == '__main__':
    main()
