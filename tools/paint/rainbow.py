"""
Builds the animated rainbow textures used by Rainbow Cartridge paint, from Oritech's own textures and this mod's
paint palettes (generate_palettes.py and derive_palettes.py must have run first).

For every Oritech texture listed in assets/minecraft/atlases/blocks.json (the painted-texture atlas), it writes an
animated texture that blends through the colours in CYCLE, each frame painted with that colour's palette. The atlas
gets a "single" source per texture that names the sprite like the other paints, e.g.
oritech:block/item_pipe_paint_rainbow, so the paint code finds it the same way.

It also writes the Rainbow Cartridge item texture: Oritech's paint bucket with scrolling rainbow paint.

Oritech's textures are CC0. Textures from other mods (Oritech Things) are skipped.

Usage: python3 -P rainbow.py <oritech assets/oritech/textures dir or oritech.jar> <this mod's src/main/resources dir>
"""
import colorsys, io, json, os, sys, zipfile
from PIL import Image

# Colours the rainbow blends through, in hue order (red, yellow, green, cyan, blue, pink, back to red).
CYCLE = ['redstone', 'industrial', 'camo', 'diamond', 'sculk', 'fluxite']
FRAME_TIME = 20  # ticks per colour; interpolated, so one full cycle takes len(CYCLE) * FRAME_TIME ticks

# Item icon: frames of scrolling rainbow on the paint in Oritech's orange paint bucket.
ICON_FRAMES = 16
ICON_FRAME_TIME = 2


def open_source(path):
    """Returns read(relative_path) -> bytes or None, for a textures dir or an Oritech jar."""
    if path.endswith('.jar'):
        z = zipfile.ZipFile(path)
        names = set(z.namelist())
        def read(rel):
            name = f'assets/oritech/textures/{rel}'
            return z.read(name) if name in names else None
        return read
    def read(rel):
        f = os.path.join(path, rel)
        return open(f, 'rb').read() if os.path.exists(f) else None
    return read


def palette(colormap_dir, name):
    return [p[:3] for p in Image.open(os.path.join(colormap_dir, f'{name}.png')).convert('RGBA').getdata()]


def first_frame(image, mcmeta):
    """Animated sources: only their first frame takes part."""
    if mcmeta is None:
        return image
    animation = json.loads(mcmeta).get('animation', {})
    w = animation.get('width', image.width)
    h = animation.get('height', w)  # frames are square unless the .mcmeta says otherwise
    return image.crop((0, 0, w, h))


def recolour(image, mapping):
    out = image.copy()
    out.putdata([mapping[p[:3]] + (p[3],) if p[3] and p[:3] in mapping else p for p in image.getdata()])
    return out


def write_animation(frames, path, frame_time, interpolate):
    w, h = frames[0].size
    strip = Image.new('RGBA', (w, h * len(frames)))
    for i, frame in enumerate(frames):
        strip.paste(frame, (0, i * h))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    strip.save(path)
    animation = {'frametime': frame_time, 'interpolate': interpolate}
    if w != h:
        animation.update(width=w, height=h)
    with open(path + '.mcmeta', 'w') as f:
        json.dump({'animation': animation}, f, indent=2)
        f.write('\n')


def rainbow_icon(bucket):
    """Recolours the warm (paint) pixels of the paint bucket into rainbow stripes that scroll across."""
    frames = []
    for n in range(ICON_FRAMES):
        frame = bucket.copy()
        px = frame.load()
        for y in range(bucket.height):
            for x in range(bucket.width):
                r, g, b, a = px[x, y]
                h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                if a == 0 or s < 0.5 or not (0.03 < h < 0.15):
                    continue  # not paint
                hue = ((x + y) / (bucket.width * 1.5) + n / ICON_FRAMES) % 1
                rgb = colorsys.hls_to_rgb(hue, min(0.75, l * 1.05), 0.85)
                px[x, y] = tuple(round(c * 255) for c in rgb) + (a,)
        frames.append(frame)
    return frames


def main():
    read = open_source(sys.argv[1])
    resources = sys.argv[2]
    colormap = os.path.join(resources, 'assets/oritech_extensions/textures/colormap/paint')
    atlas_path = os.path.join(resources, 'assets/minecraft/atlases/blocks.json')
    atlas = json.load(open(atlas_path))

    # Drop rainbow sources from an earlier run, then rebuild them from the palette sources.
    atlas['sources'] = [s for s in atlas['sources'] if not s.get('sprite', '').endswith('_paint_rainbow')]
    rainbow_sources, skipped = [], []
    for source in atlas['sources']:
        if source.get('type') != 'minecraft:paletted_permutations':
            continue
        key_name = source['palette_key'].split('/')[-1]
        key = palette(colormap, key_name)
        prefix = key_name.removesuffix('key')  # '' / 'pipe_' / 'structure_' / ...
        mappings = [dict(zip(key, palette(colormap, f'{prefix}{colour}'))) for colour in CYCLE]
        for texture in source['textures']:
            namespace, path = texture.split(':')
            data = read(f'{path}.png') if namespace == 'oritech' else None
            if data is None:
                skipped.append(texture)
                continue
            image = first_frame(Image.open(io.BytesIO(data)).convert('RGBA'), read(f'{path}.png.mcmeta'))
            out = os.path.join(resources, 'assets/oritech_extensions/textures/rainbow', namespace, f'{path}.png')
            write_animation([recolour(image, m) for m in mappings], out, FRAME_TIME, True)
            rainbow_sources.append({'type': 'minecraft:single',
                                    'resource': f'oritech_extensions:rainbow/{namespace}/{path}',
                                    'sprite': f'{namespace}:{path}_paint_rainbow'})
    atlas['sources'] += rainbow_sources
    with open(atlas_path, 'w') as f:
        json.dump(atlas, f, indent=2)
        f.write('\n')

    bucket = Image.open(io.BytesIO(read('item/orange_paint.png'))).convert('RGBA')
    write_animation(rainbow_icon(bucket), os.path.join(resources, 'assets/oritech_extensions/textures/item/rainbow_cartridge.png'),
                    ICON_FRAME_TIME, False)

    print(f'rainbow: {len(rainbow_sources)} textures; skipped {len(skipped)} from other mods: {", ".join(skipped)}')


if __name__ == '__main__':
    main()
