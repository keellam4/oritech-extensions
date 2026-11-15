#!/usr/bin/env python3
"""Draws the Cargo Vault ("Stasis Cell") block textures.

The orange parts use only colours from colormap/paint/extender_key, so the paint system recolours them like the
Machine Extender. The emitter strip above the glass is drawn dim: CargoVaultRenderer lights it up to show how full
the vault is. The stored item floats in the glass, drawn by the renderer too.

    python3 tools/storage/cargo_vault_textures.py
"""
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "common/src/main/resources/assets/oritech_extensions/textures/block"

COLOURS = {
    # orange, from Oritech's Machine Extender (all in extender_key)
    "H": "#e09f67", "h": "#dc8b4e", "E": "#e87f4a", "O": "#c36c2b", "I": "#b25020", "S": "#c63c33", "D": "#9c3215",
    # teal glow
    "t": "#3597ad", "T": "#3ec9d5", "W": "#9cfff5", "g": "#1f4f5a",
    # dark glass
    "1": "#161b21", "2": "#232a33", "3": "#343b49", "4": "#4c5466",
    # steel
    "a": "#4a4f56", "b": "#6c727a", "c": "#959ba3", "d": "#c4c9ce",
}

FRONT = [
    "ddcHHHHHHHHHHcbS",
    "dbaOOOOOOOOOOabS",
    "caDDDDDDDDDDDDaS",
    "HODggggggggggDOS",  # fill gauge: lit by the renderer
    "HOD1222222221DOS",
    "HOD1233333421DOS",
    "HOD1233334321DOS",
    "HOD1233343321DOS",
    "HOD1233433321DOS",
    "HOD1234333321DOS",
    "HOD1243333321DOS",
    "HOD1222222221DOS",
    "HODtTTWWWWTTtDOS",
    "caDDDDDDDDDDDDaS",
    "dbaOOOOOOOOOOabS",
    "cbaSSSSSSSSSSabS",
]


def frame(x, y):
    if y == 0:
        return "H" if x < 15 else "S"
    if y == 15 or x == 15:
        return "S"
    if x == 0:
        return "H"
    return None


def side(x, y):
    f = frame(x, y)
    if f:
        return f
    if x in (1, 14) or y in (1, 14):
        return "I"
    if 4 <= x <= 11 and 3 <= y <= 12:
        if y % 2 == 1:
            return "2"
        return "S" if x == 4 else "h" if x == 11 else "O"
    if (x, y) in ((3, 13), (12, 13)):
        return "T"
    return "O"


def top(x, y):
    if x in (0, 15) or y in (0, 15):
        return "c" if (x in (0, 15) and y in (0, 15)) else "b"
    if x in (1, 14) or y in (1, 14):
        return "O"
    r = math.hypot(x - 7.5, y - 7.5)
    if r < 1.6:
        return "W"
    if r < 2.8:
        return "T"
    if r < 3.7:
        return "a"
    return "c" if (x + y) % 2 == 0 and x < 8 and y < 8 else "b"


def draw(rows):
    image = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            c = COLOURS[ch]
            image.putpixel((x, y), tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (255,))
    return image


def grid(fn):
    return ["".join(fn(x, y) for x in range(16)) for y in range(16)]


def multi_front(size, cells):
    """Quad (16 px, 2x2) and Grid (32 px, 4x4) fronts: 1 px frame, 6 px cells, 2 px separators.
    Each cell: a dim 4 px gauge on its top row (lit by the renderer), dark glass below."""
    rows = []
    starts = [1 + 8 * i for i in range(cells)]
    for y in range(size):
        row = ""
        for x in range(size):
            cx = next((i for i, s0 in enumerate(starts) if s0 <= x < s0 + 6), None)
            cy = next((i for i, s0 in enumerate(starts) if s0 <= y < s0 + 6), None)
            last = size - 1
            corner = x in (0, last) and y in (0, last)
            if corner:
                row += "c"
            elif y == 0:
                row += "H"
            elif y == last or x == last:
                row += "S"
            elif x == 0:
                row += "H"
            elif cx is not None and cy is not None:
                lx, ly = x - starts[cx], y - starts[cy]
                if ly == 0:
                    row += "g" if 1 <= lx <= 4 else "D"
                elif lx in (0, 5) or ly == 5:
                    row += "1"
                elif lx + ly == 5:
                    row += "4"
                elif lx + ly in (4, 6):
                    row += "3"
                else:
                    row += "2"
            elif cx is None and cy is None:
                row += "b"  # where separators cross
            else:
                # separators: shadow then highlight, for depth
                coord = x if cx is None else y
                offset = (coord - 1) % 8
                row += "S" if offset == 6 else "h"
        rows.append(row)
    return rows


