#!/usr/bin/env python3
"""Leonida skin generator: Jason, Lucia (outfits), pedestrians, police, SWAT.
Writes 64x64 Minecraft skins (modern layout) into the mod's texture folder.
Each body part is painted with a function paint(part, face, x, y, w, h) -> RGBA or None,
where y is the vertical pixel inside the face (0 = top of the limb)."""
import random, os, sys, json, zlib
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"

# ---------------------------------------------------------------- layout
def box(u, v, w, h, d):
    """Return dict face -> (x0, y0, fw, fh) for a Minecraft cuboid at texture origin (u, v)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }

def parts(slim):
    aw = 3 if slim else 4
    return {
        "head": box(0, 0, 8, 8, 8), "hat": box(32, 0, 8, 8, 8),
        "body": box(16, 16, 8, 12, 4), "jacket": box(16, 32, 8, 12, 4),
        "rarm": box(40, 16, aw, 12, 4), "rsleeve": box(40, 32, aw, 12, 4),
        "larm": box(32, 48, aw, 12, 4), "lsleeve": box(48, 48, aw, 12, 4),
        "rleg": box(0, 16, 4, 12, 4), "rpants": box(0, 32, 4, 12, 4),
        "lleg": box(16, 48, 4, 12, 4), "lpants": box(0, 48, 4, 12, 4),
    }

BASE_OF = {"hat": "head", "jacket": "body", "rsleeve": "rarm", "lsleeve": "larm", "rpants": "rleg", "lpants": "lleg"}

# ---------------------------------------------------------------- colour helpers
def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3)) + (a[3] if len(a) > 3 else 255,)

def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (c[3] if len(c) > 3 else 255,)

class Rng:
    def __init__(self, seed):
        self.r = random.Random(seed)
    def j(self, c, amt=0.06):
        f = 1 + self.r.uniform(-amt, amt)
        return shade(c, f)

FACE_LIGHT = {"front": 1.0, "top": 1.06, "right": 0.9, "left": 0.9, "back": 0.82, "bottom": 0.72}

# ---------------------------------------------------------------- skin painter
class Skin:
    def __init__(self, slim, seed=1):
        self.slim = slim
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.P = parts(slim)
        self.rng = Rng(seed)

    def paint(self, part, fn, light=True, jitter=0.05):
        for face, (x0, y0, fw, fh) in self.P[part].items():
            for y in range(fh):
                for x in range(fw):
                    c = fn(face, x, y, fw, fh)
                    if c is None:
                        continue
                    if len(c) == 3:
                        c = c + (255,)
                    if c[3] == 0:
                        continue
                    if light:
                        c = shade(c, FACE_LIGHT[face])
                    if jitter:
                        c = self.rng.j(c, jitter)
                    self.img.putpixel((x0 + x, y0 + y), c)

    def put_face(self, part, face, rows, pal, light=False):
        x0, y0, fw, fh = self.P[part][face]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "." or ch == " ":
                    continue
                c = pal[ch]
                if light:
                    c = shade(c, FACE_LIGHT[face])
                self.img.putpixel((x0 + x, y0 + y), c)

    def px(self, part, face, x, y, c):
        x0, y0, fw, fh = self.P[part][face]
        if 0 <= x < fw and 0 <= y < fh:
            self.img.putpixel((x0 + x, y0 + y), c)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)

# ---------------------------------------------------------------- body building blocks
SKIN_TONES = {
    "jason": hexc("d9a07a"), "lucia": hexc("c48a60"),
    "pale": hexc("f0c8a8"), "fair": hexc("e3b08c"), "tan": hexc("c98f66"), "olive": hexc("b3845a"),
    "brown": hexc("8d5a3b"), "dark": hexc("6b4229"), "deep": hexc("4a2c1c"),
}
HAIR = {
    "jason": hexc("4a3222"), "lucia": hexc("2b1a12"), "black": hexc("1b1512"), "dkbrown": hexc("3a2619"),
    "brown": hexc("6a4a30"), "blonde": hexc("d8b46a"), "platinum": hexc("e8e0c8"), "red": hexc("9a4524"),
    "grey": hexc("9a9692"), "white": hexc("dcdad6"), "pink": hexc("e070b0"), "purple": hexc("8a4ac8"),
}

def skin_fn(tone):
    def f(face, x, y, w, h):
        return tone
    return f

def legs_fn(tone, pants=None, pants_len=12, shoe=None, shoe_h=1, sock=None, belt=None, stripe=None):
    """pants_len: how many rows from the top are covered (12 full, 5 shorts)."""
    def f(face, x, y, w, h):
        if face == "top":
            return pants or tone
        if face == "bottom":
            return shoe or tone
        if shoe and y >= h - shoe_h:
            if y == h - 1 and face in ("front",):
                return shade(shoe, 0.8)
            return shoe
        if sock and y >= h - shoe_h - 1 and y < h - shoe_h:
            return sock
        if pants and y < pants_len:
            c = pants
            if belt and y == 0:
                return belt
            if stripe and face in ("right", "left") and x == 1:
                return stripe
            if y == pants_len - 1 and pants_len < 12:
                return shade(c, 0.85)
            return c
        return tone
    return f

def arms_fn(tone, sleeve=None, sleeve_len=0, cuff=None, glove=None, watch=None, side="r", slim=False):
    def f(face, x, y, w, h):
        if face == "top":
            return sleeve if sleeve_len > 0 else tone
        if face == "bottom":
            return glove or tone
        if glove and y >= h - 3:
            return glove
        if sleeve and y < sleeve_len:
            if cuff and y == sleeve_len - 1:
                return cuff
            return sleeve
        if watch and y == h - 4:
            return watch
        return tone
    return f

def torso_fn(tone, top=None, neckline="crew", top_len=12, trim=None, pattern=None, low=None, low_from=99):
    """top covers the torso; neckline: crew, v, tank, scoop, none. low: colour of rows >= low_from (pants waist)."""
    def f(face, x, y, w, h):
        if low and y >= low_from and face != "top":
            return low
        if top is None:
            return tone
        if face == "top":
            if neckline in ("tank", "scoop", "bikini") and 2 <= x <= 5 and y >= 1:
                return tone
            return top
        if face == "bottom":
            return low or top
        if y >= top_len:
            return tone if not low else low
        if face == "front":
            if neckline == "crew" and y == 0 and 3 <= x <= 4:
                return tone
            if neckline == "v" and ((y == 0 and 2 <= x <= 5) or (y == 1 and 3 <= x <= 4)):
                return tone
            if neckline == "tank" and ((y == 0 and 2 <= x <= 5) or (y == 1 and 3 <= x <= 4)):
                return tone
            if neckline == "scoop" and ((y == 0 and 1 <= x <= 6) or (y == 1 and 2 <= x <= 5) or (y == 2 and 3 <= x <= 4)):
                return tone
            if neckline == "tank" and y == 0 and x in (0, 7):
                return tone
        if pattern:
            p = pattern(face, x, y, w, h)
            if p:
                return p
        if trim and y == top_len - 1:
            return trim
        return top
    return f

def tank_arm_gap(tone):
    """tank tops leave shoulders bare: the arm top face stays skin."""
    return tone

# ---------------------------------------------------------------- generic faces
def generic_face(sk, tone, hair, eye, style, rng, female=False, beard=None, lips=None, glasses=None, brow=None):
    s = tone
    sd = shade(tone, 0.86)
    H = hair
    Hl = shade(hair, 1.18)
    brow = brow or shade(hair, 0.85)
    W = hexc("f2efe8")
    E = eye
    M = lips or (mix(tone, hexc("8a3a3a"), 0.45) if female else shade(tone, 0.72))
    B = beard
    pal = {"S": s, "s": sd, "H": H, "h": Hl, "b": brow, "W": W, "e": E, "m": M, "n": shade(tone, 0.9),
           "B": B or s, "k": hexc("1a1210"), "g": glasses or hexc("202020"), "G": shade(glasses or hexc("202020"), 1.6)}
    tops = {
        "short":   ["HHHHHHHH", "HHhHHHhH", "HSSSSSSH"],
        "buzz":    ["HHHHHHHH", "SHHHHHHS", "SSSSSSSS"],
        "bald":    ["SSSSSSSS", "SSSSSSSS", "SSSSSSSS"],
        "long":    ["HHHHHHHH", "HHHhhHHH", "HSSSSSSH"],
        "bob":     ["HHHHHHHH", "HhHHHHhH", "HHSSSSHH"],
        "fringe":  ["HHHHHHHH", "HHHHHHHH", "HHhHHhHH"],
        "curly":   ["HhHhHhHH", "HHhHHhHh", "hSSSSSSH"],
        "afro":    ["HhHhHHhH", "hHHHhHHH", "HSSSSSSH"],
        "cap":     None,
    }
    top = tops.get(style) or tops["short"]
    eyes = ["SbbSSbbS", "SWeSSeWS"] if not female else ["SbbSSbbS", "kWeSSeWk"]
    if female:
        eyes = ["SbbSSbbS", "SkeSSekS" if rng.r.random() < 0.3 else "SWeSSeWS"]
    side = "H" if style in ("long", "bob", "curly", "afro") else "S"
    mid = ["%sSSnnSS%s" % (side, side)]
    if beard:
        low = ["BSmmmmSB", "BBBBBBBB"] if rng.r.random() < 0.6 else ["BBmmmmBB", "BBBBBBBB"]
    else:
        low = ["%sSmmmmS%s" % (side, side), "%sSSSSSS%s" % (side, side)] if not female else ["%sSSmmSS%s" % (side, side), "%sSSSSSS%s" % (side, side)]
    rows = top + eyes + mid + low
    if style in ("long", "bob", "curly", "afro"):
        rows[3] = "H" + rows[3][1:7] + "H"
        rows[4] = "H" + rows[4][1:7] + "H"
    sk.put_face("head", "front", rows, pal)
    if glasses:
        sk.put_face("head", "front", ["........", "........", "........", "gggggggg", ".gG..Gg."], pal)

def generic_head(sk, tone, hair, style, rng):
    """Paint top/side/back faces of the head for a hairstyle (front painted by generic_face)."""
    Hh = hair
    def f(face, x, y, w, h):
        if face == "top":
            return None if style == "bald" else Hh
        if face == "bottom":
            return tone
        if style == "bald":
            return tone
        if style == "buzz":
            return Hh if y < 3 else tone
        if style == "short":
            if face == "back":
                return Hh if y < 6 else tone
            return Hh if (y < 3 or (y < 5 and x >= 5) if face == "right" else (y < 3 or (y < 5 and x <= 2))) else tone
        if style in ("long", "curly", "afro"):
            return Hh
        if style in ("bob", "fringe"):
            return Hh if y < 7 else tone
        return Hh if y < 4 else tone
    sk.paint("head", lambda face, x, y, w, h: tone if face != "front" else None, jitter=0.02)
    sk.paint("head", f, jitter=0.07)

def ear(sk, tone, earring=None):
    # ear pixel on the side faces
    for face, ex in (("right", 4), ("left", 3)):
        sk.px("head", face, ex, 4, shade(tone, 0.82))
        if earring:
            sk.px("head", face, ex, 5, earring)

# ---------------------------------------------------------------- hat/overlay helpers
def hair_overlay(sk, hair, style, rng):
    """Volume on the hat layer for long hair and curls."""
    H = hair
    def f(face, x, y, w, h):
        if style in ("long", "curly", "afro"):
            if face == "front":
                if y <= 1 or ((x == 0 or x == w - 1) and y <= (7 if style != "afro" else 6)):
                    return shade(H, 1.05 if (x + y) % 3 else 1.2)
                return None
            if face == "bottom":
                return None
            if face == "top":
                return H
            if style == "afro" or rng.r.random() < 0.9:
                return shade(H, 1.0 + (0.18 if (x * 7 + y * 3) % 5 == 0 else 0))
            return None
        if style == "bob":
            if face == "front":
                return H if (y == 0 or ((x == 0 or x == w - 1) and y < 7)) else None
            if face in ("top",):
                return H
            if face in ("bottom",):
                return None
            return H if y < 7 else None
        return None
    sk.paint("hat", f, jitter=0.08)

def cap_overlay(sk, color, front_logo=None, backwards=False):
    """Simple cap shell on the hat layer (the brim is a 3D accessory model)."""
    def f(face, x, y, w, h):
        if face == "top":
            return color
        if face == "bottom":
            return None
        if y < 3:
            if backwards and face == "back" and y == 2 and 2 <= x <= 5:
                return None  # snapback gap
            if front_logo and face == ("back" if backwards else "front") and y == 1 and 3 <= x <= 4:
                return front_logo
            return color
        return None
    sk.paint("hat", f, jitter=0.04)

def sunglasses(sk, frame, lens):
    sk.put_face("hat", "front", ["........", "........", "........", "ffffffff", ".LL..LL.", "........", "........", "........"],
                {"f": frame, "L": lens})
    for face in ("right", "left"):
        for x in range(8):
            sk.px("hat", face, x, 3, frame)

def bandana(sk, color, pattern_c=None, rows_from=4):
    """Cloth mask over nose and mouth (hat layer), with a knot at the back."""
    def f(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if face == "front":
            if y >= rows_from + 1 or (y == rows_from and 1 <= x <= 6):
                if pattern_c and (x + y * 2) % 4 == 0:
                    return pattern_c
                if y == 7 and x in (3, 4):
                    return shade(color, 0.8)
                return color
            return None
        if face == "back":
            if y == rows_from + 1 and 2 <= x <= 5:
                return shade(color, 0.85)
            return None
        if y == rows_from + 1 or (y >= rows_from + 2 and x <= (3 if face == "left" else 4) and face != "back"):
            return color if not (pattern_c and (x + y) % 3 == 0) else pattern_c
        return None
    sk.paint("hat", f, jitter=0.05)

# ---------------------------------------------------------------- main characters
def jason(outfit):
    sk = Skin(False, seed=100 + zlib.crc32(outfit.encode()) % 1000)
    tone = SKIN_TONES["jason"]
    hair = HAIR["jason"]
    stub = mix(tone, hexc("3c2a20"), 0.42)
    # head
    sk.paint("head", lambda face, x, y, w, h: tone, jitter=0.02)
    def hairf(face, x, y, w, h):
        if face == "top":
            return shade(hair, 1.15) if (x in (2, 3) and y > 1) else hair
        if face == "bottom":
            return None
        if face == "back":
            return hair if y < 5 else (shade(hair, 0.9) if y == 5 else None)
        if face == "front":
            return None
        # sides: short fade, longer at top
        if y < 2:
            return hair
        if y < 4:
            return shade(hair, 0.85) if ((face == "right" and x >= 2) or (face == "left" and x <= 5)) else None
        if y < 6 and ((face == "right" and x >= 5) or (face == "left" and x <= 2)):
            return mix(hair, tone, 0.5)
        return None
    sk.paint("head", hairf, jitter=0.08)
    # fuller short beard and stubble (cover art): jaw, chin, upper lip and the underside of the chin
    beard = hexc("3a281c")
    B1 = mix(tone, beard, 0.62)
    B2 = mix(tone, beard, 0.78)
    B0 = mix(tone, beard, 0.38)
    br = random.Random(31)
    def bpx(face, x, y, strength=1):
        c = (B0, B1, B2)[min(2, max(0, strength + br.choice((-1, 0, 0, 1)) // 2))]
        sk.px("head", face, x, y, c)
    for face in ("right", "left"):
        for x in range(8):
            fx = x if face == "left" else 7 - x       # distance from the face edge
            if fx <= 4:
                bpx(face, x, 7, 2 if fx <= 2 else 1)
            if fx <= 3:
                bpx(face, x, 6, 1 if fx <= 1 else 0)
            if fx <= 1:
                bpx(face, x, 5, 0)
    for x in range(8):
        for y in range(8):
            bpx("bottom", x, y, 1)  # underside of the head (chin)
    pal = {"H": hair, "h": shade(hair, 1.3), "d": shade(hair, 0.75), "S": tone, "s": shade(tone, 0.88),
           "t": shade(tone, 1.06), "b": hexc("2a1b12"), "W": hexc("f4f1ea"), "e": hexc("4f7fa8"),
           "E": hexc("2b3a48"), "n": shade(tone, 0.8), "N": shade(tone, 0.68), "m": hexc("9a6656"),
           "B": B1, "C": B2, "c": B0}
    sk.put_face("head", "front", [
        "HHhHHHhH",
        "HHhdSStH",
        "tSSSSSSt",
        "sbbSSbbs",
        "SWeSSeWS",
        "cSsnnsSc",
        "BCCmmCCB",
        "CBCBBCBC",
    ], pal)
    # hat layer: slicked back side-parted hair with some lift, and a 3D beard at the chin
    def jhat(face, x, y, w, h):
        if face == "top":
            return (shade(hair, 1.2) if x == 2 else hair) if y <= 5 else (hair if 1 <= x <= 6 and y == 6 else None)
        if face == "front":
            if y == 0:
                return shade(hair, 1.15) if x in (2, 5) else hair
            if y == 7 and 1 <= x <= 6:
                return B2 if (x + y) % 2 else B1
            return None
        if face in ("right", "left"):
            fx = x if face == "left" else 7 - x
            if y == 0 and fx >= 1:
                return hair
            if y == 7 and fx <= 2:
                return B2
            return None
        return None
    sk.paint("hat", jhat, jitter=0.06)
    ear(sk, tone)
    tattoo = hexc("3c4652")
    def tattoo_arm(base, sleeve_len, side):
        def f(face, x, y, w, h):
            c = base(face, x, y, w, h)
            if c == tone or (c and c[:3] == tone[:3]):
                # forearm and upper arm ink, more on the left arm (cover art)
                if side == "l" and face in ("front", "left", "back") and (y in (5, 6, 8, 9) and (x + y) % 3 != 0):
                    return tattoo
                if side == "l" and face == "left" and 3 <= y <= 10 and x % 2 == y % 2:
                    return tattoo
                if side == "r" and face in ("right", "front") and y in (2, 3) and x != 1:
                    return tattoo
            return c
        return f
    jeans = hexc("3b5a85")
    jeans_dk = hexc("2a3f60")
    boots = hexc("6a4a32")
    sneaker = hexc("e8e8e8")
    chain = hexc("e0b84a")
    if outfit == "everyday":
        # white ribbed tank, gold chain, blue jeans, tan work boots, tattooed arms
        tank = hexc("f0eee8")
        def rib(face, x, y, w, h):
            return shade(tank, 0.93) if x % 2 == 0 and face in ("front", "back") else None
        sk.paint("body", torso_fn(tone, tank, "tank", 9, pattern=rib, low=jeans, low_from=9))
        sk.paint("body", lambda face, x, y, w, h: hexc("2a2018") if (face == "front" and y == 9) else None)
        sk.px("body", "front", 4, 9, hexc("c8a040"))
        for p in [(1, 0), (2, 1), (3, 2), (4, 2), (5, 1), (6, 0)]:
            sk.px("body", "front", p[0], p[1], chain)
        sk.px("body", "front", 4, 3, chain)
        sk.paint("rarm", tattoo_arm(arms_fn(tone), 0, "r"))
        sk.paint("larm", tattoo_arm(arms_fn(tone), 0, "l"))
        sk.paint("rarm", lambda face, x, y, w, h: hexc("2a2a2a") if (y == 8 and face != "top" and face != "bottom") else None)
        sk.paint("rleg", legs_fn(tone, jeans, 12, boots, 2))
        sk.paint("lleg", legs_fn(tone, jeans, 12, boots, 2))
        # jeans seams and fade
        for leg in ("rleg", "lleg"):
            sk.paint(leg, lambda face, x, y, w, h: shade(jeans, 1.15) if (face == "front" and 3 <= y <= 6 and x in (1, 2)) else None)
    elif outfit == "heist":
        # grey tee, black and white bandana mask (cover art), black cargo pants, black boots, black gloves
        tee = hexc("8c8c8a")
        sk.paint("body", torso_fn(tone, tee, "crew", 10, low=hexc("26262a"), low_from=10))
        sk.paint("body", lambda face, x, y, w, h: hexc("111111") if (face == "front" and y == 10) else None)
        sk.paint("jacket", lambda face, x, y, w, h: (shade(tee, 0.9) if face != "top" and face != "bottom" and y < 10 and (x + y) % 5 == 0 else None))
        sk.paint("rarm", tattoo_arm(arms_fn(tone, tee, 4, glove=hexc("1c1c1c")), 4, "r"))
        sk.paint("larm", tattoo_arm(arms_fn(tone, tee, 4, glove=hexc("1c1c1c")), 4, "l"))
        sk.paint("rsleeve", lambda face, x, y, w, h: tee if (y < 4 and face not in ("bottom",)) else None)
        sk.paint("lsleeve", lambda face, x, y, w, h: tee if (y < 4 and face not in ("bottom",)) else None)
        cargo = hexc("26262a")
        sk.paint("rleg", legs_fn(tone, cargo, 12, hexc("141414"), 2))
        sk.paint("lleg", legs_fn(tone, cargo, 12, hexc("141414"), 2))
        for leg in ("rleg", "lleg"):
            sk.paint(leg, lambda face, x, y, w, h: shade(cargo, 1.25) if (face in ("right", "left") and 5 <= y <= 7 and x in (1, 2)) else None)
        bandana(sk, hexc("1a1a1a"), hexc("d8d8d8"))
    elif outfit == "swim":
        # shirtless, camo board shorts, backwards cap and green mirror shades (trailer two, the keys)
        sk.paint("body", torso_fn(tone, None, low=hexc("6b6a45"), low_from=10))
        # abs shading and chain
        for (x, y) in [(3, 5), (4, 5), (3, 7), (4, 7), (2, 3), (5, 3)]:
            sk.px("body", "front", x, y, shade(tone, 0.88))
        for p in [(1, 0), (2, 1), (3, 2), (4, 2), (5, 1), (6, 0)]:
            sk.px("body", "front", p[0], p[1], chain)
        # chest tattoo script
        for x in range(1, 7):
            sk.px("body", "front", x, 2 if x not in (3, 4) else 3, tattoo) if x in (1, 6) else None
        sk.paint("rarm", tattoo_arm(arms_fn(tone), 0, "r"))
        sk.paint("larm", tattoo_arm(arms_fn(tone), 0, "l"))
        sk.paint("rarm", lambda face, x, y, w, h: hexc("2a2a2a") if (y == 8 and face not in ("top", "bottom")) else None)
        camo = [hexc("6b6a45"), hexc("4d4b30"), hexc("8a8156"), hexc("3a3a26")]
        def camo_f(face, x, y, w, h):
            return camo[((x * 3 + y * 5 + (x * y) % 3) // 2) % 4]
        sk.paint("rleg", legs_fn(tone, camo[0], 6, hexc("2a2a2a"), 1))
        sk.paint("lleg", legs_fn(tone, camo[0], 6, hexc("2a2a2a"), 1))
        for leg in ("rleg", "lleg"):
            sk.paint(leg, lambda face, x, y, w, h: camo_f(face, x, y, w, h) if (y < 6 and face not in ("bottom",)) else None)
        sk.paint("body", lambda face, x, y, w, h: camo_f(face, x, y, w, h) if (y >= 10 and face not in ("top",)) else None)
        cap_overlay(sk, hexc("2e2e30"), backwards=True)
        sunglasses(sk, hexc("1a1a1a"), hexc("3fd07a"))
    elif outfit == "casual":
        # white tee from the prison release scene, khaki shorts, white sneakers
        tee = hexc("ece9e2")
        sk.paint("body", torso_fn(tone, tee, "crew", 11, low=hexc("b49c74"), low_from=11))
        sk.paint("rarm", tattoo_arm(arms_fn(tone, tee, 4), 4, "r"))
        sk.paint("larm", tattoo_arm(arms_fn(tone, tee, 4), 4, "l"))
        sk.paint("rleg", legs_fn(tone, hexc("b49c74"), 6, sneaker, 1, sock=hexc("f4f4f4")))
        sk.paint("lleg", legs_fn(tone, hexc("b49c74"), 6, sneaker, 1, sock=hexc("f4f4f4")))
        sk.px("body", "front", 2, 3, hexc("5a7a9a"))
        sk.px("body", "front", 3, 3, hexc("5a7a9a"))
    return sk

def lucia(outfit):
    sk = Skin(True, seed=200 + zlib.crc32(outfit.encode()) % 1000)
    tone = SKIN_TONES["lucia"]
    hair = HAIR["lucia"]
    hl = hexc("4a2e1e")
    hoop = hexc("e6c050")
    sk.paint("head", lambda face, x, y, w, h: tone, jitter=0.02)
    def hairf(face, x, y, w, h):
        # hair pulled back into a high ponytail: covers top, back, upper sides
        if face == "top":
            return hl if x in (3, 4) and y < 6 and y % 2 == 0 else hair
        if face == "bottom":
            return None
        if face == "back":
            return hair if y < 7 else None
        if face == "front":
            return None
        if y < 3:
            return hair
        if y < 6 and ((face == "right" and x >= 3) or (face == "left" and x <= 4)):
            return hair
        if y == 6 and ((face == "right" and x >= 6) or (face == "left" and x <= 1)):
            return hair
        return None
    sk.paint("head", hairf, jitter=0.07)
    pal = {"H": hair, "h": hl, "S": tone, "s": shade(tone, 0.88), "t": shade(tone, 1.07), "b": hexc("1a0f0a"),
           "W": hexc("f2eee6"), "e": hexc("3a2216"), "k": hexc("120a08"), "n": shade(tone, 0.82),
           "r": mix(tone, hexc("c0605a"), 0.18), "L": hexc("9a4f4c"), "l": hexc("6e3434")}
    # centre part, hair pulled tight to the high ponytail, loose strands framing the face (cover art)
    sk.put_face("head", "front", [
        "HHHhhHHH",
        "HHHkkHHH",
        "HtSSSStH",
        "HbbSSbbH",
        "kWekkeWk",
        "SrSnnSrS",
        "SSlLLlSS",
        "SsSSSSsS",
    ], pal)
    sk.px("head", "front", 4, 4, tone)
    sk.px("head", "front", 3, 4, tone)
    ear(sk, tone, hoop)
    # big gold hoops: a ring of pixels down the side
    for face, ex in (("right", 4), ("left", 3)):
        sk.px("head", face, ex, 6, hoop)
        sk.px("head", face, ex + (1 if face == "right" else -1), 6, hoop)
        sk.px("head", face, ex + (1 if face == "right" else -1), 7, hoop)
    tattoo = hexc("3c3a48")
    # hat layer: the volume of hair swept up to the crown, so the ponytail reads from the front too
    def vol(face, x, y, w, h):
        if face == "top":
            return hl if (x + y) % 3 == 0 else hair
        if face == "front":
            if y == 0:
                return hl if x in (3, 4) else hair
            if y == 1 and x in (0, 1, 6, 7):
                return hair
            if 2 <= y <= 5 and x in (0, 7):
                return shade(hair, 1.1) if y % 2 else hair
            return None
        if face == "back":
            return (hl if (x * 3 + y) % 5 == 0 else hair) if y < 5 else None
        if face in ("right", "left"):
            fx = x if face == "left" else 7 - x
            if y <= 2:
                return hair if (x + y) % 4 else hl
            if y <= 4 and fx >= 3:
                return hair
            return None
        return None
    sk.paint("hat", vol, jitter=0.06)
    jeans_l = hexc("8aa6c8")
    black = hexc("1d1b1e")
    def larm_ink(face, x, y, w, h):
        # small script tattoo on the inner left forearm
        if face == "front" and y in (7, 8) and x in (0, 2):
            return tattoo
        return None
    if outfit == "everyday":
        # black tank top, high-waisted light jeans, white sneakers (cover art)
        sk.paint("body", torso_fn(tone, black, "scoop", 8, low=jeans_l, low_from=8))
        sk.paint("body", lambda face, x, y, w, h: hexc("6a4a2a") if (face == "front" and y == 8) else None)
        for p in [(2, 2), (3, 3), (4, 3), (5, 2)]:
            sk.px("body", "front", p[0], p[1], hexc("e6c050"))
        sk.paint("rarm", arms_fn(tone, watch=hexc("e6c050")))
        sk.paint("larm", arms_fn(tone))
        sk.paint("larm", larm_ink)
        sk.paint("rleg", legs_fn(tone, jeans_l, 12, hexc("f0f0f0"), 1))
        sk.paint("lleg", legs_fn(tone, jeans_l, 12, hexc("f0f0f0"), 1))
        for leg in ("rleg", "lleg"):
            sk.paint(leg, lambda face, x, y, w, h: shade(jeans_l, 1.1) if (face == "front" and 2 <= y <= 5 and x in (1, 2)) else None)
            sk.paint(leg, lambda face, x, y, w, h: shade(jeans_l, 0.85) if (face == "front" and y == 10) else None)
    elif outfit == "heist":
        # black tank, red paisley bandana (trailer one store robbery), dark jeans, black gloves
        sk.paint("body", torso_fn(tone, black, "tank", 9, low=hexc("2a3448"), low_from=9))
        sk.paint("rarm", arms_fn(tone, glove=hexc("1a1a1a")))
        sk.paint("larm", arms_fn(tone, glove=hexc("1a1a1a")))
        sk.paint("larm", larm_ink)
        dj = hexc("2a3448")
        sk.paint("rleg", legs_fn(tone, dj, 12, hexc("161616"), 2))
        sk.paint("lleg", legs_fn(tone, dj, 12, hexc("161616"), 2))
        bandana(sk, hexc("b02a2a"), hexc("e8d0c0"))
    elif outfit == "swim":
        # pink and violet bikini, gold anklet, flip-flops
        pink = hexc("e8559a")
        violet = hexc("7a3ad0")
        def bikini(face, x, y, w, h):
            if face == "front":
                if y in (2, 3) and x in (1, 2, 5, 6):
                    return pink
                if y == 1 and x in (2, 5):
                    return pink
                if y >= 10 and 1 <= x <= 6:
                    return pink if y == 10 or 2 <= x <= 5 else None
                if y == 11:
                    return pink if 2 <= x <= 5 else None
            if face == "back":
                if y == 3:
                    return pink
                if y >= 10 and (y == 10 or 2 <= x <= 5):
                    return pink
            if face in ("right", "left"):
                if y == 3:
                    return pink
                if y == 10:
                    return violet
            if face == "bottom":
                return pink if 2 <= x <= 5 else None
            return None
        sk.paint("body", torso_fn(tone, None))
        sk.paint("body", bikini)
        sk.paint("rarm", arms_fn(tone, watch=hexc("e6c050")))
        sk.paint("larm", arms_fn(tone))
        sk.paint("larm", larm_ink)
        sk.paint("rleg", legs_fn(tone, None, 0, hexc("e8559a"), 1))
        sk.paint("lleg", legs_fn(tone, None, 0, hexc("e8559a"), 1))
        sk.paint("lleg", lambda face, x, y, w, h: hexc("e6c050") if (y == 10 and face not in ("top", "bottom")) else None)
        sunglasses(sk, hexc("3a2230"), hexc("2a1a3a"))
    elif outfit == "jumpsuit":
        # Leonida corrections jumpsuit (trailer one opening)
        orange = hexc("e86a2a")
        sk.paint("body", torso_fn(tone, orange, "v", 12, pattern=lambda face, x, y, w, h: (hexc("f0f0f0") if face == "back" and y in (3, 4) and 1 <= x <= 6 and (x + y) % 2 else None)))
        sk.paint("rarm", arms_fn(tone, orange, 4))
        sk.paint("larm", arms_fn(tone, orange, 4))
        sk.paint("rleg", legs_fn(tone, orange, 12, hexc("f2f2f2"), 1))
        sk.paint("lleg", legs_fn(tone, orange, 12, hexc("f2f2f2"), 1))
        sk.px("body", "front", 1, 2, hexc("1a1a1a"))
        sk.px("body", "front", 2, 2, hexc("1a1a1a"))
    return sk

# ---------------------------------------------------------------- pedestrians
TOPS = {
    "tee": ("crew", 4), "polo": ("v", 4), "tank": ("tank", 0), "hawaiian": ("v", 5), "hoodie": ("crew", 12),
    "longsleeve": ("crew", 11), "crop": ("scoop", 0), "dress": ("scoop", 0), "vest": ("crew", 4),
    "bikini": ("bikini", 0), "none": ("none", 0), "button": ("v", 11), "jersey": ("crew", 0),
    "suit": ("v", 12), "blouse": ("v", 4), "silk": ("v", 4), "fishing": ("crew", 11), "flannel": ("crew", 11),
    "overalls": ("crew", 4), "camo": ("crew", 4), "tracksuit": ("crew", 12),
}

def pattern_for(kind, top, rng):
    acc = shade(top, 1.35) if sum(top[:3]) < 400 else shade(top, 0.7)
    flower = hexc(rng.r.choice(["f4f0e0", "f06a6a", "f2d040", "6ad0e0"]))
    if kind == "hawaiian":
        return lambda face, x, y, w, h: flower if (x * 5 + y * 3) % 7 == 0 else (hexc("2a8a5a") if (x * 3 + y) % 9 == 0 else None)
    if kind == "stripes":
        return lambda face, x, y, w, h: acc if y % 3 == 0 else None
    if kind == "hivis":
        return lambda face, x, y, w, h: hexc("d8d8d8") if y in (6, 7) else None
    if kind == "logo":
        return lambda face, x, y, w, h: acc if (face == "front" and 3 <= y <= 4 and 2 <= x <= 5) else None
    if kind == "plaid":
        dk = shade(top, 0.6)
        return lambda face, x, y, w, h: (dk if (x % 4 == 0 or y % 4 == 0) else None) if face != "top" else None
    if kind == "camo":
        cs = [hexc("4d4b30"), hexc("8a8156"), hexc("3a3a26"), None]
        return lambda face, x, y, w, h: cs[((x * 3 + y * 5 + (x * y) % 3) // 2) % 4]
    if kind == "sparkle":
        hi = shade(top, 1.5)
        return lambda face, x, y, w, h: hi if (x * 7 + y * 3) % 5 == 0 else None
    if kind == "fishing":
        return lambda face, x, y, w, h: (shade(top, 0.8) if (face == "front" and y in (2, 3) and x in (1, 2, 5, 6)) else None)
    if kind == "number":
        return lambda face, x, y, w, h: acc if (face in ("front", "back") and 2 <= y <= 6 and x in (3, 4) or (face == "back" and y in (2, 6) and 2 <= x <= 5)) else None
    return None

def pedestrian(seed, kind, force_female=None, force_slim=None):
    rng = Rng(seed)
    R = rng.r
    female = R.random() < 0.5
    slim_roll = R.random() < 0.85
    if force_female is not None:
        female = force_female
    sk = Skin(force_slim if force_slim is not None else (female and slim_roll), seed=seed)
    tone_name = R.choice(["pale", "fair", "fair", "tan", "tan", "olive", "olive", "brown", "brown", "dark", "deep"])
    tone = SKIN_TONES[tone_name]
    old = R.random() < {"tourist": 0.35, "boater": 0.4, "suburb": 0.3, "swamp": 0.25, "nightlife": 0.03, "business": 0.2}.get(kind, 0.15)
    hair_name = R.choice(["black", "dkbrown", "brown", "blonde"] + (["platinum", "red", "pink"] if female else ["black"]))
    if old:
        hair_name = R.choice(["grey", "white", "grey"])
    hair = HAIR[hair_name]
    if female:
        style = R.choice(["long", "long", "bob", "curly", "fringe"] + (["afro"] if tone_name in ("brown", "dark", "deep") else []))
    else:
        style = R.choice(["short", "short", "buzz", "bald" if old or R.random() < 0.3 else "short", "curly"] + (["afro"] if tone_name in ("dark", "deep") else []))
    eye = hexc(R.choice(["3a2216", "4a3020", "2a1a12", "4f6f88", "5a7a3a"]))
    beard = None
    if not female and R.random() < 0.45:
        beard = mix(tone, hair, 0.6)
    generic_head(sk, tone, hair, style, rng)
    generic_face(sk, tone, hair, eye, style, rng, female=female, beard=beard,
                 lips=(hexc(R.choice(["9a4040", "b04060", "7a3a3a", "c05070"])) if female else None))
    ear(sk, tone, hexc("e6c050") if female and R.random() < 0.5 else None)
    hair_overlay(sk, hair, style, rng)

    def col(*names):
        return hexc(R.choice(names))
    shoe = col("f0f0f0", "202020", "6a4a32", "c03030", "3050a0", "e0d0b0")
    # outfit by kind
    if kind == "beach":
        if female:
            top_kind = R.choice(["bikini", "bikini", "crop", "tank"])
        else:
            top_kind = R.choice(["none", "none", "tank", "hawaiian"])
        top = col("e8559a", "30b0c0", "f0d040", "f07040", "ffffff", "202020", "50c060", "8a4ac8")
        bottom = col("30b0c0", "f07040", "2050a0", "e8559a", "202020", "f0d040", "e8e0d0")
        bottom_len = 2 if female and top_kind == "bikini" else R.choice([5, 6])
        shoe = col("e8559a", "30b0c0", "f0f0f0", "202020")
        pat = "hawaiian" if top_kind == "hawaiian" else None
    elif kind == "tourist":
        top_kind = R.choice(["hawaiian", "tee", "polo", "tee"] + (["dress", "tank"] if female else []))
        top = col("f2f2f2", "f0c0d0", "a0d0f0", "f0e0a0", "e86a2a", "60a0d0", "d0e8c0")
        bottom = col("c8b48a", "e8e0d0", "6a7a9a", "3b5a85", "f2f2f2")
        bottom_len = R.choice([6, 6, 12])
        pat = "hawaiian" if top_kind == "hawaiian" else R.choice([None, "logo", "stripes"])
    elif kind == "worker":
        job = R.choice(["construction", "construction", "delivery", "mechanic", "chef", "security", "office"])
        if job == "construction":
            top_kind = "vest"; top = hexc("f0a020") if R.random() < 0.5 else hexc("c8e030"); pat = "hivis"
            bottom = hexc("3b5a85"); bottom_len = 12; shoe = hexc("6a4a32")
        elif job == "delivery":
            top_kind = "polo"; top = col("6a3a1e", "e0302a", "2a4a9a"); pat = "logo"; bottom = hexc("4a3a2a"); bottom_len = 6
        elif job == "mechanic":
            top_kind = "button"; top = col("3a4a6a", "5a5a5a"); pat = "logo"; bottom = top; bottom_len = 12; shoe = hexc("202020")
        elif job == "chef":
            top_kind = "button"; top = hexc("f2f2f2"); pat = None; bottom = hexc("2a2a2a"); bottom_len = 12; shoe = hexc("202020")
        elif job == "security":
            top_kind = "button"; top = hexc("202024"); pat = "logo"; bottom = hexc("202024"); bottom_len = 12; shoe = hexc("101010")
        else:
            top_kind = "button"; top = col("f2f2f2", "a8c8e8", "e8d8e8"); pat = None; bottom = col("2a2a34", "4a4a50", "3a3a4a"); bottom_len = 12; shoe = hexc("2a1a10")
    elif kind == "business":
        top_kind = R.choice(["suit", "suit", "button"] + (["blouse", "dress"] if female else []))
        top = col("1e2434", "2a2a2e", "3a3a42", "4a4038") if top_kind == "suit" else col("f2f2f2", "a8c8e8", "e8d8e8", "f0e8d8")
        if top_kind == "dress":
            top = col("1e2434", "7a1e2a", "2a2a2e", "e8e0d0")
        bottom = col("1e2434", "2a2a2e", "3a3a42") if top_kind != "suit" else top
        bottom_len = 12 if not (female and top_kind == "blouse" and R.random() < 0.5) else 4
        shoe = col("1a1210", "2a1a10", "101010")
        pat = None
    elif kind == "nightlife":
        top_kind = R.choice(["dress", "dress", "crop", "tank"] if female else ["silk", "silk", "tee", "tank", "button"])
        top = col("e83a8a", "30d0e0", "f0d040", "1a1a1a", "f2f2f2", "8a4ac8", "d02030", "e0b0ff")
        bottom = col("f2f2f2", "1a1a1a", "202840", "e8e0d0") if not female else col("1a1a1a", "f2f2f2", "e83a8a")
        bottom_len = 12 if not female else R.choice([3, 4, 12])
        shoe = col("f2f2f2", "1a1a1a", "e0b84a", "d02030")
        pat = "sparkle" if female and R.random() < 0.6 else ("hawaiian" if top_kind == "silk" and R.random() < 0.4 else None)
    elif kind == "boater":
        top_kind = R.choice(["fishing", "fishing", "tee", "tank", "fishing"])
        top = col("a8c8e0", "e8e0c8", "f2f2f2", "c8d8b0", "f0c8a0", "6a8aa8")
        bottom = col("c8b48a", "8a8a70", "3b5a85", "e8e0d0", "5a6a5a")
        bottom_len = R.choice([5, 6, 6])
        shoe = col("6a4a32", "f2f2f2", "e8e8e8")
        pat = "fishing" if top_kind == "fishing" else None
    elif kind == "swamp":
        top_kind = R.choice(["flannel", "flannel", "tee", "tank", "overalls", "camo"])
        if top_kind == "flannel":
            top = col("b02a2a", "2a5a3a", "2a4a8a", "8a6a2a"); pat = "plaid"
        elif top_kind == "camo":
            top = hexc("6b6a45"); pat = "camo"
        else:
            top = col("f2f2f2", "5a5a5a", "3a6a3a", "c0b090", "202020"); pat = None
        bottom = col("3b5a85", "4a4a3a", "6b6a45", "2a3f60") if top_kind != "overalls" else hexc("3b5a85")
        bottom_len = 12
        shoe = col("f2f2f2", "f2f2f2", "4a3a2a")  # white rubber boots
    elif kind == "suburb":
        top_kind = R.choice(["polo", "tee", "hoodie", "longsleeve", "tracksuit"] + (["dress", "tank"] if female else ["polo"]))
        top = col("6a8aa8", "f2f2f2", "c8a0b8", "3a6a5a", "e8d8b0", "a04040", "4a4a6a", "d0d0d0")
        bottom = col("3b5a85", "8aa6c8", "c8b48a", "4a4a4a", "2a2a3a", "e8e0d0")
        if top_kind == "tracksuit":
            bottom = top
        bottom_len = R.choice([6, 12, 12])
        shoe = col("f2f2f2", "e8e8e8", "3050a0", "202020")
        pat = "stripes" if top_kind == "tracksuit" else R.choice([None, None, "logo"])
    else:  # local
        top_kind = R.choice(["tee", "tee", "tank", "jersey", "hoodie", "longsleeve", "polo"] + (["crop", "dress"] if female else []))
        top = col("202020", "f2f2f2", "c03030", "2a4a9a", "e8559a", "30b0c0", "f0d040", "3a6a3a", "8a4ac8", "e86a2a", "a0a0a0")
        bottom = col("3b5a85", "2a3f60", "202020", "c8b48a", "8aa6c8", "4a4a4a", "e8e0d0")
        bottom_len = R.choice([6, 12, 12])
        pat = R.choice([None, None, "logo", "stripes", "number" if top_kind == "jersey" else None])
    neck, sleeve = TOPS[top_kind]
    pattern = pattern_for(pat, top, rng) if pat else None
    if top_kind == "none":
        sk.paint("body", torso_fn(tone, None, low=bottom, low_from=10))
    elif top_kind == "bikini":
        bik = top
        def bf(face, x, y, w, h):
            if face == "front" and ((y in (2, 3) and x in (1, 2, 5, 6)) or (y == 1 and x in (2, 5))):
                return bik
            if face in ("back", "right", "left") and y == 3:
                return bik
            if y >= 10 and face not in ("top",):
                return bottom if (face != "front" or y == 10 or 2 <= x <= 5) else None
            return None
        sk.paint("body", torso_fn(tone, None))
        sk.paint("body", bf)
    else:
        top_len = 7 if top_kind == "crop" else (12 if top_kind == "dress" else 10)
        low = bottom if top_kind != "dress" else None
        sk.paint("body", torso_fn(tone, top, neck, top_len, pattern=pattern, low=low, low_from=top_len))
        if top_kind == "hoodie":
            sk.paint("jacket", lambda face, x, y, w, h: (top if (face == "back" and y < 3) else None))
    arm_args = dict(sleeve=top if sleeve else None, sleeve_len=sleeve)
    watch = hexc("2a2a2a") if R.random() < 0.3 else None
    sk.paint("rarm", arms_fn(tone, watch=watch, **arm_args))
    sk.paint("larm", arms_fn(tone, **arm_args))
    if top_kind == "dress":
        skirt = top
        sk.paint("rleg", legs_fn(tone, skirt, 4, shoe, 1))
        sk.paint("lleg", legs_fn(tone, skirt, 4, shoe, 1))
        sk.paint("rpants", lambda face, x, y, w, h: skirt if y < 5 and face not in ("top", "bottom") else None)
        sk.paint("lpants", lambda face, x, y, w, h: skirt if y < 5 and face not in ("top", "bottom") else None)
    else:
        sk.paint("rleg", legs_fn(tone, bottom, bottom_len, shoe, 1))
        sk.paint("lleg", legs_fn(tone, bottom, bottom_len, shoe, 1))
    # district details
    if top_kind == "suit":
        shirt = col("f2f2f2", "a8c8e8", "f0e8d8")
        tie = col("8a1e2a", "1e3a6a", "3a3a3a", "6a5a1e")
        def lapel(face, x, y, w, h):
            if face != "front" or y > 9:
                return None
            if x in (3, 4):
                return tie if (not female and y >= 1) else shirt
            if (x == 2 or x == 5) and y < 5:
                return shirt if y < 2 else shade(top, 1.25)
            return None
        sk.paint("body", lapel)
        sk.paint("jacket", lambda face, x, y, w, h: (shade(top, 1.08) if face in ("right", "left", "back") and y < 11 else None))
        sk.paint("rarm", lambda face, x, y, w, h: shirt if (y == 11 and face not in ("top", "bottom")) else None)
        sk.paint("larm", lambda face, x, y, w, h: shirt if (y == 11 and face not in ("top", "bottom")) else None)
    if top_kind == "overalls":
        den = hexc("3b5a85")
        def ov(face, x, y, w, h):
            if face == "front" and y >= 4:
                return den if y >= 5 or 1 <= x <= 6 else None
            if face in ("front", "back") and y < 5 and x in (1, 6):
                return den
            if face in ("back", "right", "left") and y >= 7:
                return den
            return None
        sk.paint("body", ov)
        sk.px("body", "front", 1, 4, hexc("e0b84a")); sk.px("body", "front", 6, 4, hexc("e0b84a"))
    if kind == "nightlife" and not female and R.random() < 0.6:
        for p in [(2, 1), (3, 2), (4, 2), (5, 1)]:
            sk.px("body", "front", p[0], p[1], hexc("e0b84a"))
    if kind == "business" and R.random() < 0.3:
        sk.put_face("hat", "front", ["........", "........", "........", "gggggggg", ".g.gg.g.", "........", "........", "........"],
                    {"g": hexc("2a2a2a")})
    if kind == "boater":
        if R.random() < 0.7:
            cap_overlay(sk, col("e8e0c8", "c8b48a", "6a8aa8", "f2f2f2"), front_logo=hexc("2a4a6a") if R.random() < 0.5 else None)
        if R.random() < 0.6:
            sunglasses(sk, hexc("1a1a1a"), hexc(R.choice(["2a3a6a", "3fd07a", "c06a2a"])))
    if kind == "swamp" and R.random() < 0.55 and style not in ("long", "curly", "afro"):
        cap_overlay(sk, col("6b6a45", "f2f2f2", "c03030", "202020", "3a6a3a"), front_logo=hexc("f2f2f2") if R.random() < 0.4 else None)
    if kind == "suburb" and R.random() < 0.2 and style not in ("long", "curly", "afro"):
        cap_overlay(sk, col("2a4a9a", "c03030", "f2f2f2"))
    # accessories
    if kind in ("beach", "tourist") and R.random() < 0.6:
        sunglasses(sk, hexc("1a1a1a"), hexc(R.choice(["202020", "3a2a1a", "2a3a6a", "3fd07a"])))
    if kind == "tourist" and R.random() < 0.5:
        cap_overlay(sk, col("f2f2f2", "e0302a", "2a4a9a", "f0d040"), front_logo=hexc("202020"))
        # camera strap
        for (x, y) in [(1, 0), (2, 1), (3, 2), (4, 3), (5, 4), (6, 5)]:
            sk.px("body", "front", x, y, hexc("202020"))
        sk.px("body", "front", 6, 6, hexc("303030"))
    if kind == "worker" and top == hexc("f0a020") or (kind == "worker" and pat == "hivis"):
        cap_overlay(sk, hexc("f2d020"))
    if kind == "local" and R.random() < 0.25 and style not in ("long", "curly", "afro"):
        cap_overlay(sk, col("202020", "c03030", "2a4a9a", "f2f2f2"), backwards=R.random() < 0.4)
    return sk

# ---------------------------------------------------------------- police and SWAT
def officer_fixed(seed, agency, female):
    return officer(seed, agency, female)

def clerk(seed):
    """Store clerk: green polo with a name tag, khakis."""
    rng = Rng(seed)
    sk = Skin(False, seed=seed)
    tone = SKIN_TONES["olive"]; hair = HAIR["black"]
    generic_head(sk, tone, hair, "short", rng)
    generic_face(sk, tone, hair, hexc("3a2216"), "short", rng, beard=mix(tone, hair, 0.6))
    ear(sk, tone)
    polo = hexc("2f7a4a")
    def pat(face, x, y, w, h):
        if face == "front" and y == 2 and x in (5, 6):
            return hexc("f2f2f2")
        if face == "front" and y in (0, 1) and x in (2, 5):
            return shade(polo, 0.8)
        return None
    sk.paint("body", torso_fn(tone, polo, "v", 10, pattern=pat, low=hexc("b49c74"), low_from=10))
    sk.paint("body", lambda face, x, y, w, h: hexc("3a2a1a") if y == 10 and face not in ("top", "bottom") else None)
    sk.paint("rarm", arms_fn(tone, polo, 4))
    sk.paint("larm", arms_fn(tone, polo, 4))
    sk.paint("rleg", legs_fn(tone, hexc("b49c74"), 12, hexc("202020"), 1))
    sk.paint("lleg", legs_fn(tone, hexc("b49c74"), 12, hexc("202020"), 1))
    return sk

def officer(seed, agency, force_female=None):
    rng = Rng(seed)
    R = rng.r
    female = R.random() < 0.3
    if force_female is not None:
        female = force_female
    sk = Skin(female, seed=seed)
    tone = SKIN_TONES[R.choice(["pale", "fair", "tan", "olive", "brown", "dark", "deep"])]
    hair = HAIR[R.choice(["black", "dkbrown", "brown", "blonde", "grey"])]
    style = "bob" if female else R.choice(["buzz", "short", "bald"])
    generic_head(sk, tone, hair, style, rng)
    generic_face(sk, tone, hair, hexc("3a2216"), style, rng, female=female,
                 beard=(mix(tone, hair, 0.6) if not female and R.random() < 0.3 else None))
    ear(sk, tone)
    if female:
        hair_overlay(sk, hair, "bob", rng)
    gold = hexc("e0c050")
    if agency == "police":
        # VCPD: dark navy uniform, gold badge, duty belt, black POLICE vest (trailer one bodycam shot)
        shirt = hexc("1f2a44"); pants = hexc("1a2238"); patch = hexc("c8a040")
    else:
        # Leonida county sheriff: tan shirt, forest green trousers
        shirt = hexc("c8b48a"); pants = hexc("2f4a32"); patch = hexc("2f4a32")
    belt = hexc("151515")
    def shirt_pat(face, x, y, w, h):
        if face == "front":
            if y == 2 and x == 5:
                return gold  # badge
            if y == 3 and x in (5,):
                return shade(gold, 0.8)
            if y == 2 and x == 2:
                return hexc("b0b0b0")  # name tag
            if x == 3 and 1 <= y <= 8 and y % 3 == 1:
                return shade(shirt, 0.75)  # buttons
            if y in (1, 2) and x in (1, 6) and agency == "sheriff":
                return shade(shirt, 0.85)
        return None
    sk.paint("body", torso_fn(tone, shirt, "v", 10, pattern=shirt_pat, low=pants, low_from=10))
    sk.paint("body", lambda face, x, y, w, h: belt if (y == 10 and face not in ("top", "bottom")) else None)
    sk.paint("body", lambda face, x, y, w, h: hexc("2a2a2a") if (face in ("right", "left") and y in (10, 11) and x in (1, 2)) else None)
    sk.px("body", "front", 4, 10, hexc("c0c0c0"))
    # vest on the jacket layer
    vest = hexc("22262c")
    if agency == "police": sk.paint("jacket", lambda face, x, y, w, h: (vest if face in ("front", "back", "right", "left") and 1 <= y <= 8 and not (face == "front" and y == 1 and 2 <= x <= 5) else None))
    if agency == "police": sk.paint("jacket", lambda face, x, y, w, h: gold if (face == "front" and y == 3 and x == 5) else None)
    if agency == "police": sk.paint("jacket", lambda face, x, y, w, h: hexc("e8e8e8") if (face == "back" and y in (3, 4) and 1 <= x <= 6 and (x + y) % 2 == 0) else None)
    aw = arms_fn(tone, shirt, 4)
    sk.paint("rarm", aw)
    sk.paint("larm", aw)
    sk.paint("larm", lambda face, x, y, w, h: patch if (face == "left" and y in (1, 2)) else None)
    sk.paint("rarm", lambda face, x, y, w, h: patch if (face == "right" and y in (1, 2)) else None)
    sk.paint("rleg", legs_fn(tone, pants, 12, hexc("101010"), 2, stripe=(gold if agency == "police" else hexc("1a2a1a"))))
    sk.paint("lleg", legs_fn(tone, pants, 12, hexc("101010"), 2, stripe=(gold if agency == "police" else hexc("1a2a1a"))))
    if R.random() < 0.5:
        sunglasses(sk, hexc("202020"), hexc("2a2a2a"))
    if agency == "sheriff":
        cap_overlay(sk, hexc("2f4a32"), front_logo=gold)
    elif R.random() < 0.6 and not female:
        # navy patrol cap with a gold badge
        cap_overlay(sk, hexc("1a2238"), front_logo=gold)
    return sk

def swat(seed):
    rng = Rng(seed)
    sk = Skin(False, seed=seed)
    R = rng.r
    tone = SKIN_TONES[R.choice(["fair", "tan", "brown", "dark"])]
    black = hexc("1c1e22") if seed % 2 == 0 else hexc("2c3226")
    vest = shade(black, 1.35)
    # balaclava head, goggles slot
    sk.paint("head", lambda face, x, y, w, h: black, jitter=0.04)
    sk.put_face("head", "front", [
        "KKKKKKKK", "KKKKKKKK", "KKKKKKKK", "KSSSSSSK", "KWeSSeWK", "KKKKKKKK", "KKKKKKKK", "KKKKKKKK"],
        {"K": black, "S": tone, "W": hexc("e8e8e8"), "e": hexc("3a2216")})
    # helmet shell on the hat layer with goggles
    helmet = shade(black, 1.15)
    def hel(face, x, y, w, h):
        if face == "top":
            return helmet
        if face == "bottom":
            return None
        if y < 3:
            return helmet
        if face == "front" and y == 3:
            return hexc("101418") if x in (0, 7) else None
        if face != "front" and y < 5:
            return helmet
        return None
    sk.paint("hat", hel, jitter=0.03)
    sk.put_face("hat", "front", ["........", "........", "gggggggg", "g......g", "........"], {"g": hexc("101418")})
    sk.paint("body", torso_fn(tone, black, "crew", 12))
    def vestp(face, x, y, w, h):
        if face == "top" or face == "bottom":
            return None
        if face == "back" and y in (3, 4) and 1 <= x <= 6:
            return hexc("e8e8e8") if (x + y) % 2 == 0 or y == 3 else vest
        if face == "front" and y in (6, 7) and x in (1, 2, 5, 6):
            return shade(vest, 0.8)  # pouches
        if face == "front" and y == 3 and 2 <= x <= 5:
            return hexc("e8e8e8") if (x % 2 == 0) else vest
        if y <= 9:
            return vest
        return None
    sk.paint("jacket", vestp)
    sk.paint("body", lambda face, x, y, w, h: hexc("101010") if y == 10 and face not in ("top", "bottom") else None)
    aw = arms_fn(tone, black, 12, glove=hexc("101010"))
    sk.paint("rarm", aw)
    sk.paint("larm", aw)
    sk.paint("rsleeve", lambda face, x, y, w, h: vest if (y < 3 and face not in ("bottom",)) else None)
    sk.paint("lsleeve", lambda face, x, y, w, h: vest if (y < 3 and face not in ("bottom",)) else None)
    sk.paint("rleg", legs_fn(tone, black, 12, hexc("0c0c0c"), 2))
    sk.paint("lleg", legs_fn(tone, black, 12, hexc("0c0c0c"), 2))
    for leg in ("rpants", "lpants"):
        sk.paint(leg, lambda face, x, y, w, h: vest if (face == "front" and y in (4, 5)) else None)  # knee pads
        sk.paint(leg, lambda face, x, y, w, h: vest if (face in ("right", "left") and 1 <= y <= 3) else None)  # thigh pouch
    return sk

# ---------------------------------------------------------------- main
def main():
    base = os.path.join(OUT, "assets/leonida/textures/entity/character")
    index = {"jason": [], "lucia": [], "pedestrian": [], "police": [], "swat": []}
    for o in ["everyday", "heist", "swim", "casual"]:
        jason(o).save(os.path.join(base, "jason", o + ".png"))
        index["jason"].append(o)
    for o in ["everyday", "heist", "swim", "jumpsuit"]:
        lucia(o).save(os.path.join(base, "lucia", o + ".png"))
        index["lucia"].append(o)
    n = 0
    for kind, count in (("local", 16), ("beach", 12), ("tourist", 10), ("worker", 10)):
        for i in range(count):
            sk = pedestrian(1000 + n * 7919, kind)
            name = "%s_%02d" % (kind, i)
            sk.save(os.path.join(base, "pedestrian", name + ".png"))
            index["pedestrian"].append({"name": name, "kind": kind, "slim": sk.slim})
            n += 1
    for i in range(8):
        ag = "police" if i < 4 or i >= 6 else "sheriff"
        sk = officer(5000 + i * 31, ag)
        name = "%s_%02d" % (ag, i)
        sk.save(os.path.join(base, "police", name + ".png"))
        index["police"].append({"name": name, "slim": sk.slim})
    for i in range(4):
        sk = swat(9000 + i)
        name = "swat_%02d" % i
        sk.save(os.path.join(base, "swat", name + ".png"))
        index["swat"].append({"name": name, "slim": False})
    # district sets for ambient NPCs: pedestrian/district/<district>_NN.png
    index["district"] = {}
    DISTRICTS = {
        "vice_beach": ["beach", "beach", "beach", "tourist", "tourist", "beach", "tourist", "beach", "beach", "tourist", "beach", "tourist"],
        "downtown": ["business"] * 8 + ["worker", "local", "business", "worker"],
        "ocean_drive": ["nightlife"] * 9 + ["tourist", "nightlife", "local"],
        "keys": ["boater"] * 9 + ["beach", "boater", "local"],
        "grassrivers": ["swamp"] * 10 + ["worker", "swamp"],
        "port_gellhorn": ["suburb"] * 10 + ["worker", "local"],
    }
    for di, (dname, kinds_) in enumerate(DISTRICTS.items()):
        index["district"][dname] = []
        for i, k in enumerate(kinds_):
            sk = pedestrian(200000 + di * 10007 + i * 613, k)
            name = "%s_%02d" % (dname, i)
            sk.save(os.path.join(base, "pedestrian", "district", name + ".png"))
            index["district"][dname].append({"name": name, "kind": k, "slim": sk.slim})
    # fixed names for the gameplay NPC renderer
    npc = os.path.join(base, "npc")
    kinds = ["local", "beach", "local", "worker", "beach", "tourist", "local", "beach"]
    for i in range(8):
        pedestrian(70000 + i * 131, kinds[i], force_female=False, force_slim=False).save(os.path.join(npc, "ped_m_%d.png" % i))
        pedestrian(80000 + i * 137, kinds[(i + 1) % 8], force_female=True, force_slim=True).save(os.path.join(npc, "ped_f_%d.png" % i))
    officer_fixed(5100, "sheriff", False).save(os.path.join(npc, "police_m.png"))
    officer_fixed(5203, "sheriff", True).save(os.path.join(npc, "police_f.png"))
    swat(9000).save(os.path.join(npc, "swat.png"))
    clerk(4242).save(os.path.join(npc, "clerk.png"))
    with open(os.path.join(OUT, "skin_index.json"), "w") as f:
        json.dump(index, f, indent=1)
    print("ok", n, "pedestrians")

if __name__ == "__main__":
    main()
