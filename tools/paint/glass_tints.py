#!/usr/bin/env python3
"""See-through tints for painted Industrial Glass (one per paint colour, drawn over the pane in the translucent
layer), plus an animated Rainbow tint.

    python3 tools/paint/glass_tints.py
"""
import json
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions/textures/block"
ALPHA = 105
COLOURS = {"diamond": (79, 214, 214), "camo": (93, 122, 58), "fluxite": (154, 79, 214), "white": (232, 232, 232),
           "industrial": (214, 184, 58), "netherite": (74, 66, 72), "redstone": (200, 50, 42), "sculk": (29, 91, 107)}


def tint(rgb):
    image = Image.new("RGBA", (16, 16), rgb + (ALPHA,))
    # A faint diagonal sheen, so the pane reads as glass rather than a flat colour.
    for i in range(16):
        for d in (0, 1):
            x = (i + d) % 16
            r, g, b = rgb
            image.putpixel((x, 15 - i), (min(255, r + 40), min(255, g + 40), min(255, b + 40), ALPHA + 25))
    return image


def main():
    for name, rgb in COLOURS.items():
        tint(rgb).save(OUT / f"glass_tint_{name}.png")
    # Same colours and timing as the Rainbow paint (tools/paint/rainbow.py), so tinted glass stays in step with it.
    cycle = ["redstone", "industrial", "camo", "diamond", "sculk", "fluxite"]
    sheet = Image.new("RGBA", (16, 16 * len(cycle)))
    for i, name in enumerate(cycle):
        sheet.paste(tint(COLOURS[name]), (0, 16 * i))
    sheet.save(OUT / "glass_tint_rainbow.png")
    (OUT / "glass_tint_rainbow.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 20, "interpolate": True}}) + "\n")


if __name__ == "__main__":
    main()