# Same windows as VaultLayout in Java: (glass x0, y0, x1, y1, gauge x0, gauge y, gauge x1), end exclusive.
DUAL_CELLS = [(1, 2, 15, 7, 3, 1, 13), (1, 10, 15, 15, 3, 9, 13)]
COMPACTING_CELLS = [(1, 2, 15, 7, 3, 1, 13), (1, 10, 7, 15, 2, 9, 6), (9, 10, 15, 15, 10, 9, 14)]


def with_arrow(rows):
    """Compacting fronts: a small steel arrow on the bar between the top and bottom windows."""
    rows = [list(r) for r in rows]
    for x in range(6, 10):
        rows[7][x] = "b"
    for x in (7, 8):
        rows[8][x] = "c"
    return ["".join(r) for r in rows]


def cells_front(cells, size=16):
    """A front with frame, separators, and for each window a dim gauge row and dark glass."""
    grid_ = [[None] * size for _ in range(size)]
    for gx0, gy0, gx1, gy1, ux0, uy, ux1 in cells:
        for x in range(gx0, gx1):
            grid_[uy][x] = "g" if ux0 <= x < ux1 else "D"
        w, h = gx1 - gx0, gy1 - gy0
        for y in range(gy0, gy1):
            for x in range(gx0, gx1):
                lx, ly = x - gx0, y - gy0
                if lx in (0, w - 1) or ly == h - 1:
                    grid_[y][x] = "1"
                elif lx + ly == w // 2 + 1:
                    grid_[y][x] = "4"
                elif lx + ly in (w // 2, w // 2 + 2):
                    grid_[y][x] = "3"
                else:
                    grid_[y][x] = "2"
    rows = []
    last = size - 1
    for y in range(size):
        row = ""
        for x in range(size):
            if x in (0, last) and y in (0, last):
                row += "c"
            elif y == 0 or (x == 0 and y < last):
                row += "H"
            elif y == last or x == last:
                row += "S"
            elif grid_[y][x] is not None:
                row += grid_[y][x]
            else:
                # Separators: shadow right below or right of a window, highlight otherwise.
                below = y > 0 and grid_[y - 1][x] is not None
                right = x > 0 and grid_[y][x - 1] is not None
                row += "S" if below or right else "h"
        rows.append(row)
    return rows


def draw_any(rows):
    size = len(rows)
    image = Image.new("RGBA", (size, size))
    for y, row in enumerate(rows):
        assert len(row) == size, row
        for x, ch in enumerate(row):
            c = COLOURS[ch]
            image.putpixel((x, y), tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (255,))
    return image


# Fluid Vault: the same front with clean glass (the fluid fills it) and a steel grille instead of the lower emitter.
FLUID_FRONT = [row if not 5 <= i <= 10 else row[:4] + "22222222" + row[12:] for i, row in enumerate(FRONT)]
FLUID_FRONT[12] = FRONT[12][:3] + "bcbcbcbcbc" + FRONT[12][13:]


def fluid_side(x, y):
    """A round steel fluid port in the middle of the side panel."""
    f = frame(x, y)
    if f:
        return f
    if x in (1, 14) or y in (1, 14):
        return "I"
    r = math.hypot(x - 7.5, y - 7.5)
    if r < 1.8:
        return "T"
    if r < 2.6:
        return "t"
    if r < 3.6:
        return "c" if x + y < 15 else "a"
    if r < 4.4:
        return "b"
    return "O"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    draw(FRONT).save(OUT / "cargo_vault_front.png")
    draw(grid(side)).save(OUT / "cargo_vault_side.png")
    draw(grid(top)).save(OUT / "cargo_vault_top.png")
    draw_any(multi_front(16, 2)).save(OUT / "quad_vault_front.png")
    draw_any(multi_front(32, 4)).save(OUT / "grid_vault_front.png")
    draw_any(cells_front(DUAL_CELLS)).save(OUT / "dual_vault_front.png")
    draw_any(with_arrow(cells_front(COMPACTING_CELLS))).save(OUT / "compacting_vault_front.png")
    draw_any(with_arrow(cells_front(DUAL_CELLS))).save(OUT / "dual_compacting_vault_front.png")
    draw(FLUID_FRONT).save(OUT / "fluid_vault_front.png")
    draw(grid(fluid_side)).save(OUT / "fluid_vault_side.png")


if __name__ == "__main__":
    main()
