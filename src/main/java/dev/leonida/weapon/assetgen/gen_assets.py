#!/usr/bin/env python3
"""Generates the weapon module's item models, textures and icons (our own art, no third-party models).

Guns are built from boxes in model pixel units: x is width (centred on 8), y is up, z runs along the
barrel with the muzzle at low z. Each box face samples one cell of a shared palette texture.
Run: python3 src/main/java/dev/leonida/weapon/assetgen/gen_assets.py
"""
import json, os, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "..", "resources", "assets", "leonida")
TEX = os.path.join(ROOT, "textures", "item", "weapon")
MOD = os.path.join(ROOT, "models", "item", "weapon")
ITEMS = os.path.join(ROOT, "items", "weapon")
GUI = os.path.join(ROOT, "textures", "gui", "weapon")
for d in (TEX, MOD, ITEMS, GUI):
    os.makedirs(d, exist_ok=True)

# palette: index -> RGB
PAL = [
    (42, 43, 46),    # 0 gunmetal
    (60, 62, 66),    # 1 dark grey
    (92, 95, 100),   # 2 mid grey
    (141, 146, 152), # 3 light steel
    (28, 28, 30),    # 4 polymer black
    (122, 74, 38),   # 5 wood
    (90, 53, 24),    # 6 dark wood
    (180, 154, 106), # 7 tan
    (91, 97, 64),    # 8 olive
    (208, 96, 42),   # 9 orange
    (160, 32, 32),   # 10 red
    (74, 90, 58),    # 11 launcher green
    (200, 204, 208), # 12 chrome
    (38, 34, 31),    # 13 rubber
    (200, 160, 64),  # 14 brass
    (48, 80, 160),   # 15 lens
]
CELLS = 8  # 8x8 cells in a 32x32 texture, 4 px each, 2 model-uv units each


