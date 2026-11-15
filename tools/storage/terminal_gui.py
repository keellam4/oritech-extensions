#!/usr/bin/env python3
"""Draws the Cargo Controller terminal background (195 x 207 in a 256 x 256 texture): search field, a 9 x 5 item
grid with a scrollbar track, and the player inventory. TerminalScreen draws the items and the scroll handle.

    python3 tools/storage/terminal_gui.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions/textures/gui/cargo_terminal.png"
W, H = 195, 207
PANEL, LIGHT, DARK, EDGE = (43, 47, 54), (78, 84, 94), (22, 25, 30), (14, 16, 19)
ORANGE, ORANGE_DARK, TEAL = (195, 108, 43), (156, 50, 21), (62, 201, 213)
SLOT_IN, SLOT_LIGHT, SLOT_DARK = (30, 33, 38), (92, 98, 108), (16, 18, 22)


def slot(d, x, y, fill=SLOT_IN):
    d.rectangle((x, y, x + 17, y + 17), fill=fill)
    d.line((x, y, x + 16, y), fill=SLOT_DARK)
    d.line((x, y, x, y + 16), fill=SLOT_DARK)
    d.line((x + 1, y + 17, x + 17, y + 17), fill=SLOT_LIGHT)
    d.line((x + 17, y + 1, x + 17, y + 17), fill=SLOT_LIGHT)


def main():
    image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, W - 1, H - 1), fill=PANEL, outline=EDGE)
    d.line((1, 1, W - 3, 1), fill=LIGHT)
    d.line((1, 1, 1, H - 3), fill=LIGHT)
    d.line((2, H - 2, W - 2, H - 2), fill=DARK)
    d.line((W - 2, 2, W - 2, H - 2), fill=DARK)
    # search field
    d.rectangle((80, 3, 172, 14), fill=EDGE)
    d.rectangle((81, 4, 171, 13), fill=DARK)
    # item grid: a dark screen with faint cells and a teal edge
    d.rectangle((6, 16, 170, 108), fill=TEAL)
    d.rectangle((7, 17, 169, 107), fill=(18, 22, 27))
    for row in range(5):
        for col in range(9):
            x, y = 8 + col * 18 - 1, 18 + row * 18 - 1
            d.rectangle((x + 1, y + 1, x + 16, y + 16), fill=(26, 31, 37))
    # scrollbar track
    d.rectangle((174, 17, 187, 108), fill=EDGE)
    d.rectangle((175, 18, 186, 107), fill=DARK)
    # orange trim between the screen and the inventory
    d.line((7, 111, W - 8, 111), fill=ORANGE)
    d.line((7, 112, W - 8, 112), fill=ORANGE_DARK)
    for row in range(3):
        for col in range(9):
            slot(d, 8 + col * 18 - 1, 125 + row * 18 - 1)
    for col in range(9):
        slot(d, 8 + col * 18 - 1, 183 - 1)
    image.save(OUT)


if __name__ == "__main__":
    main()
