"""Draws the Oritech Extensions icon (32x32 pixel art, upscaled). Run: python3 -P icon.py"""
from PIL import Image, ImageDraw
N = 32
im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
px = im.load()
def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = c
H = lambda s: tuple(int(s[i:i+2], 16) for i in (0, 2, 4)) + (255,)

# rounded dark steel tile
bg, bg2, edge = H('1d2128'), H('232831'), H('3b424d')
rect(1, 0, 30, 31, bg); rect(0, 1, 31, 30, bg)
for y in range(1, 31):
    for x in range(1, 31):
        if y > 16: px[x, y] = bg2
for x in range(2, 30): px[x, 0] = edge; px[x, 31] = edge
for y in range(2, 30): px[0, y] = edge; px[31, y] = edge
px[1, 1] = px[30, 1] = px[1, 30] = px[30, 30] = edge

# door housing (top beam) with orange hazard stripe
rect(2, 3, 24, 6, H('4a515c')); rect(2, 3, 24, 3, H('6b7480')); rect(2, 6, 24, 6, H('2c3138'))
for x in range(3, 24):
    if (x // 2) % 2 == 0: px[x, 5] = H('d18b33')
    else: px[x, 5] = H('2c3138')


# side rails
rect(4, 7, 4, 28, H('3b424d')); rect(22, 7, 22, 28, H('3b424d'))

# slatted door: original orange (top), extension painted white, then sculk teal
bands = [
    (7, 14, H('e89a45'), H('ae6531'), H('7c3a14')),   # orange (Oritech default look)
    (15, 21, H('f2f2f2'), H('bdbdbd'), H('8a8a8a')),   # white paint
    (22, 28, H('2fb3b9'), H('0f6670'), H('083640')),   # sculk-ish teal paint
]
for y0, y1, hi, mid, lo in bands:
    for y in range(y0, y1 + 1):
        row = (y - y0) % 2
        c = hi if row == 0 else mid
        rect(5, y, 21, y, c)
        px[5, y] = lo; px[21, y] = lo
    rect(5, y1, 21, y1, lo)
# seams where extensions join
for y in (14, 21):
    for x in range(5, 22):
        px[x, y] = H('1d2128') if x % 3 == 0 else px[x, y]

# extend arrow on the right
arrow = H('ffd04a'); arrow_dk = H('b38040')
rect(27, 10, 28, 21, arrow); rect(27, 10, 27, 21, arrow_dk)
for i, (x0, x1) in enumerate([(25, 30), (26, 29), (27, 28)]):
    rect(x0, 22 + i, x1, 22 + i, arrow)



big = im.resize((512, 512), Image.NEAREST)
big.save('icon_512.png')  # upload this one to CurseForge / Modrinth
im.resize((128, 128), Image.NEAREST).save('icon_128.png')  # = common/src/main/resources/icon.png (in-game mod list)
