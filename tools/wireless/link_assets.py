#!/usr/bin/env python3
"""Cargo Link assets: the Handheld Link item icon, and the Uplink Mast (dish relay) and Antenna Segment block
textures and models. Blocks are drawn as one 16 x 16 front view; the model boxes show the matching part of it on
all four sides (empty pixels inside a box become dark steel), like the Induction Pylon.

    python3 tools/wireless/link_assets.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions"
C = {"H": "#e09f67", "O": "#c36c2b", "I": "#b25020", "S": "#c63c33", "D": "#9c3215",
     "t": "#3597ad", "T": "#3ec9d5", "W": "#9cfff5", "g": "#1f4f5a",
     "1": "#161b21", "2": "#232a33", "a": "#4a4f56", "b": "#6c727a", "c": "#959ba3"}


def rgba(key):
    h = C[key].lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def handheld(x, y):
    if x == 11 and y <= 3:
        return "c" if y > 0 else "T"
    if not (4 <= x <= 11 and 3 <= y <= 14):
        return None
    if x in (4, 11) or y in (3, 14):
        return "S" if x == 11 or y == 14 else "H"
    if 5 <= x <= 10 and 4 <= y <= 9:
        return "W" if (x, y) == (9, 5) else ("T" if y in (5, 7) and 6 <= x <= 9 else "g")
    if 10 <= y <= 13:
        if (x, y) in ((6, 11), (8, 11), (7, 10), (7, 12)):
            return "O"
        return "a" if y == 13 else "2"
    return "1"


def mast(x, row):
    if row >= 11:
        if row == 15:
            return "D"
        if row == 11:
            return "H" if 2 <= x <= 13 else None
        return ("O" if (x + row) % 2 else "I") if 2 <= x <= 13 else None
    if row <= 4 and abs(x - 7.5) < 6 - row:
        return "T" if row == 0 else "c" if row <= 2 else "b"
    if 6 <= x <= 9:
        return "a" if x in (6, 9) else ("T" if row % 3 == 0 else "2")
    return None


def segment(x, row):
    if row in (3, 11) and 3 <= x <= 12:
        return "D" if x in (3, 12) else "O"
    if 6 <= x <= 9:
        return "a" if x in (6, 9) else ("T" if row % 4 == 0 else "2")
    return None


# Boxes (x0, y0, x1, y1) in pixels, y up from the bottom; square in x and z.
BOXES = {
    "uplink_mast": [(2, 0, 14, 5), (6, 5, 10, 12), (4, 12, 12, 14), (2, 14, 14, 16)],
    "antenna_segment": [(6, 0, 10, 16), (3, 4, 13, 5), (3, 12, 13, 13)],
}
DRAW = {"uplink_mast": mast, "antenna_segment": segment}


def picture(fn, boxes):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for row in range(16):
        y = 15 - row
        for x in range(16):
            key = fn(x, row)
            inside = any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in boxes)
            if key:
                image.putpixel((x, row), rgba(key))
            elif inside:
                image.putpixel((x, row), rgba("a"))
    return image


def elements(boxes):
    out = []
    for x0, y0, x1, y1 in boxes:
        side = [x0, 16 - y1, x1, 16 - y0]
        cap = [x0, 16 - y1, x1, min(16, 16 - y1 + max(1, x1 - x0))]
        faces = {d: {"uv": side, "texture": "#tex"} for d in ("north", "south", "east", "west")}
        faces["up"] = {"uv": cap, "texture": "#tex"}
        faces["down"] = {"uv": cap, "texture": "#tex"}
        out.append({"from": [x0, y0, x0], "to": [x1, y1, x1], "faces": faces})
    return out


def main():
    for name, boxes in BOXES.items():
        picture(DRAW[name], boxes).save(ROOT / f"textures/block/{name}.png")
        model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                 "textures": {"tex": f"oritech_extensions:block/{name}", "particle": f"oritech_extensions:block/{name}"},
                 "elements": elements(boxes)}
        (ROOT / f"models/block/{name}.json").write_text(json.dumps(model, indent=2) + "\n")
        (ROOT / f"blockstates/{name}.json").write_text(json.dumps({"variants": {"": {"model": f"oritech_extensions:block/{name}"}}}, indent=2) + "\n")
        (ROOT / f"models/item/{name}.json").write_text(json.dumps({"parent": f"oritech_extensions:block/{name}"}, indent=2) + "\n")
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            key = handheld(x, y)
            if key:
                icon.putpixel((x, y), rgba(key))
    icon.save(ROOT / "textures/item/cargo_link.png")
    (ROOT / "models/item/cargo_link.json").write_text(json.dumps(
        {"parent": "minecraft:item/handheld", "textures": {"layer0": "oritech_extensions:item/cargo_link"}}, indent=2) + "\n")


if __name__ == "__main__":
    main()
