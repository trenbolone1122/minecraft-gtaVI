#!/usr/bin/env python3
"""Generates Ammu-Nation shop block assets: gun counter, gun rack and ammo box (models, blockstates, textures).
Run after gen_assets.py (the rack texture reuses the gun icons)."""
import json
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..", "..", "..", "..", "resources", "assets", "leonida")
TEX = os.path.join(ROOT, "textures", "block", "weapon")
MOD = os.path.join(ROOT, "models", "block", "weapon")
BST = os.path.join(ROOT, "blockstates", "weapon")
ITEMS = os.path.join(ROOT, "items", "weapon")
ICONS = os.path.join(ROOT, "textures", "item", "weapon")
for d in (TEX, MOD, BST, ITEMS):
    os.makedirs(d, exist_ok=True)


def save_json(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)


def icon(name, width, flip=False):
    im = Image.open(os.path.join(ICONS, name + "_icon.png")).convert("RGBA")
    bb = im.getbbox()
    im = im.crop(bb)
    h = max(1, round(im.height * width / im.width))
    im = im.resize((width, h), Image.LANCZOS)
    if flip:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return im


def noise(img, amount, seed):
    import random
    r = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            c = px[x, y]
            k = r.randint(-amount, amount)
            px[x, y] = (max(0, min(255, c[0] + k)), max(0, min(255, c[1] + k)), max(0, min(255, c[2] + k)), c[3])


