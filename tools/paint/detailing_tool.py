#!/usr/bin/env python3
"""Draws the Detailing Tool (the "reservoir brush") item textures and models.

One texture per paint colour plus an empty one: the glass window in the handle and the bristles show the
selected colour. The rainbow texture is animated through the same colours as the Rainbow Color Cartridge.
Shapes are drawn along the item's diagonal: u = x - y runs from the handle (bottom left) to the tip
(top right), v = x + y - 15 runs across it.

    python3 tools/paint/detailing_tool.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "common/src/main/resources/assets/oritech_extensions"
TEXTURES = ASSETS / "textures/item"
MODELS = ASSETS / "models/item"

OUTLINE = "#1c1c1c"
# dark, mid, light, highlight; taken from Oritech's Color Cartridge textures
PAINTS = {
    "orange": ["#a85402", "#dd7a06", "#f29619", "#ffc05a"],
    "diamond": ["#1d587a", "#277999", "#339ebd", "#8adcec"],
    "camo": ["#053600", "#074800", "#0a6200", "#5aa010"],
    "fluxite": ["#b8737a", "#dc9196", "#e7b3b7", "#f6dcde"],
    "white": ["#7d8183", "#b0b0b0", "#dcdcdc", "#ffffff"],
    "industrial": ["#8a7420", "#cfb035", "#ddc64c", "#f3e27a"],
    "netherite": ["#2a1d08", "#462f0c", "#654511", "#9a6c22"],
    "redstone": ["#5e0000", "#8c0000", "#b90000", "#ff4a35"],
    "sculk": ["#091a1f", "#12272d", "#21404a", "#3fa7b0"],
}
# Same order as tools/paint/rainbow.py
RAINBOW_CYCLE = ["redstone", "industrial", "camo", "diamond", "sculk", "fluxite"]
EMPTY_WINDOW = ["#22292b", "#2e3a3c", "#3d4c4f", "#6f8488"]
DRY_BRISTLES = ["#6b5a3e", "#9c8560", "#c4ad84", "#e6d6b0"]
MATERIALS = {
    "steel": ["#404141", "#6e6f6f", "#9b9b9b", "#d0d0d0"],
    "dark": ["#1f2020", "#2e2e2e", "#404141", "#5a5b5b"],
}

# (u from, u to, v from, v to, material); v bounds may depend on u. "window" and "bristles" take the paint.
SHAPE = [
    (-12, -11, 0, 1, "steel"),
    (-10, -8, 0, 1, "dark"),
    (-7, -3, -1, 2, "window"),
    (-2, 0, 0, 1, "dark"),
    (1, 3, -1, 2, "steel"),
    (4, 10, lambda u: -1 if u < 9 else 0, lambda u: 2 if u < 8 else 1, "bristles"),
    (11, 12, 0, 1, "bristles"),
]


def rgba(hex_colour):
    return tuple(int(hex_colour[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def draw(window, bristles):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            u, v = x - y, x + y - 15
            for u0, u1, lo, hi, material in SHAPE:
                if not u0 <= u <= u1:
                    continue
                a = lo(u) if callable(lo) else lo
                b = hi(u) if callable(hi) else hi
                if a <= v <= b:
                    tones = {"window": window, "bristles": bristles}.get(material) or MATERIALS[material]
                    if b - a <= 1:
                        shade = 2 if v == a else 1
                    else:
                        shade = 3 if v == a else 0 if v == b else 2 if v - a <= (b - a) / 2 else 1
                    if material == "bristles" and u == u1 and v == a:
                        shade = 3
                    image.putpixel((x, y), rgba(tones[shade]))
                break
    pixels = image.load()
    outline = []
    for y in range(16):
        for x in range(16):
            if pixels[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and pixels[nx, ny][3] and pixels[nx, ny] != rgba(OUTLINE):
                    outline.append((x, y))
                    break
    for point in outline:
        pixels[point] = rgba(OUTLINE)
    return image


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)

    draw(EMPTY_WINDOW, DRY_BRISTLES).save(TEXTURES / "detailing_tool.png")
    for name, tones in PAINTS.items():
        draw(tones, tones).save(TEXTURES / f"detailing_tool_{name}.png")

    frames = [draw(PAINTS[name], PAINTS[name]) for name in RAINBOW_CYCLE]
    strip = Image.new("RGBA", (16, 16 * len(frames)))
    for i, frame in enumerate(frames):
        strip.paste(frame, (0, 16 * i))
    strip.save(TEXTURES / "detailing_tool_rainbow.png")
    (TEXTURES / "detailing_tool_rainbow.png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": 20, "interpolate": True}}, indent=2) + "\n")

    # DetailingToolItem sets custom_model_data to (Paint ordinal + 1) while the selected colour is loaded.
    order = list(PAINTS) + ["rainbow"]  # Paint enum order
    overrides = [{"predicate": {"custom_model_data": i + 1},
                  "model": f"oritech_extensions:item/detailing_tool_{name}"} for i, name in enumerate(order)]
    model = {"parent": "minecraft:item/handheld", "textures": {"layer0": "oritech_extensions:item/detailing_tool"},
             "overrides": overrides}
    (MODELS / "detailing_tool.json").write_text(json.dumps(model, indent=2) + "\n")
    for name in order:
        (MODELS / f"detailing_tool_{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/handheld",
             "textures": {"layer0": f"oritech_extensions:item/detailing_tool_{name}"}}, indent=2) + "\n")


if __name__ == "__main__":
    main()
