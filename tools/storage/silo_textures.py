#!/usr/bin/env python3
"""Draws the Fluid Silo ("Cryo Hex") block textures: armoured casing cells, grid glass with teal studs (clear in
between, so the fluid shows), and the controller's teal core. Orange uses only extender_key colours.

    python3 tools/storage/silo_textures.py
"""
import math
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions/textures/block"
C = {"H": "#e09f67", "O": "#c36c2b", "I": "#b25020", "S": "#c63c33", "D": "#9c3215",
     "t": "#3597ad", "T": "#3ec9d5", "W": "#9cfff5", "g": "#1f4f5a",
     "1": "#161b21", "2": "#232a33", "3": "#343b49", "k": "#2a2e33"}


def edge(x, y):
    if y == 0:
        return "H" if x < 15 else "S"
    if y == 15 or x == 15:
        return "S"
    if x == 0:
        return "H"
    return None


def casing(x, y):
    e = edge(x, y)
    if e:
        return e
    if x in (1, 14) or y in (1, 14):
        return "D"
    cx, cy = (x - 2) % 6, (y - 2) % 6
    if cx == 0 or cy == 0:
        return "k"
    return "2" if (x // 6 + y // 6) % 2 else "3"


def glass(x, y):
    if x in (0, 15) or y in (0, 15):
        return "D"
    if (x - 1) % 5 == 0 and (y - 1) % 5 == 0:
        return "T"
    if (x - 1) % 5 == 0 or (y - 1) % 5 == 0:
        return "k"
    return None


def controller(x, y):
    e = edge(x, y)
    if e:
        return e
    if x in (1, 14) or y in (1, 14):
        return "D"
    r = math.hypot(x - 7.5, y - 7.5)
    if r < 1.8:
        return "W"
    if r < 3.2:
        return "T"
    if r < 4.2:
        return "g"
    if r < 5.6:
        return "O" if (int(math.degrees(math.atan2(y - 7.5, x - 7.5))) // 45) % 2 else "I"
    return "2"


def draw(fn):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            key = fn(x, y)
            if key:
                h = C[key].lstrip("#")
                image.putpixel((x, y), tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,))
    return image


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    draw(casing).save(OUT / "silo_casing.png")
    draw(glass).save(OUT / "silo_glass.png")
    front = draw(controller)
    front.save(OUT / "silo_controller_front.png")
    # Before the silo forms, the controller's core is dark (JNI-97); it lights up once the structure is complete.
    dark = {(31, 79, 90): (40, 46, 54), (62, 201, 213): (58, 64, 72), (156, 255, 245): (84, 90, 98)}
    off = front.copy()
    for y in range(off.height):
        for x in range(off.width):
            r, g, b, a = off.getpixel((x, y))
            if (r, g, b) in dark:
                off.putpixel((x, y), dark[(r, g, b)] + (a,))
    off.save(OUT / "silo_controller_front_off.png")


if __name__ == "__main__":
    main()
