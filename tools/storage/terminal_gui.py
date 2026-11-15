#!/usr/bin/env python3
"""Draws the Cargo Controller and Crafting Terminal backgrounds (212 wide, in 256 x 288 textures): an Oritech
steel panel with an orange header trim, a teal-glowing screen with scan lines for the item list, a scrollbar
track, a column for the sort and filter buttons, the crafting grid (Crafting Terminal), and the player inventory.
TerminalScreen draws the items, the scroll handle and the buttons on top. Positions match TerminalMenu.

    python3 tools/storage/terminal_gui.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions/textures/gui"
W = 212
PANEL, LIGHT, DARK, EDGE = (43, 47, 54), (78, 84, 94), (22, 25, 30), (14, 16, 19)
ORANGE, ORANGE_LIGHT, ORANGE_DARK = (195, 108, 43), (224, 159, 103), (156, 50, 21)
TEAL, TEAL_DARK = (62, 201, 213), (31, 79, 90)
SCREEN, SCREEN_LINE, CELL = (16, 20, 25), (20, 25, 31), (24, 30, 37)
SLOT_IN, SLOT_LIGHT, SLOT_DARK = (30, 33, 38), (92, 98, 108), (16, 18, 22)


def slot(d, x, y):
    d.rectangle((x, y, x + 17, y + 17), fill=SLOT_IN)
    d.line((x, y, x + 16, y), fill=SLOT_DARK)
    d.line((x, y, x, y + 16), fill=SLOT_DARK)
    d.line((x + 1, y + 17, x + 17, y + 17), fill=SLOT_LIGHT)
    d.line((x + 17, y + 1, x + 17, y + 17), fill=SLOT_LIGHT)


def panel(d, h):
    d.rectangle((0, 0, W - 1, h - 1), fill=PANEL, outline=EDGE)
    d.line((1, 1, W - 3, 1), fill=LIGHT)
    d.line((1, 1, 1, h - 3), fill=LIGHT)
    d.line((2, h - 2, W - 2, h - 2), fill=DARK)
    d.line((W - 2, 2, W - 2, h - 2), fill=DARK)
    # steel corner bolts
    for x, y in ((3, 3), (W - 5, 3), (3, h - 5), (W - 5, h - 5)):
        d.rectangle((x, y, x + 1, y + 1), fill=LIGHT)


def draw(crafting):
    rows = 5 if crafting else 8
    inventory_y = 195 if crafting else 183
    h = inventory_y + 82
    image = Image.new("RGBA", (256, 288), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    panel(d, h)
    # header: orange trim under the title, teal lights at the ends
    d.line((6, 17, W - 7, 17), fill=ORANGE)
    d.line((6, 18, W - 7, 18), fill=ORANGE_DARK)
    for x in (4, W - 5):
        d.rectangle((x - 1, 16, x, 19), fill=TEAL)
    # search field with an orange underline
    d.rectangle((104, 4, 188, 15), fill=EDGE)
    d.rectangle((105, 5, 187, 14), fill=DARK)
    d.line((105, 14, 187, 14), fill=ORANGE)
    # the screen: teal glow border, scan lines, cells
    grid_bottom = 20 + rows * 18
    d.rectangle((5, 18 - 0, 171, grid_bottom + 1), fill=TEAL_DARK)
    d.rectangle((6, 19, 170, grid_bottom), fill=TEAL)
    d.rectangle((7, 20 - 1 + 1, 169, grid_bottom - 1), fill=SCREEN)
    for y in range(21, grid_bottom - 1, 2):
        d.line((7, y, 169, y), fill=SCREEN_LINE)
    for row in range(rows):
        for col in range(9):
            x, y = 8 + col * 18, 20 + row * 18
            d.rectangle((x, y, x + 15, y + 15), fill=CELL)
    # scrollbar track
    d.rectangle((174, 19, 187, grid_bottom), fill=EDGE)
    d.rectangle((175, 20, 186, grid_bottom - 1), fill=DARK)
    # button column frame
    d.rectangle((190, 18, 211 - 1, 20 + 3 * 18 + 1), fill=EDGE)
    if crafting:
        # crafting area: a dark plate with the 3 x 3 grid and a big result slot
        d.rectangle((6, 115, W - 7, 178), fill=DARK)
        d.line((6, 115, W - 7, 115), fill=ORANGE_DARK)
        for row in range(3):
            for col in range(3):
                slot(d, 30 + col * 18 - 1, 120 + row * 18 - 1)
        d.rectangle((124 - 5, 138 - 5, 124 + 20, 138 + 20), fill=ORANGE)
        d.rectangle((124 - 4, 138 - 4, 124 + 19, 138 + 19), fill=ORANGE_DARK)
        slot(d, 124 - 1, 138 - 1)
    # trim above the inventory
    d.line((6, inventory_y - 14, W - 7, inventory_y - 14), fill=ORANGE)
    d.line((6, inventory_y - 13, W - 7, inventory_y - 13), fill=ORANGE_DARK)
    for row in range(3):
        for col in range(9):
            slot(d, 8 + col * 18 - 1, inventory_y + row * 18 - 1)
    for col in range(9):
        slot(d, 8 + col * 18 - 1, inventory_y + 58 - 1)
    return image


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    draw(False).save(OUT / "cargo_terminal.png")
    draw(True).save(OUT / "crafting_terminal.png")


if __name__ == "__main__":
    main()
