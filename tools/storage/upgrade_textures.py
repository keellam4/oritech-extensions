#!/usr/bin/env python3
"""Draws the vault upgrade card item textures: a steel card with an Oritech-orange top edge, gold contacts at the
bottom and an emblem in the middle. Capacity cards take their colour from Oritech's ingot textures.

    python3 tools/storage/upgrade_textures.py <oritech assets/oritech/textures dir>
"""
import colorsys
import sys
from collections import Counter
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions/textures/item"
C = {"K": "#15181c", "a": "#4a4f56", "b": "#6c727a", "c": "#959ba3", "d": "#c4c9ce",
     "O": "#c36c2b", "o": "#e09f67", "D": "#9c3215", "y": "#d9a93a", "Y": "#f3d67a", "w": "#2b3038"}

CARD = [
    "................",
    "..KKKKKKKKKKKK..",
    ".KoOOOOOOOOOODK.",
    ".KcddddddddddbK.",
    ".Kcwwwwwwwwwwa K".replace(" ", "a")[:16],
    ".Kcwwwwwwwwwwab.".replace("ab.", "aK."),
    ".KcwwwwwwwwwwaK.",
    ".KcwwwwwwwwwwaK.",
    ".KcwwwwwwwwwwaK.",
    ".KcwwwwwwwwwwaK.",
    ".KcwwwwwwwwwwaK.",
    ".KcwwwwwwwwwwaK.",
    ".KcbbbbbbbbbbaK.",
    ".KKyKyKyKyKyKKK.",
    "...YKYKYKYKY....",
    "................",
]


def rgba(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def card():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(CARD):
        for x, ch in enumerate(row[:16]):
            if ch != ".":
                im.putpixel((x, y), rgba(C[ch]))
    return im


def put(im, pixels, colour):
    for x, y in pixels:
        im.putpixel((x, y), colour if isinstance(colour, tuple) else rgba(colour))


def ingot_colours(textures, name):
    img = Image.open(textures / "item" / f"{name}.png").convert("RGBA")
    counts = Counter(p[:3] for p in img.getdata() if p[3] > 0)
    coloured = [c for c, _ in counts.most_common(12)]
    coloured.sort(key=lambda c: colorsys.rgb_to_hls(*[v / 255 for v in c])[1])
    return [c + (255,) for c in (coloured[len(coloured) // 4], coloured[len(coloured) // 2], coloured[-2])]


def capacity(colours, level):
    im = card()
    dark, mid, light = colours
    # stacked bars, more for higher tiers
    for i in range(min(5, level + 1)):
        y = 10 - i * 1 - i
        if y < 5:
            break
        put(im, [(x, y) for x in range(5, 11)], mid)
        put(im, [(5, y), (10, y)], dark)
        put(im, [(x, y - 1) for x in range(6, 10)], light) if y - 1 >= 5 and i == min(4, level) else None
    return im


def main():
    textures = Path(sys.argv[1])
    for level, name in enumerate(["steel", "electrum", "energite", "duratium", "prometheum"]):
        capacity(ingot_colours(textures, f"{name}_ingot"), level).save(OUT / f"{name}_capacity_upgrade.png")

    im = card()  # void: a dark swirl
    put(im, [(6, 6), (7, 6), (8, 6), (9, 7), (9, 8), (8, 9), (7, 9), (6, 8), (6, 7)], "#7a3fa8")
    put(im, [(7, 7), (8, 7), (8, 8), (7, 8)], "#120818")
    put(im, [(10, 6), (5, 9), (10, 10), (5, 5)], "#b37ad9")
    im.save(OUT / "void_upgrade.png")

    im = card()  # pull: teal arrow pointing in (down)
    put(im, [(7, y) for y in range(5, 10)] + [(8, y) for y in range(5, 10)], "#3ec9d5")
    put(im, [(5, 8), (6, 9), (9, 9), (10, 8), (7, 10), (8, 10)], "#9cfff5")
    im.save(OUT / "pull_upgrade.png")

    im = card()  # push: orange arrow pointing out (up)
    put(im, [(7, y) for y in range(6, 11)] + [(8, y) for y in range(6, 11)], "#e87f4a")
    put(im, [(5, 7), (6, 6), (9, 6), (10, 7), (7, 5), (8, 5)], "#ffc05a")
    im.save(OUT / "push_upgrade.png")

    im = card()  # redstone: a lit dot with a wire
    put(im, [(7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)], "#b90000")
    put(im, [(7, 7), (8, 7), (7, 8), (8, 8)], "#ff4a35")
    put(im, [(x, 10) for x in range(4, 12)], "#5e0000")
    im.save(OUT / "redstone_upgrade.png")


if __name__ == "__main__":
    main()
