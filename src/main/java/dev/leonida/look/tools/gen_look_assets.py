#!/usr/bin/env python3
"""Generate every look block asset: textures (albedo + labPBR _n/_s), block models, blockstates, item definitions and
lang. Run from anywhere:  python3 src/main/java/dev/leonida/look/tools/gen_look_assets.py [--only prefix] [--sheet out.png]

Block ids must match dev.leonida.look.LookBlocks (game test LookClientTest checks every state has a model)."""
import json
import math
import os
import shutil
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import materials as M  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, *[".."] * 7))
A = os.path.join(ROOT, "src/main/resources/assets/leonida")
TEX = os.path.join(A, "textures/block/look")
MOD = os.path.join(A, "models/block/look")
IMOD = os.path.join(A, "models/item/look")
BS = os.path.join(A, "blockstates/look")
ITEMS = os.path.join(A, "items/look")
LANG = os.path.join(A, "lang/parts/look.json")

ONLY = None
SHEET = None
args = sys.argv[1:]
if "--only" in args:
    ONLY = args[args.index("--only") + 1]
if "--sheet" in args:
    SHEET = args[args.index("--sheet") + 1]

made_tex = {}
lang = {"itemGroup.leonida.look": "Leonida Materials"}
blocks = []


def T(name):
    return "leonida:block/look/" + name


def MR(name):
    return "leonida:block/look/" + name


def tex(name, fn, *a, **k):
    if name in made_tex:
        return made_tex[name]
    if ONLY is None or name.startswith(ONLY):
        m = fn(name, *a, **k)
        m.save(os.path.join(TEX, name))
        made_tex[name] = m
    else:
        made_tex[name] = None
    return made_tex[name]


def model(name, data):
    with open(os.path.join(MOD, name + ".json"), "w") as f:
        json.dump(data, f, indent=1)


def state(name, data):
    with open(os.path.join(BS, name + ".json"), "w") as f:
        json.dump(data, f, indent=1)


def item(name, model_name=None, flat=None):
    if flat:
        with open(os.path.join(IMOD, name + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": T(flat)}}, f, indent=1)
        ref = "leonida:item/look/" + name
    else:
        ref = MR(model_name or name)
    with open(os.path.join(ITEMS, name + ".json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": ref}}, f, indent=1)


def title(name):
    words = name.split("_")
    grimy = words[-1] == "grimy"
    if grimy:
        words = words[:-1]
    t = " ".join(w.capitalize() for w in words)
    return t + (" (Grimy)" if grimy else "")


def block(name, display=None):
    blocks.append(name)
    lang["block.leonida.look." + name] = display or title(name)

# ------------------------------------------------------------------ model helpers


def cube_all(name, texname, rot4=False):
    model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": T(texname)}})
    if rot4:
        state(name, {"variants": {"": [{"model": MR(name)}, {"model": MR(name), "y": 90},
                                       {"model": MR(name), "y": 180}, {"model": MR(name), "y": 270}]}})
    else:
        state(name, {"variants": {"": {"model": MR(name)}}})
    item(name)
    block(name)


def cube_bt(name, top, side, bottom=None):
    model(name, {"parent": "minecraft:block/cube_bottom_top",
                 "textures": {"top": T(top), "side": T(side), "bottom": T(bottom or side)}})


def slab_stairs(base, top, side=None, bottom=None, slab=True, stairs=True, base_model=None):
    side = side or top
    bottom = bottom or top
    tx = {"bottom": T(bottom), "top": T(top), "side": T(side)}
    full = base_model or base
    if slab:
        n = base + "_slab"
        model(n, {"parent": "minecraft:block/slab", "textures": tx})
        model(n + "_top", {"parent": "minecraft:block/slab_top", "textures": tx})
        state(n, {"variants": {"type=bottom": {"model": MR(n)}, "type=top": {"model": MR(n + "_top")},
                               "type=double": {"model": MR(full)}}})
        item(n)
        block(n)
    if stairs:
        n = base + "_stairs"
        model(n, {"parent": "minecraft:block/stairs", "textures": tx})
        model(n + "_inner", {"parent": "minecraft:block/inner_stairs", "textures": tx})
        model(n + "_outer", {"parent": "minecraft:block/outer_stairs", "textures": tx})
        state(n, stairs_state(n))
        item(n)
        block(n)


def stairs_state(n):
    with open(os.path.join(HERE, "stairs_template.json")) as f:
        t = f.read()
    t = t.replace("minecraft:block/oak_stairs", MR(n))
    return json.loads(t)


FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_state(name, model_for=lambda f: None, extra=None):
    v = {}
    for f, y in FACING_Y.items():
        if extra:
            for key, mname in extra.items():
                e = {"model": MR(mname)}
                if y:
                    e["y"] = y
                v[f"facing={f},{key}"] = e
        else:
            e = {"model": MR(name)}
            if y:
                e["y"] = y
            v[f"facing={f}"] = e
    state(name, {"variants": v})


def axis_state(name, mname=None):
    m = MR(mname or name)
    state(name, {"variants": {"axis=z": {"model": m}, "axis=x": {"model": m, "y": 90}}})


def faces(texmap, cull=True, uv=None, emission=None, tint=None):
    out = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        t = texmap.get(d, texmap.get("*"))
        if t is None:
            continue
        f = {"texture": t}
        if cull and (cull is True or d in cull):
            f["cullface"] = d
        if uv and d in uv:
            f["uv"] = uv[d]
        if tint is not None:
            f["tintindex"] = tint
        out[d] = f
    return out


def el(fr, to, fcs, emission=None, shade=None, rotation=None):
    e = {"from": fr, "to": to, "faces": fcs}
    if emission:
        e["light_emission"] = emission
    if shade is not None:
        e["shade"] = shade
    if rotation:
        e["rotation"] = rotation
    return e

# ------------------------------------------------------------------ palms


def post_el(w, tex_side, tex_end, cx=8.0, cz=8.0):
    a, b = (16 - w) / 2, (16 + w) / 2
    x0, x1, z0, z1 = cx - w / 2, cx + w / 2, cz - w / 2, cz + w / 2
    side_uv = [a, 0, b, 16]
    return el([x0, 0, z0], [x1, 16, z1], {
        "north": {"texture": tex_side, "uv": side_uv}, "south": {"texture": tex_side, "uv": side_uv},
        "east": {"texture": tex_side, "uv": side_uv}, "west": {"texture": tex_side, "uv": side_uv},
        "up": {"texture": tex_end, "uv": [a, a, b, b]}, "down": {"texture": tex_end, "uv": [a, a, b, b]}})


PALM_STYLE = {
    # pitches per segment (deg, + up), segment lengths (px), half-plane roll (deg), plane width (px)
    "royal": dict(crown=11, pitches=[40, 14, -16, -40], lengths=[7, 9, 10, 9], roll=18, width=18, up=2),
    "coconut": dict(crown=12, pitches=[28, 0, -28, -56], lengths=[7, 10, 10, 10], roll=30, width=19, up=1),
    "sabal": dict(crown=14, pitches=None, lengths=None, roll=22, width=22, up=3),
}


def palm_crown_model(kind):
    import random
    import palm as P
    st = PALM_STYLE[kind]
    rnd = random.Random(kind)
    els = []
    tf = "#frond"
    boot = "#boot"
    els.append(el([5, 0, 5], [11, 7, 11], faces({"*": boot}, cull=False)))
    n = st["crown"]
    for i in range(n):
        yaw = i * 360.0 / n + rnd.uniform(-9, 9)
        dist = 2.5
        sx = 8 - dist * math.sin(math.radians(yaw))
        sz = 8 - dist * math.cos(math.radians(yaw))
        if kind == "sabal":
            pitch = rnd.choice([50, 35, 20, 5, -10, -25])
            els += P.frond([sx, 7.5, sz], yaw, [pitch + 15, pitch], [5, 16], 0, st["roll"], tf,
                           widths=[1.5, st["width"]], v0=0, v1=16)
            continue
        jit = rnd.uniform(-8, 8)
        els += P.frond([sx, 7.5, sz], yaw, [p + jit for p in st["pitches"]], st["lengths"], st["width"], st["roll"], tf)
    # young upright fronds / spear
    for j in range(st["up"]):
        yaw = rnd.uniform(0, 360)
        if kind == "sabal":
            els += P.frond([8, 7, 8], yaw, [70, 55], [5, 13], 0, st["roll"], tf, widths=[1.5, 18])
        else:
            els += P.frond([8, 7, 8], yaw, [75, 60, 40], [5, 6, 6], 12, 12, tf)
    if kind == "coconut":
        for (cx, cz) in [(5.5, 7), (9, 5.5), (10, 9.5), (7, 10.5)]:
            els.append(el([cx - 2, 2.5, cz - 2], [cx + 2, 6.5, cz + 2], faces({"*": "#nut"}, cull=False)))
    P.check_bounds(els)
    tx = {"particle": T(f"palm_frond_{kind}"), "frond": T(f"palm_frond_{kind}"), "boot": T("palm_boot"), "nut": T("coconut")}
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tx, "elements": els}


