#!/usr/bin/env python3
"""HD (128x128) versions of the Jason and Lucia skins.

Reads the 64x64 skins written by skingen.py, scales them 2x (same vanilla layout, so the player model
maps them unchanged) and repaints the head at full 16x16 per face: eyes, brows, beard, lips, hoops,
and HD versions of the bandanas and sunglasses. Usage: python3 hd.py OUT (after skingen.py OUT)."""
import os, sys, random
from PIL import Image
from skingen import hexc, mix, shade, SKIN_TONES, HAIR

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
BASE = os.path.join(OUT, "assets/leonida/textures/entity/character")

# 128x128 face rectangles (x0, y0) for the head (u=0,v=0) and hat (u=64,v=0) cuboids, 16x16 each
def faces(u):
    return {"top": (u + 16, 0), "bottom": (u + 32, 0), "right": (u, 16), "front": (u + 16, 16),
            "left": (u + 32, 16), "back": (u + 48, 16)}
HEAD = faces(0)
HAT = faces(64)


def put(img, rect, rows, pal, rng=None, jitter=0.0):
    x0, y0 = rect
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == "_":
                img.putpixel((x0 + x, y0 + y), (0, 0, 0, 0))
                continue
            c = pal[ch]
            if rng and jitter:
                c = shade(c, 1 + rng.uniform(-jitter, jitter))
            img.putpixel((x0 + x, y0 + y), c)


def clear(img, rect, x0=0, y0=0, x1=16, y1=16):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((rect[0] + x, rect[1] + y), (0, 0, 0, 0))


def side_px(img, rect, face, fx, y, c):
    """fx = distance from the face edge (0 = next to the face)."""
    x = fx if face == "left" else 15 - fx
    img.putpixel((rect[0] + x, rect[1] + y), c)


def up2(path):
    im = Image.open(path).convert("RGBA")
    return im.resize((128, 128), Image.NEAREST)


# ------------------------------------------------------------------ Jason
JASON_FACE = [
    "HHHHhHHHHHHhHHHH",
    "HHhHHhHHHHhHHHhH",
    "HHHHHdHhHHHHHHHH",
    "HHdtSSSSttSSHHdH",
    "HtSSSSSSSSSSSStH",
    "SSSSwwSSSSwwSSSS",
    "SSbbbbSSSSbbbbSS",
    "SbkkkksSSskkkkbS",
    "SsWeEWsSSsWEeWsS",
    "SSsssSSnnSSsssSS",
    "cSSSSSSnnSSSSSSc",
    "BcSSSSnNNnSSSScB",
    "BBcBMMMMMMMMBcBB",
    "BBBMMmmmmmmMMBBB",
    "BCBBBCBmmBCBBBCB",
    "CBCBCCBCCBCCBCBC",
]