# ---------------------------------------------------------------- textures
def tex_counter():
    S = 64
    # cabinet: black laminate, red stripe under the glass, steel kick plate
    side = Image.new("RGBA", (S, S), (24, 24, 28, 255))
    d = ImageDraw.Draw(side)
    d.rectangle((0, 0, S, 5), fill=(180, 22, 30, 255))
    d.rectangle((0, S - 7, S, S), fill=(110, 112, 118, 255))
    d.line((0, S - 7, S, S - 7), fill=(160, 162, 168, 255))
    noise(side, 4, 1)
    side.save(os.path.join(TEX, "counter_side.png"))
    # glass case top: red felt with a pistol, an SMG and price tags under glass, steel frame, sheen
    top = Image.new("RGBA", (S, S), (120, 20, 26, 255))
    noise(top, 6, 2)
    p = icon("pistol", 24)
    top.alpha_composite(p, (6, 8))
    top.alpha_composite(icon("smg", 30, True), (28, 34))
    top.alpha_composite(icon("pistol", 20, True), (36, 10))
    d = ImageDraw.Draw(top)
    for (x, y) in ((8, 26), (40, 24), (12, 52)):
        d.rectangle((x, y, x + 8, y + 4), fill=(240, 236, 220, 255))
        d.line((x + 2, y + 2, x + 6, y + 2), fill=(30, 30, 30, 255))
    sheen = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ds = ImageDraw.Draw(sheen)
    for i in range(3):
        ds.line((10 + i * 18, S, S, 10 + i * 18), fill=(255, 255, 255, 46), width=3)
    top.alpha_composite(sheen)
    d.rectangle((0, 0, S - 1, S - 1), outline=(190, 192, 198, 255), width=2)
    top.save(os.path.join(TEX, "counter_top.png"))
    # glass sides: lit case interior seen through glass
    gs = Image.new("RGBA", (S, 24), (120, 20, 26, 255))
    d = ImageDraw.Draw(gs)
    d.rectangle((0, 0, S, 9), fill=(70, 74, 80, 255))
    d.rectangle((0, 0, S, 2), fill=(250, 246, 220, 255))
    gs.alpha_composite(icon("pistol", 14), (6, 10))
    gs.alpha_composite(icon("pistol", 14, True), (40, 10))
    for i in range(2):
        d.line((8 + i * 30, 23, 22 + i * 30, 3), fill=(255, 255, 255, 60), width=2)
    d.rectangle((0, 0, S - 1, 23), outline=(190, 192, 198, 255), width=2)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(gs.resize((S, 24)), (0, 0))
    out = gs.resize((S, S // 2))
    full = Image.new("RGBA", (S, S), (0, 0, 0, 255))
    full.paste(out, (0, 0))
    full.save(os.path.join(TEX, "counter_glass.png"))


def tex_rack():
    S = 64
    img = Image.new("RGBA", (S, S), (58, 60, 64, 255))
    d = ImageDraw.Draw(img)
    for y in range(3, S, 6):
        for x in range(3, S, 6):
            d.point((x, y), fill=(26, 27, 30, 255))
    noise(img, 3, 3)
    d.rectangle((0, 0, S - 1, S - 1), outline=(30, 22, 16, 255), width=2)
    # hooks first, then a rifle and a shotgun across the top, pistol and SMG at the bottom
    for y in (12, 32):
        for x in (14, 48):
            d.rectangle((x, y, x + 2, y + 3), fill=(170, 170, 170, 255))
    for name, w, y in (("rifle", 48, 4), ("shotgun", 52, 27)):
        ic = icon(name, w)
        img.alpha_composite(ic, ((S - ic.width) // 2, y))
    img.alpha_composite(icon("pistol", 18), (8, 46))
    img.alpha_composite(icon("smg", 24, True), (34, 48))
    img.save(os.path.join(TEX, "gun_rack.png"))
    edge = Image.new("RGBA", (16, 16), (44, 32, 22, 255))
    noise(edge, 5, 4)
    edge.save(os.path.join(TEX, "gun_rack_edge.png"))


STENCIL = {
    "A": ["010", "101", "111", "101", "101"],
    "M": ["10001", "11011", "10101", "10001", "10001"],
    "O": ["111", "101", "101", "101", "111"],
}


def stencil(d, x, y, word, col, scale=1):
    for ch in word:
        g = STENCIL[ch]
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == "1":
                    d.rectangle((x + c * scale, y + r * scale, x + (c + 1) * scale - 1, y + (r + 1) * scale - 1), fill=col)
        x += (len(g[0]) + 1) * scale


def tex_ammo():
    S = 32
    od = (78, 90, 52, 255)
    side = Image.new("RGBA", (S, S), od)
    noise(side, 6, 5)
    d = ImageDraw.Draw(side)
    d.rectangle((0, 0, S - 1, S - 1), outline=(52, 60, 34, 255), width=1)
    # long side: yellow AMMO stencil in the lower half (the box is 8 px tall: v 16..32 maps there)
    stencil(d, 3, 19, "AMMO", (226, 196, 60, 255), 1)
    d.line((2, 27, 29, 27), fill=(226, 196, 60, 255))
    side.save(os.path.join(TEX, "ammo_box_side.png"))
    lid = Image.new("RGBA", (S, S), (64, 74, 42, 255))
    noise(lid, 5, 6)
    d = ImageDraw.Draw(lid)
    d.rectangle((0, 0, S - 1, S - 1), outline=(40, 46, 26, 255), width=1)
    d.rectangle((12, 12, 20, 19), fill=(40, 40, 40, 255))
    lid.save(os.path.join(TEX, "ammo_box_lid.png"))
    inner = Image.new("RGBA", (S, S), (26, 30, 18, 255))
    noise(inner, 4, 7)
    inner.save(os.path.join(TEX, "ammo_box_inner.png"))
    full_top = Image.new("RGBA", (S, S), (26, 30, 18, 255))
    d = ImageDraw.Draw(full_top)
    for i in range(6):
        d.rectangle((3 + i * 4, 6, 5 + i * 4, 26), fill=(196, 150, 60, 255))
        d.rectangle((3 + i * 4, 6, 5 + i * 4, 9), fill=(150, 152, 158, 255))
    full_top.save(os.path.join(TEX, "ammo_box_rounds.png"))


# ---------------------------------------------------------------- models
def face(tex, uv=None, cull=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def el(fr, to, faces):
    return {"from": fr, "to": to, "faces": faces}


def model_counter():
    cab = el([0, 0, 0], [16, 10, 16], {
        "north": face("#side", [0, 6, 16, 16], "north"), "south": face("#side", [0, 6, 16, 16], "south"),
        "east": face("#side", [0, 6, 16, 16], "east"), "west": face("#side", [0, 6, 16, 16], "west"),
        "down": face("#side", [0, 0, 16, 16], "down"), "up": face("#side", [0, 0, 16, 16])})
    glass = el([0.5, 10, 0.5], [15.5, 15.5, 15.5], {
        "north": face("#glass", [0, 0, 16, 8]), "south": face("#glass", [0, 0, 16, 8]),
        "east": face("#glass", [0, 0, 16, 8]), "west": face("#glass", [0, 0, 16, 8]),
        "up": face("#top", [0, 0, 16, 16])})
    save_json(os.path.join(MOD, "gun_counter.json"), {
        "parent": "minecraft:block/block",
        "textures": {"side": "leonida:block/weapon/counter_side", "glass": "leonida:block/weapon/counter_glass",
                     "top": "leonida:block/weapon/counter_top", "particle": "leonida:block/weapon/counter_side"},
        "elements": [cab, glass]})


def model_rack():
    panel = el([0, 0, 14], [16, 16, 16], {
        "north": face("#front", [0, 0, 16, 16]), "south": face("#edge", [0, 0, 16, 16], "south"),
        "east": face("#edge", [14, 0, 16, 16]), "west": face("#edge", [0, 0, 2, 16]),
        "up": face("#edge", [0, 14, 16, 16]), "down": face("#edge", [0, 0, 16, 2])})
    save_json(os.path.join(MOD, "gun_rack.json"), {
        "parent": "minecraft:block/block",
        "textures": {"front": "leonida:block/weapon/gun_rack", "edge": "leonida:block/weapon/gun_rack_edge",
                     "particle": "leonida:block/weapon/gun_rack_edge"},
        "display": {"gui": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.625] * 3}},
        "elements": [panel]})


def model_ammo(full):
    body_top = "#rounds" if not full else "#lid"
    els = [el([1, 0, 4], [15, 7, 12], {
        "north": face("#side", [0, 9, 16, 16]), "south": face("#side", [0, 9, 16, 16]),
        "east": face("#lid", [4, 9, 12, 16]), "west": face("#lid", [4, 9, 12, 16]),
        "down": face("#lid", [1, 4, 15, 12], "down"), "up": face("#inner" if not full else "#lid", [1, 4, 15, 12])})]
    if full:
        els.append(el([0.5, 7, 3.5], [15.5, 8, 12.5], {
            "north": face("#lid", [0, 0, 16, 1]), "south": face("#lid", [0, 0, 16, 1]),
            "east": face("#lid", [0, 0, 9, 1]), "west": face("#lid", [0, 0, 9, 1]),
            "up": face("#lid", [0.5, 3.5, 15.5, 12.5]), "down": face("#lid", [0, 0, 15, 9])}))
        els.append(el([6, 8, 7.5], [10, 8.75, 8.5], {
            "north": face("#inner"), "south": face("#inner"), "east": face("#inner"), "west": face("#inner"),
            "up": face("#inner")}))
    else:
        els.append(el([2, 1, 5], [14, 6.5, 11], {"up": face("#rounds", [2, 5, 14, 11])}))
        # lid leaning against the back
        els.append(el([0.5, 7, 12], [15.5, 16, 13], {
            "north": face("#lid", [0.5, 3.5, 15.5, 12.5]), "south": face("#lid", [0.5, 3.5, 15.5, 12.5]),
            "east": face("#lid", [0, 0, 1, 9]), "west": face("#lid", [0, 0, 1, 9]),
            "up": face("#lid", [0, 0, 15, 1])}))
    name = "ammo_box" if full else "ammo_box_empty"
    tex = {"side": "leonida:block/weapon/ammo_box_side", "lid": "leonida:block/weapon/ammo_box_lid",
           "inner": "leonida:block/weapon/ammo_box_inner", "rounds": "leonida:block/weapon/ammo_box_rounds",
           "particle": "leonida:block/weapon/ammo_box_side"}
    save_json(os.path.join(MOD, name + ".json"), {"parent": "minecraft:block/block", "textures": tex, "elements": els})


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def blockstate(name, extra=None):
    v = {}
    if extra is None:
        for f, r in ROT.items():
            v["facing=" + f] = {"model": "leonida:block/weapon/" + name, **({"y": r} if r else {})}
    else:
        prop, vals = extra
        for val, model in vals.items():
            for f, r in ROT.items():
                v["facing=%s,%s=%s" % (f, prop, val)] = {"model": "leonida:block/weapon/" + model, **({"y": r} if r else {})}
    save_json(os.path.join(BST, name + ".json"), {"variants": v})
    save_json(os.path.join(ITEMS, name + ".json"), {"model": {"type": "minecraft:model", "model": "leonida:block/weapon/" + name}})


if __name__ == "__main__":
    tex_counter()
    tex_rack()
    tex_ammo()
    model_counter()
    model_rack()
    model_ammo(True)
    model_ammo(False)
    blockstate("gun_counter")
    blockstate("gun_rack")
    blockstate("ammo_box", ("full", {"true": "ammo_box", "false": "ammo_box_empty"}))
    print("ok")