def palm_frond_model(kind, diagonal):
    import palm as P
    st = PALM_STYLE[kind]
    tf = "#frond"
    if diagonal:
        yaw, start = -45.0, [-4.0, 11.0, 20.0]
    else:
        yaw, start = 0.0, [8.0, 11.0, 23.0]
    if kind == "sabal":
        els = P.frond(start, yaw, [25, 5], [8, 24], 0, st["roll"], tf, widths=[1.5, 30])
    else:
        lengths = [13, 15, 15, 14]
        pitches = [22, -4, -30, -56] if kind == "royal" else [14, -14, -40, -64]
        els = P.frond(start, yaw, pitches, lengths, st["width"] + 6, st["roll"], tf)
    P.check_bounds(els)
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": T(f"palm_frond_{kind}"), "frond": T(f"palm_frond_{kind}")}, "elements": els}


def palms():
    tex("palm_boot", M.palm_boot)
    tex("coconut", M.coconut)
    for kind in ("royal", "coconut", "sabal"):
        tex(f"palm_trunk_{kind}", M.palm_trunk, kind)
        tex(f"palm_trunk_{kind}_top", M.palm_trunk, kind, end=True)
        if kind == "sabal":
            tex("palm_frond_sabal", M.palm_fan)
        else:
            tex(f"palm_frond_{kind}", M.palm_frond, kind)
    tex("palm_crownshaft_royal", M.crownshaft)
    # straight trunks
    for name, w, side, end in (("palm_trunk_royal", 10, "palm_trunk_royal", "palm_trunk_royal_top"),
                               ("palm_crownshaft_royal", 10, "palm_crownshaft_royal", "palm_trunk_royal_top"),
                               ("palm_trunk_sabal", 12, "palm_trunk_sabal", "palm_trunk_sabal_top")):
        model(name, {"parent": "minecraft:block/block", "textures": {"particle": T(side), "s": T(side), "e": T(end)},
                     "elements": [post_el(w, "#s", "#e")]})
        state(name, {"variants": {"": [{"model": MR(name)}, {"model": MR(name), "y": 90}, {"model": MR(name), "y": 180},
                                       {"model": MR(name), "y": 270}]}})
        item(name)
        block(name)
    # leaning coconut trunk: shift toward facing (north in the model)
    v = {}
    for sh in range(4):
        mn = f"palm_trunk_coconut_shift{sh}"
        model(mn, {"parent": "minecraft:block/block",
                   "textures": {"particle": T("palm_trunk_coconut"), "s": T("palm_trunk_coconut"), "e": T("palm_trunk_coconut_top")},
                   "elements": [dict(post_el(8, "#s", "#e", cz=8 - 4 * sh), rotation={"origin": [8, 8, 8 - 4 * sh], "axis": "x", "angle": -14})]})
        for f, y in FACING_Y.items():
            e = {"model": MR(mn)}
            if y:
                e["y"] = y
            v[f"facing={f},shift={sh}"] = e
    state("palm_trunk_coconut", {"variants": v})
    item("palm_trunk_coconut", "palm_trunk_coconut_shift0")
    block("palm_trunk_coconut")
    for kind in ("royal", "coconut", "sabal"):
        cn = f"palm_crown_{kind}"
        model(cn, palm_crown_model(kind))
        state(cn, {"variants": {"": [{"model": MR(cn)}, {"model": MR(cn), "y": 90}, {"model": MR(cn), "y": 180},
                                     {"model": MR(cn), "y": 270}]}})
        item(cn, flat=f"palm_frond_{kind}")
        block(cn)
        fn = f"palm_frond_{kind}"
        model(fn, palm_frond_model(kind, False))
        model(fn + "_diagonal", palm_frond_model(kind, True))
        fv = {}
        for f, y in FACING_Y.items():
            for dg in (False, True):
                e = {"model": MR(fn + ("_diagonal" if dg else ""))}
                if y:
                    e["y"] = y
                fv[f"diagonal={'true' if dg else 'false'},facing={f}"] = e
        state(fn, {"variants": fv})
        item(fn, flat=fn)
        block(fn)


# ------------------------------------------------------------------ Keys house pass 2


