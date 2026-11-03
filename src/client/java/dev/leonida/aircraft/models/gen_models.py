#!/usr/bin/env python3
"""Generates the Leonida aircraft Blockbench models (.bbmodel) and the shared palette texture.

Run from the worktree root:  python3 src/client/java/dev/leonida/aircraft/models/gen_models.py
Output: src/main/resources/assets/leonida/objects/aircraft/*.bbmodel (variant 0 as <id>, others as <id>_v<n>)
        src/main/resources/assets/leonida/textures/entity/aircraft/palette.png
        src/main/resources/assets/leonida/textures/item/aircraft/*.png
Units are Blockbench pixels (16 = one block = one metre). Forward is +Z, up is +Y, +X is the pilot's left,
the entity origin is at y = 0. Airframes follow the real donors at 1:1 scale:
  Western Duster       Boeing-Stearman PT-17 biplane (9.8 m span, 7.6 m long)
  Floatplane           de Havilland Canada DHC-2 Beaver on floats (14.6 m span, 9.2 m long)
  Mammoth Dodo         Grumman G-21 Goose style twin engine amphibian (14.9 m span, 11.7 m long)
  Buckingham Maverick  Bell 206 JetRanger (10.2 m rotor), also Police Maverick and Weazel News Maverick
  Nagasaki Buzzard     MD 500 Defender (8.1 m rotor), police livery
  Buckingham Shamal    Learjet 35 business jet (12 m span, 14.8 m long)
Naming convention read by the renderer: cubes named glass* are translucent, lamp* glow at full brightness;
bones propeller*, rotor, tail_rotor spin and searchlight aims at the target.
The models are read by the Blockbench renderer ported from Immersive Aircraft (GPL-3.0).
"""
import json
import math
import os
import uuid as uuidlib

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), *[".."] * 7))
RES = os.path.join(ROOT, "src/main/resources/assets/leonida")
OBJ_DIR = os.path.join(RES, "objects/aircraft")
TEX = os.path.join(RES, "textures/entity/aircraft/palette.png")
ITEM_TEX = os.path.join(RES, "textures/item/aircraft")
TEX_ID = "leonida:textures/entity/aircraft/palette.png"
W = H = 256

# ---------------------------------------------------------------- palette
COLORS = {
    "white": (238, 240, 242), "offwhite": (222, 218, 208), "lightgray": (178, 182, 188), "gray": (120, 124, 130),
    "darkgray": (62, 64, 70), "black": (24, 24, 28), "glass": (70, 105, 140), "glass_dark": (30, 45, 62),
    "pink": (255, 92, 168), "hotpink": (236, 40, 140), "teal": (0, 150, 150), "aqua": (90, 206, 230),
    "skyblue": (130, 190, 235), "navy": (22, 38, 84), "blue": (30, 90, 200), "red": (200, 30, 36),
    "orange": (245, 130, 40), "yellow": (246, 196, 40), "gold": (200, 160, 70), "green": (30, 90, 55),
    "chrome": (215, 220, 225), "rubber": (30, 30, 32), "lamp": (255, 250, 225), "lamp_blue": (50, 100, 255),
    "lamp_red": (255, 40, 40), "lamp_green": (40, 230, 80), "tan": (150, 100, 60), "leather": (95, 55, 35),
    "cream": (240, 228, 196), "steel": (95, 104, 116), "vcpd": (18, 52, 140), "vcpd_dark": (12, 28, 70),
    "plum": (168, 40, 112), "plum_dark": (112, 28, 74), "magenta": (214, 50, 140), "maroon": (120, 24, 40),
    "beaver": (238, 184, 30), "beaver_dark": (180, 130, 20), "float": (196, 200, 204), "olive": (70, 90, 60),
    "seat": (50, 50, 56), "dash": (34, 34, 38), "bronze": (170, 120, 60), "weazel": (190, 20, 30),
    "ice": (200, 225, 240), "silver": (190, 194, 200), "panel": (205, 208, 212),
}
SWATCH = {}
DECALS = []  # (name, bg, fg, text) filled before build_palette


def decal_name(bg, fg, text):
    name = "decal_" + "".join(c for c in text.lower() if c.isalnum()) + "_" + bg + "_" + fg
    if name not in [d[0] for d in DECALS]:
        DECALS.append((name, bg, fg, text))
    return name