def palette_texture():
    rnd = random.Random(7)
    img = Image.new("RGBA", (32, 32))
    for i, c in enumerate(PAL):
        cx, cy = (i % CELLS) * 4, (i // CELLS) * 4
        for x in range(4):
            for y in range(4):
                n = rnd.randint(-7, 7)
                img.putpixel((cx + x, cy + y), tuple(max(0, min(255, v + n)) for v in c) + (255,))
    img.save(os.path.join(TEX, "palette.png"))


def uv(ci):
    u, v = (ci % CELLS) * 2, (ci // CELLS) * 2
    return [u + 0.25, v + 0.25, u + 1.75, v + 1.75]


def element(b):
    x0, y0, z0, x1, y1, z1, c = b[:7]
    e = {"from": [x0, y0, z0], "to": [x1, y1, z1],
         "faces": {f: {"uv": uv(c), "texture": "#p"} for f in ("north", "south", "east", "west", "up", "down")}}
    if len(b) > 7:
        axis, angle, ox, oy, oz = b[7]
        e["rotation"] = {"axis": axis, "angle": angle, "origin": [ox, oy, oz]}
    return e


def W(w):  # x range centred on 8
    return 8 - w / 2, 8 + w / 2


def box(w, y0, y1, z0, z1, c, rot=None, xoff=0.0):
    a, b = W(w)
    t = (a + xoff, y0, z0, b + xoff, y1, z1, c)
    return t + (rot,) if rot else t


GUNS = {
    "pistol": [  # Hawk P9: boxy polymer striker pistol (Glock-like)
        box(2.0, 9.0, 11.0, 1.5, 11.5, 0),   # slide
        box(2.1, 9.4, 10.8, 9.6, 9.9, 4), box(2.1, 9.4, 10.8, 10.2, 10.5, 4), box(2.1, 9.4, 10.8, 10.8, 11.1, 4),  # serrations
        box(0.2, 10.0, 10.9, 4.2, 7.0, 4, None, 1.05),  # ejection port
        box(1.6, 8.0, 9.0, 2.0, 11.5, 4),    # frame
        box(1.4, 7.4, 8.0, 2.0, 4.6, 4),     # accessory rail
        box(1.5, 7.6, 7.8, 3.0, 3.4, 1),
        box(0.8, 9.6, 10.4, 1.3, 1.5, 1),    # muzzle
        box(1.8, 3.6, 8.0, 8.2, 11.2, 4, ("x", 22.5, 8, 8, 10)),  # grip
        box(1.9, 3.2, 3.6, 8.2, 11.2, 1, ("x", 22.5, 8, 8, 10)),  # magazine base
        box(1.0, 6.8, 7.2, 5.0, 8.2, 4),     # square trigger guard
        box(1.0, 7.0, 8.0, 4.8, 5.2, 4),
        box(0.4, 7.2, 8.0, 6.6, 6.9, 3),     # trigger
        box(0.6, 11, 11.5, 2, 2.6, 3),       # front sight
        box(1.4, 11, 11.5, 10.6, 11.4, 4),   # rear sight
    ],
    "smg": [
        box(2.2, 8.5, 11.2, 0, 13, 4),       # receiver
        box(1.0, 9.4, 10.4, -3.5, 0, 1),     # barrel
        box(1.6, 9.0, 10.8, -1.5, 0, 0),     # barrel shroud
        box(1.6, 2.5, 8.5, 4.5, 6.5, 0, ("x", -22.5, 8, 8.5, 5.5)),  # mag
        box(1.8, 3.5, 8.5, 9.5, 11.8, 13, ("x", 22.5, 8, 8.5, 10.5)), # grip
        box(1.2, 9.2, 10.2, 13, 19, 1),      # folding stock bar
        box(1.8, 7.5, 10.6, 18.5, 19.5, 4),  # butt plate
        box(0.8, 11.2, 12, 1, 2, 3), box(1.2, 11.2, 12, 11, 12, 3),
    ],
    "rifle": [  # Duke 556: AR-15 / M4 style carbine
        box(0.8, 10.0, 10.8, -11, -3, 1),     # barrel
        box(1.2, 9.8, 11.0, -12.5, -11, 0),   # birdcage flash hider
        box(1.0, 10.8, 12.2, -4.2, -3.2, 0),  # front sight base
        box(0.4, 12.2, 13.6, -3.9, -3.5, 0),  # front sight post
        box(1.0, 9.0, 10.0, -4.2, -3.2, 0),   # gas block underside
        box(2.4, 8.8, 11.8, -3, 6.5, 4),      # handguard
        box(2.6, 9.2, 11.4, -2.5, -1.9, 1), box(2.6, 9.2, 11.4, -0.5, 0.1, 1), box(2.6, 9.2, 11.4, 1.5, 2.1, 1),
        box(2.6, 9.2, 11.4, 3.5, 4.1, 1), box(2.6, 9.2, 11.4, 5.4, 6.0, 1),  # handguard ribs
        box(2.8, 8.6, 12.0, 6.5, 7.3, 0),     # delta ring
        box(2.2, 10.4, 12.4, 7.3, 15.5, 0),   # upper receiver
        box(2.0, 8.2, 10.4, 7.6, 15.0, 0),    # lower receiver
        box(1.4, 12.4, 12.9, 7.3, 15.5, 1),   # flat-top rail
        box(1.2, 12.9, 14.0, 9.0, 9.8, 0), box(1.2, 12.9, 14.0, 13.8, 14.8, 0),  # carry handle legs
        box(1.4, 14.0, 14.6, 9.0, 14.8, 0),   # carry handle
        box(1.0, 14.6, 15.2, 13.9, 14.6, 0),  # rear aperture
        box(0.2, 10.8, 11.8, 10.5, 13.5, 2, None, 1.15),  # ejection port cover
        box(0.6, 11.2, 12.0, 14.2, 15.2, 1, None, 1.2),   # forward assist
        box(1.8, 12.0, 12.6, 15.5, 16.3, 1),  # charging handle
        box(1.8, 7.6, 8.4, 8.4, 11.2, 0),     # magwell
        box(1.4, 5.4, 8.2, 8.7, 10.9, 4),     # magazine
        box(1.4, 2.6, 5.8, 9.3, 11.5, 4, ("x", -22.5, 8, 5.6, 10.2)),  # magazine curve
        box(1.0, 7.2, 7.6, 11.4, 14.0, 0),    # trigger guard
        box(0.4, 7.6, 8.2, 12.2, 12.5, 3),    # trigger
        box(1.6, 4.0, 8.4, 14.6, 16.6, 4, ("x", 22.5, 8, 8.4, 15.6)),  # pistol grip
        box(1.2, 10.2, 11.4, 15.5, 22, 1),    # buffer tube
        box(1.8, 8.8, 11.8, 19.5, 24.5, 4),   # collapsible stock
        box(1.6, 7.6, 8.8, 20.5, 24.5, 4),
        box(0.6, 8.2, 8.8, 18.5, 20.5, 1),    # stock lever
        box(2.0, 7.4, 12.0, 24.5, 25.2, 13),  # butt pad
    ],
    "shotgun": [  # Moreland 850: pump action (Remington 870 style), wood furniture
        box(1.1, 10.8, 11.9, -12, 6, 1),      # barrel
        box(0.5, 11.9, 12.4, -11.8, -11.3, 12),  # bead sight
        box(1.0, 9.5, 10.6, -8.5, 6, 0),      # magazine tube
        box(1.2, 9.4, 10.7, -9.3, -8.5, 1),   # magazine cap
        box(1.3, 9.3, 12.0, -8.3, -7.6, 0),   # barrel clamp
        box(2.2, 8.7, 11.0, -5.5, 2.0, 5),    # pump forend
    ] + [box(2.5, 8.9, 10.8, z, z + 0.5, 6) for z in (-5.0, -3.8, -2.6, -1.4, -0.2, 1.0)] + [  # forend ribs
        box(0.3, 9.6, 10.2, 2.0, 6.5, 3, None, 0.9),  # action bar
        box(2.4, 8.2, 11.6, 6.0, 13.5, 0),    # receiver
        box(2.0, 11.6, 12.2, 6.0, 13.5, 0),   # rounded receiver top
        box(0.2, 10.0, 11.2, 7.0, 10.5, 4, None, 1.25),  # ejection port
        box(1.8, 7.8, 8.2, 6.5, 11, 4),       # loading port
        box(1.2, 6.6, 7.0, 10.5, 13.2, 0),    # trigger guard
        box(1.0, 7.0, 8.2, 10.5, 10.9, 0),
        box(0.4, 7.2, 8.2, 11.7, 12.0, 3),    # trigger
        box(2.2, 6.5, 10.8, 13.5, 19, 5, ("x", 22.5, 8, 9, 13.5)),  # wrist
        box(2.4, 6.0, 11.2, 18, 25, 5),       # stock
        box(2.6, 5.8, 11.4, 25, 26, 13),      # recoil pad
    ],
    "sniper": [
        box(0.9, 10.1, 11.0, -15, 2, 0),     # barrel
        box(1.3, 9.9, 11.2, -16, -15, 1),    # muzzle brake
        box(2.2, 8.4, 11.2, 1, 12, 6),       # fore stock
        box(2.4, 8.6, 11.8, 6, 14, 1),       # action
        box(1.8, 12.6, 14.6, 0, 12, 4),      # scope tube
        box(2.6, 12.2, 15.0, -1.5, 1.0, 4),  # scope objective bell
        box(2.2, 12.3, 14.9, 11.5, 13, 4),   # eyepiece
        box(2.0, 12.6, 14.6, -1.6, -1.5, 15),# lens
        box(1.0, 11.8, 12.6, 2, 3, 2), box(1.0, 11.8, 12.6, 9, 10, 2),  # mounts
        box(0.8, 10.4, 11.2, 12, 13, 12, None, 1.6),  # bolt handle
        box(1.4, 7.0, 8.6, 7.5, 10, 0),      # mag
        box(2.2, 5.5, 10.6, 14, 18, 6, ("x", 22.5, 8, 8.5, 14)),  # wrist
        box(2.4, 5.0, 11.4, 18, 27, 6),      # stock
        box(2.6, 4.8, 11.6, 27, 28, 13),     # pad
        box(2.2, 11.4, 12.4, 19, 25, 6),     # cheek riser
    ],
    "rpg": [
        box(3.0, 9.0, 12.0, -10, 20, 11),    # tube
        box(3.4, 8.8, 12.2, -10.5, -9.5, 8), # front ring
        box(3.4, 8.8, 12.2, 19.5, 20.5, 8),  # rear ring
        box(3.6, 8.6, 12.4, -15, -10.5, 8),  # warhead body
        box(2.6, 9.1, 11.9, -16, -15, 9),    # warhead nose
        box(1.6, 4.5, 9.0, 1, 3, 4, ("x", 22.5, 8, 9, 2)),    # front grip
        box(1.6, 4.0, 9.0, 8, 10, 4, ("x", 22.5, 8, 9, 9)),   # rear grip
        box(1.0, 7.8, 9.0, 4.6, 7.4, 1),     # trigger housing
        box(1.6, 12.0, 14.2, 5, 8, 1, None, -1.4),  # sight box
        box(0.8, 13.0, 13.8, 4.8, 5.1, 15, None, -1.4),
    ],
}

# display transforms. Model barrel points to -z (north).
def display(length, fp_scale=0.85, gui_scale=None):
    gs = gui_scale or min(1.0, 15.0 / length)
    return {
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [-1.0, 2.6, -2.0], "scale": [fp_scale] * 3},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [-1.0, 2.6, -2.0], "scale": [fp_scale] * 3},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 1.0], "scale": [0.75] * 3},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 1.5, 1.0], "scale": [0.75] * 3},
        "ground": {"rotation": [0, 90, 0], "translation": [0, 1, 0], "scale": [0.5] * 3},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [gs] * 3},
        "head": {"rotation": [0, 90, 0], "translation": [0, 10, 0], "scale": [0.8] * 3},
        "gui": {"rotation": [0, 90, 0], "translation": [0, -1.5, 0], "scale": [gs] * 3},
    }