def keys_pass2():
    vs = {f: [] for f in FACING_Y}
    for i in range(3):
        tn = f"siding_teal_faded_{i}"
        tex(tn, M.siding, variant=i + 5, faded=True)
        mn = f"siding_teal_lined_faded_{i}"
        model(mn, {"parent": "minecraft:block/cube", "textures": {
            "particle": T(tn), "north": T(tn), "south": T("panel_cream"), "east": T(f"siding_teal_faded_{(i + 1) % 3}"),
            "west": T(f"siding_teal_faded_{(i + 2) % 3}"), "up": T(tn), "down": T(tn)}})
        for f, y in FACING_Y.items():
            e = {"model": MR(mn)}
            if y:
                e["y"] = y
            vs[f].append(e)
    state("siding_teal_lined_faded", {"variants": {f"facing={f}": v for f, v in vs.items()}})
    item("siding_teal_lined_faded", "siding_teal_lined_faded_0")
    block("siding_teal_lined_faded", "Faded Teal Siding (Cream Lined)")

    tex("trim_white_peeling", M.trim_peeling)
    cube_all("trim_white_peeling", "trim_white_peeling")
    slab_stairs("trim_white_peeling", "trim_white_peeling", stairs=False)

    tex("wood_dark", M.wood_dark)
    W, G = "#w", "#g"
    els = []
    for (x, z) in ((1, 1), (13.5, 1), (1, 13.5), (13.5, 13.5)):
        els.append(el([x, 0, z], [x + 1.5, 6, z + 1.5], faces({"*": W}, cull=["down"])))
    els += [el([0.5, 6, 0.5], [15.5, 7, 2], faces({"*": W}, cull=False)), el([0.5, 6, 14], [15.5, 7, 15.5], faces({"*": W}, cull=False)),
            el([0.5, 6, 2], [2, 7, 14], faces({"*": W}, cull=False)), el([14, 6, 2], [15.5, 7, 14], faces({"*": W}, cull=False)),
            el([1.5, 2, 1.5], [14.5, 2.5, 14.5], faces({"up": W, "down": W, "north": W, "south": W, "east": W, "west": W}, cull=False)),
            el([2, 6.4, 2], [14, 6.6, 14], faces({"up": G, "down": G}, cull=False))]
    model("glass_coffee_table", {"parent": "minecraft:block/block", "textures": {"particle": T("wood_dark"), "w": T("wood_dark"),
                                                                                "g": T("clear_glass")}, "elements": els})
    state("glass_coffee_table", {"variants": {"": {"model": MR("glass_coffee_table")}}})
    item("glass_coffee_table")
    block("glass_coffee_table", "Glass Coffee Table")

    for part in ("front_lower", "front_upper", "side", "top"):
        tex(f"fridge_white_{part}", M.fridge, part)
    for half, top in (("lower", 16), ("upper", 15)):
        mn = f"fridge_white_{half}"
        f = T(f"fridge_white_front_{half}")
        sd_ = T("fridge_white_side")
        els = [el([1, 0, 2], [15, top, 16], {
            "north": {"texture": "#f", "uv": [1, 16 - top, 15, 16]}, "south": {"texture": "#s", "uv": [1, 16 - top, 15, 16]},
            "east": {"texture": "#s", "uv": [0, 16 - top, 14, 16]}, "west": {"texture": "#s", "uv": [2, 16 - top, 16, 16]},
            "up": {"texture": "#t", "uv": [1, 2, 15, 16]}, "down": {"texture": "#s", "uv": [1, 2, 15, 16]}})]
        hy = (3, 15) if half == "lower" else (1, 13)
        els.append(el([12.5, hy[0], 1], [13.5, hy[1], 2], faces({"*": "#h"}, cull=False)))
        model(mn, {"parent": "minecraft:block/block", "textures": {"particle": sd_, "f": f, "s": sd_, "t": T("fridge_white_top"),
                                                                    "h": T("fridge_white_side")}, "elements": els})
    v = {}
    for f_, y in FACING_Y.items():
        for half in ("lower", "upper"):
            e = {"model": MR(f"fridge_white_{half}")}
            if y:
                e["y"] = y
            v[f"facing={f_},half={half}"] = e
    state("fridge_white", {"variants": v})
    item("fridge_white", flat="fridge_white_front_upper")
    block("fridge_white", "White Fridge")

    tex("cooler_white_side", M.cooler, "side")
    tex("cooler_white_top", M.cooler, "top")
    model("cooler_white", {"parent": "minecraft:block/block", "textures": {"particle": T("cooler_white_side"), "s": T("cooler_white_side"),
                                                                           "t": T("cooler_white_top")}, "elements": [
        el([2, 0, 4], [14, 8, 12], {"north": {"texture": "#s", "uv": [0, 0, 16, 16]}, "south": {"texture": "#s", "uv": [0, 0, 16, 16]},
                                    "east": {"texture": "#s", "uv": [4, 0, 12, 16]}, "west": {"texture": "#s", "uv": [4, 0, 12, 16]},
                                    "up": {"texture": "#t", "uv": [0, 0, 16, 16]}, "down": {"texture": "#t", "uv": [0, 0, 16, 16], "cullface": "down"}}),
        el([1, 6, 7], [2, 7, 9], faces({"*": "#t"}, cull=False)), el([14, 6, 7], [15, 7, 9], faces({"*": "#t"}, cull=False))]})
    facing_state("cooler_white")
    item("cooler_white")
    block("cooler_white", "Picnic Cooler")

    tex("bucket_white_side", M.bucket, "side")
    tex("bucket_white_top", M.bucket, "top")
    model("bucket_white", {"parent": "minecraft:block/block", "textures": {"particle": T("bucket_white_side"), "s": T("bucket_white_side"),
                                                                           "t": T("bucket_white_top"), "h": T("hose_reel_frame")}, "elements": [
        el([4.5, 0, 4.5], [11.5, 9, 11.5], {d: {"texture": "#s", "uv": [0, 1, 16, 16]} for d in ("north", "south", "east", "west")}
           | {"up": {"texture": "#t"}, "down": {"texture": "#t", "cullface": "down"}}),
        el([4, 8.5, 4], [12, 10, 12], {d: {"texture": "#s", "uv": [0, 0, 16, 2]} for d in ("north", "south", "east", "west")}
           | {"up": {"texture": "#t"}, "down": {"texture": "#t"}}),
        el([3.7, 9, 7.6], [4, 12.5, 8.4], faces({"*": "#h"}, cull=False)), el([12, 9, 7.6], [12.3, 12.5, 8.4], faces({"*": "#h"}, cull=False)),
        el([4, 12.2, 7.6], [12, 12.5, 8.4], faces({"*": "#h"}, cull=False))]})
    state("bucket_white", {"variants": {"": [{"model": MR("bucket_white")}, {"model": MR("bucket_white"), "y": 90}]}})
    item("bucket_white")
    block("bucket_white", "White Bucket")

    tex("hose_reel_coil", M.hose, "coil")
    tex("hose_reel_wrap", M.hose, "wrap")
    tex("hose_reel_frame", M.hose, "reel")
    C, Wr, F = "#c", "#w", "#f"
    model("hose_reel", {"parent": "minecraft:block/block", "textures": {"particle": T("hose_reel_wrap"), "c": T("hose_reel_coil"),
                                                                        "w": T("hose_reel_wrap"), "f": T("hose_reel_frame")}, "elements": [
        el([4, 2, 15], [12, 14, 16], faces({"*": F}, cull=["south"])),
        el([7, 7, 13], [9, 9, 15], faces({"*": F}, cull=False)),
        el([3, 5, 9], [13, 11, 13], faces({"north": Wr, "east": Wr, "west": Wr, "up": Wr, "down": Wr}, cull=False)),
        el([5, 3, 9], [11, 13, 13], faces({"east": Wr, "west": Wr, "up": Wr, "down": Wr}, cull=False)),
        el([2, 2, 8.9], [14, 14, 8.9], {"north": {"texture": C}}),
        el([7.5, 7.5, 7], [8.5, 8.5, 8.9], faces({"*": F}, cull=False)),
        el([7.5, 8.5, 7], [8.5, 12, 8], faces({"*": F}, cull=False)),
        el([7, 11.5, 5.5], [9, 12.5, 7], faces({"*": F}, cull=False))]})
    facing_state("hose_reel")
    item("hose_reel")
    block("hose_reel", "Garden Hose Reel")