def jason_hd(outfit):
    img = up2(os.path.join(BASE, "jason", outfit + ".png"))
    tone = SKIN_TONES["jason"]
    hair = HAIR["jason"]
    beard = hexc("3a281c")
    rng = random.Random(77 + len(outfit))
    pal = {"H": hair, "h": shade(hair, 1.3), "d": shade(hair, 0.72), "S": tone, "s": shade(tone, 0.88),
           "t": shade(tone, 1.06), "w": shade(tone, 0.94), "b": hexc("2a1b12"), "k": hexc("3a2a20"),
           "W": hexc("f4f1ea"), "e": hexc("5a8ab4"), "E": hexc("1e2a36"), "n": shade(tone, 0.82),
           "N": shade(tone, 0.66), "m": hexc("9a6656"), "M": mix(tone, beard, 0.85),
           "B": mix(tone, beard, 0.62), "C": mix(tone, beard, 0.8), "c": mix(tone, beard, 0.36)}
    put(img, HEAD["front"], JASON_FACE, pal, rng, 0.04)
    # stubble texture: break up the flat beard with random lighter and darker hairs
    fx0, fy0 = HEAD["front"]
    for y in range(11, 16):
        for x in range(16):
            c = img.getpixel((fx0 + x, fy0 + y))
            if c[:3] in (pal["B"][:3], pal["C"][:3]) and rng.random() < 0.3:
                img.putpixel((fx0 + x, fy0 + y), pal["C"] if rng.random() < 0.5 else pal["c"])
    # sides: beard along the jaw towards the ear, sideburns
    for face in ("right", "left"):
        for fx in range(9):
            for y in range(10, 16):
                lim = {10: 1, 11: 2, 12: 4, 13: 6, 14: 8, 15: 9}[y]
                if fx < lim:
                    side_px(img, HEAD[face], face, fx, y, pal["C"] if rng.random() < 0.5 else pal["B"])
        for y in range(6, 10):
            side_px(img, HEAD[face], face, 5, y, pal["B"])
        side_px(img, HEAD[face], face, 7, 8, shade(tone, 0.8))   # ear
        side_px(img, HEAD[face], face, 7, 9, shade(tone, 0.85))
    # chin underside
    bx, by = HEAD["bottom"]
    for y in range(16):
        for x in range(16):
            img.putpixel((bx + x, by + y), pal["C"] if rng.random() < 0.4 else pal["B"])
    # hat front: keep the hair lift on rows 0-1, a scruffy 3D beard edge, clear everything else
    hf = HAT["front"]
    clear(img, hf, 0, 6 if outfit == "swim" else 2, 16, 16)
    if True:
        for x in range(1, 15):
            if rng.random() < 0.7:
                img.putpixel((hf[0] + x, hf[1] + 15), pal["C"])
            if 2 <= x <= 13 and rng.random() < 0.35:
                img.putpixel((hf[0] + x, hf[1] + 14), pal["B"])
    if outfit == "heist":
        bandana(img, hexc("1a1a1a"), hexc("d8d8d8"), rng, paisley=False)
    if outfit == "swim":
        shades(img, hexc("1a1a1a"), hexc("3fd07a"), hexc("b8ffd8"))
    return img


# ------------------------------------------------------------------ Lucia
LUCIA_FACE = [
    "HHHhHHHkkHHHhHHH",
    "HHhHHHHkkHHHHhHH",
    "HHHHHtSSSStHHHHH",
    "HHHtSSSSSSSStHHH",
    "HHSSSSSSSSSSSSHH",
    "HtSSSSSSSSSSSStH",
    "HSbbbbSSSSbbbbSH",
    "kkkkkksSSskkkkkk",
    "HSWeEWsSSsWEeWSH",
    "SSsSsSSnnSSsSsSS",
    "SrSSSSSnnSSSSSrS",
    "SSrSSSnNNnSSSrSS",
    "SSSSSlLLLLlSSSSS",
    "SSSSSLLpLLLSSSSS",
    "SsSSSSlllSSSSSsS",
    "SSsSSSSSSSSSSsSS",
]