FP_SCALE = {"pistol": 0.6, "smg": 0.58, "rifle": 0.52, "shotgun": 0.52, "sniper": 0.5, "rpg": 0.52}


def gun_model(name, boxes):
    zs = [b[2] for b in boxes] + [b[5] for b in boxes]
    length = max(zs) - min(zs)
    model = {"textures": {"p": "leonida:item/weapon/palette", "particle": "leonida:item/weapon/palette"},
             "gui_light": "front",
             "elements": [element(b) for b in boxes],
             "display": display(length, FP_SCALE[name])}
    with open(os.path.join(MOD, name + ".json"), "w") as f:
        json.dump(model, f, indent=1)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def gun_icon(name, boxes, size=64):
    """Side-view icon (muzzle to the left) rendered from the same boxes, used in GUI, wheel and toasts."""
    zs = [b[2] for b in boxes] + [b[5] for b in boxes]
    ys = [b[1] for b in boxes] + [b[4] for b in boxes]
    z0, z1, y0, y1 = min(zs), max(zs), min(ys), max(ys)
    span = max(z1 - z0, (y1 - y0)) + 1
    s = (size - 4) / span
    ox = 2 + ((size - 4) - (z1 - z0) * s) / 2
    oy = 2 + ((size - 4) - (y1 - y0) * s) / 2
    big = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    import math
    # draw back to front: wider boxes first so details sit on top
    for b in sorted(boxes, key=lambda b: -(b[3] - b[0])):
        bx0, by0, bz0, bx1, by1, bz1, c = b[:7]
        pts = [(bz0, by0), (bz1, by0), (bz1, by1), (bz0, by1)]
        if len(b) > 7 and b[7] and b[7][0] == "x":
            _, ang, _, oyy, ozz = b[7]
            a = math.radians(ang)
            pts = [(ozz + (z - ozz) * math.cos(a) - (y - oyy) * math.sin(a),
                    oyy + (z - ozz) * math.sin(a) + (y - oyy) * math.cos(a)) for z, y in pts]
        poly = [((ox + (z - z0) * s) * 4, (size - (oy + (y - y0) * s)) * 4) for z, y in pts]
        d.polygon(poly, fill=PAL[c] + (255,), outline=shade(PAL[c], 1.5) + (255,), width=3)
    img = big.resize((size, size), Image.LANCZOS)
    # dark outline for readability on any background
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    a = img.split()[3]
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        sh = Image.new("RGBA", img.size, (10, 10, 12, 0))
        sh.putalpha(a.point(lambda v: 200 if v > 60 else 0))
        out.alpha_composite(sh, (dx, dy)) if dx >= 0 and dy >= 0 else out.alpha_composite(sh.crop((-dx, -dy, size, size)), (0, 0))
    out.alpha_composite(img)
    out.save(os.path.join(TEX, name + "_icon.png"))


