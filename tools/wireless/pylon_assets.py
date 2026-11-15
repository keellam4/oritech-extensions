#!/usr/bin/env python3
"""Induction Pylon textures and models, for its three styles (Tesla Spire, Coil Obelisk, Ring Pylon).

Each style is drawn as one 16 x 32 front view, then cut into the bottom and top block textures. The models are
boxes (plinth, column, orb, rings, ...) whose faces show the matching part of that drawing, so the pylon looks
the same from all four sides. Pixels inside a box that the drawing leaves empty are filled with dark steel.

    python3 tools/wireless/pylon_assets.py
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions"
C = {"H": "#e09f67", "O": "#c36c2b", "I": "#b25020", "S": "#c63c33", "D": "#9c3215",
     "t": "#3597ad", "T": "#3ec9d5", "W": "#9cfff5", "g": "#1f4f5a",
     "2": "#232a33", "3": "#343b49", "a": "#4a4f56", "b": "#6c727a", "c": "#959ba3", "k": "#2a2e33"}


def spire(x, y):
    cx = 7.5
    if y >= 27:
        if y == 27:
            return "H" if 1 <= x <= 14 else None
        if 1 <= x <= 14:
            return "S" if x in (1, 14) or y == 31 else ("O" if y % 2 else "I")
        return None
    if y <= 5:
        r = math.hypot(x - cx, y - 3)
        return "W" if r < 1.5 else "T" if r < 2.6 else "t" if r < 3.6 else None
    half = 2.5 + (y - 6) * 0.12
    if abs(x - cx) <= half:
        if (y - 6) % 4 in (0, 1):
            return "O" if abs(x - cx) < half - 0.6 else "D"
        return "a" if abs(x - cx) > half - 1 else ("T" if abs(x - cx) < 0.6 and y % 3 else "3")
    return None


def obelisk(x, y):
    if y <= 3:
        if x in (2, 13) and y >= 1:
            return "b"
        if x in (3, 12) and y >= 1:
            return "c"
        if 6 <= x <= 9 and y >= 1:
            return "T" if y == 1 else "W"
        return None
    if y == 4:
        return "H" if 1 <= x <= 14 else None
    if x in (1, 14):
        return "S" if y < 31 else "D"
    if x in (0, 15):
        return None
    if y in (5, 31):
        return "D"
    if y in (12, 20, 28):
        return "O"
    if 5 <= x <= 10 and 7 <= y <= 26:
        if x in (5, 10):
            return "k"
        return "W" if x in (7, 8) and (y // 2) % 2 else "T" if 6 <= x <= 9 else "g"
    if (x, y) in ((3, 8), (12, 8), (3, 24), (12, 24)):
        return "c"
    return "2" if (x + y) % 5 else "3"


def rings(x, y):
    cx = 7.5
    if y >= 26:
        if y == 26:
            return "H" if 2 <= x <= 13 else None
        if 2 <= x <= 13:
            return "S" if x in (2, 13) or y == 31 else ("O" if (x + y) % 2 else "I")
        return None
    if y <= 4:
        r = math.hypot(x - cx, y - 2.5)
        return "W" if r < 1.2 else "T" if r < 2.6 else None
    for ry in (8, 15, 22):
        if abs(y - ry) <= 1 and 1 <= x <= 14:
            if y == ry:
                return "T" if 3 <= x <= 12 else "t"
            return "D" if x in (1, 14) else "O"
    if 6 <= x <= 9:
        return "c" if x == 7 else "a"
    return None


# Boxes in pixels over the whole 2-block pylon: (x0, y0, x1, y1), y measured from the bottom (0..32), square in x/z.
BOXES = {
    "spire": [(1, 0, 15, 5), (3, 5, 13, 14), (4, 14, 12, 26), (4, 26, 12, 32)],
    "obelisk": [(1, 0, 15, 28), (2, 28, 4, 31), (12, 28, 14, 31), (6, 28, 10, 31)],
    "rings": [(2, 0, 14, 6), (6, 6, 10, 27), (1, 8, 15, 11), (1, 15, 15, 18), (1, 22, 15, 25), (5, 27, 11, 32)],
}
STYLES = {"spire": ("induction_pylon", spire), "obelisk": ("induction_pylon_obelisk", obelisk), "rings": ("induction_pylon_rings", rings)}


def rgba(key):
    h = C[key].lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def picture(fn, boxes):
    image = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    for row in range(32):
        y = 31 - row  # height above the bottom, for this pixel row
        for x in range(16):
            key = fn(x, row)
            inside = any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in boxes)
            if key:
                image.putpixel((x, row), rgba(key))
            elif inside:
                image.putpixel((x, row), rgba("a"))
    return image


def elements(boxes, half):
    """Model elements for one half (0 = bottom, 1 = top), with face UVs into that half's texture."""
    low, high = half * 16, half * 16 + 16
    out = []
    for x0, y0, x1, y1 in boxes:
        a, b = max(y0, low), min(y1, high)
        if a >= b:
            continue
        ya, yb = a - low, b - low
        side = [x0, 16 - yb, x1, 16 - ya]
        cap = [x0, 16 - yb, x1, min(16, 16 - yb + max(1, x1 - x0))]
        faces = {d: {"uv": side, "texture": "#tex"} for d in ("north", "south", "east", "west")}
        faces["up"] = {"uv": cap, "texture": "#tex"}
        faces["down"] = {"uv": cap, "texture": "#tex"}
        out.append({"from": [x0, ya, x0], "to": [x1, yb, x1], "faces": faces})
    return out


def main():
    textures = ROOT / "textures/block"
    textures.mkdir(parents=True, exist_ok=True)
    for style, (name, fn) in STYLES.items():
        image = picture(fn, BOXES[style])
        image.crop((0, 16, 16, 32)).save(textures / f"{name}_bottom.png")
        image.crop((0, 0, 16, 16)).save(textures / f"{name}_top.png")
        icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        icon.alpha_composite(image.resize((8, 16), Image.NEAREST), (4, 0))
        (ROOT / "textures/item").mkdir(parents=True, exist_ok=True)
        icon.save(ROOT / f"textures/item/{name}.png")
        for half, part in ((0, "bottom"), (1, "top")):
            model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                     "textures": {"tex": f"oritech_extensions:block/{name}_{part}", "particle": f"oritech_extensions:block/{name}_bottom"},
                     "elements": elements(BOXES[style], half)}
            (ROOT / f"models/block/{name}_{part}.json").write_text(json.dumps(model, indent=2) + "\n")
        (ROOT / f"blockstates/{name}.json").write_text(json.dumps({"variants": {
            "half=lower": {"model": f"oritech_extensions:block/{name}_bottom"},
            "half=upper": {"model": f"oritech_extensions:block/{name}_top"}}}, indent=2) + "\n")
        (ROOT / f"models/item/{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"oritech_extensions:item/{name}"}}, indent=2) + "\n")


if __name__ == "__main__":
    main()
