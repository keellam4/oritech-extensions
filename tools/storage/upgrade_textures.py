#!/usr/bin/env python3
"""Draws the vault upgrade item textures as small machine parts:
capacity = compression canisters filled with the tier's metal, void = a caged singularity, pull/push = a matching
pair of emitters (teal chevrons in, orange chevrons out), redstone = a relay valve.

    python3 tools/storage/upgrade_textures.py
"""
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/oritech_extensions/textures/item"
from PIL import Image
import math
H=lambda s:tuple(int(s[i:i+2],16) for i in (1,3,5))+(255,)
P={'K':'#15181c','a':'#4a4f56','b':'#6c727a','c':'#959ba3','d':'#c4c9ce','e':'#e6eaee',
'O':'#c36c2b','o':'#e09f67','D':'#9c3215','t':'#26927f','T':'#3ec9d5','W':'#9cfff5',
'1':'#161b21','2':'#232a33','3':'#343b49','p':'#5b2a86','P':'#9b59d0','q':'#d6b3ff','k':'#050307',
'r':'#6b0d0d','R':'#d01f1f','Z':'#ff7a5c','y':'#8a4a1c','Y':'#c77a3a','u':'#e8a060'}
TIERS={'steel':['#5a5f66','#8f959c','#c9ced3'],'electrum':['#8a7a2a','#d9c24a','#fff2a0'],'energite':['#1f6e5a','#3fc79a','#a8ffdc'],
'duratium':['#4a2a6e','#8e5ad0','#d2b0ff'],'prometheum':['#7a1e1e','#e0503a','#ffb08a']}
def grid(rows):
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y,r in enumerate(rows):
        for x,ch in enumerate(r):
            if ch!='.': im.putpixel((x,y),H(P[ch]))
    return im