def item_def(name, hand_model, gui_model):
    """In GUI, ground and fixed frames show the flat icon; in hand the 3D model."""
    d = {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                   "cases": [{"when": ["gui", "ground", "fixed"], "model": {"type": "minecraft:model", "model": gui_model}}],
                   "fallback": {"type": "minecraft:model", "model": hand_model}}}
    with open(os.path.join(ITEMS, name + ".json"), "w") as f:
        json.dump(d, f, indent=1)


# First person poses for melee: smaller than vanilla tools. The bat rests upright, ready to swing;
# the knife points forward like a stab. Rotation z turns the sprite in its own plane before the y turn.
MELEE_DISPLAY = {
    "bat": {"r": [0, -90, 30], "t": [-1.0, 1.6, -2.5], "s": 0.5, "tp": [0, -90, 55]},
    "knife": {"r": [0, -90, -5], "t": [-0.5, 3.6, -1.0], "s": 0.32, "tp": [0, -90, 20]},
}


def flat_model(name, tex, parent="minecraft:item/generated"):
    model = {"parent": parent, "textures": {"layer0": tex}}
    m = MELEE_DISPLAY.get(name)
    if m:
        lr = [m["r"][0], -m["r"][1], -m["r"][2]]
        model["display"] = {
            "firstperson_righthand": {"rotation": m["r"], "translation": m["t"], "scale": [m["s"]] * 3},
            "firstperson_lefthand": {"rotation": lr, "translation": m["t"], "scale": [m["s"]] * 3},
            "thirdperson_righthand": {"rotation": m["tp"], "translation": [0, 4.0, 0.5], "scale": [0.85] * 3},
            "thirdperson_lefthand": {"rotation": [m["tp"][0], -m["tp"][1], -m["tp"][2]], "translation": [0, 4.0, 0.5], "scale": [0.85] * 3},
        }
    with open(os.path.join(MOD, name + ".json"), "w") as f:
        json.dump(model, f, indent=1)