def lucia_hd(outfit):
    img = up2(os.path.join(BASE, "lucia", outfit + ".png"))
    tone = SKIN_TONES["lucia"]
    hair = HAIR["lucia"]
    hl = hexc("4a2e1e")
    hoop = hexc("e6c050")
    hoop_d = hexc("a8862a")
    rng = random.Random(91 + len(outfit))
    pal = {"H": hair, "h": hl, "S": tone, "s": shade(tone, 0.88), "t": shade(tone, 1.07), "b": hexc("1a0f0a"),
           "k": hexc("120a08"), "W": hexc("f2eee6"), "e": hexc("5a3420"), "E": hexc("140c08"),
           "n": shade(tone, 0.84), "N": shade(tone, 0.68), "r": mix(tone, hexc("c0605a"), 0.2),
           "l": hexc("7a3c3a"), "L": hexc("a4504e"), "p": hexc("c27270")}
    put(img, HEAD["front"], LUCIA_FACE, pal, rng, 0.03)
    # big gold hoops hanging from the ear lobes, drawn as rings on both side faces
    for face in ("right", "left"):
        side_px(img, HEAD[face], face, 7, 8, shade(tone, 0.8))
        ring = [(6, 10), (7, 10), (8, 10), (5, 11), (9, 11), (5, 12), (9, 12), (5, 13), (9, 13), (6, 14), (7, 14), (8, 14)]
        for (fx, y) in ring:
            side_px(img, HEAD[face], face, fx, y, hoop if y < 13 else hoop_d)
        side_px(img, HEAD[face], face, 7, 9, hoop)
    # hat layer: wavy strands framing the face and a centre part, plus volume to the crown
    hf = HAT["front"]
    clear(img, hf, 0, 0, 16, 16)
    strands = [
        "HHHhHHH__HHHhHHH",
        "HHhHH______HHhHH",
        "HH____________HH",
        "Hh____________hH",
        "HH____________HH",
        "_H____________H_",
        "H_____________hH",
        "HH____________H_",
        "_H____________HH",
        "Hh_____________H",
        "H_____________H_",
        "________________",
        "________________",
        "________________",
        "________________",
        "________________",
    ]
    put(img, hf, strands, {"H": hair, "h": hl}, rng, 0.06)
    if outfit == "heist":
        bandana(img, hexc("b02a2a"), hexc("e8d0c0"), rng, paisley=True)
    if outfit == "swim":
        shades(img, hexc("3a2230"), hexc("2a1a3a"), hexc("8a6ab0"))
    return img


# ------------------------------------------------------------------ shared HD overlays
def bandana(img, color, pat, rng, paisley):
    """Bandana tied over the nose: front rows 9-15 with a drape point, sides, knot at the back."""
    hf = HAT["front"]
    dark = shade(color, 0.78)
    for y in range(9, 16):
        for x in range(16):
            if y == 9 and not (3 <= x <= 12):
                continue
            c = color
            if paisley:
                if (x * 3 + y * 5) % 11 == 0 or ((x + 2 * y) % 7 == 0 and y % 2):
                    c = pat
                elif (x + y) % 9 == 0:
                    c = hexc("1a1a1a")
            else:
                if (x % 4 == 1 and y % 3 == 0) or (x % 4 == 3 and y % 3 == 2):
                    c = pat
            if y == 15 and 6 <= x <= 9:
                c = dark
            if x in (0, 15):
                c = dark
            img.putpixel((hf[0] + x, hf[1] + y), shade(c, 1 + rng.uniform(-0.05, 0.05)))
    for face in ("right", "left"):
        for fx in range(16):
            for y in range(10, 16):
                if y <= 11 or fx < 16 - (y - 10) * 2:
                    c = color if not (paisley and (fx + y) % 6 == 0) else pat
                    side_px(img, HAT[face], face, fx, y, shade(c, 0.95))
    bk = HAT["back"]
    for y in range(10, 13):
        for x in range(4, 12):
            img.putpixel((bk[0] + x, bk[1] + y), dark)
    for (x, y) in [(7, 13), (8, 13), (7, 14), (8, 14), (6, 15), (9, 15)]:
        img.putpixel((bk[0] + x, bk[1] + y), color)


def shades(img, frame, lens, glint):
    hf = HAT["front"]
    rows = [
        "ffffffffffffffff",
        "fLLLLLffffLLLLLf",
        "fLgLLLf..fLgLLLf",
        ".fLLLf....fLLLf.",
    ]
    put(img, (hf[0], hf[1] + 6), rows, {"f": frame, "L": lens, "g": glint})
    for face in ("right", "left"):
        for fx in range(12):
            side_px(img, HAT[face], face, fx, 6, frame)


def main():
    for o in ["everyday", "heist", "swim", "casual"]:
        p = os.path.join(BASE, "hd", "jason", o + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        jason_hd(o).save(p)
    for o in ["everyday", "heist", "swim", "jumpsuit"]:
        p = os.path.join(BASE, "hd", "lucia", o + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        lucia_hd(o).save(p)
    print("ok hd")


if __name__ == "__main__":
    main()