def outline(im):
    px=im.load(); add=[]
    for y in range(16):
        for x in range(16):
            if px[x,y][3]==0 and any(0<=x+dx<16 and 0<=y+dy<16 and px[x+dx,y+dy][3] and px[x+dx,y+dy][:3]!=H(P['K'])[:3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): add.append((x,y))
    for p in add: px[p]=H(P['K'])
    return im
# 1 Capacity: compression cell canister, tier-coloured compressed layers
def capacity(tier, level):
    c0,c1,c2=[H(x) for x in TIERS[tier]]
    rows=["......dc........",
          ".....bddcb......",
          "...bcdddddcb....",
          "...OooooooOD....",
          "...a12222221a...".replace('a...','a..')[:16],
          ]
    im=Image.new('RGBA',(16,16),(0,0,0,0)); px=im.load()
    def put(x,y,ch): px[x,y]=H(P[ch]) if isinstance(ch,str) else ch
    # terminal + cap
    for x in (7,8): put(x,1,'d')
    put(7,0,'c'); put(8,0,'b')
    for x in range(4,12): put(x,2,'c' if x<8 else 'b')
    for x in range(3,13): put(x,3,'d' if x<6 else 'c' if x<10 else 'b')
    for x in range(3,13): put(x,4,'o' if x<6 else 'O' if x<11 else 'D')
    # glass body rows 5..11, x 3..12 with steel side rails
    fill_rows=[2,3,4,5,7][level]
    for y in range(5,12):
        for x in range(3,13):
            if x in (3,12): put(x,y,'b' if x==3 else 'a'); continue
            filled = (11-y) < fill_rows
            if filled:
                top = (11-y) == fill_rows-1
                col = c2 if top else (c1 if (y+x)%5 else c0)
                if x in (4,11): col=c0
                put(x,y,col)
            else:
                put(x,y,'1' if x in (4,11) else ('3' if x==6 and y in (6,7) else '2'))
    for x in range(3,13): put(x,12,'o' if x<6 else 'O' if x<11 else 'D')
    for x in range(3,13): put(x,13,'c' if x<10 else 'b')
    for x in (5,6,9,10): put(x,14,'a')
    return outline(im)
# 2 Void: singularity in a steel ring with orange clamps
def void():
    im=Image.new('RGBA',(16,16),(0,0,0,0)); px=im.load()
    cx,cy=7.5,7.5
    for y in range(16):
        for x in range(16):
            r=math.hypot(x-cx,y-cy)
            # accretion disc: tilted ellipse
            ex=(x-cx); ey=(y-cy)*2.2
            er=math.hypot(ex,ey)
            if r<2.3: px[x,y]=H(P['k'])
            elif 3.2<=er<=5.6 and abs(y-cy)<2.6: px[x,y]=H(P['P']) if er<4.4 else H(P['p'])
            elif r<3.2: px[x,y]=H(P['p']) if r>2.8 else H(P['k'])
            elif 6.0<=r<7.2: px[x,y]=H(P['c']) if (x+y)<15 else H(P['a'])
    for (x,y) in [(2,2),(13,2),(2,13),(13,13),(3,2),(2,3),(12,2),(13,3),(2,12),(3,13),(13,12),(12,13)]:
        px[x,y]=H(P['O'])
    px[5,6]=H(P['q']); px[10,8]=H(P['T']); px[4,8]=H(P['q'])
    return outline(im)
# 5 Redstone: relay valve (vacuum tube) glowing red
def redstone():
    im=Image.new('RGBA',(16,16),(0,0,0,0)); px=im.load()
    def put(x,y,ch): px[x,y]=H(P[ch])
    # glass bulb
    for y in range(1,11):
        for x in range(4,12):
            r=math.hypot((x-7.5)/4.0,(y-5.5)/5.0)
            if r<=1.0:
                put(x,y,'3' if r>0.82 else '2')
    put(5,3,'e'); put(5,4,'d')
    # filament
    for (x,y) in [(7,8),(8,8),(7,7),(8,7),(6,6),(9,6),(6,5),(9,5),(7,4),(8,4)]: put(x,y,'R')
    put(7,5,'Z'); put(8,5,'Z'); put(7,6,'Z'); put(8,6,'Z')
    # base
    for y in (11,12):
        for x in range(4,12): put(x,y,'O' if y==11 else 'c')
    for x in (5,7,8,10): put(x,13,'a')
    put(4,11,'o'); put(11,11,'D')
    return outline(im)

def emitter(mode):
    im=Image.new('RGBA',(16,16),(0,0,0,0)); px=im.load()
    def put(x,y,ch): px[x,y]=H(P[ch])
    glow,bright,dim = ('T','W','t') if mode=='pull' else ('O','Z','D')
    # housing (shared): steel body with an aperture on top and an orange/teal band
    for y in range(9,15):
        for x in range(3,13): put(x,y,'c' if x<5 else 'b' if x<11 else 'a')
    for x in range(3,13): put(x,9,'d' if x<9 else 'c')
    for x in range(5,11): put(x,9,glow)                 # aperture rim
    for x in range(6,10): put(x,10,bright if mode=='push' else '1')
    put(5,10,'2'); put(10,10,'2')
    for x in range(4,12): put(x,12, 'O' if mode=='push' else 't')  # band
    put(4,13,'d'); put(11,13,'a')
    # chevrons above
    rows = [2,5] if True else []
    for i,y0 in enumerate((1,4)):
        col = (dim if i==0 else glow) if mode=='pull' else (glow if i==1 else bright)
        if mode=='pull':  # pointing down, into the aperture
            pts=[(5,y0),(6,y0+1),(7,y0+2),(8,y0+2),(9,y0+1),(10,y0)]
        else:             # pointing up, out of it
            pts=[(5,y0+2),(6,y0+1),(7,y0),(8,y0),(9,y0+1),(10,y0+2)]
        for x,y in pts: put(x,y,col)
    # a small item cube travelling
    cy = 7 if mode=='pull' else 7
    put(7,cy,'e' if mode=='pull' else 'u'); put(8,cy,'d' if mode=='pull' else 'Z')
    return outline(im)


def main():
    for level, tier in enumerate(["steel", "electrum", "energite", "duratium", "prometheum"]):
        capacity(tier, level).save(OUT / f"{tier}_capacity_upgrade.png")
    void().save(OUT / "void_upgrade.png")
    emitter("pull").save(OUT / "pull_upgrade.png")
    emitter("push").save(OUT / "push_upgrade.png")
    redstone().save(OUT / "redstone_upgrade.png")


if __name__ == "__main__":
    main()