def simple_item(name, parent="minecraft:item/generated"):
    flat_model(name, "leonida:item/weapon/" + name, parent)
    with open(os.path.join(ITEMS, name + ".json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "leonida:item/weapon/" + name}}, f, indent=1)


# ---- 16x16 sprites for melee and ammo ----
def sprite(name, pixels, colors):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(pixels):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colors[ch] + (255,))
    img.save(os.path.join(TEX, name + ".png"))


SPRITES = {
    "bat": ([
        "................",
        "..............kk",
        ".............kWk",
        "............kWwk",
        "...........kWwk.",
        "..........kWwk..",
        ".........kWwk...",
        "........kWwk....",
        ".......kWwk.....",
        "......kwwk......",
        ".....kwwk.......",
        "....kttk........",
        "...kttk.........",
        "..kttk..........",
        ".kkkk...........",
        ".kk.............",
    ], {"k": (40, 26, 14), "W": (226, 188, 130), "w": (196, 150, 90), "t": (30, 30, 32)}),
    "knife": ([
        "................",
        "..............k.",
        ".............kSk",
        "............kSsk",
        "...........kSsk.",
        "..........kSsk..",
        ".........kSsk...",
        "........kSsk....",
        ".......kSsk.....",
        "....kk.kssk.....",
        ".....kgkkk......",
        "......kbk.......",
        ".....kbbk.......",
        "....kbbk........",
        "...kbbk.........",
        "...kkk..........",
    ], {"k": (20, 20, 22), "S": (230, 234, 238), "s": (160, 166, 172), "g": (90, 92, 96), "b": (44, 40, 36)}),
    "ammo_pistol": ([
        "................",
        "................",
        "................",
        "...kkkkkkkkkk...",
        "..kyyyyyyyyyyk..",
        "..kyyyyyyyyyyk..",
        "..kddddddddddk..",
        "..kdwwwwwwwwdk..",
        "..kdwkwkwkwwdk..",
        "..kdwwwwwwwwdk..",
        "..kddddddddddk..",
        "..kddddddddddk..",
        "...kkkkkkkkkk...",
        "................",
        "................",
        "................",
    ], {"k": (20, 20, 22), "y": (210, 170, 60), "d": (60, 70, 90), "w": (220, 220, 220)}),
    "ammo_smg": ([
        "................",
        "......kkkk......",
        ".....kbbbbk.....",
        ".....kbbbbk.....",
        ".....kmmmmk.....",
        ".....kmmmmk.....",
        ".....kmmmmk.....",
        ".....kmmmmk.....",
        ".....kmmmmk.....",
        ".....kmmmmk.....",
        ".....kmmmmk.....",
        ".....kmmmmk.....",
        ".....kmmmmk.....",
        ".....klllllk....",
        "......kkkkkk....",
        "................",
    ], {"k": (15, 15, 17), "b": (200, 160, 64), "m": (50, 52, 56), "l": (80, 82, 86)}),
    "ammo_rifle": ([
        "................",
        ".....kkkk.......",
        "....kbbbbk......",
        "....kmmmmk......",
        "....kmmmmk......",
        "....kmmmmmk.....",
        "....kmmmmmk.....",
        ".....kmmmmk.....",
        ".....kmmmmmk....",
        ".....kmmmmmk....",
        "......kmmmmk....",
        "......kmmmmmk...",
        "......kmmmmmk...",
        ".......kllllk...",
        ".......kkkkkk...",
        "................",
    ], {"k": (15, 15, 17), "b": (200, 160, 64), "m": (70, 66, 50), "l": (90, 86, 70)}),
    "ammo_shotgun": ([
        "................",
        "................",
        "..rrr..rrr..rrr.",
        ".rRRr.rRRr.rRRr.",
        ".rRRr.rRRr.rRRr.",
        ".rRRr.rRRr.rRRr.",
        ".rRRr.rRRr.rRRr.",
        ".rRRr.rRRr.rRRr.",
        ".rRRr.rRRr.rRRr.",
        ".rRRr.rRRr.rRRr.",
        ".bBBb.bBBb.bBBb.",
        ".bBBb.bBBb.bBBb.",
        ".bbbb.bbbb.bbbb.",
        "................",
        "................",
        "................",
    ], {"r": (110, 20, 20), "R": (190, 40, 36), "b": (150, 110, 40), "B": (214, 176, 80)}),
    "ammo_sniper": ([
        "................",
        "...k...k...k....",
        "..kgk.kgk.kgk...",
        "..kgk.kgk.kgk...",
        "..kBk.kBk.kBk...",
        "..kBk.kBk.kBk...",
        "..kBk.kBk.kBk...",
        "..kBk.kBk.kBk...",
        "..kBk.kBk.kBk...",
        "..kBk.kBk.kBk...",
        "..kBk.kBk.kBk...",
        "..kbk.kbk.kbk...",
        "..kkk.kkk.kkk...",
        "................",
        "................",
        "................",
    ], {"k": (30, 24, 10), "g": (120, 120, 126), "B": (214, 176, 80), "b": (150, 110, 40)}),
    "ammo_rocket": ([
        "................",
        "..............k.",
        ".............krk",
        "............krRk",
        "...........kgRk.",
        "..........kggk..",
        ".........kggk...",
        "........kggk....",
        ".......kggk.....",
        "......kggk......",
        "..kk.kggk.......",
        "..kfkggk........",
        "...kfgk.........",
        "..kfkfk.........",
        ".kfk.kfk........",
        ".kk...kk........",
    ], {"k": (16, 18, 14), "r": (150, 40, 30), "R": (200, 70, 40), "g": (84, 100, 64), "f": (120, 124, 110)}),
}


def gui_textures():
    """Weapon wheel ring, highlighted segment and crosshair."""
    import math
    S = 256
    ring = Image.new("RGBA", (S * 2, S * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(ring)
    c = S
    R, r = S - 4, int(S * 0.38)
    d.ellipse((c - R, c - R, c + R, c + R), fill=(12, 14, 18, 175))
    d.ellipse((c - r, c - r, c + r, c + r), fill=(0, 0, 0, 0))
    for i in range(8):
        a = math.radians(i * 45 - 90 - 22.5)
        d.line((c + r * math.cos(a), c + r * math.sin(a), c + R * math.cos(a), c + R * math.sin(a)), fill=(255, 255, 255, 60), width=4)
    d.ellipse((c - R, c - R, c + R, c + R), outline=(255, 255, 255, 90), width=4)
    d.ellipse((c - r, c - r, c + r, c + r), outline=(255, 255, 255, 90), width=4)
    ring.resize((S, S), Image.LANCZOS).save(os.path.join(GUI, "wheel.png"))
    # highlighted segment pointing up (index 0); the client rotates nothing, so make 8 images
    for i in range(8):
        seg = Image.new("RGBA", (S * 2, S * 2), (0, 0, 0, 0))
        d = ImageDraw.Draw(seg)
        a0, a1 = i * 45 - 90 - 22.5, i * 45 - 90 + 22.5
        d.pieslice((c - R, c - R, c + R, c + R), a0, a1, fill=(255, 255, 255, 70))
        d.arc((c - R, c - R, c + R, c + R), a0, a1, fill=(120, 200, 255, 255), width=10)
        d.ellipse((c - r, c - r, c + r, c + r), fill=(0, 0, 0, 0))
        seg.resize((S, S), Image.LANCZOS).save(os.path.join(GUI, "wheel_sel_%d.png" % i))


def scope_texture():
    """Sniper scope: transparent lens with a soft dark rim, black outside the circle (reticle is drawn by code)."""
    import math
    S = 512
    img = Image.new("RGBA", (S, S), (0, 0, 0, 255))
    px = img.load()
    c = (S - 1) / 2.0
    R = S * 0.49
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - c, y - c) / R
            if r < 0.80:
                a = 0
            elif r < 1.0:
                t = (r - 0.80) / 0.20
                a = int(255 * t * t)
            else:
                a = 255
            px[x, y] = (0, 0, 0, a)
    img.save(os.path.join(GUI, "scope.png"))


if __name__ == "__main__":
    palette_texture()
    for n, b in GUNS.items():
        gun_model(n, b)
        gun_icon(n, b)
        flat_model(n + "_icon", "leonida:item/weapon/%s_icon" % n)
        item_def(n, "leonida:item/weapon/" + n, "leonida:item/weapon/%s_icon" % n)
    for n, (px, cols) in SPRITES.items():
        sprite(n, px, cols)
        simple_item(n, "minecraft:item/handheld" if n in ("bat", "knife") else "minecraft:item/generated")
    gui_textures()
    scope_texture()
    print("ok")
