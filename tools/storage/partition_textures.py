#!/usr/bin/env python3
"""Partition upgrade icons: a silo cell split into coloured fluid bands (2, 3, or a shimmering grid for the
Matrix), framed in the tier's metal.

    python3 tools/storage/partition_textures.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions"
FRAME = {"partition_upgrade": ((150, 156, 164), (74, 79, 86)), "advanced_partition_upgrade": ((232, 196, 92), (150, 112, 40)),
         "partition_matrix": ((180, 120, 230), (90, 50, 130))}
BANDS = {"partition_upgrade": [(200, 70, 30), (62, 118, 228)], "advanced_partition_upgrade": [(200, 70, 30), (62, 201, 213), (62, 118, 228)],
         "partition_matrix": None}


def icon(name):
    light, dark = FRAME[name]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(1, 15):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (1, 14)
            if edge:
                colour = light if (x == 3 or y == 1) else dark
            elif BANDS[name]:
                bands = BANDS[name]
                index = min(len(bands) - 1, (y - 2) * len(bands) // 12)
                colour = bands[index]
                if (y - 2) * len(bands) % 12 < len(bands) and y > 2:
                    colour = (35, 42, 51)
            else:
                colour = (62, 201, 213) if (x + y) % 3 == 0 else (156, 255, 245) if (x * y) % 5 == 0 else (60, 40, 90)
            image.putpixel((x, y), colour + (255,))
    for x in (6, 7, 8, 9):
        image.putpixel((x, 0), dark + (255,))
    return image


def main():
    for name in FRAME:
        icon(name).save(ROOT / f"textures/item/{name}.png")
        (ROOT / f"models/item/{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"oritech_extensions:item/{name}"}}, indent=2) + "\n")


if __name__ == "__main__":
    main()