# ------------------------------------------------------------------ catalogue


def window_block(name, texname, glow_tex, glow_blind_tex):
    lo, hi = -0.08, 16.08
    full = {d: [0, 0, 16, 16] for d in ("north", "south", "east", "west")}
    model(name, {
        "parent": "minecraft:block/block",
        "textures": {"particle": T(texname), "all": T(texname), "glow": T(glow_tex), "glow_b": T(glow_blind_tex)},
        "elements": [
            el([0, 0, 0], [16, 16, 16], faces({"*": "#all"})),
            el([lo, lo, lo], [hi, hi, hi], faces({"north": "#glow", "south": "#glow", "east": "#glow", "west": "#glow"}, uv=full, tint=1)),
            el([lo, lo, lo], [hi, hi, hi], faces({"north": "#glow_b", "south": "#glow_b", "east": "#glow_b", "west": "#glow_b"}, uv=full, tint=2)),
        ]})
    model(name + "_inv", {"parent": "minecraft:block/cube_all", "textures": {"all": T(texname)}})
    state(name, {"variants": {"glow=" + g: {"model": MR(name)} for g in ("auto", "on", "off")}})
    item(name, name + "_inv")
    block(name)


def run():
    for d in (TEX, MOD, IMOD, BS, ITEMS):
        if ONLY is None and os.path.isdir(d):
            shutil.rmtree(d)
        os.makedirs(d, exist_ok=True)

    # ---- facades
    for c, col in M.PASTEL.items():
        tex(f"stucco_{c}", M.stucco, col)
        tex(f"stucco_{c}_grimy", M.stucco, col, grimy=True)
        cube_all(f"stucco_{c}", f"stucco_{c}")
        cube_all(f"stucco_{c}_grimy", f"stucco_{c}_grimy")
    for c, col in M.EARTH.items():
        tex(f"stucco_{c}", M.stucco, col)
        tex(f"stucco_{c}_grimy", M.stucco, col, grimy=True)
        cube_all(f"stucco_{c}", f"stucco_{c}")
        cube_all(f"stucco_{c}_grimy", f"stucco_{c}_grimy")
    for c, col in M.CONDO.items():
        tex(f"condo_{c}", M.condo, col)
        tex(f"condo_{c}_grimy", M.condo, col, grimy=True)
        cube_all(f"condo_{c}", f"condo_{c}")
        cube_all(f"condo_{c}_grimy", f"condo_{c}_grimy")
        slab_stairs(f"condo_{c}", f"condo_{c}", stairs=False)
    for c, col in M.GREYS.items():
        tex(f"panel_{c}", M.panel, col)
        cube_all(f"panel_{c}", f"panel_{c}")
    for c, col in M.METAL.items():
        tex(f"metal_{c}", M.corrugated, col)
        cube_all(f"metal_{c}", f"metal_{c}")

    # ---- glass
    cur = None
    for t, col in M.GLASS.items():
        cur = tex(f"glass_{t}", M.curtain_glass, col)
        tex(f"spandrel_{t}", M.curtain_glass, col, spandrel=True)
        cube_all(f"spandrel_{t}", f"spandrel_{t}")
    mull = M._mullion_mask(M.HI, 3)
    tex("glow_curtain", lambda n, *a: M.glow(n, M.HI, mull))
    tex("glow_curtain_blinds", lambda n, *a: M.glow(n, M.HI, mull, blinds=True))
    for t in M.GLASS:
        window_block(f"glass_{t}", f"glass_{t}", "glow_curtain", "glow_curtain_blinds")
    for f, col in M.FRAME.items():
        tex(f"frame_{f}", M.frame_metal, col)
        cube_all(f"frame_{f}", f"frame_{f}")
    for k in ("condo", "deco", "house", "ribbon", "clerestory", "shopfront"):
        w = tex(f"window_{k}", M.window, k)
        mask = w.frame_mask if w is not None else np.zeros((M.HI, M.HI))
        tex(f"glow_{k}", lambda n, *a, mask=mask: M.glow(n, M.HI, mask))
        tex(f"glow_{k}_blinds", lambda n, *a, mask=mask: M.glow(n, M.HI, mask, blinds=True))
        window_block(f"window_{k}", f"window_{k}", f"glow_{k}", f"glow_{k}_blinds")

    tex("clear_glass", M.clear_glass)
    cube_all("clear_glass", "clear_glass")
    tex("glass_railing", M.glass_railing)
    model("glass_railing", {"parent": "minecraft:block/block", "textures": {"particle": T("glass_railing"), "t": T("glass_railing")},
                            "elements": [el([0, 0, 0], [16, 16, 1], faces({"north": "#t", "south": "#t", "east": "#t", "west": "#t", "up": "#t", "down": "#t"},
                                                                        cull=["north", "east", "west", "up", "down"],
                                                                        uv={"east": [0, 0, 1, 16], "west": [15, 0, 16, 16], "up": [0, 0, 16, 1], "down": [0, 0, 16, 1]}))]})
    facing_state("glass_railing")
    item("glass_railing")
    block("glass_railing")

    tex("trim_white", M.trim)
    cube_all("trim_white", "trim_white")
    slab_stairs("trim_white", "trim_white")

    # ---- deco details
    for a, col in M.ACCENT.items():
        at = "trim_white" if a == "white" else f"accent_{a}"
        if a != "white":
            tex(at, M.stucco, col)
        tex(f"speed_line_{a}", M.speed_line, col)
        # eyebrow: thin ledge at the top, projecting from the south wall to the north
        model(f"eyebrow_{a}", {"parent": "minecraft:block/block", "textures": {"particle": T(at), "t": T(at)},
                               "elements": [el([0, 13, 6], [16, 16, 16], faces({"*": "#t"}, cull=["south", "east", "west"]))]})
        facing_state(f"eyebrow_{a}")
        item(f"eyebrow_{a}")
        block(f"eyebrow_{a}")
        model(f"fin_{a}", {"parent": "minecraft:block/block", "textures": {"particle": T(at), "t": T(at)},
                           "elements": [el([6, 0, 0], [10, 16, 16], faces({"*": "#t"}, cull=["south", "up", "down"]))]})
        facing_state(f"fin_{a}")
        item(f"fin_{a}")
        block(f"fin_{a}")
        model(f"speed_line_{a}", {"parent": "minecraft:block/block", "textures": {"particle": T(at), "t": T(f"speed_line_{a}")},
                                  "elements": [el([0, 0, 15.6], [16, 16, 15.6], faces({"north": "#t"}, cull=False))]})
        facing_state(f"speed_line_{a}")
        item(f"speed_line_{a}")
        block(f"speed_line_{a}")
    for a, col in M.AWNING.items():
        tex(f"awning_{a}", M.awning, col)
        t = "#t"
        model(f"awning_{a}", {"parent": "minecraft:block/block", "textures": {"particle": T(f"awning_{a}"), "t": T(f"awning_{a}")},
                              "elements": [
                                  el([0, 14.5, 0], [16, 15.5, 16], faces({"*": t}, cull=False),
                                     rotation={"origin": [8, 15, 16], "axis": "x", "angle": -22.5}),
                                  el([0, 6.5, 0.8], [16, 9.5, 1.3], faces({"north": t, "south": t, "down": t}, cull=False,
                                                                          uv={"north": [0, 0, 16, 3], "south": [0, 0, 16, 3], "down": [0, 0, 16, 1]})),
                              ]})
        facing_state(f"awning_{a}")
        item(f"awning_{a}")
        block(f"awning_{a}")
    for c, col in M.NEON.items():
        tex(f"neon_{c}", M.neon, col)
        base = {"parent": "minecraft:block/block", "textures": {"particle": T(f"neon_{c}"), "t": T(f"neon_{c}"), "b": T("frame_dark")}}
        h = dict(base)
        h["elements"] = [el([0, 7, 13], [16, 9, 15], faces({"*": "#t"}, cull=["east", "west"]), emission=15, shade=False),
                         el([3, 7.5, 15], [4, 8.5, 16], faces({"*": "#b"}, cull=False)),
                         el([12, 7.5, 15], [13, 8.5, 16], faces({"*": "#b"}, cull=False))]
        model(f"neon_{c}", h)
        v = dict(base)
        v["elements"] = [el([7, 0, 13], [9, 16, 15], faces({"*": "#t"}, cull=["up", "down"]), emission=15, shade=False),
                         el([7.5, 3, 15], [8.5, 4, 16], faces({"*": "#b"}, cull=False)),
                         el([7.5, 12, 15], [8.5, 13, 16], faces({"*": "#b"}, cull=False))]
        model(f"neon_{c}_vertical", v)
        facing_state(f"neon_{c}", extra={"vertical=false": f"neon_{c}", "vertical=true": f"neon_{c}_vertical"})
        item(f"neon_{c}")
        block(f"neon_{c}", f"Neon Tube {c.capitalize()}")

    tex("roll_up_door", M.roll_up_door, (0.42, 0.45, 0.48))
    tex("roll_up_door_white", M.roll_up_door, (0.80, 0.81, 0.80))
    cube_all("roll_up_door", "roll_up_door")
    cube_all("roll_up_door_white", "roll_up_door_white")
    tex("door_brown_lower", M.door, "lower")
    tex("door_brown_upper", M.door, "upper")
    model("door_brown_lower", {"parent": "minecraft:block/cube_all", "textures": {"all": T("door_brown_lower")}})
    model("door_brown_upper", {"parent": "minecraft:block/cube_all", "textures": {"all": T("door_brown_upper")}})
    state("door_brown", {"variants": {"half=lower": {"model": MR("door_brown_lower")}, "half=upper": {"model": MR("door_brown_upper")}}})
    item("door_brown", "door_brown_lower")
    block("door_brown", "Brown Facade Door")

    tex("concrete", M.concrete)
    cube_all("concrete", "concrete", rot4=True)
    slab_stairs("concrete", "concrete")

    # ---- roofs
    tex("clay_tile", M.barrel_tile, None)
    tex("clay_tile_aged", M.barrel_tile, None, aged=True)
    tex("shingle", M.shingle)
    tex("metal_roof", M.metal_roof)
    for r in ("clay_tile", "clay_tile_aged", "shingle", "metal_roof"):
        cube_all(r, r)
        slab_stairs(r, r)
    tex("gravel_roof", M.gravel_roof)
    cube_all("gravel_roof", "gravel_roof", rot4=True)
    tex("membrane_roof", M.membrane_roof)
    cube_all("membrane_roof", "membrane_roof", rot4=True)
    model("parapet_cap", {"parent": "minecraft:block/block", "textures": {"particle": T("trim_white"), "t": T("trim_white")},
                          "elements": [el([0, 0, 0], [16, 4, 16], faces({"*": "#t"}, cull=["north", "south", "east", "west", "down"]))]})
    state("parapet_cap", {"variants": {"": {"model": MR("parapet_cap")}}})
    item("parapet_cap")
    block("parapet_cap")
    tex("ac_unit_side", M.ac_unit, "side")
    tex("ac_unit_top", M.ac_unit, "top")
    model("ac_unit", {"parent": "minecraft:block/block", "textures": {"particle": T("ac_unit_side"), "s": T("ac_unit_side"), "t": T("ac_unit_top")},
                      "elements": [el([1, 0, 1], [15, 12, 15], faces({"north": "#s", "south": "#s", "east": "#s", "west": "#s", "up": "#t", "down": "#s"},
                                                                     cull=["down"]))]})
    state("ac_unit", {"variants": {"": [{"model": MR("ac_unit")}, {"model": MR("ac_unit"), "y": 90}]}})
    item("ac_unit")
    block("ac_unit", "AC Unit")

    # ---- roads
    for v, bid in (("fresh", "asphalt"), ("worn", "asphalt_worn"), ("patched", "asphalt_patched"), ("cracked", "asphalt_cracked"),
                   ("wheelpath", "asphalt_wheelpath")):
        tex(bid, M.asphalt, v)
        cube_all(bid, bid, rot4=True)
    slab_stairs("asphalt", "asphalt")
    for k in ("line_white", "line_yellow", "line_yellow_double", "stop_bar", "zebra"):
        tex(k, M.marking, k)
        cube_bt(k, k, "asphalt")
        axis_state(k)
        item(k)
        block(k)
    for k in ("line_white_edge", "arrow_straight", "arrow_left", "arrow_right"):
        tex(k, M.marking, k)
        cube_bt(k, k, "asphalt")
        facing_state(k)
        item(k)
        block(k)

    tex("sidewalk_side", M.sidewalk, side=True)
    sv = {}
    for jx in range(3):
        for jz in range(3):
            t = f"sidewalk_{jx}{jz}"
            tex(t, M.sidewalk, jx, jz)
            tx = {"bottom": T("sidewalk_side"), "top": T(t), "side": T("sidewalk_side")}
            cube_bt(t, t, "sidewalk_side")
            model(t + "_slab", {"parent": "minecraft:block/slab", "textures": tx})
            model(t + "_slab_top", {"parent": "minecraft:block/slab_top", "textures": tx})
            sv[f"jx={jx},jz={jz},type=bottom"] = {"model": MR(t + "_slab")}
            sv[f"jx={jx},jz={jz},type=top"] = {"model": MR(t + "_slab_top")}
            sv[f"jx={jx},jz={jz},type=double"] = {"model": MR(t)}
    state("sidewalk", {"variants": sv})
    item("sidewalk", "sidewalk_00_slab")
    block("sidewalk")

    for p in ("top", "side", "face"):
        tex(f"kerb_{p}", M.kerb, p)
    ktx = {"particle": T("kerb_side"), "top": T("kerb_top"), "side": T("kerb_side"), "face": T("kerb_face")}
    for nm, y0, y1 in (("kerb", 0, 16), ("kerb_slab", 0, 8), ("kerb_slab_top", 8, 16)):
        sideuv = [0, 16 - y1, 16, 16 - y0]
        cull = ["north", "south", "east", "west", "down"] + (["up"] if y1 == 16 else [])
        if y0 == 8:
            cull = ["north", "south", "east", "west", "up"]
        model(nm, {"parent": "minecraft:block/block", "textures": ktx, "elements": [
            el([0, y0, 0], [16, y1, 16], faces({"north": "#face", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#side"},
                                               cull=cull, uv={"north": sideuv, "south": sideuv, "east": sideuv, "west": sideuv}))]})
    kv = {}
    for f, y in FACING_Y.items():
        for typ, nm in (("bottom", "kerb_slab"), ("top", "kerb_slab_top"), ("double", "kerb")):
            e = {"model": MR(nm)}
            if y:
                e["y"] = y
            kv[f"facing={f},type={typ}"] = e
    state("kerb", {"variants": kv})
    item("kerb", "kerb_slab")
    block("kerb")

    tex("jersey_barrier", M.barrier)
    model("jersey_barrier", {"parent": "minecraft:block/block", "textures": {"particle": T("jersey_barrier"), "t": T("jersey_barrier")},
                             "elements": [el([2, 0, 0], [14, 3, 16], faces({"*": "#t"}, cull=["north", "south", "down"])),
                                          el([4, 3, 0], [12, 6, 16], faces({"*": "#t"}, cull=["north", "south"])),
                                          el([5.5, 6, 0], [10.5, 13, 16], faces({"*": "#t"}, cull=["north", "south"]))]})
    axis_state("jersey_barrier")
    item("jersey_barrier")
    block("jersey_barrier")

    # ---- ground
    for k, bid in (("beach", "beach_sand"), ("wet", "wet_sand"), ("seabed", "seabed_sand")):
        tex(bid, M.sand, k)
        cube_all(bid, bid, rot4=True)
    tex("coral_stone", M.coral_stone)
    cube_all("coral_stone", "coral_stone", rot4=True)
    tex("terrazzo", M.terrazzo)
    cube_all("terrazzo", "terrazzo", rot4=True)
    tex("tile_floor", M.tile_floor)
    cube_all("tile_floor", "tile_floor")
    tex("marble", M.marble)
    cube_all("marble", "marble", rot4=True)
    tex("wood_deck", M.wood_deck)
    cube_all("wood_deck", "wood_deck")
    slab_stairs("wood_deck", "wood_deck")

    # ---- props
    tex("chain_link_fence", M.chain_link)
    for part in ("post_ends", "post", "cap", "cap_alt", "side", "side_alt"):
        model(f"chain_link_fence_{part}", {"parent": f"minecraft:block/template_bars_{part}",
                                           "textures": {"bars": T("chain_link_fence"), "edge": T("utility_pole"), "particle": T("chain_link_fence")}})
    with open(os.path.join(HERE, "iron_bars_template.json")) as f:
        s = f.read().replace("minecraft:block/iron_bars_", "leonida:block/look/chain_link_fence_")
    state("chain_link_fence", json.loads(s))
    item("chain_link_fence", flat="chain_link_fence")
    block("chain_link_fence")

    tex("utility_pole", M.pole)
    model("utility_pole", {"parent": "minecraft:block/block", "textures": {"particle": T("utility_pole"), "t": T("utility_pole")},
                           "elements": [el([5, 0, 5], [11, 16, 11], faces({"*": "#t"}, cull=["up", "down"]))]})
    state("utility_pole", {"variants": {"": {"model": MR("utility_pole")}}})
    item("utility_pole")
    block("utility_pole")
    model("utility_pole_crossarm", {"parent": "minecraft:block/block",
                                    "textures": {"particle": T("utility_pole"), "t": T("utility_pole"), "i": T("trim_white")},
                                    "elements": [el([5, 0, 5], [11, 16, 11], faces({"*": "#t"}, cull=["up", "down"])),
                                                 el([6.5, 10, 0], [9.5, 13, 16], faces({"*": "#t"}, cull=False)),
                                                 el([7, 13, 1.5], [9, 15.5, 3.5], faces({"*": "#i"}, cull=False)),
                                                 el([7, 13, 12.5], [9, 15.5, 14.5], faces({"*": "#i"}, cull=False))]})
    axis_state("utility_pole_crossarm")
    item("utility_pole_crossarm")
    block("utility_pole_crossarm", "Utility Pole with Crossarm")

    for c, col in M.CONTAINER.items():
        tex(f"container_{c}_side", M.container, col, "side")
        tex(f"container_{c}_top", M.container, col, "top")
        cube_bt(f"container_{c}", f"container_{c}_top", f"container_{c}_side", f"container_{c}_top")
        state(f"container_{c}", {"variants": {"": {"model": MR(f"container_{c}")}}})
        item(f"container_{c}")
        block(f"container_{c}", f"Shipping Container {c.capitalize()}")

    # ---- batch 2: stadiums and arenas (vicecity requests)
    tex("concrete", M.concrete)
    for c, col in M.SEAT.items():
        tex(f"stadium_seat_{c}", M.stadium_seat, col)
        n_ = f"stadium_seat_{c}"
        tx = {"bottom": T("concrete"), "top": T(n_), "side": T(n_), "particle": T(n_)}
        model(n_, {"parent": "minecraft:block/stairs", "textures": tx})
        model(n_ + "_inner", {"parent": "minecraft:block/inner_stairs", "textures": tx})
        model(n_ + "_outer", {"parent": "minecraft:block/outer_stairs", "textures": tx})
        with open(os.path.join(HERE, "stairs_template.json")) as f:
            st = json.loads(f.read().replace("minecraft:block/oak_stairs", MR(n_)))
        state(n_, st)
        item(n_)
        block(n_, f"Stadium Seats {c.capitalize()}")
    tex("roof_membrane_white", M.standing_seam, (0.86, 0.87, 0.86))
    cube_all("roof_membrane_white", "roof_membrane_white")
    slab_stairs("roof_membrane_white", "roof_membrane_white", stairs=False)
    for c, col, metal in (("silver", (0.70, 0.72, 0.74), True), ("white", (0.88, 0.89, 0.88), False)):
        tex(f"arena_panel_{c}", M.arena_panel, col, metal=metal)
        cube_all(f"arena_panel_{c}", f"arena_panel_{c}")
        slab_stairs(f"arena_panel_{c}", f"arena_panel_{c}", stairs=False)
    for c, col in M.LED.items():
        tex(f"led_ribbon_{c}", M.led, col)
        tex(f"led_panel_{c}", M.led, col, ribbon=False)
        base = {"parent": "minecraft:block/block", "textures": {"particle": T(f"led_ribbon_{c}"), "t": T(f"led_ribbon_{c}"), "b": T("frame_dark")}}
        h = dict(base)
        h["elements"] = [el([0, 7, 15], [16, 9, 15.9], faces({"*": "#t"}, cull=["east", "west"],
                                                              uv={"north": [0, 4, 16, 12], "south": [0, 4, 16, 12]}), emission=15, shade=False),
                         el([0, 6.5, 15.9], [16, 9.5, 16], faces({"north": "#b", "up": "#b", "down": "#b"}, cull=["east", "west"]))]
        model(f"led_ribbon_{c}", h)
        v = dict(base)
        v["elements"] = [el([7, 0, 15], [9, 16, 15.9], faces({"*": "#t"}, cull=["up", "down"],
                                                              uv={"north": [4, 0, 12, 16], "south": [4, 0, 12, 16]}), emission=15, shade=False),
                         el([6.5, 0, 15.9], [9.5, 16, 16], faces({"north": "#b", "east": "#b", "west": "#b"}, cull=["up", "down"]))]
        model(f"led_ribbon_{c}_vertical", v)
        facing_state(f"led_ribbon_{c}", extra={"vertical=false": f"led_ribbon_{c}", "vertical=true": f"led_ribbon_{c}_vertical"})
        item(f"led_ribbon_{c}")
        block(f"led_ribbon_{c}", "LED Ribbon " + c.replace("_", " ").title())
        model(f"led_panel_{c}", {"parent": "minecraft:block/block", "textures": {"particle": T(f"led_panel_{c}"), "t": T(f"led_panel_{c}")},
                                 "elements": [el([0, 0, 0], [16, 16, 16], faces({"*": "#t"}), emission=15, shade=False)]})
        state(f"led_panel_{c}", {"variants": {"": {"model": MR(f"led_panel_{c}")}}})
        item(f"led_panel_{c}")
        block(f"led_panel_{c}", "LED Panel " + c.replace("_", " ").title())
    for c, col in M.TRUSS.items():
        tex(f"steel_truss_{c}", M.steel_truss, col)
        cube_all(f"steel_truss_{c}", f"steel_truss_{c}")
    tex("court_wood", M.court_wood)
    cube_all("court_wood", "court_wood")
    slab_stairs("court_wood", "court_wood", stairs=False)
    tex("turf_grass", M.turf)
    tex("turf_grass_dark", M.turf, dark=True)
    cube_all("turf_grass", "turf_grass", rot4=True)
    cube_all("turf_grass_dark", "turf_grass_dark", rot4=True)

    # ---- batch 2: Keys houses (landmark requests)
    for dark in (False, True):
        bn = "siding_teal_weathered" + ("_dark" if dark else "")
        vs = []
        for i in range(3):
            tex(f"{bn}_{i}", M.siding, variant=i, dark=dark)
            model(f"{bn}_{i}", {"parent": "minecraft:block/cube_all", "textures": {"all": T(f"{bn}_{i}")}})
            vs.append({"model": MR(f"{bn}_{i}")})
        state(bn, {"variants": {"": vs}})
        item(bn, f"{bn}_0")
        block(bn, "Weathered Teal Siding" + (" (Dark)" if dark else ""))
    tex("panel_cream", M.cream_panel)
    cube_all("panel_cream", "panel_cream")
    lang["block.leonida.look.panel_cream"] = "Cream Board Paneling"
    model("siding_teal_lined", {"parent": "minecraft:block/cube", "textures": {
        "particle": T("siding_teal_weathered_0"), "north": T("siding_teal_weathered_0"), "south": T("panel_cream"),
        "east": T("siding_teal_weathered_1"), "west": T("siding_teal_weathered_2"), "up": T("siding_teal_weathered_0"),
        "down": T("siding_teal_weathered_0")}})
    facing_state("siding_teal_lined")
    item("siding_teal_lined")
    block("siding_teal_lined", "Teal Siding (Cream Lined)")
    tex("weathered_wood", M.weathered_wood)
    tex("weathered_wood_top", M.weathered_wood, end=True)
    model("weathered_wood", {"parent": "minecraft:block/cube_column", "textures": {"end": T("weathered_wood_top"), "side": T("weathered_wood")}})
    model("weathered_wood_horizontal", {"parent": "minecraft:block/cube_column_horizontal",
                                        "textures": {"end": T("weathered_wood_top"), "side": T("weathered_wood")}})
    state("weathered_wood", {"variants": {"axis=y": {"model": MR("weathered_wood")},
                                          "axis=x": {"model": MR("weathered_wood_horizontal"), "x": 90, "y": 90},
                                          "axis=z": {"model": MR("weathered_wood_horizontal"), "x": 90}}})
    item("weathered_wood")
    block("weathered_wood")
    slab_stairs("weathered_wood", "weathered_wood", stairs=False)
    tex("wood_brown", M.wood_brown)
    cube_all("wood_brown", "wood_brown")
    slab_stairs("wood_brown", "wood_brown")
    def railing(rn, texname, display):
        w = {"particle": T(texname), "t": T(texname)}
        model(rn + "_post", {"parent": "minecraft:block/block", "textures": w, "elements": [
            el([6.5, 0, 6.5], [9.5, 15, 9.5], faces({"*": "#t"}, cull=["down"])),
            el([6, 15, 6], [10, 16, 10], faces({"*": "#t"}, cull=["up"]))]})
        model(rn + "_side", {"parent": "minecraft:block/block", "textures": w, "elements": [
            el([7, 13.5, 0], [9, 15.5, 6.5], faces({"north": "#t", "east": "#t", "west": "#t", "up": "#t", "down": "#t"}, cull=["north"])),
            el([7.5, 2, 0], [8.5, 3.5, 6.5], faces({"north": "#t", "east": "#t", "west": "#t", "up": "#t", "down": "#t"}, cull=["north"])),
            el([7.25, 3.5, 0.75], [8.75, 13.5, 2.25], faces({"north": "#t", "south": "#t", "east": "#t", "west": "#t"}, cull=False)),
            el([7.25, 3.5, 4], [8.75, 13.5, 5.5], faces({"north": "#t", "south": "#t", "east": "#t", "west": "#t"}, cull=False))]})
        model(rn + "_inventory", {"parent": "minecraft:block/block", "textures": w,
                                         "display": {"gui": {"rotation": [30, 135, 0], "scale": [0.625, 0.625, 0.625]}}, "elements": [
            el([6.5, 0, 0], [9.5, 15, 3], faces({"*": "#t"}, cull=False)),
            el([6.5, 0, 13], [9.5, 15, 16], faces({"*": "#t"}, cull=False)),
            el([7, 13.5, 3], [9, 15.5, 13], faces({"*": "#t"}, cull=False)),
            el([7.5, 2, 3], [8.5, 3.5, 13], faces({"*": "#t"}, cull=False)),
            el([7.25, 3.5, 5], [8.75, 13.5, 6.5], faces({"*": "#t"}, cull=False)),
            el([7.25, 3.5, 9.5], [8.75, 13.5, 11], faces({"*": "#t"}, cull=False))]})
        mp = [{"apply": {"model": MR(rn + "_post")}}]
        for d, y in FACING_Y.items():
            a = {"model": MR(rn + "_side")}
            if y:
                a["y"] = y
            mp.append({"when": {d: "true"}, "apply": a})
        state(rn, {"multipart": mp})
        item(rn, rn + "_inventory")
        block(rn, display)
    railing("wood_railing", "wood_brown", "Wood Deck Railing")
    railing("wood_railing_weathered", "weathered_wood", "Weathered Deck Railing")
    # weathered fence post for stilts (vanilla fence geometry)
    fx = {"texture": T("weathered_wood"), "particle": T("weathered_wood")}
    model("weathered_wood_fence_post", {"parent": "minecraft:block/fence_post", "textures": fx})
    model("weathered_wood_fence_side", {"parent": "minecraft:block/fence_side", "textures": fx})
    model("weathered_wood_fence_inventory", {"parent": "minecraft:block/fence_inventory", "textures": fx})
    mp = [{"apply": {"model": MR("weathered_wood_fence_post")}}]
    for d, y in FACING_Y.items():
        a = {"model": MR("weathered_wood_fence_side"), "uvlock": True}
        if y:
            a["y"] = y
        mp.append({"when": {d: "true"}, "apply": a})
    state("weathered_wood_fence", {"multipart": mp})
    item("weathered_wood_fence", "weathered_wood_fence_inventory")
    block("weathered_wood_fence", "Weathered Wood Fence Post")
    keys_pass2()

    def panel_block(bn, texname, depth, display):
        model(bn, {"parent": "minecraft:block/block", "textures": {"particle": T(texname), "t": T(texname)},
                   "elements": [el([0, 0, 16 - depth], [16, 16, 16], faces({"*": "#t"}, cull=["south", "east", "west", "up", "down"],
                                                                         uv={"east": [16 - depth, 0, 16, 16], "west": [0, 0, depth, 16],
                                                                             "up": [0, 16 - depth, 16, 16], "down": [0, 0, 16, depth]}))]})
        facing_state(bn)
        item(bn)
        block(bn, display)
    tex("shutter_grey", M.shutter)
    panel_block("shutter_grey", "shutter_grey", 1.5, "Louvered Shutter Grey")
    tex("lattice_panel", M.lattice)
    panel_block("lattice_panel", "lattice_panel", 1, "Lattice Skirt Panel")
    tex("bamboo_blind", M.bamboo_blind)
    panel_block("bamboo_blind", "bamboo_blind", 0.5, "Bamboo Blind")
    w = tex("window_cottage", M.cottage_window)
    mask = w.frame_mask if w is not None else np.zeros((M.HI, M.HI))
    tex("glow_cottage", lambda n, *a, mask=mask: M.glow(n, M.HI, mask))
    tex("glow_cottage_blinds", lambda n, *a, mask=mask: M.glow(n, M.HI, mask, blinds=True))
    window_block("window_cottage", "window_cottage", "glow_cottage", "glow_cottage_blinds")
    w = tex("window_porthole", M.porthole)
    mask = w.frame_mask if w is not None else np.zeros((M.HI, M.HI))
    tex("glow_porthole", lambda n, *a, mask=mask: M.glow(n, M.HI, mask))
    tex("glow_porthole_blinds", lambda n, *a, mask=mask: M.glow(n, M.HI, mask, blinds=True))
    window_block("window_porthole", "window_porthole", "glow_porthole", "glow_porthole_blinds")
    tex("door_cottage_lower", M.cottage_door, "lower")
    tex("door_cottage_upper", M.cottage_door, "upper")
    model("door_cottage_lower", {"parent": "minecraft:block/cube_all", "textures": {"all": T("door_cottage_lower")}})
    model("door_cottage_upper", {"parent": "minecraft:block/cube_all", "textures": {"all": T("door_cottage_upper")}})
    state("door_cottage", {"variants": {"half=lower": {"model": MR("door_cottage_lower")}, "half=upper": {"model": MR("door_cottage_upper")}}})
    item("door_cottage", "door_cottage_upper")
    block("door_cottage", "Cottage Door")
    tex("tarp_blue", M.tarp)
    model("tarp_blue", {"parent": "minecraft:block/block", "textures": {"particle": T("tarp_blue"), "t": T("tarp_blue")},
                        "elements": [el([0, 0, 0], [16, 1, 16], faces({"*": "#t"}, cull=["north", "south", "east", "west", "down"],
                                                                     uv={d: [0, 15, 16, 16] for d in ("north", "south", "east", "west")}))]})
    state("tarp_blue", {"variants": {"": [{"model": MR("tarp_blue")}, {"model": MR("tarp_blue"), "y": 90}]}})
    item("tarp_blue")
    block("tarp_blue", "Blue Tarp")
    tex("tropical_fabric", M.tropical_fabric)
    cube_all("tropical_fabric", "tropical_fabric")
    slab_stairs("tropical_fabric", "tropical_fabric")
    slab_stairs("weathered_wood", "weathered_wood", slab=False)
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open", "top_right", "top_right_open"):
        model(f"door_cottage_openable_{part}", {"parent": f"minecraft:block/door_{part}",
                                                "textures": {"bottom": T("door_cottage_lower"), "top": T("door_cottage_upper")}})
    with open(os.path.join(HERE, "door_template.json")) as f:
        state("door_cottage_openable", json.loads(f.read().replace("minecraft:block/oak_door_", "leonida:block/look/door_cottage_openable_")))
    item("door_cottage_openable", flat="door_cottage_upper")
    block("door_cottage_openable", "Cottage Door (Openable)")
    palms()
    tex("oyster_shell_gravel", M.oyster_gravel)
    cube_all("oyster_shell_gravel", "oyster_shell_gravel", rot4=True)

    # windows last in lang order is fine; fix names for curtain glass
    for t in M.GLASS:
        lang[f"block.leonida.look.glass_{t}"] = f"Curtain Wall Glass {t.capitalize()}"
        lang[f"block.leonida.look.spandrel_{t}"] = f"Spandrel {t.capitalize()}"

    if ONLY is None:
        with open(LANG, "w") as f:
            json.dump(lang, f, indent=2, ensure_ascii=False)
            f.write("\n")
        with open(os.path.join(HERE, "block_ids.txt"), "w") as f:
            f.write("\n".join(blocks) + "\n")
    print(f"{len(blocks)} blocks, {sum(1 for v in made_tex.values() if v is not None)} textures")

    if SHEET:
        names = [k for k, v in made_tex.items() if v is not None]
        cols = 12
        cell = 132
        rows = (len(names) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell, rows * cell), (40, 40, 40, 255))
        for i, k in enumerate(names):
            im = Image.open(os.path.join(TEX, k + ".png")).convert("RGBA").resize((128, 128), Image.NEAREST)
            sheet.alpha_composite(im, ((i % cols) * cell + 2, (i // cols) * cell + 2))
        sheet.save(SHEET)


if __name__ == "__main__":
    run()
