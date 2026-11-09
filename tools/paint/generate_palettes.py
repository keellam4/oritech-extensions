"""
Builds the palette strips used to paint textures with Oritech's paint colours.

Oritech paints a machine by swapping in a recoloured copy of its texture. Comparing every texture with its painted
copies shows each paint is one consistent colour swap: the same ~28 orange shades always become the same new shades.
This script extracts that swap from an Oritech jar and writes it as vanilla "paletted_permutations" palettes:

  textures/colormap/paint/key.png          the orange shades (1 px each)
  textures/colormap/paint/<colour>.png     what each shade becomes with that paint (same order)

Usage: python3 -P generate_palettes.py <oritech.jar> <output assets/oritech_extensions dir>
"""
import colorsys, io, os, sys, zipfile
from PIL import Image

VARIANTS = ['camo', 'diamond', 'fluxite', 'white', 'industrial', 'netherite', 'redstone', 'sculk']
jar = zipfile.ZipFile(sys.argv[1])
out = os.path.join(sys.argv[2], 'textures', 'colormap', 'paint')
os.makedirs(out, exist_ok=True)

names = set(jar.namelist())
tex = 'assets/oritech/textures/block/models/'
bases = sorted({n.rsplit('/', 1)[1].rsplit('_', 1)[0] for n in names if n.startswith(tex + 'colored/') and n.endswith('.png')})
load = lambda n: Image.open(io.BytesIO(jar.read(n))).convert('RGBA')

swap = {v: {} for v in VARIANTS}
for v in VARIANTS:
    for b in bases:
        a_name, c_name = f'{tex}{b}.png', f'{tex}colored/{b}_{v}.png'
        if a_name not in names or c_name not in names: continue
        a, c = load(a_name), load(c_name)
        if a.size != c.size: continue
        for p, q in zip(a.getdata(), c.getdata()):
            if p != q and p[3] == 255:
                swap[v].setdefault(p[:3], q[:3])

# Keep only the warm orange/red accent shades. Some machines also change screens or trims (cyan -> black,
# blue -> gold) in their painted textures; those are machine-specific and would wrongly blacken glow/screen
# details on other blocks.
def warm(c):
    h, s, _ = colorsys.rgb_to_hsv(*[x / 255 for x in c])
    return (h * 360 <= 45 or h * 360 >= 330) and s >= 0.4
keys = sorted(k for k in set().union(*[set(m) for m in swap.values()]) if warm(k))
def strip(colours, path):
    im = Image.new('RGBA', (len(colours), 1))
    im.putdata([c + (255,) for c in colours])
    im.save(path)
strip(keys, os.path.join(out, 'key.png'))
for v in VARIANTS:
    strip([swap[v].get(k, k) for k in keys], os.path.join(out, f'{v}.png'))
print(f'{len(keys)} shades, {len(bases)} source models -> {out}')