def build_palette():
    img = Image.new("RGBA", (W, H), (255, 0, 255, 255))
    d = ImageDraw.Draw(img)
    for i, (name, rgb) in enumerate(COLORS.items()):
        x, y = (i % 32) * 8, (i // 32) * 8
        a = 150 if name.startswith("glass") else 255
        d.rectangle([x, y, x + 7, y + 7], fill=rgb + (a,))
        if name.startswith("glass"):  # a soft highlight streak so glass reads as glass
            d.line([x + 1, y + 6, x + 6, y + 1], fill=tuple(min(255, c + 60) for c in rgb) + (190,))
        SWATCH[name] = (x + 1.5, y + 1.5, x + 6.5, y + 6.5)
    for i, (name, bg, fg, text) in enumerate(DECALS):
        x0, y0 = (i % 2) * 128, 32 + (i // 2) * 16
        d.rectangle([x0, y0, x0 + 127, y0 + 15], fill=COLORS[bg] + (255,))
        font = ImageFont.load_default(13)
        tw = d.textlength(text, font=font)
        d.text((x0 + (128 - tw) / 2, y0 + 0.5), text, fill=COLORS[fg] + (255,), font=font)
        SWATCH[name] = (x0 + 0.5, y0 + 0.5, x0 + 127.5, y0 + 15.5)
    # parachute cell stripes
    stripes = ["hotpink", "white", "teal", "yellow", "white", "hotpink", "orange", "white"]
    for i, s in enumerate(stripes):
        d.rectangle([i * 16, 240, i * 16 + 15, 255], fill=COLORS[s] + (255,))
    SWATCH["chute"] = (0.5, 240.5, 127.5, 255.5)
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    img.save(TEX)


def uid():
    return str(uuidlib.uuid4())


class Model:
    def __init__(self, name):
        self.name = name
        self.elements = []
        self.root = []
        self.bones = {}

    def bone(self, name, origin=(0, 0, 0), parent=None):
        b = {"name": name, "uuid": uid(), "origin": list(origin), "rotation": [0, 0, 0], "children": []}
        (self.bones[parent]["children"] if parent else self.root).append(b)
        self.bones[name] = b
        return name

    def box(self, x0, y0, z0, x1, y1, z1, color, bone=None, rot=None, origin=None, decals=None, name="cube", skip=()):
        """decals: {side: swatch_name or (swatch_name, flip)} overriding single faces. skip: sides left out."""
        faces = {}
        for side in ("north", "east", "south", "west", "up", "down"):
            if side in skip:
                faces[side] = {"uv": [0, 0, 0, 0], "texture": None}
                continue
            sw = color
            flip = False
            if decals and side in decals:
                sw = decals[side]
                if isinstance(sw, tuple):
                    sw, flip = sw
            u0, v0, u1, v1 = SWATCH[sw]
            if flip:
                u0, u1 = u1, u0
            faces[side] = {"uv": [u0, v0, u1, v1], "texture": 0}
        lo = [min(x0, x1), min(y0, y1), min(z0, z1)]
        hi = [max(x0, x1), max(y0, y1), max(z0, z1)]
        if origin is None:
            origin = [(lo[i] + hi[i]) / 2 for i in range(3)]
        e = {"name": name, "type": "cube", "uuid": uid(), "from": lo, "to": hi, "origin": list(origin),
             "rotation": list(rot or (0, 0, 0)), "faces": faces}
        self.elements.append(e)
        (self.bones[bone]["children"] if bone else self.root).append(e["uuid"])

    def mirror(self, x0, y0, z0, x1, y1, z1, color, **kw):
        self.box(x0, y0, z0, x1, y1, z1, color, **kw)
        kw = dict(kw)
        if "rot" in kw and kw["rot"]:
            r = kw["rot"]
            kw["rot"] = (r[0], -r[1], -r[2])
        if "origin" in kw and kw["origin"]:
            o = kw["origin"]
            kw["origin"] = (-o[0], o[1], o[2])
        if "decals" in kw and kw["decals"]:
            dd = {}
            for k, v in kw["decals"].items():
                k2 = {"east": "west", "west": "east"}.get(k, k)
                if isinstance(v, tuple):
                    v = (v[0], not v[1])
                else:
                    v = (v, True)
                dd[k2] = v
            kw["decals"] = dd
        self.box(-x1, y0, z0, -x0, y1, z1, color, **kw)

    def rod(self, a, b, thick, color, bone=None):
        """A thin box from point a to point b (only rotations in the X/Y or Z/Y plane are exact)."""
        ax, ay, az = a
        bx, by, bz = b
        mx, my, mz = (ax + bx) / 2, (ay + by) / 2, (az + bz) / 2
        dx, dy, dz = bx - ax, by - ay, bz - az
        length = math.sqrt(dx * dx + dy * dy + dz * dz)
        rz = -math.degrees(math.atan2(dx, dy)) if abs(dx) > 1e-6 else 0.0
        rx = math.degrees(math.atan2(dz, math.sqrt(dx * dx + dy * dy))) if abs(dz) > 1e-6 else 0.0
        t = thick / 2
        self.box(mx - t, my - length / 2, mz - t, mx + t, my + length / 2, mz + t, color, bone=bone,
                 rot=(rx, 0, rz), origin=(mx, my, mz))

    def save(self):
        data = {
            "meta": {"format_version": "4.9", "model_format": "free", "box_uv": False},
            "name": self.name,
            "resolution": {"width": W, "height": H},
            "elements": self.elements,
            "outliner": self.root,
            "textures": [{"uuid": uid(), "id": "0", "name": TEX_ID, "width": W, "height": H, "uv_width": W,
                          "uv_height": H}],
            "animations": [],
        }
        os.makedirs(OBJ_DIR, exist_ok=True)
        with open(os.path.join(OBJ_DIR, self.name + ".bbmodel"), "w") as f:
            json.dump(data, f, separators=(",", ":"))


# ---------------------------------------------------------------- shared parts
def rbox(m, x0, y0, z0, x1, y1, z1, color, ch=None, **kw):
    """A box with chamfered long edges (two crossed boxes), reads as rounded at block scale."""
    w, h = abs(x1 - x0), abs(y1 - y0)
    if ch is None:
        ch = min(w, h) * 0.22
    ch = min(ch, w / 2 - 0.01, h / 2 - 0.01)
    if ch <= 0.05:
        m.box(x0, y0, z0, x1, y1, z1, color, **kw)
        return
    m.box(x0 + ch, y0, z0, x1 - ch, y1, z1, color, **kw)
    m.box(x0, y0 + ch, z0, x1, y1 - ch, z1, color, **kw)


def _cut(lo, hi, holes):
    """The parts of [lo, hi] left after removing the (a, b) holes."""
    out = [(lo, hi)]
    for a, b in holes:
        nxt = []
        for p, q in out:
            if b <= p or a >= q:
                nxt.append((p, q))
                continue
            if a > p:
                nxt.append((p, a))
            if b < q:
                nxt.append((b, q))
        out = nxt
    return [(p, q) for p, q in out if q - p > 0.05]


def _shell_slice(m, cx, cy, hw, hh, ch, zl, zh, color, colors, H, kw):
    """One hollow fuselage slice: floor, side walls with window holes, chamfer corners and roof."""
    zm = (zl + zh) / 2
    wall = H.get("wall", 0.8)
    floor = H["floor"]
    yb, yt = cy - hh, cy + hh
    k = dict(kw)
    k["skip"] = ("north", "south")
    holes = [(y0, y1) for (z0, z1, y0, y1) in H.get("openings", []) if min(z0, z1) <= zm <= max(z0, z1)]
    roof_open = any(min(a, b) <= zm <= max(a, b) for a, b in H.get("roof_open", []))
    rbox(m, cx - hw, yb, zl, cx + hw, floor, zh, color, ch=ch, **k)
    for sg in (1, -1):
        def bx(xa, xb, ya, yb_, col=color):
            lo, hi = sorted((cx + sg * xa, cx + sg * xb))
            m.box(lo, ya, zl, hi, yb_, zh, col, **k)
        for p, q in _cut(floor, yt - ch, holes):
            bx(hw - wall, hw, p, q)
        if not roof_open:
            bx(hw - ch - wall, hw, yt - ch - wall, yt - ch)
            bx(hw - ch - wall, hw - ch, yt - ch, yt)
        for (b0, b1, c) in colors or []:  # paint bands as thin strips on the outside of the wall
            for p, q in _cut(max(b0, floor), min(b1, yt - ch), holes):
                bx(hw - 0.1, hw + 0.08, p, q, c)
    if not roof_open:
        m.box(cx - hw + ch, yt - wall, zl, cx + hw - ch, yt, zh, color, **k)
    for (b0, b1, c) in colors or []:  # bands below the floor stay full width boxes
        lo, hi = max(b0, yb + ch), min(b1, floor)
        if hi - lo > 0.2:
            m.box(cx - hw - 0.08, lo, zl - 0.02, cx + hw + 0.08, hi, zh + 0.02, c, **kw)
        if b0 <= yb < b1:
            m.box(cx - hw + ch, yb - 0.08, zl - 0.02, cx + hw - ch, min(b1, yb + ch + 0.01), zh + 0.02, c, **kw)


def loft(m, stations, color, cx=0.0, step=4.0, round_=0.3, colors=None, hollow=None, **kw):
    """Fuselage from stations (z, cy, hw, hh) ordered front to back, cut into short chamfered slices.
    colors: optional list of (y_from, y_to, color) bands painted over the slices (stripes, belly).
    hollow: optional cabin, dict(z=(front, back), floor=y, openings=[(z0, z1, y0, y1)], roof_open=[(z0, z1)],
    nose=(front, back, top)): slices inside z become a shell so seated players can see out, slices inside
    nose are cut off at top (the windshield sits above)."""
    for (za, ya, wa, ha), (zb, yb, wb, hb) in zip(stations, stations[1:]):
        n = max(1, int(math.ceil(abs(za - zb) / step)))
        for i in range(n):
            t0, t1 = i / n, (i + 1) / n
            tm = (t0 + t1) / 2
            z0 = za + (zb - za) * t0
            z1 = za + (zb - za) * t1
            zl, zh = min(z0, z1), max(z0, z1)
            cy = ya + (yb - ya) * tm
            hw = wa + (wb - wa) * tm
            hh = ha + (hb - ha) * tm
            ch = min(hw, hh) * round_
            if kw.get("name", "").startswith("glass"):  # one clean shell per slice, no inner faces to see through
                first = (za, ya, wa, ha) == stations[0] and i == 0
                last = (zb, yb, wb, hb) == stations[-1] and i == n - 1
                # the rear cap of a glass nose is inside the cabin: never draw it, it would tint the whole view
                skip = tuple(sd for sd, keep in (("south", first), ("north", False)) if not keep)
                m.box(cx - hw, cy - hh, zl, cx + hw, cy + hh, zh, color, skip=skip, **kw)
                continue
            zm = (zl + zh) / 2
            if hollow and min(hollow["z"]) <= zm <= max(hollow["z"]):
                _shell_slice(m, cx, cy, hw, hh, ch, zl, zh, color, colors, hollow, kw)
                continue
            top = cy + hh
            if hollow and "nose" in hollow and min(hollow["nose"][:2]) <= zm <= max(hollow["nose"][:2]):
                top = min(top, hollow["nose"][2])
                if top <= cy - hh + 0.5:
                    continue
            rbox(m, cx - hw, cy - hh, zl, cx + hw, top, zh, color, ch=min(ch, (top - cy + hh) / 2), **kw)
            for (b0, b1, c) in colors or []:
                if top < cy + hh:
                    b1 = min(b1, top)
                    if b1 <= b0:
                        continue
                lo, hi = max(b0, cy - hh + ch), min(b1, cy + hh - ch)
                if hi - lo > 0.2:  # the band on the sides
                    m.box(cx - hw - 0.08, lo, zl - 0.02, cx + hw + 0.08, hi, zh + 0.02, c, **kw)
                if b0 <= cy - hh < b1:  # band includes the belly
                    m.box(cx - hw + ch, cy - hh - 0.08, zl - 0.02, cx + hw - ch, min(b1, cy - hh + ch + 0.01),
                          zh + 0.02, c, **kw)
                if b0 < cy + hh <= b1:  # band includes the roof
                    m.box(cx - hw + ch, max(b0, cy + hh - ch - 0.01), zl - 0.02, cx + hw - ch, cy + hh + 0.08,
                          zh + 0.02, c, **kw)


def station_at(stations, z):
    for (za, ya, wa, ha), (zb, yb, wb, hb) in zip(stations, stations[1:]):
        if zb <= z <= za or za <= z <= zb:
            t = 0 if za == zb else (z - za) / (zb - za)
            return ya + (yb - ya) * t, wa + (wb - wa) * t, ha + (hb - ha) * t
    return stations[-1][1:]


def disc(m, axis, c, r, depth, color, bone=None, name="cube"):
    """A round disc or cylinder from three crossed boxes (16 sided silhouette). axis: 'x', 'y' or 'z'."""
    cx, cy, cz = c
    d = depth / 2
    for a, b in ((r * 0.924, r * 0.383), (r * 0.383, r * 0.924), (r * 0.707, r * 0.707)):
        if axis == "z":
            m.box(cx - a, cy - b, cz - d, cx + a, cy + b, cz + d, color, bone=bone, name=name)
        elif axis == "x":
            m.box(cx - d, cy - a, cz - b, cx + d, cy + a, cz + b, color, bone=bone, name=name)
        else:
            m.box(cx - a, cy - d, cz - b, cx + a, cy + d, cz + b, color, bone=bone, name=name)


def wheel(m, x, z, r, w, y=0.0, hub="chrome", tire="rubber"):
    disc(m, "x", (x, y + r, z), r, w, tire)
    disc(m, "x", (x, y + r, z), r * 0.5, w + 0.3, hub)
    m.box(x - w / 2 - 0.25, y + r - 0.6, z - 0.6, x + w / 2 + 0.25, y + r + 0.6, z + 0.6, "darkgray")


def propeller(m, bone, x, y, z, radius, blades=2, blade="black", tip="yellow", spinner="chrome", spin_r=2.6, chord=2.4):
    m.bone(bone, origin=(x, y, z))
    disc(m, "z", (x, y, z + 1.5), spin_r, 3.2, spinner, bone=bone)
    disc(m, "z", (x, y, z + 3.6), spin_r * 0.55, 1.4, spinner, bone=bone)
    for i in range(blades):
        ang = 360.0 * i / blades
        m.box(x - chord / 2, y, z - 0.4, x + chord / 2, y + radius, z + 0.6, blade, bone=bone,
              rot=(0, 0, ang), origin=(x, y, z))
        if tip:
            m.box(x - chord / 2 - 0.05, y + radius - 2, z - 0.45, x + chord / 2 + 0.05, y + radius, z + 0.65, tip,
                  bone=bone, rot=(0, 0, ang), origin=(x, y, z))


def rotor(m, y, z, radius, blades, chord=5.0, color="darkgray", tip="white", hub="steel", x=0.0):
    m.bone("rotor", origin=(x, y, z))
    disc(m, "y", (x, y + 0.5, z), 3.0, 2.6, hub, bone="rotor")
    m.box(x - 1, y + 1.8, z - 1, x + 1, y + 3.4, z + 1, hub, bone="rotor")
    for i in range(blades):
        ang = 360.0 * i / blades
        m.box(x - chord / 2, y - 0.3, z + 3, x + chord / 2, y + 0.5, z + radius, color, bone="rotor",
              rot=(0, ang, 0), origin=(x, y, z))
        m.box(x - chord / 2 - 0.05, y - 0.35, z + radius - 4, x + chord / 2 + 0.05, y + 0.55, z + radius, tip,
              bone="rotor", rot=(0, ang, 0), origin=(x, y, z))
        m.box(x - 1.2, y - 0.2, z + 1.5, x + 1.2, y + 0.9, z + 6, hub, bone="rotor", rot=(0, ang, 0), origin=(x, y, z))


def tail_rotor(m, x, y, z, radius, blades=2, color="darkgray", tip="red"):
    m.bone("tail_rotor", origin=(x, y, z))
    disc(m, "x", (x, y, z), 1.6, 1.8, "steel", bone="tail_rotor")
    for i in range(blades):
        ang = 360.0 * i / blades
        m.box(x - 0.3, y, z - 1.1, x + 0.3, y + radius, z + 1.1, color, bone="tail_rotor", rot=(ang, 0, 0),
              origin=(x, y, z))
        m.box(x - 0.35, y + radius - 2.5, z - 1.15, x + 0.35, y + radius, z + 1.15, tip, bone="tail_rotor",
              rot=(ang, 0, 0), origin=(x, y, z))


def wing(m, x_in, x_out, y, z_lead, z_trail, thick, color, tip_round=True, aileron=None, dihedral=0.0, **kw):
    """Right-hand wing panel from x_in to x_out (positive), mirrored to the left. Rounded tip if asked."""
    tip = 6 if tip_round else 0
    chord = z_lead - z_trail
    for sgn in (1, -1):
        def b(xa, xb, ya, yb, za, zb, col):
            lo, hi = sorted((sgn * xa, sgn * xb))
            r = (0, 0, -sgn * dihedral) if dihedral else None
            m.box(lo, ya, zb, hi, yb, za, col, rot=r, origin=(sgn * x_in, y, 0) if dihedral else None, **kw)
        b(x_in, x_out - tip, y, y + thick, z_lead, z_trail, color)
        if tip:
            b(x_out - tip, x_out - tip / 2, y, y + thick, z_lead - chord * 0.06, z_trail + chord * 0.05, color)
            b(x_out - tip / 2, x_out, y + 0.15, y + thick - 0.15, z_lead - chord * 0.2, z_trail + chord * 0.16, color)
        if aileron:
            b(x_out - tip - (x_out - x_in) * 0.38, x_out - tip, y - 0.05, y + thick + 0.05, z_trail + chord * 0.24,
              z_trail - 0.05, aileron)


def skid_gear(m, x, y_floor, z_front, z_back, z_cross=(18, -12), color="silver"):
    for s in (1, -1):
        m.box(s * x - 0.9, 0, z_back, s * x + 0.9, 1.6, z_front, color)
        m.box(s * x - 0.9, 0.4, z_front - 0.5, s * x + 0.9, 2.0, z_front + 5, color, rot=(-28, 0, 0),
              origin=(s * x, 0.8, z_front))
        for zc in z_cross:
            m.rod((s * x, 1.4, zc), (s * (x - 3.5), y_floor, zc), 1.4, color)
        m.box(s * x - 0.6, 1.4, z_cross[0] - 3, s * x + 0.6, 2.4, z_cross[0] + 3, "darkgray")  # step


def nav_lights(m, x, y, z, tail=None, beacon=None):
    m.box(x - 0.8, y - 0.8, z - 0.8, x + 0.4, y + 0.8, z + 0.8, "lamp_red", name="lamp")  # port, pilot's left
    m.box(-x - 0.4, y - 0.8, z - 0.8, -x + 0.8, y + 0.8, z + 0.8, "lamp_green", name="lamp")  # starboard
    if tail:
        m.box(tail[0] - 0.6, tail[1] - 0.6, tail[2] - 1.0, tail[0] + 0.6, tail[1] + 0.6, tail[2], "lamp", name="lamp")
    if beacon:
        m.box(beacon[0] - 0.9, beacon[1], beacon[2] - 0.9, beacon[0] + 0.9, beacon[1] + 1.2, beacon[2] + 0.9,
              "lamp_red", name="lamp")


def seat(m, x, y, z, w=7, color="seat", back=11, base=None):
    m.box(x - w / 2, y - 2, z - 3, x + w / 2, y, z + 4, color)
    if base is not None and base < y - 2:
        m.box(x - w / 2 + 1, base, z - 2, x + w / 2 - 1, y - 2, z + 3, "darkgray")
    m.box(x - w / 2, y, z - 4.5, x + w / 2, y + back, z - 2.5, color, rot=(-10, 0, 0), origin=(x, y, z - 3))


# ---------------------------------------------------------------- decals used by the liveries
D_POLICE = decal_name("white", "vcpd", "POLICE")
D_POLICE_NAVY = decal_name("vcpd_dark", "white", "POLICE")
D_VCPD = decal_name("vcpd", "white", "VCPD  AIR 1")
D_VCPD2 = decal_name("vcpd_dark", "white", "VCPD  AIR 2")
D_WEAZEL = decal_name("weazel", "white", "WEAZEL NEWS")
D_WEAZEL_W = decal_name("white", "weazel", "WEAZEL NEWS")
D_BUCK_W = decal_name("white", "navy", "BUCKINGHAM")
D_BUCK_B = decal_name("black", "gold", "BUCKINGHAM")
D_BUCK_R = decal_name("red", "white", "BUCKINGHAM")
D_MAMMOTH = decal_name("white", "navy", "MAMMOTH")
D_MAMMOTH_C = decal_name("cream", "teal", "MAMMOTH")
D_WESTERN = decal_name("plum", "cream", "WESTERN")
D_WESTERN_N = decal_name("navy", "yellow", "WESTERN")
D_WESTERN_R = decal_name("red", "cream", "WESTERN")
REG_BEAVER = [decal_name("beaver", "black", "N-724LB"), decal_name("white", "red", "N-724LB"),
              decal_name("green", "cream", "N-724LB")]
REG_DODO = [decal_name("white", "navy", "N-305VC"), decal_name("cream", "teal", "N-305VC")]
REG_SHAMAL = [decal_name("white", "navy", "N-88LX"), decal_name("black", "gold", "N-88LX")]


# ---------------------------------------------------------------- Western Duster (Boeing-Stearman PT-17)
DUSTER_VARIANTS = [
    dict(body="plum", wing="plum_dark", trim="cream", decal=D_WESTERN),  # Launch Trailer, pink smoke over the beach
    dict(body="navy", wing="yellow", trim="yellow", decal=D_WESTERN_N),  # classic Stearman trainer
    dict(body="red", wing="cream", trim="cream", decal=D_WESTERN_R),
]


def western_duster(m, v):
    body, wingc, trim = v["body"], v["wing"], v["trim"]
    st = [(40, 27, 7.0, 8.0), (24, 27, 7.0, 8.5), (4, 27, 6.5, 8.0), (-16, 26.5, 5.5, 6.5), (-36, 26, 4.2, 5.0),
          (-56, 25.5, 2.6, 3.4), (-68, 25.5, 1.4, 2.2)]
    loft(m, st, body, step=4, round_=0.35)
    for z0, z1, y in ((40, 4, 29.5), (4, -36, 28.6), (-36, -60, 27.0)):  # cheat line following the fuselage
        hw = station_at(st, (z0 + z1) / 2)[1]
        m.mirror(hw * 0.6, y, z1, hw + 0.1, y + 1.0, z0, trim)
    # fin decal and turtle deck
    m.box(-4.5, 35, -40, 4.5, 36.4, 0, body)
    # radial engine: black ring cowl, nine cylinders, chrome hub
    disc(m, "z", (0, 27, 45), 9.6, 9, "black")
    disc(m, "z", (0, 27, 49.6), 7.2, 0.6, "darkgray")
    for i in range(9):
        a = math.radians(i * 40)
        cxp, cyp = math.sin(a) * 6.0, 27 + math.cos(a) * 6.0
        m.box(cxp - 1.1, cyp - 1.1, 48.5, cxp + 1.1, cyp + 1.1, 50.6, "steel")
        m.box(cxp - 1.3, cyp - 0.3, 49.4, cxp + 1.3, cyp + 0.3, 50.0, "silver")
    m.mirror(5, 19, 36, 6.5, 20.5, 46, "darkgray")  # exhaust stubs
    propeller(m, "propeller", 0, 27, 51, 21, blades=2, blade="steel", tip="yellow", spinner="chrome", spin_r=3.2, chord=2.6)
    # cockpits: leather coaming, dark tubs, seats, panels, small windscreens
    for z0, z1 in ((10, 0), (-10, -21)):
        m.box(-5.2, 35.0, z1, 5.2, 35.6, z0, "leather")
        m.box(-4.2, 35.05, z1 + 1, 4.2, 35.7, z0 - 1, "black")
        m.box(-4, 30, z1 + 1.5, 4, 31, z1 + 6, "leather")  # seat cushion
        m.box(-4, 31, z1 + 0.8, 4, 35, z1 + 2, "leather")  # seat back
        m.box(-4, 31.5, z0 - 1.6, 4, 35, z0 - 1, "dash")
        for dx in (-2.2, 0, 2.2):
            m.box(dx - 0.7, 33, z0 - 1.7, dx + 0.7, 34.4, z0 - 1.55, "silver")
        m.box(-3.5, 35.4, z0 + 0.6, 3.5, 39, z0 + 1.0, "glass", rot=(-25, 0, 0), origin=(0, 35.4, z0 + 0.8), name="glass_ws")
    # upper wing (with center cut-out), lower wing, ailerons
    wing(m, 4, 78, 47.5, 36, 13, 2.2, wingc, aileron=body)
    m.box(-4, 47.5, 13, 4, 49.7, 30, wingc)
    wing(m, 6.5, 75, 19.5, 33, 11, 2.0, wingc, aileron=body, dihedral=2.5)
    # cabane struts and N interplane struts
    for s in (1, -1):
        m.rod((s * 5, 35, 31), (s * 8, 47.5, 31), 1.0, "silver")
        m.rod((s * 5, 35, 18), (s * 8, 47.5, 18), 1.0, "silver")
        m.rod((s * 52, 21.5, 30), (s * 52, 47.5, 32), 1.2, "silver")
        m.rod((s * 52, 21.5, 14), (s * 52, 47.5, 16), 1.2, "silver")
        m.rod((s * 52, 21.5, 15), (s * 52, 47.5, 31), 0.8, "silver")
        # flying and landing wires
        m.rod((s * 8, 21, 25), (s * 50, 47.5, 25), 0.35, "lightgray")
        m.rod((s * 8, 47.5, 22), (s * 50, 21.5, 22), 0.35, "lightgray")
    # main gear: split axle legs, wheels with chrome hubs
    for s in (1, -1):
        m.rod((s * 18, 6, 30), (s * 6, 19.5, 33), 1.4, "silver")
        m.rod((s * 18, 6, 30), (s * 6, 19.5, 24), 1.2, "silver")
        m.rod((s * 18, 6, 30), (s * 2, 19.2, 29), 1.0, "silver")
        wheel(m, s * 19.5, 30, 6, 3.4, hub="chrome")
    # tail: stabiliser with elevators, fin and rudder (rounded), tailwheel
    m.mirror(1, 25.6, -68, 20, 26.9, -58, wingc)
    m.mirror(1, 25.5, -72, 18, 27.0, -68, body)
    for y0, y1, z0, z1 in ((26.5, 32.5, -70, -56), (32.5, 37.5, -71, -59), (37.5, 41.5, -71, -62), (41.5, 43.5, -70, -65)):
        m.box(-0.8, y0, z0, 0.8, y1, z1, wingc)
    m.box(-0.9, 26.5, -74, 0.9, 41.5, -70, body)  # rudder
    m.box(-0.95, 32.5, -66, 0.95, 36.5, -57.5, wingc, decals={"east": v["decal"], "west": (v["decal"], True)})
    m.rod((0, 3.5, -64), (0, 23, -66), 1.2, "silver")
    wheel(m, 0, -63, 2.2, 1.6)
    # smoke generator pipes on the lower wing trailing edge
    m.mirror(10, 18.2, 6, 11.4, 19.6, 12, "steel")
    nav_lights(m, 78.2, 48.6, 26)


# ---------------------------------------------------------------- Floatplane (DHC-2 Beaver on EDO floats)
BEAVER_VARIANTS = [
    dict(body="beaver", trim="black", wing="beaver", float="float", reg=REG_BEAVER[0]),  # Trailer 2 marina
    dict(body="white", trim="red", wing="white", float="float", reg=REG_BEAVER[1]),
    dict(body="green", trim="cream", wing="green", float="silver", reg=REG_BEAVER[2]),
]


def floatplane(m, v):
    body, trim = v["body"], v["trim"]
    WS = 42.8  # windshield rake: base on the cowl deck (y 44, z 44) up to the wing leading edge (y 51.4, z 36)
    # floats (EDO style): lofted pontoons with upturned bows, step and water rudders
    for s in (1, -1):
        fx = s * 21
        fst = [(52, 9.5, 1.5, 2.0), (48, 8, 3.5, 4.0), (40, 6.5, 4.6, 5.2), (10, 6.0, 4.8, 5.5), (-14, 6.0, 4.8, 5.5),
               (-40, 6.8, 3.8, 4.5), (-62, 8.0, 2.4, 3.2), (-72, 9.0, 1.2, 2.0)]
        loft(m, fst, v["float"], cx=fx, step=4, round_=0.35, colors=[(4.0, 6.5, trim)])
        m.box(fx - 0.6, 11.4, -60, fx + 0.6, 12.0, 40, "darkgray")  # deck rail
        m.box(fx - 0.4, 0.6, -72, fx + 0.4, 6, -68, "darkgray")  # water rudder
        for z in (30, -6):
            m.rod((fx, 11.4, z), (s * 8, 27, z + 2), 1.4, "silver")
        m.rod((fx, 11.4, 30), (s * 8, 27, -4), 0.8, "silver")
    for z in (30, -6):
        m.rod((21, 10.5, z), (-21, 10.5, z), 1.0, "silver")
    st = [(54, 35.5, 9.0, 9.0), (44, 37, 10.0, 11.0), (30, 38.5, 10.6, 12.5), (8, 38.5, 10.6, 12.5),
          (-14, 39.5, 9.6, 11.0), (-34, 41.5, 7.2, 8.0), (-56, 43.5, 5.0, 5.8), (-76, 45.5, 3.0, 3.6),
          (-86, 46.5, 1.8, 2.4)]
    loft(m, st, body, step=4, round_=0.25, colors=[(29.0, 31.0, trim)],
         hollow=dict(z=(44, -10), floor=27, nose=(54, 44, 44), roof_open=[(2, 44)],
                     openings=[(43.5, 37, 40, 48.0), (36, 25, 39.5, 48.5), (24, 12, 39.5, 48.5), (11, -6, 39.5, 48.5)]))
    # big round cowl with nine cylinder radial behind a grille, oil cooler scoop, exhaust
    disc(m, "z", (0, 35.5, 58), 9.8, 10, body)
    disc(m, "z", (0, 35.5, 63.1), 7.8, 0.4, "black")
    for i in range(9):
        a = math.radians(i * 40 + 20)
        m.box(math.sin(a) * 6 - 1, 35.5 + math.cos(a) * 6 - 1, 62.6, math.sin(a) * 6 + 1, 35.5 + math.cos(a) * 6 + 1, 63.4, "steel")
    m.box(-3.5, 25.5, 46, 3.5, 28, 56, "darkgray")
    m.box(-0.8, 28.5, 44, 0.8, 30.5, 50, "black")
    m.mirror(8, 27, 38, 10.5, 29, 46, "darkgray")
    propeller(m, "propeller", 0, 35.5, 63.6, 20.5, blades=2, blade="black", tip="yellow", spinner=body, spin_r=3.4, chord=2.8)
    # cabin glazing: sloped windshield, side windows and door frames, roof skylights
    m.box(-9.8, 43.6, 33.0, 9.8, 44.0, 44, "glass", rot=(WS, 0, 0), origin=(0, 44, 44), name="glass_ws")
    m.box(-0.35, 44.0, 33.0, 0.35, 44.5, 44, body, rot=(WS, 0, 0), origin=(0, 44, 44))  # slim centre post
    for z0, z1 in ((36, 25), (24, 12), (11, -6)):
        m.mirror(10.55, 39.5, z1, 10.75, 48.5, z0, "glass", name="glass_side")
    m.mirror(10.15, 40, 37, 10.35, 48, 43.5, "glass", name="glass_side")  # quarter windows beside the windshield
    m.mirror(10.6, 28, 24.5, 10.9, 50, 25.5, body)  # door pillars
    m.mirror(10.6, 28, 11.2, 10.9, 50, 12.0, body)
    m.mirror(10.7, 36, 23.0, 11.0, 36.6, 25.0, "chrome")  # door handles
    m.box(-9.5, 50.9, 2, 9.5, 51.1, 14, "glass", name="glass_roof")
    m.mirror(9.6, 48.2, 2, 10.6, 51.5, 36, body)  # cabin roof rails under the wing root
    # interior: dash, yokes, front seats, rear bench
    m.box(-9, 34.5, 33, 9, 39, 35, "dash")
    for x in (5, -5):
        m.box(x - 2.5, 36.4, 31, x + 2.5, 37.4, 31.8, "black")
        seat(m, x, 30.4, 24, w=7, back=12, base=27)
    seat(m, 0, 30.4, 6, w=17, back=12, base=27)
    # high wing with fuel caps, ailerons, flaps and twin struts
    wing(m, 0, 117, 51.5, 36, 10, 3.0, v["wing"], aileron=trim)
    m.box(-10, 51.4, 10, 10, 54.5, 36, v["wing"])
    for s in (1, -1):
        m.rod((s * 10, 29.5, 26), (s * 62, 51.5, 31), 1.6, "silver")
        m.rod((s * 10, 29.5, 22), (s * 62, 51.5, 16), 1.6, "silver")
        m.box(s * 30 - 1, 54.4, 26, s * 30 + 1, 54.9, 28, "chrome")
    # tail: big rounded fin with dorsal fillet, stabiliser with elevators, registration
    m.mirror(1.5, 45.4, -88, 33, 46.9, -72, v["wing"])
    m.mirror(1.5, 45.3, -92, 30, 47.0, -88, trim)
    for y0, y1, z0, z1 in ((47, 54, -92, -60), (54, 62, -93, -70), (62, 68, -93, -76), (68, 72, -92, -81)):
        m.box(-1, y0, z0, 1, y1, z1, body)
    m.box(-1.1, 47, -96, 1.1, 70, -92, trim)
    m.box(-5.0, 41, -60, 5.0, 42.5, -40, body)
    m.box(-0.5, 42, -60, 0.5, 47, -40, body, rot=(-8, 0, 0))
    m.mirror(1.03, 50, -90, 1.13, 55, -66, body, decals={"west": v["reg"], "east": (v["reg"], True)})
    nav_lights(m, 117.2, 53, 24, tail=(0, 64, -93.5), beacon=(0, 54, 0))


# ---------------------------------------------------------------- Mammoth Dodo (twin engine amphibian)
DODO_VARIANTS = [
    dict(body="white", stripe="blue", stripe2="red", belly="navy", decal=D_MAMMOTH, reg=REG_DODO[0]),
    dict(body="cream", stripe="teal", stripe2="gold", belly="green", decal=D_MAMMOTH_C, reg=REG_DODO[1]),
]


def dodo(m, v):
    body = v["body"]
    st = [(84, 30, 3.0, 5.0), (78, 28, 8.0, 12.0), (66, 27.5, 11.0, 17.0), (40, 28, 12.0, 19.0), (0, 28, 12.0, 19.0),
          (-22, 30, 11.0, 17.0), (-48, 35, 8.5, 12.5), (-78, 41, 5.5, 8.0), (-104, 46, 2.6, 5.0), (-108, 47, 1.8, 4.0)]
    loft(m, st, body, step=4, round_=0.3, colors=[(8, 15, v["belly"]), (29, 32, v["stripe"]), (32, 33.5, v["stripe2"])],
         hollow=dict(z=(64, -20), floor=25, nose=(84, 64, 39), roof_open=[(57, 64)],
                     openings=[(52, 60, 38, 45)] + [(z - 3, z + 3, 36, 42) for z in (38, 26, 14, 2, -10)]))
    m.box(-0.8, 8.5, -22, 0.8, 9.6, 80, v["belly"])  # keel
    m.mirror(11.3, 8, -24, 12.6, 15, -20, v["belly"])  # hull step
    # cockpit glazing and cabin windows
    m.box(-10.5, 39, 58, 10.5, 45, 70, "glass", rot=(-40, 0, 0), origin=(0, 40, 64), name="glass_ws")
    m.mirror(11.6, 38, 52, 12.2, 45, 60, "glass", name="glass_cock")
    for z in (38, 26, 14, 2, -10):
        m.mirror(11.9, 36, z - 3, 12.2, 42, z + 3, "glass", name="glass_win")
    m.mirror(12.0, 23, 0, 12.1, 27, 28, body, decals={"west": v["decal"], "east": (v["decal"], True)})
    m.mirror(11.95, 18, -18, 12.15, 33, -17, "darkgray")  # cabin door
    m.mirror(11.95, 18, -27, 12.15, 33, -26, "darkgray")
    # interior seats behind the glass
    for x in (5, -5):
        seat(m, x, 30, 54, w=7, base=25)
        for z in (29, 6):
            seat(m, x, 30, z, w=7, base=25)
    m.box(-10, 33, 61, 10, 37, 63, "dash")
    # high wing across the top of the hull
    wing(m, 0, 119, 47.5, 38, 4, 3.4, body, aileron=v["stripe"])
    m.box(-11, 47, 4, 11, 51, 38, body)
    # engines: nacelles with round cowls, exhaust, propellers
    for s, bone in ((1, "propeller_l"), (-1, "propeller_r")):
        x = s * 30
        nst = [(54, 46, 6.5, 6.5), (46, 46, 8.0, 8.0), (30, 46, 7.8, 8.2), (10, 45, 6.0, 6.5), (-6, 46, 3.0, 3.0)]
        loft(m, nst, body, cx=x, step=4, round_=0.35, colors=[(37.5, 40.5, v["stripe"])])
        disc(m, "z", (x, 46, 54.2), 6.0, 0.4, "black")
        m.box(x - 1, 37.5, 26, x + 1, 39, 34, "darkgray")
        propeller(m, bone, x, 46, 55, 20, blades=2, blade="black", tip="yellow", spinner="chrome", spin_r=2.8)
        # wingtip float and strut
        fx = s * 96
        loft(m, [(24, 30, 1.5, 1.5), (20, 29, 3.2, 3.0), (6, 29, 3.4, 3.0), (-4, 30, 1.6, 1.5)], body, cx=fx, step=4,
             colors=[(25, 28.5, v["belly"])])
        m.rod((fx, 32, 14), (fx, 47.5, 18), 1.4, "silver")
        m.rod((fx, 32, 4), (fx, 47.5, 10), 1.0, "silver")
        # main gear on the hull sides
        m.rod((s * 16, 6, 0), (s * 11, 18, 2), 2.0, "silver")
        m.rod((s * 16, 6, 0), (s * 11, 14, -8), 1.2, "silver")
        wheel(m, s * 16.5, 0, 6.2, 3.4)
    # tail: tall fin, stabiliser on the hull, tail wheel
    for y0, y1, z0, z1 in ((48, 60, -110, -80), (60, 72, -111, -88), (72, 82, -111, -95), (82, 88, -110, -100)):
        m.box(-1.2, y0, z0, 1.2, y1, z1, body)
    m.box(-1.3, 50, -114, 1.3, 86, -110, v["stripe"])
    m.mirror(1.5, 52, -110, 36, 53.6, -94, body)
    m.mirror(1.5, 51.9, -113, 32, 53.7, -110, v["stripe"])
    m.mirror(1.25, 64, -98, 1.35, 70, -84, "white", decals={"west": v["reg"], "east": (v["reg"], True)})
    m.rod((0, 3, -60), (0, 23, -62), 1.4, "silver")
    wheel(m, 0, -60, 2.8, 2.0)
    nav_lights(m, 119.2, 49, 20, tail=(0, 76, -111.5), beacon=(0, 51, 10))


# ---------------------------------------------------------------- Bell 206 family (Maverick)
MAV_STATIONS = [(45, 23, 3.0, 6.0), (42, 24, 7.0, 10.0), (36, 26, 9.6, 13.0), (24, 27, 10.4, 15.0), (2, 27, 10.4, 15.0),
                (-14, 27, 10.0, 14.6), (-22, 28.5, 8.2, 12.0)]
BOOM = [(-20, 32, 6.0, 6.0), (-30, 33, 4.2, 4.2), (-70, 34, 3.8, 3.8), (-100, 35, 2.4, 2.3), (-106, 35, 2.0, 2.0)]


def maverick_airframe(m, main, stripe, stripe2, belly, boom_decal=None, door_decal=None, door_decal_bg="white"):
    # cabin: front half glass bubble, painted lower nose and chin
    glass_front = [s for s in MAV_STATIONS if s[0] >= 18]
    rest = [s for s in MAV_STATIONS if s[0] <= 24]
    loft(m, glass_front, "glass", step=3, round_=0.38, name="glass_nose")
    loft(m, rest, main, step=3, round_=0.3, colors=[(10, 15, belly), (21, 23, stripe), (23, 24.2, stripe2)],
         hollow=dict(z=(24, -14), floor=13, openings=[(22, 6, 28.5, 39), (4, -12, 28.5, 39)]))
    # opaque lower nose and centre frame over the glass
    loft(m, [(45.2, 18.5, 3.2, 3.0), (42.2, 18.0, 7.2, 4.6), (36.2, 18.5, 9.8, 6.6), (24, 19, 10.6, 7.2)], main,
         step=3, round_=0.3, colors=[(10, 15, belly), (21, 23, stripe), (23, 24.2, stripe2)])
    m.box(-0.5, 24, 36, 0.5, 40, 44, main, rot=(-40, 0, 0), origin=(0, 32, 40))
    m.box(-10.0, 41.2, 14, 10.0, 42.4, 30, main)  # roof
    m.mirror(10.5, 26, 23.8, 10.9, 42, 25.2, main)  # A pillar
    # doors with windows, handles, door decals
    for z0, z1 in ((22, 6), (4, -12)):
        m.mirror(10.5, 28.5, z1, 10.7, 39, z0, "glass", name="glass_door")
        m.mirror(10.55, 27.5, z0, 10.8, 40, z0 + 1, main)
    m.mirror(10.45, 24, 5, 10.85, 41, 6, main)
    m.mirror(10.6, 26.2, 18, 10.95, 27, 20.5, "chrome")
    m.mirror(10.6, 26.2, 0, 10.95, 27, 2.5, "chrome")
    if door_decal:
        m.mirror(10.65, 14.5, 4, 10.75, 18.0, 20, door_decal_bg, decals={"west": door_decal, "east": (door_decal, True)})
    # interior: instrument console, cyclic, seats
    m.box(-8, 21, 33, 8, 25, 36, "dash")
    m.box(-3, 25, 34, 3, 26.5, 35.5, "black")
    for x in (5.2, -5.2):
        seat(m, x, 18, 22, w=7.5, back=12, base=13)
        m.box(x - 0.4, 18, 28, x + 0.4, 24, 28.8, "black")
    seat(m, 0, 18, 2, w=18, back=13, base=13)
    # engine and transmission fairing, intakes, exhaust, mast
    loft(m, [(18, 44, 6.0, 2.6), (8, 44.5, 7.0, 3.2), (-8, 45, 7.0, 3.4), (-22, 44, 5.2, 2.8), (-30, 41.5, 3.4, 1.8)],
         main, step=3, round_=0.4)
    m.mirror(6.8, 43, 4, 7.4, 46.5, 10, "darkgray")  # intakes
    m.box(-2.5, 46, -26, 2.5, 49.5, -20, "darkgray")  # exhaust stack
    m.box(-1.8, 47, -25.6, 1.8, 49.8, -20.4, "black")
    m.box(-1.6, 47.5, -2.6, 1.6, 52, 0.6, "steel")
    # boom, fins, stabiliser with end plates
    loft(m, BOOM, main, step=4, round_=0.35, colors=[(29.5, 31.5, stripe)])
    if boom_decal:
        m.mirror(4.25, 31.4, -68, 4.35, 35.4, -34, "white", decals={"west": boom_decal, "east": (boom_decal, True)})
    m.mirror(2.0, 34.2, -84, 17, 35.6, -76, main)
    m.mirror(16.5, 32, -85, 17.5, 39, -77, stripe)
    for y0, y1, z0, z1 in ((37, 44, -110, -98), (44, 50, -112, -103), (50, 55, -113, -107)):
        m.box(-0.9, y0, z0, 0.9, y1, z1, main)
    m.box(-0.9, 22, -109, 0.9, 33, -102, main, rot=(20, 0, 0), origin=(0, 33, -105))
    m.box(-0.5, 21, -106, 0.5, 22, -100, "silver")  # tail skid
    tail_rotor(m, 3.2, 38, -104, 13, blades=2, color="darkgray", tip="red")
    rotor(m, 52.5, -1, 81, 2, chord=5.2, color="darkgray", tip="white")
    skid_gear(m, 12, 12.5, 32, -26, z_cross=(18, -12))
    nav_lights(m, 17.6, 35.6, -80, tail=(0, 40, -112.5), beacon=(0, 49.8, -23))
    m.box(-0.3, 7.5, 10, 0.3, 12, 11, "darkgray")  # belly antenna


MAV_VARIANTS = [
    dict(main="white", stripe="navy", stripe2="gold", belly="navy", boom=D_BUCK_W),
    dict(main="black", stripe="gold", stripe2="black", belly="black", boom=D_BUCK_B),
    dict(main="red", stripe="white", stripe2="red", belly="darkgray", boom=D_BUCK_R),
]


def maverick(m, v):
    maverick_airframe(m, v["main"], v["stripe"], v["stripe2"], v["belly"], boom_decal=v["boom"])


def police_maverick(m, v):
    # VCPD: white with blue belly and cheat line, POLICE on the boom, unit number on the doors
    maverick_airframe(m, "white", "vcpd", "vcpd_dark", "vcpd", boom_decal=D_POLICE, door_decal=D_VCPD,
                      door_decal_bg="vcpd")
    # Nightsun searchlight on the right side of the nose, FLIR ball, light bar
    m.box(-11.5, 13, 26, -9.5, 15, 34, "darkgray")
    m.bone("searchlight", origin=(-12, 11, 31))
    disc(m, "z", (-12, 11, 31.5), 2.6, 5, "steel", bone="searchlight")
    disc(m, "z", (-12, 11, 34.1), 2.1, 0.4, "lamp", bone="searchlight", name="lamp_search")
    m.box(-12.5, 13, 30.5, -11.5, 14.5, 32.5, "darkgray", bone="searchlight")
    disc(m, "y", (3, 9.5, 38), 2.6, 4.5, "darkgray")
    m.box(1.6, 8.0, 39.4, 4.4, 10.5, 40.8, "glass_dark", name="glass_flir")
    m.box(-4, 10.8, -8, 4, 12.2, 8, "dash")
    for i, x in enumerate((-3, -1, 1, 3)):
        m.box(x - 0.9, 10.2, -7, x + 0.9, 10.9, 7, "lamp_red" if i < 2 else "lamp_blue", name="lamp_bar")


def news_maverick(m, v):
    maverick_airframe(m, "white", "weazel", "navy", "weazel", boom_decal=D_WEAZEL_W, door_decal=D_WEAZEL,
                      door_decal_bg="weazel")
    # gyro stabilised camera ball under the nose
    m.box(-0.8, 10, 34, 0.8, 13, 36, "darkgray")
    disc(m, "y", (0, 7.5, 35), 3.4, 5, "white")
    m.box(-1.6, 6.2, 37.8, 1.6, 8.8, 38.6, "glass_dark", name="glass_cam")


# ---------------------------------------------------------------- Nagasaki Buzzard (MD 500 Defender), police
def buzzard(m, v):
    main, trim = "vcpd_dark", "white"
    st = [(30, 23, 3.0, 5.0), (26, 24, 7.0, 9.5), (18, 25.5, 9.0, 12.5), (6, 26.5, 9.6, 13.8), (-6, 27, 9.2, 13.2),
          (-14, 28, 7.6, 11.0), (-20, 30, 5.2, 7.0)]
    loft(m, [s for s in st if s[0] >= 10], "glass", step=3, round_=0.42, name="glass_nose")
    loft(m, [s for s in st if s[0] <= 18], main, step=3, round_=0.38, colors=[(20, 22.5, trim)],
         hollow=dict(z=(18, -12), floor=13, openings=[(14, -10, 27, 37)]))
    loft(m, [(30.2, 18.5, 3.2, 3.0), (26.2, 18.0, 7.2, 5.0), (18, 18.5, 9.2, 6.6)], main, step=3, round_=0.35)
    m.box(-9, 39.0, 6, 9, 40.6, 18, main)
    m.mirror(9.2, 24, 15.6, 9.7, 39.5, 16.6, main)
    m.mirror(9.4, 27, -10, 9.7, 37, 14, "glass", name="glass_door")
    m.mirror(9.5, 22.5, -12, 9.8, 26.6, 14, trim, decals={"west": D_POLICE_NAVY, "east": (D_POLICE_NAVY, True)})
    for x in (4.5, -4.5):
        seat(m, x, 18, 10, w=6.5, base=13)
    m.box(-7, 21, 22, 7, 25, 25, "dash")
    loft(m, [(10, 40.5, 5.5, 2.4), (-4, 41, 6.2, 3.0), (-18, 39, 4.0, 2.2)], main, step=3, round_=0.4)
    m.box(-1.4, 42.5, -2.5, 1.4, 46, 0.5, "steel")
    m.box(-2, 31, -24, 2, 33, -20, "darkgray")  # exhaust
    loft(m, [(-18, 31, 3.6, 3.6), (-50, 33, 2.6, 2.6), (-72, 34, 2.0, 2.0)], main, step=4, round_=0.4,
         colors=[(31.8, 33.2, trim)])
    # T tail with end plates, ventral fin, tail rotor
    for y0, y1, z0, z1 in ((35, 42, -78, -66), (42, 49, -79, -71)):
        m.box(-0.8, y0, z0, 0.8, y1, z1, main)
    m.mirror(0.8, 48.8, -80, 13, 50, -72, main)
    m.mirror(12.5, 46, -80, 13.5, 53, -72, trim)
    m.box(-0.8, 24, -74, 0.8, 33, -68, main, rot=(25, 0, 0), origin=(0, 33, -71))
    tail_rotor(m, 2.6, 36, -72, 11, blades=2, color="darkgray", tip="white")
    rotor(m, 46.5, -1, 64, 5, chord=4.4, color="darkgray", tip="white")
    skid_gear(m, 10.5, 13, 22, -16, z_cross=(12, -8))
    # searchlight under the nose, light bar, nav lights
    m.bone("searchlight", origin=(-6, 10.5, 22))
    disc(m, "z", (-6, 10.5, 22.5), 2.2, 4, "steel", bone="searchlight")
    disc(m, "z", (-6, 10.5, 24.6), 1.8, 0.4, "lamp", bone="searchlight", name="lamp_search")
    m.box(-6.5, 12, 21.5, -5.5, 13, 23.5, "darkgray", bone="searchlight")
    for i, x in enumerate((-2.5, -0.8, 0.8, 2.5)):
        m.box(x - 0.7, 11.2, -6, x + 0.7, 11.9, 6, "lamp_red" if i < 2 else "lamp_blue", name="lamp_bar")
    nav_lights(m, 13.6, 50.4, -76, tail=(0, 46, -79.5), beacon=(0, 44, -14))


# ---------------------------------------------------------------- Buckingham Shamal (Learjet 35)
SHAMAL_VARIANTS = [
    dict(body="white", stripe="navy", stripe2="gold", reg=REG_SHAMAL[0]),
    dict(body="black", stripe="gold", stripe2="silver", reg=REG_SHAMAL[1]),
]


def shamal(m, v):
    body = v["body"]
    st = [(118, 26, 1.5, 2.0), (114, 26.5, 4.0, 5.0), (104, 27.5, 7.5, 9.0), (90, 28.5, 9.6, 11.6), (70, 29, 10.0, 12.0),
          (-30, 29, 10.0, 12.0), (-60, 30.5, 8.6, 10.6), (-90, 33, 6.0, 7.6), (-112, 36, 3.2, 4.4), (-120, 37, 1.6, 2.6)]
    loft(m, st, body, step=5, round_=0.38, colors=[(24.5, 26.5, v["stripe"]), (26.5, 27.4, v["stripe2"])],
         hollow=dict(z=(96, -30), floor=18.5, nose=(118, 96, 33.5), roof_open=[(86, 96)],
                     openings=[(84, 92, 33, 38.5)] + [(z - 2.2, z + 2.2, 32.5, 37) for z in (68, 56, 44, 32, 20, 8)]))
    # cockpit glazing, cabin windows, door
    m.box(-8.4, 34, 88, 8.4, 38.5, 100, "glass_dark", rot=(-28, 0, 0), origin=(0, 35, 94), name="glass_ws")
    m.mirror(9.2, 33, 84, 9.6, 38.5, 92, "glass_dark", name="glass_cock")
    for z in (68, 56, 44, 32, 20, 8):
        m.mirror(9.95, 32.5, z - 2.2, 10.2, 37, z + 2.2, "glass_dark", name="glass_win")
    m.box(9.9, 18, 74, 10.25, 37, 75, "darkgray")
    m.box(9.9, 18, 84, 10.25, 37, 85, "darkgray")
    m.mirror(9.95, 22, -40, 10.15, 25.6, -12, body, decals={"west": v["reg"], "east": (v["reg"], True)})
    for x in (4.5, -4.5):
        seat(m, x, 22, 82, w=6.5, base=18.5)
        for z in (60, 40):
            seat(m, x, 22, z, w=7, color="leather", base=18.5)
    m.box(-8, 27, 92, 8, 31, 95, "dash")
    # low wing with tip tanks, flaps
    # tapered, slightly swept low wing with dihedral, built from spanwise segments
    # 3 px spanwise strips, each rotated by the same dihedral about the wing root, so the panel is one
    # flat plane and only the sweep and taper step (by under a pixel per strip)
    n, seg, dih = 32, 3, 3.3
    for k in range(n):
        x0, x1 = k * seg, (k + 1) * seg
        t = (k + 0.5) / n
        lead, chord, th = 26 - 19 * t, 38 - 21.5 * t, 2.4 - 0.9 * t
        for sg in (1, -1):
            lo, hi = sorted((sg * x0, sg * x1))
            r, o = (0, 0, sg * dih), (0, 18.5, 0)
            m.box(lo, 18.5, lead - chord, hi, 18.5 + th, lead, body, rot=r, origin=o)
            if t > 0.5:
                m.box(lo, 18.45, lead - chord - 0.05, hi, 18.55 + th, lead - chord * 0.75, v["stripe"], rot=r, origin=o)
    for s in (1, -1):
        loft(m, [(20, 24.5, 1.0, 1.0), (14, 24.5, 3.4, 3.4), (-6, 24.5, 3.6, 3.6), (-20, 24.5, 1.6, 1.6)], body, cx=s * 98,
             step=4, round_=0.4, colors=[(23.7, 25.3, v["stripe"])])
        # rear engines on pylons
        x = s * 18
        loft(m, [(-44, 38, 6.0, 6.0), (-50, 38, 6.6, 6.6), (-70, 38, 6.2, 6.2), (-84, 38, 4.6, 4.6)], body, cx=x, step=4,
             round_=0.4, colors=[(36.5, 39.5, v["stripe"])])
        disc(m, "z", (x, 38, -43.7), 5.0, 0.4, "black")
        disc(m, "z", (x, 38, -44.2), 2.0, 1.0, "steel")
        disc(m, "z", (x, 38, -84.2), 3.4, 0.6, "black")
        m.box(min(x, s * 9), 36.5, -72, max(x, s * 9), 39.5, -52, body)
    # T tail
    for k in range(17):  # swept fin in 2 px strips
        y0 = 40 + k * 2
        t = (k + 0.5) / 17
        m.box(-1.1, y0, -122 - 3 * t, 1.1, y0 + 2, -94 - 18 * t, body)
    m.box(-1.2, 41, -126, 1.2, 74, -122, v["stripe"])
    m.mirror(1.1, 73.5, -128, 30, 75, -114, body)
    m.mirror(1.1, 73.4, -131, 27, 75.1, -128, v["stripe"])
    # tricycle gear
    m.rod((0, 3.5, 98), (0, 17, 99), 1.6, "silver")
    wheel(m, 0, 98, 3.5, 2.6)
    for s in (1, -1):
        m.rod((s * 26, 4.2, -4), (s * 24, 18.5, -2), 2.0, "silver")
        wheel(m, s * 26, -4, 4.2, 3.2)
    nav_lights(m, 101.8, 24.5, 10, tail=(0, 74.5, -125.5), beacon=(0, 41.2, 10))
    m.box(-0.8, 15.5, 40, 0.8, 16.8, 42, "lamp_red", name="lamp_belly")


def parachute():
    m = Model("parachute")
    cells = 7
    span = 104
    top = 68
    cell_w = span / cells
    for i in range(cells):
        x0 = -span / 2 + i * cell_w
        x1 = x0 + cell_w
        cx = (x0 + x1) / 2
        t = cx / (span / 2)
        drop = 14 * t * t
        ang = -32 * t  # bank outer cells downward
        sw = ["hotpink", "white", "teal", "yellow", "teal", "white", "hotpink"][i]
        m.box(x0 - 0.3, top - drop, -14, x1 + 0.3, top - drop + 5, 14, sw, rot=(0, 0, ang), origin=(cx, top - drop, 0))
    # lines from the canopy to the harness
    for x in (-48, -30, -12, 12, 30, 48):
        t = x / (span / 2)
        yy = top - 14 * t * t
        for z in (-10, 10):
            m.rod((x * 0.95, yy, z * 0.6), (math.copysign(4, x), 26, 0), 0.4, "lightgray")
    # harness pack on the back of the rider
    m.box(-4, 14, -5, 4, 26, -2, "darkgray")
    m.save()


def item_icons():
    os.makedirs(ITEM_TEX, exist_ok=True)

    def icon(name, body, accent, kind):
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        c, a = COLORS[body] + (255,), COLORS[accent] + (255,)
        k = COLORS["black"] + (255,)
        g = COLORS["skyblue"] + (255,)
        if kind == "biplane":
            d.rectangle([1, 3, 14, 4], fill=a)
            d.rectangle([1, 8, 14, 9], fill=a)
            d.rectangle([7, 2, 8, 14], fill=c)
            d.rectangle([5, 13, 10, 13], fill=c)
            d.rectangle([3, 4, 3, 8], fill=k)
            d.rectangle([12, 4, 12, 8], fill=k)
            d.rectangle([5, 1, 10, 1], fill=k)
        elif kind == "float":
            d.rectangle([0, 4, 15, 5], fill=c)
            d.rectangle([7, 2, 8, 13], fill=c)
            d.rectangle([5, 12, 10, 12], fill=c)
            d.rectangle([4, 6, 5, 14], fill=COLORS["float"] + (255,))
            d.rectangle([10, 6, 11, 14], fill=COLORS["float"] + (255,))
            d.rectangle([5, 1, 10, 1], fill=k)
        elif kind == "dodo":
            d.rectangle([0, 5, 15, 6], fill=c)
            d.rectangle([6, 2, 9, 14], fill=c)
            d.rectangle([3, 3, 4, 8], fill=a)
            d.rectangle([11, 3, 12, 8], fill=a)
            d.rectangle([6, 9, 9, 10], fill=a)
            d.rectangle([5, 14, 10, 14], fill=c)
        elif kind == "jet":
            d.rectangle([7, 0, 8, 15], fill=c)
            d.polygon([(7, 5), (0, 10), (0, 11), (7, 9)], fill=c)
            d.polygon([(8, 5), (15, 10), (15, 11), (8, 9)], fill=c)
            d.rectangle([0, 9, 0, 12], fill=a)
            d.rectangle([15, 9, 15, 12], fill=a)
            d.rectangle([5, 12, 6, 13], fill=a)
            d.rectangle([9, 12, 10, 13], fill=a)
            d.rectangle([5, 15, 10, 15], fill=c)
            d.rectangle([7, 1, 8, 2], fill=g)
        elif kind in ("heli", "buzzard"):
            if kind == "heli":
                d.ellipse([3, 6, 11, 12], fill=c)
                d.rectangle([11, 8, 15, 9], fill=c)
                d.rectangle([14, 6, 15, 9], fill=c)
            else:
                d.ellipse([3, 5, 10, 12], fill=c)
                d.rectangle([10, 8, 14, 9], fill=c)
                d.rectangle([13, 5, 15, 5], fill=c)
            d.rectangle([4, 7, 6, 9], fill=g)
            d.rectangle([1, 4, 14, 4], fill=k)
            d.rectangle([7, 4, 7, 6], fill=k)
            d.rectangle([2, 14, 11, 14], fill=COLORS["silver"] + (255,))
            d.rectangle([4, 11, 10, 11], fill=a)
        elif kind == "chute":
            d.pieslice([1, 1, 14, 12], 180, 360, fill=COLORS["hotpink"] + (255,))
            d.pieslice([4, 1, 11, 12], 180, 360, fill=COLORS["teal"] + (255,))
            d.line([2, 7, 7, 13], fill=k)
            d.line([13, 7, 8, 13], fill=k)
            d.rectangle([7, 12, 8, 14], fill=COLORS["darkgray"] + (255,))
        im.save(os.path.join(ITEM_TEX, name + ".png"))

    icon("western_duster", "plum", "plum_dark", "biplane")
    icon("floatplane", "beaver", "black", "float")
    icon("dodo", "white", "blue", "dodo")
    icon("maverick", "white", "navy", "heli")
    icon("police_maverick", "white", "vcpd", "heli")
    icon("news_maverick", "white", "weazel", "heli")
    icon("buzzard", "vcpd_dark", "white", "buzzard")
    icon("shamal", "white", "navy", "jet")
    icon("parachute", "hotpink", "teal", "chute")


AIRCRAFT = [
    ("western_duster", western_duster, DUSTER_VARIANTS),
    ("floatplane", floatplane, BEAVER_VARIANTS),
    ("dodo", dodo, DODO_VARIANTS),
    ("maverick", maverick, MAV_VARIANTS),
    ("police_maverick", police_maverick, [{}]),
    ("news_maverick", news_maverick, [{}]),
    ("buzzard", buzzard, [{}]),
    ("shamal", shamal, SHAMAL_VARIANTS),
]

if __name__ == "__main__":
    build_palette()
    for f in os.listdir(OBJ_DIR) if os.path.isdir(OBJ_DIR) else []:
        if f.endswith(".bbmodel"):
            os.remove(os.path.join(OBJ_DIR, f))
    for name, fn, variants in AIRCRAFT:
        for i, v in enumerate(variants):
            m = Model(name if i == 0 else "%s_v%d" % (name, i))
            fn(m, v)
            m.save()
            print(m.name, len(m.elements), "cubes")
    parachute()
    for f in ("prop_plane", "crop_duster", "seaplane", "helicopter", "police_helicopter", "news_helicopter"):
        p = os.path.join(ITEM_TEX, f + ".png")
        if os.path.exists(p):
            os.remove(p)
    item_icons()
    print("models written to", OBJ_DIR)
