#!/usr/bin/env python3
"""Draws the vault upgrade screen background (176 x 200, in a 256 x 256 texture): Oritech-style dark steel panel,
an orange trim, five upgrade slots (one per kind), a dark strip behind the Pull and Push side pickers, and the
player inventory. VaultUpgradeScreen draws the lock and the side pickers on top.

    python3 tools/storage/vault_gui.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions/textures/gui/vault_upgrades.png"
W, H = 176, 200
PANEL, LIGHT, DARK, EDGE = (43, 47, 54), (78, 84, 94), (22, 25, 30), (14, 16, 19)
ORANGE, ORANGE_DARK, TEAL = (195, 108, 43), (156, 50, 21), (62, 201, 213)
SLOT_IN, SLOT_LIGHT, SLOT_DARK = (30, 33, 38), (92, 98, 108), (16, 18, 22)


def slot(d, x, y):
    """An 18 x 18 slot whose item area starts at (x + 1, y + 1)."""
    d.rectangle((x, y, x + 17, y + 17), fill=SLOT_IN)
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
    # orange trim under the title, with a teal light at each end
    d.line((7, 16, W - 8, 16), fill=ORANGE)
    d.line((7, 17, W - 8, 17), fill=ORANGE_DARK)
    for x in (5, W - 6):
        d.rectangle((x - 1, 15, x, 18), fill=TEAL)
    # upgrade slots (VaultUpgradeMenu.UPGRADES_X/Y are the item positions, so the frame is one pixel up and left)
    for i in range(5):
        slot(d, 44 + i * 18 - 1, 20 - 1)
    # side pickers strip
    d.rectangle((7, 42, W - 8, 100), fill=DARK)
    # player inventory (VaultUpgradeMenu.INVENTORY_Y)
    for row in range(3):
        for col in range(9):
            slot(d, 8 + col * 18 - 1, 118 + row * 18 - 1)
    for col in range(9):
        slot(d, 8 + col * 18 - 1, 176 - 1)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)


if __name__ == "__main__":
    main()
