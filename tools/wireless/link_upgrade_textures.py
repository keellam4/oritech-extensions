#!/usr/bin/env python3
"""Icons for the Cargo Link upgrades (Infinite Range, Dimensional) and the Network Anchor.

    python3 tools/wireless/link_upgrade_textures.py
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions"
EDGE, PLATE, DARK = (156, 50, 21), (195, 108, 43), (35, 42, 51)


def chip(core):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            image.putpixel((x, y), (EDGE if edge else DARK) + (255,))
    for i in range(3, 13, 3):  # pins
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            image.putpixel((x, y), PLATE + (255,))
    for (x, y), colour in core.items():
        image.putpixel((x, y), colour + (255,))
    return image


def infinity():
    core = {}
    for t in range(0, 64):
        a = t / 64 * 2 * math.pi
        x = 7.5 + 4 * math.cos(a)
        y = 7.5 + 2.2 * math.sin(2 * a)
        core[(int(round(x)), int(round(y)))] = (62, 201, 213)
    core[(8, 8)] = (156, 255, 245)
    return core


def portal():
    core = {}
    for y in range(4, 12):
        for x in range(4, 12):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 3.8:
                core[(x, y)] = (156, 255, 245) if r < 1.2 else (180, 120, 230) if r < 2.4 else (90, 50, 130)
    return core


def anchor():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    o, d, t = PLATE + (255,), EDGE + (255,), (62, 201, 213, 255)
    for y in range(3, 13):
        image.putpixel((7, y), o)
        image.putpixel((8, y), d)
    for x in range(5, 11):
        image.putpixel((x, 5), o)
    for dx in range(0, 5):
        image.putpixel((3 + dx, 12 - (dx // 2)), o)
        image.putpixel((12 - dx, 12 - (dx // 2)), d)
    for x, y in ((6, 1), (7, 1), (8, 1), (9, 1), (6, 2), (9, 2), (6, 3), (9, 3)):
        image.putpixel((x, y), t)
    return image


def main():
    for name, image in (("infinite_range_upgrade", chip(infinity())), ("dimensional_upgrade", chip(portal())), ("network_anchor", anchor())):
        image.save(ROOT / f"textures/item/{name}.png")
        (ROOT / f"models/item/{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"oritech_extensions:item/{name}"}}, indent=2) + "\n")


if __name__ == "__main__":
    main()
