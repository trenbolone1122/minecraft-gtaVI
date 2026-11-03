#!/usr/bin/env python3
"""High detail Jason and Lucia models.

Builds each outfit as a cube model on the vanilla PlayerModel skeleton (head, body, arms, legs, so
every vanilla pose and animation still drives it), with far more geometry than a player skin: chunky
hair, beard cubes, curls, shoulder caps, pockets, shoes, hoops. Cubes are authored in half units
(hu, 1 hu = 1/32 block) inside a child part scaled 0.5. Each outfit gets a 512x512 texture
(2 texels per hu) painted from 3D material functions with baked shading: face light, soft edge
occlusion, contact occlusion between cubes, top highlights and fine noise.

Usage: python3 hdmodel.py REPO_ROOT
Writes src/main/resources/assets/leonida/textures/entity/character/model/<char>_<outfit>.png,
model/blank.png and src/client/java/dev/leonida/character/model/HdModels.java."""
import math, os, random, sys
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/leonida/textures/entity/character/model")
JAVA = os.path.join(ROOT, "src/client/java/dev/leonida/character/model/HdModels.java")
GRID = 256          # declared texture size in hu
S = 2               # texels per hu
IMG = GRID * S


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mul(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def hnoise(*k):
    """Deterministic hash noise in [0, 1)."""
    h = 2166136261
    for v in k:
        h ^= int(v * 1000003) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFFFF) / float(0x1000000)


def vnoise(x, y, z, scale, seed=0):
    """Smooth value noise for mottled cloth."""
    x, y, z = x / scale, y / scale, z / scale
    xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
    xf, yf, zf = x - xi, y - yi, z - zi

    def sm(t):
        return t * t * (3 - 2 * t)
    u, v, w = sm(xf), sm(yf), sm(zf)
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                n = hnoise(xi + dx, yi + dy, zi + dz, seed)
                acc += n * (u if dx else 1 - u) * (v if dy else 1 - v) * (w if dz else 1 - w)
    return acc


class Cube:
    def __init__(self, part, tag, x, y, z, w, h, d, ao=True):
        self.part, self.tag = part, tag
        self.x0, self.y0, self.z0 = x, y, z
        self.w, self.h, self.d = int(w), int(h), int(d)
        self.x1, self.y1, self.z1 = x + w, y + h, z + d
        self.ao = ao
        self.u = self.v = 0

    def inside(self, X, Y, Z, eps=0.01):
        return (self.x0 + eps < X < self.x1 - eps and self.y0 + eps < Y < self.y1 - eps
                and self.z0 + eps < Z < self.z1 - eps)


class Model:
    def __init__(self, name, slim):
        self.name, self.slim = name, slim
        self.cubes = []

    def add(self, part, tag, x, y, z, w, h, d, ao=True, mirror=False):
        self.cubes.append(Cube(part, tag, x, y, z, w, h, d, ao))
        if mirror:
            other = {"right_arm": "left_arm", "right_leg": "left_leg"}.get(part)
            if other:
                self.cubes.append(Cube(other, tag, -x - w, y, z, w, h, d, ao))
            else:
                self.cubes.append(Cube(part, tag, -x - w, y, z, w, h, d, ao))

    def limb(self, part, tag, x, y, z, w, h, d, ao=True):
        """Add to a right limb and mirror onto the left one."""
        self.add(part, tag, x, y, z, w, h, d, ao, mirror=True)


# ------------------------------------------------------------------ face art (32x32 texels on the head front)
def face_canvas(skin, pal):
    return [[skin] * 32 for _ in range(32)]


def jason_face(c):
    skin = c["skin"]; sh = mul(skin, 0.86); sh2 = mul(skin, 0.74); hi = mul(skin, 1.07)
    hair = c["hair"]; brow = rgb("2e1d12"); lid = rgb("3a2416"); white = rgb("eeeae2"); wsh = rgb("cfc6ba")
    iris = rgb("5f86a8"); irisd = rgb("3c5a78"); pupil = rgb("141010"); glint = rgb("ffffff")
    beard = c["beard"]; lip = rgb("a8705e"); lipd = rgb("6a3e32"); lipl = rgb("bf8a76")
    F = face_canvas(skin, None)
    for y in range(32):
        for x in range(32):
            col = skin
            if y < 6:
                col = hair
            elif y < 10 and (x + y) % 7 == 0:
                col = hi
            if x <= 2 or x >= 29:
                col = sh
            F[y][x] = col
    # brows: heavy, inner ends lower (focused look)
    for x in range(4, 13):
        rows = (10, 11) if x < 9 else (11, 12)
        if x == 4:
            rows = (11,)
        for r in rows:
            F[r][x] = brow
            F[r][31 - x] = brow
        F[rows[0] - 1][x] = mul(skin, 0.95)
        F[rows[0] - 1][31 - x] = mul(skin, 0.95)
    # eyes
    for side in (0, 1):
        def P(x, y, col):
            F[y][x if side == 0 else 31 - x] = col
        for x in range(6, 12):
            P(x, 13, lid)
        P(5, 13, lid)
        P(6, 14, wsh); P(7, 14, white); P(8, 14, iris); P(9, 14, pupil); P(10, 14, iris); P(11, 14, wsh)
        P(6, 15, sh); P(7, 15, wsh); P(8, 15, irisd); P(9, 15, irisd); P(10, 15, wsh); P(11, 15, sh)
        P(8, 14, glint) if side == 0 else P(10, 14, glint)
        for x in range(7, 11):
            P(x, 16, sh)
        for x in range(6, 12):
            P(x, 17, mul(skin, 0.93))
    # nose shading (the nose itself is a cube)
    for y in range(13, 19):
        F[y][14] = mul(skin, 0.92)
    for x in range(12, 20):
        F[21][x] = sh
    # stubble on the cheeks thickening into the beard
    for y in range(17, 32):
        for x in range(32):
            if 12 <= x <= 19 and y < 21:
                continue
            dens = (y - 16) / 9.0
            if x <= 4 or x >= 27:
                dens += 0.4
            if hnoise(x, y, 7) < dens:
                F[y][x] = lerp(skin, beard, 0.55 + 0.35 * hnoise(x, y, 9))
    # mouth with a slight smirk
    for x in range(11, 21):
        F[23][x] = lip
    for x in range(10, 22):
        F[24][x] = lipd
    F[23][21] = lipd
    for x in range(12, 20):
        F[25][x] = lipl if 14 <= x <= 17 else lip
    for y in range(26, 32):
        for x in range(32):
            F[y][x] = lerp(beard, mul(beard, 1.4), hnoise(x, y, 3) * 0.6)
    return F


def lucia_face(c):
    skin = c["skin"]; sh = mul(skin, 0.87); hi = mul(skin, 1.06)
    hair = c["hair"]; brow = rgb("1c110b"); lash = rgb("100a08"); white = rgb("f0ece4"); wsh = rgb("d2c8bc")
    iris = rgb("6a4026"); irisd = rgb("3e2414"); pupil = rgb("0c0806"); glint = rgb("ffffff")
    lip = rgb("a85a58"); lipd = rgb("6c3434"); lipl = rgb("c27a74"); blush = lerp(skin, rgb("d06a62"), 0.22)
    F = face_canvas(skin, None)
    for y in range(32):
        for x in range(32):
            col = skin
            d = min(x, 31 - x)
            line = 5 + (3 if d <= 2 else 2 if d <= 4 else 1 if d <= 6 else 0) - (1 if 14 <= x <= 17 else 0)
            if y < line:
                col = hair
            elif y == line:
                col = lerp(skin, hair, 0.5)     # soft hairline
            elif y == line + 1:
                col = lerp(skin, hair, 0.2)
            elif y < 9 and (x * 3 + y) % 11 == 0:
                col = hi
            if (x <= 1 or x >= 30) and y > line + 1 or ((x <= 3 or x >= 28) and y >= 24):
                col = sh
            F[y][x] = col
    # arched, defined brows
    arch = {5: (12,), 6: (11, 12), 7: (10, 11), 8: (10, 11), 9: (10, 11), 10: (10, 11), 11: (11,), 12: (11,)}
    for x, rows in arch.items():
        for r in rows:
            F[r][x] = brow
            F[r][31 - x] = brow
    for side in (0, 1):
        def P(x, y, col):
            F[y][x if side == 0 else 31 - x] = col
        for x in range(5, 13):
            P(x, 13, lash)
        P(4, 13, lash); P(3, 12, lash)           # winged liner
        P(5, 14, lash); P(6, 14, white); P(7, 14, iris); P(8, 14, pupil); P(9, 14, pupil); P(10, 14, iris); P(11, 14, white); P(12, 14, wsh)
        P(6, 15, wsh); P(7, 15, irisd); P(8, 15, iris); P(9, 15, iris); P(10, 15, irisd); P(11, 15, wsh)
        P(7, 14, glint) if side == 0 else P(10, 14, glint)
        for x in range(7, 11):
            P(x, 16, sh)
        for x in range(4, 9):
            for y in range(18, 21):
                P(x, y, blush)
    for y in range(15, 20):
        F[y][14] = mul(skin, 0.93)
    F[20][14] = mul(skin, 0.7); F[20][17] = mul(skin, 0.7)
    for x in range(15, 17):
        F[20][x] = mul(skin, 0.9)
    for x in range(13, 19):
        F[21][x] = sh
    # full lips with a cupid's bow
    for x in range(12, 20):
        F[23][x] = lip
    F[23][15] = lipl; F[23][16] = lipl
    for x in range(11, 21):
        F[24][x] = lipd
    for x in range(12, 20):
        F[25][x] = lipl if 14 <= x <= 16 else lip
    for x in range(13, 19):
        F[26][x] = sh
    return F


# ------------------------------------------------------------------ characters
JASON_C = {"skin": rgb("c9875c"), "hair": rgb("4a3020"), "hairhi": rgb("74503a"), "beard": rgb("4a3222"),
           "tattoo": rgb("39475a")}
LUCIA_C = {"skin": rgb("c98e62"), "hair": rgb("2a1810"), "hairhi": rgb("5a3a26"), "gold": rgb("e8c04e"),
           "tattoo": rgb("3a3848")}


def jason_model(outfit):
    m = Model("jason_" + outfit, False)
    # head
    m.add("head", "head", -8, -16, -8, 16, 16, 16)
    m.add("head", "nose", -1, -8.5, -9, 2, 3, 1)
    m.add("head", "ear", -9, -10, -1, 1, 4, 2, mirror=True)
    if outfit == "swim":
        m.add("head", "cap", -9, -19, -9, 18, 6, 18)
        m.add("head", "capbrim", -8, -15, 9, 16, 1, 6)
        m.add("head", "hair", -9, -13, -6, 1, 2, 15, mirror=True)
        m.add("head", "hair", -9, -13, 8, 18, 4, 1)
        m.add("head", "shades", -9, -12, -9, 18, 3, 1)
        m.add("head", "shadesarm", -9, -12, -8, 1, 1, 8, mirror=True)
    else:
        m.add("head", "hair", -9, -19, -9, 18, 5, 18)
        m.add("head", "hair", -9, -14, -6, 1, 3, 15, mirror=True)
        m.add("head", "hair", -9, -14, 8, 18, 6, 1)
        m.add("head", "hair", -9, -20, -10, 7, 5, 4)
        m.add("head", "hair", -2, -21, -10, 8, 5, 5)
        m.add("head", "hair", 6, -19, -10, 3, 5, 4)
        m.add("head", "hair", -6, -21, -6, 10, 3, 10)
        m.add("head", "hair", -3, -20, 4, 10, 2, 4)
    m.add("head", "beard", -9, -8, -9, 2, 8, 9, mirror=True)
    m.add("head", "beard", -9, -12, -4, 1, 4, 3, mirror=True)
    m.add("head", "beard", -6, -3, -9, 12, 4, 6)
    m.add("head", "beard", -4, -5.5, -9, 8, 1, 1)
    if outfit == "heist":
        m.add("head", "bandana", -10, -9, -10, 20, 10, 10)
        m.add("head", "bandana", -3, -8, 0, 6, 3, 10)
        m.add("head", "bandanaknot", -2, -8, 9, 4, 4, 2)
    # body
    m.add("body", "torso", -8, 0, -4, 16, 24, 8)
    m.add("body", "neck", -3, -1, -3, 6, 2, 6)
    m.add("body", "chest", -7, 2, -5, 14, 8, 1)
    if outfit in ("everyday", "casual", "heist"):
        m.add("body", "belt", -9, 19, -5, 18, 2, 10)
        m.add("body", "waist", -9, 21, -5, 18, 3, 10)
        m.add("body", "waist", -1, 24, -4.5, 2, 3, 9)
    else:
        m.add("body", "waist", -9, 20, -5, 18, 4, 10)
    # arms (right then mirrored)
    m.limb("right_arm", "arm", -6, -4, -4, 8, 24, 8)
    if outfit in ("heist", "casual"):
        m.limb("right_arm", "sleeve", -7, -5, -5, 10, 11, 10)
    else:
        m.limb("right_arm", "shoulder", -7, -5, -5, 9, 5, 10)
    if outfit == "everyday":
        m.add("left_arm", "band", -3, 16, -5, 10, 2, 10)
    if outfit == "swim":
        m.add("right_arm", "watch", -7, 16, -5, 10, 2, 10)
    # legs
    short = {"everyday": 11, "heist": 20, "swim": 9, "casual": 10}[outfit]
    m.limb("right_leg", "shorts", -5.5, 0, -5, 9, short, 10)
    m.limb("right_leg", "pocket", -6.5, 3 if short < 15 else 8, -3, 1, 6, 5)
    m.limb("right_leg", "leg", -4, 0, -4, 8, 21, 8)
    if outfit == "swim":
        m.limb("right_leg", "shoe", -4.5, 22, -5, 9, 2, 9)
    else:
        m.limb("right_leg", "shoe", -4.5, 20, -6, 9, 4, 11)
    return m


def lucia_model(outfit):
    m = Model("lucia_" + outfit, True)
    m.add("head", "head", -8, -16, -8, 16, 16, 16)
    m.add("head", "ear", -9, -10, -1, 1, 4, 2, mirror=True)
    # Big curly hair that frames the face instead of boxing it: a centre parted cap, strands over the
    # forehead edges and temples, side volume set back off the cheeks and falling past the jaw, a soft
    # domed crown and a back cascade that tapers towards the neck. Curls are many small staggered cubes
    # on half unit offsets inside an ellipsoid envelope, so the silhouette reads round.
    m.add("head", "hair", -8.5, -17.5, -8.5, 17, 3, 17)          # cap
    m.add("head", "hair", -7, -18.5, -7, 14, 1, 14)              # dome step
    m.add("head", "hair", -9.5, -15, -5, 2, 15, 13, mirror=True)  # side shell, set back from the cheeks
    m.add("head", "hair", -8.5, -14.5, 6, 17, 12, 3)              # back of the head
    m.add("head", "hair", -7.5, -3, 7, 15, 5, 3)                  # back cascade, tapering
    m.add("head", "hair", -6, 2, 7, 12, 4, 3)
    m.add("head", "hair", -4.5, 6, 7.5, 9, 3, 2)
    # strands falling over the forehead edges and down the temples (centre left clear for the part)
    m.add("head", "fringe", -8.5, -15, -9, 3, 4, 1, mirror=True)
    m.add("head", "fringe", -9.5, -13, -9, 2, 6, 2, mirror=True)
    m.add("head", "fringe", -6, -15.5, -9.5, 2, 2, 1, mirror=True)
    m.add("head", "fringe", -10, -8, -7, 2, 5, 2, mirror=True)
    # locks falling along the jaw line past the chin, narrowing the visible face to an oval
    m.add("head", "fringe", -9, -10, -8.5, 2, 9, 1, mirror=True)
    m.add("head", "fringe", -9.5, -2, -8, 2, 3, 2, mirror=True)
    m.add("head", "fringe", -8.5, -7, -9, 1, 4, 1, mirror=True)
    rng = random.Random(4243)
    curls = []
    tries = 0
    while len(curls) < 150 and tries < 20000:
        tries += 1
        s = rng.choice((2, 2, 3, 3, 3, 4))
        zone = rng.random()
        if zone < 0.2:      # crown dome
            cx = rng.uniform(-8, 8); cy = rng.uniform(-20, -17); cz = rng.uniform(-6, 8)
        elif zone < 0.55:   # sides, from the temples down past the jaw
            cx = rng.choice((-1, 1)) * rng.uniform(9, 11); cy = rng.uniform(-16, 4); cz = rng.uniform(-4, 9)
        else:               # back, tapering down to the shoulder blades
            cy = rng.uniform(-16, 10)
            half = 10.5 if cy < -2 else max(4.0, 10.5 - (cy + 2) * 0.55)
            cx = rng.uniform(-half, half); cz = rng.uniform(8, 11)
        # ellipsoid envelope around the head for a round outline
        ex, ey, ez = cx / 12.0, (cy + 8) / 13.5, (cz - 1) / 11.5
        if ey < 0.7 and ex * ex + ey * ey + ez * ez > 1.0:
            continue
        x, y, z = cx - s / 2, cy - s / 2, cz - s / 2
        x, y, z = round(x * 2) / 2, round(y * 2) / 2, round(z * 2) / 2
        # keep the face, the cheeks and the hoops clear
        if z < -4.5 and y > -16 and abs(cx) < 9.5:
            continue
        if y + s > -7 and y < 1 and z < 2 and z + s > -4 and abs(cx) > 8.5:
            continue
        # below the jaw, stay behind the shoulders so arms never clip through the hair
        if y + s > 1 and z < 3.5:
            continue
        curls.append((x, y, z, s))
    for (x, y, z, s) in curls:
        m.add("head", "curl", x, y, z, s, s, s)
    # gold hoops: a square ring under each ear
    for sx in (-1, 1):
        xx = -10.5 if sx < 0 else 9.5
        m.add("head", "hoop", xx, -6, -3, 1, 1, 4)
        m.add("head", "hoop", xx, -1, -3, 1, 1, 4)
        m.add("head", "hoop", xx, -5, -3, 1, 4, 1)
        m.add("head", "hoop", xx, -5, 0, 1, 4, 1)
    if outfit == "heist":
        m.add("head", "bandana", -9, -9, -9, 18, 9, 9)
        m.add("head", "bandanaknot", -2, -8, 8, 4, 4, 2)
    if outfit == "swim":
        m.add("head", "shades", -9, -12, -9, 18, 3, 1)
    # body: slimmer, waist taper, crop top
    m.add("body", "chest", -7.5, 0, -3.5, 15, 9, 7)
    m.add("body", "bust", -6, 3, -4.5, 12, 5, 1)
    m.add("body", "waist", -6.5, 9, -3, 13, 7, 6)
    m.add("body", "hips", -7.5, 16, -3.5, 15, 8, 7)
    if outfit != "swim":
        m.add("body", "hips", -1, 24, -3.5, 2, 3, 7)
    m.add("body", "neck", -2.5, -1, -2.5, 5, 2, 5)
    m.limb("right_arm", "arm", -4, -4, -3, 6, 24, 6)
    if outfit == "jumpsuit":
        m.limb("right_arm", "sleeve", -4.5, -4.5, -3.5, 7, 9, 7)
    m.limb("right_arm", "bangle", -4.5, 16, -3.5, 7, 1, 7)
    short = {"everyday": 6, "heist": 17, "swim": 0, "jumpsuit": 18}[outfit]
    if short:
        m.limb("right_leg", "shorts", -4.5, 0, -4, 8, short, 8)
    m.limb("right_leg", "leg", -3.5, 0, -3.5, 7, 21, 7)
    if outfit == "everyday":
        m.limb("right_leg", "shoe", -4.5, 15, -6, 8, 9, 10)
    elif outfit == "swim":
        m.limb("right_leg", "shoe", -4, 23, -5, 7, 1, 8)
    else:
        m.limb("right_leg", "shoe", -4.5, 18, -6, 8, 6, 10)
    return m


# ------------------------------------------------------------------ materials (3D, part-local hu)
def camo(X, Y, Z):
    n = vnoise(X, Y, Z, 3.5, 11)
    cs = [rgb("6b6a45"), rgb("4d4b30"), rgb("8a8156"), rgb("3a3a26")]
    return cs[min(3, int(n * 4.2))]


def jason_mat(outfit, part, tag, X, Y, Z, face, F):
    c = JASON_C
    skin = c["skin"]
    if tag == "head":
        if face == "front":
            i = int((X + 8) * 2); j = int((Y + 16) * 2)
            return F[max(0, min(31, j))][max(0, min(31, i))]
        if Y < -11 or face in ("top", "back"):
            return c["hair"] if (face != "back" or Y < -6) else lerp(skin, c["beard"], 0.3)
        if face in ("right", "left") and Y > -8 and Z < 0:
            return lerp(skin, c["beard"], 0.5 + 0.3 * hnoise(X, Y, Z))
        return skin
    if tag in ("nose", "ear", "neck"):
        if tag == "nose" and face == "bottom":
            return mul(skin, 0.7)
        if tag == "neck":
            return lerp(skin, c["beard"], 0.25) if Y < 0 else mul(skin, 0.9)
        return skin
    if tag == "hair":
        n = vnoise(X, Y, Z, 1.6, 5)
        col = lerp(c["hair"], c["hairhi"], max(0, n - 0.45) * 1.6)
        if face == "top" and hnoise(int(X * 2), int(Z * 2), 1) < 0.25:
            col = mul(col, 1.18)
        return col
    if tag == "beard":
        n = hnoise(int(X * 2), int(Y * 2), int(Z * 2))
        return lerp(c["beard"], mul(c["beard"], 1.5), n * 0.55) if n > 0.2 else mul(c["beard"], 0.75)
    if tag in ("cap", "capbrim"):
        base = rgb("2c2c30")
        if tag == "cap" and face == "back" and -16 < Y < -14 and abs(X) < 3:
            return rgb("101012")
        return mul(base, 1.0 + 0.08 * vnoise(X, Y, Z, 2, 3))
    if tag == "shades":
        if face == "front" and -11.5 < Y < -9.5 and (1.5 < abs(X) < 7.5):
            g = rgb("2fd27a") if Y < -10.5 else rgb("1a8a50")
            if abs(X) > 5.5 and Y < -10.8:
                g = rgb("c8ffe0")
            return g
        return rgb("161616")
    if tag == "shadesarm":
        return rgb("161616")
    if tag in ("bandana", "bandanaknot"):
        base = rgb("161616")
        if (int(X * 2) % 5 == 0 and int(Y * 2) % 4 == 0) or (int(X * 2 + 2) % 5 == 0 and int(Y * 2 + 2) % 4 == 0):
            return rgb("d8d8d8")
        return base
    if tag in ("torso", "chest"):
        return jason_top(outfit, X, Y, Z, face)
    if tag == "belt":
        if face == "front" and abs(X) < 1.5 and 19.3 < Y < 20.7:
            return rgb("c8c8c8")
        return rgb("4e3624")
    if tag == "waist":
        return jason_bottom(outfit, X, Y, Z)
    if tag in ("arm", "shoulder"):
        return jason_arm(outfit, part, X, Y, Z, face)
    if tag == "sleeve":
        return rgb("8c8c8a") if outfit == "heist" else rgb("ece9e2")
    if tag == "band":
        return rgb("1e1e1e") if Y > 16.4 else rgb("3a3a3a")
    if tag == "watch":
        return rgb("e0b84a") if face == "front" and abs(Z) < 1 else rgb("202020")
    if tag in ("shorts", "pocket"):
        col = jason_bottom(outfit, X, Y, Z)
        if tag == "pocket":
            if Y < 4.2 or (Y > 8.6 and outfit == "heist"):
                col = mul(col, 0.78)
            if face != "back" and (Y < 3.6):
                col = mul(col, 0.85)
        else:
            if Y < 1:
                col = mul(col, 0.9)
        return col
    if tag == "leg":
        if Y > 18.5 and outfit == "casual":
            return rgb("f2f2f2")
        if hnoise(int(X * 2), int(Y * 2), int(Z * 2)) < 0.06:
            return mul(skin, 0.86)
        return skin
    if tag == "shoe":
        if outfit == "heist":
            return rgb("141414") if Y < 23 else rgb("2a2a2a")
        if outfit == "swim":
            return rgb("2a2a2e") if face != "top" else rgb("3a3a40")
        if Y > 23:
            return rgb("d8d8d8")
        if face == "top" and abs(X) < 1.5:
            return rgb("f8f8f8") if int(Z * 2) % 2 == 0 else rgb("b0b0b0")
        return rgb("ececec") if outfit == "casual" else rgb("c8c8c8")
    return skin


def jason_top(outfit, X, Y, Z, face):
    c = JASON_C; skin = c["skin"]
    chain = rgb("e3b84c")
    if outfit in ("everyday", "swim") and face == "front":
        cy = 1.6 + 0.07 * X * X
        if abs(X) < 5.2 and abs(Y - cy) < 0.4:
            return chain
        if abs(X) < 0.8 and cy + 0.4 < Y < 4.6:
            return rgb("f0cc60")
    if outfit == "swim":
        if face in ("front",) and (abs(X) < 0.3 and 8 < Y < 18):
            return mul(skin, 0.9)
        if face == "front" and 9 < Y < 18 and abs(abs(X) - 2.8) < 0.3 and int(Y) % 3 == 0:
            return mul(skin, 0.88)
        if face == "front" and 3 < Y < 5.5 and 3.5 < abs(X) < 6.5 and hnoise(int(X * 2), int(Y * 2), 4) < 0.5:
            return c["tattoo"]
        return skin
    if outfit == "everyday":
        tank = rgb("eeece6")
        scoop = (face == "front" and Y < 3.2 - 0.06 * X * X and abs(X) < 5) or (face == "back" and Y < 1.2 and abs(X) < 4)
        arm = abs(X) > 6.3 - (0 if Y > 8 else 0) and Y < 8 - 2.5 * max(0, abs(X) - 6.3)
        strapgap = Y < 4.5 and abs(X) > 6.4
        if scoop or strapgap or (abs(X) > 6.3 and Y < 7):
            return skin
        col = tank
        if face in ("front", "back") and int(X * 2) % 2 == 0:
            col = mul(tank, 0.94)
        if Y > 17:
            col = mul(col, 0.95)
        return col
    if outfit == "heist":
        tee = rgb("8c8c8a")
        if face == "front" and Y < 1.4 - 0.05 * X * X and abs(X) < 3.5:
            return skin
        return mul(tee, 0.97 + 0.06 * vnoise(X, Y, Z, 2.5, 8))
    tee = rgb("ece9e2")
    if face == "front" and Y < 1.6 - 0.05 * X * X and abs(X) < 3.5:
        return skin
    if face == "front" and 3 < Y < 5 and -5.5 < X < -2.5:
        return rgb("5a7a9a")
    return tee


def jason_bottom(outfit, X, Y, Z):
    if outfit == "everyday":
        # olive camo: green base with dark and light blotches
        base = lerp(rgb("4a5a2a"), rgb("566634"), vnoise(X, Y, Z, 1.5, 21))
        if vnoise(X, Y, Z, 3.2, 22) > 0.62:
            base = rgb("323d1e")
        elif vnoise(X + 7, Y, Z, 2.8, 23) > 0.66:
            base = rgb("71804a")
        if hnoise(int(X * 2), int(Y * 2), int(Z * 2)) < 0.08:
            base = mul(base, 0.88)
        return base
    if outfit == "heist":
        return mul(rgb("26262a"), 0.95 + 0.1 * vnoise(X, Y, Z, 2, 2))
    if outfit == "swim":
        return camo(X, Y, Z)
    return lerp(rgb("a89068"), rgb("c4ac80"), vnoise(X, Y, Z, 3, 6))


def jason_arm(outfit, part, X, Y, Z, face):
    c = JASON_C; skin = c["skin"]
    col = skin
    if outfit == "heist" and Y > 15.5:
        return rgb("1c1c1c")
    if Y > 16 and face == "bottom":
        return mul(skin, 0.85)
    # ink: forearm band and upper-arm sleeve pieces, heavier on the left arm (cover art)
    # linework: contour lines of a smooth noise field read as inked flash art rather than blotches
    t = lerp(skin, c["tattoo"], 0.55)
    if part == "left_arm":
        n = vnoise(X, Y, Z, 3.2, 31)
        if 2 < Y < 13 and (abs(n - 0.5) < 0.035 or abs(n - 0.72) < 0.03):
            col = t
        if 12.5 < Y < 13.2:
            col = t
    else:
        n = vnoise(X, Y, Z, 3.0, 37)
        if 0 < Y < 7 and face in ("right", "front") and abs(n - 0.55) < 0.035:
            col = t
    if Y > 16:
        col = skin if col == skin else col
        if face == "front" and Y > 18.5 and int(X * 2) % 3 == 0:
            col = mul(skin, 0.9)  # knuckles
    return col


def lucia_mat(outfit, part, tag, X, Y, Z, face, F):
    c = LUCIA_C; skin = c["skin"]
    if tag == "head":
        if face == "front":
            i = int((X + 8) * 2); j = int((Y + 16) * 2)
            return F[max(0, min(31, j))][max(0, min(31, i))]
        if Y < -10 or face in ("top", "back"):
            return c["hair"]
        return skin
    if tag in ("ear", "neck"):
        return skin if tag == "ear" else mul(skin, 0.92)
    if tag == "hair" and abs(X) < 0.6 and Z < 2 and (face == "top" or (face == "front" and Y < -15)):
        return lerp(skin, c["hair"], 0.55)      # centre part
    if tag in ("hair", "curl", "fringe"):
        n = vnoise(X, Y, Z, 1.2, 9)
        col = lerp(c["hair"], c["hairhi"], max(0, n - 0.4) * 1.5)
        h = hnoise(int(X * 2), int(Y * 2), int(Z * 2))
        if h < 0.12:
            col = mul(col, 1.35)
        elif h > 0.9:
            col = mul(col, 0.7)
        return col
    if tag == "hoop":
        return c["gold"] if face != "bottom" else mul(c["gold"], 0.7)
    if tag in ("bandana", "bandanaknot"):
        base = rgb("b02a2a")
        p = vnoise(X * 1.7, Y * 1.7, Z, 1.0, 13)
        if p > 0.68:
            return rgb("eadbd0")
        if p < 0.22:
            return rgb("1a1010")
        return base
    if tag == "shades":
        if face == "front" and -11.5 < Y < -9.5 and (1.5 < abs(X) < 7.5):
            return rgb("8a6ab0") if (abs(X) > 5.5 and Y < -10.8) else rgb("2a1a3a")
        return rgb("3a2230")
    if tag in ("chest", "bust", "waist", "hips"):
        return lucia_body(outfit, tag, X, Y, Z, face)
    if tag == "arm":
        if outfit == "heist" and Y > 15.5:
            return rgb("1a1a1a")
        if part == "left_arm" and face == "front" and 9 < Y < 11 and int(X * 2) % 2 == 0 and abs(X) < 2:
            return c["tattoo"]
        return skin
    if tag == "sleeve":
        return rgb("e86a2a")
    if tag == "bangle":
        return c["gold"]
    if tag == "shorts":
        if outfit == "jumpsuit":
            return rgb("e86a2a")
        dn = rgb("8eb0d8") if outfit == "everyday" else rgb("2a3448")
        col = lerp(dn, mul(dn, 1.18), vnoise(X, Y, Z, 1.8, 17))
        if outfit == "everyday" and Y > 5 and hnoise(int(X * 2), int(Z * 2), 3) < 0.55:
            col = rgb("e4ecf4")   # frayed hem
        if face == "front" and abs(X + 0.5) < 0.3:
            col = mul(col, 0.85)
        return col
    if tag == "leg":
        if outfit == "swim" and Y > 20:
            return skin
        return skin
    if tag == "shoe":
        if outfit == "everyday":
            if Y > 23:
                return rgb("f2f2f2")
            if face == "front" and Y > 20.5:
                return rgb("f2f2f2")
            if face == "top" or (face == "front" and Y < 20.5 and abs(X + 0.5) < 1.2 and int(Y * 2) % 2 == 0):
                return rgb("f0f0f0") if face == "front" else rgb("2a2a2e")
            if face in ("right", "left") and 18 < Y < 19.5:
                return rgb("f2f2f2")
            return rgb("26262a")
        if outfit == "swim":
            return rgb("e8559a")
        if outfit == "jumpsuit":
            return rgb("f2f2f2") if Y < 23 else rgb("c8c8c8")
        return rgb("161616") if Y < 23 else rgb("2a2a2a")
    return skin


def lucia_body(outfit, tag, X, Y, Z, face):
    c = LUCIA_C; skin = c["skin"]; black = rgb("1e1c20")
    if outfit == "everyday":
        if tag in ("chest", "bust"):
            if face == "front" and Y < 2.5 - 0.05 * X * X and abs(X) < 5:
                return skin
            if Y < 2.0 and abs(X) > 4.5 and abs(X) < 6.0:
                return black
            if Y < 2.0 and abs(X) >= 4.5:
                return skin
            if face == "front" and tag == "chest" and abs(X) < 0.6 and 1.5 < Y < 3:
                return c["gold"]
            return mul(black, 1.0 + 0.25 * vnoise(X, Y, Z, 1.5, 3))
        if tag == "waist":
            if face == "front" and abs(X) < 0.5 and 12 < Y < 13:
                return mul(skin, 0.7)
            return skin
        dn = rgb("8eb0d8")
        col = lerp(dn, mul(dn, 1.18), vnoise(X, Y, Z, 1.8, 17))
        if 16 < Y < 17.5:
            col = mul(dn, 0.85)
        if face == "front" and abs(X) < 0.4 and Y > 18:
            col = mul(col, 0.85)
        if face == "front" and abs(X) < 0.8 and 16.6 < Y < 17.4:
            col = rgb("d0b060")
        return col
    if outfit == "heist":
        if tag in ("chest", "bust", "waist"):
            if face == "front" and Y < 2.5 - 0.05 * X * X and abs(X) < 4.5:
                return skin
            if Y < 2 and abs(X) > 5.5:
                return skin
            return mul(black, 1.0 + 0.25 * vnoise(X, Y, Z, 1.5, 3))
        return rgb("2a3448")
    if outfit == "swim":
        pink = rgb("e8559a")
        if tag == "bust":
            return pink
        if tag == "chest":
            if 3 < Y < 8 and abs(X) < 6.5 and face == "front":
                return pink
            if 6.5 < Y < 7.5:
                return pink
            if Y < 3 and abs(abs(X) - 3.5) < 0.6 and face in ("front", "back", "top"):
                return pink
            return skin
        if tag == "hips":
            if (face == "front" and (Y > 18.5 - 0 and abs(X) < 4.5 - (Y - 18.5) * 0.4)) or (16.5 < Y < 18.2) or (face == "back" and Y > 17):
                return pink
            return skin
        return skin
    orange = rgb("e86a2a")
    if face == "front" and tag == "chest" and Y < 3 - abs(X) * 0.5:
        return skin
    if face == "back" and tag == "chest" and 3 < Y < 5 and abs(X) < 5 and (int(X * 2) + int(Y * 2)) % 2 == 0:
        return rgb("f0f0f0")
    return mul(orange, 0.96 + 0.08 * vnoise(X, Y, Z, 2, 5))


# ------------------------------------------------------------------ painting
FACE_LIGHT = {"top": 1.05, "front": 1.0, "right": 0.93, "left": 0.93, "back": 0.88, "bottom": 0.78}


def face_rects(c):
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def point(c, face, a, b):
    """Texel centre (a, b) in hu inside a face -> 3D point and outward normal."""
    if face == "front":
        return (c.x0 + a, c.y0 + b, c.z0), (0, 0, -1)
    if face == "back":
        return (c.x1 - a, c.y0 + b, c.z1), (0, 0, 1)
    if face == "right":
        return (c.x0, c.y0 + b, c.z1 - a), (-1, 0, 0)
    if face == "left":
        return (c.x1, c.y0 + b, c.z0 + a), (1, 0, 0)
    if face == "top":
        return (c.x0 + a, c.y0, c.z1 - b), (0, -1, 0)
    return (c.x0 + a, c.y1, c.z0 + b), (0, 1, 0)


def pack(cubes):
    order = sorted(cubes, key=lambda c: -(c.d + c.h))
    x = y = row_h = 0
    for c in order:
        w, h = 2 * (c.d + c.w), c.d + c.h
        if x + w > GRID:
            x = 0; y += row_h; row_h = 0
        c.u, c.v = x, y
        x += w
        row_h = max(row_h, h)
    if y + row_h > GRID:
        raise SystemExit("texture atlas overflow: %d" % (y + row_h))


def paint(model, matfn, F):
    img = Image.new("RGBA", (IMG, IMG), (0, 0, 0, 0))
    px = img.load()
    by_part = {}
    for c in model.cubes:
        by_part.setdefault(c.part, []).append(c)
    for c in model.cubes:
        others = [o for o in by_part[c.part] if o is not c]
        for face, (fu, fv, fw, fh) in face_rects(c).items():
            for j in range(fh * S):
                for i in range(fw * S):
                    a = (i + 0.5) / S; b = (j + 0.5) / S
                    (X, Y, Z), n = point(c, face, a, b)
                    col = matfn(c.part, c.tag, X, Y, Z, face, F)
                    f = FACE_LIGHT[face]
                    # soft edge occlusion
                    e = min(a, b, fw - a, fh - b)
                    f *= 1 - 0.09 * max(0.0, 1 - e / 1.4)
                    # contact occlusion against neighbouring cubes of the same part
                    if c.ao:
                        occ = 0
                        for dist, wgt in ((0.6, 0.55), (1.6, 0.3), (3.0, 0.15)):
                            P = (X + n[0] * dist, Y + n[1] * dist, Z + n[2] * dist)
                            for o in others:
                                if o.inside(*P):
                                    occ += wgt
                                    break
                        f *= 1 - 0.32 * occ
                    # soft highlight on the upper part of side faces, darker towards the bottom of limbs
                    if face in ("front", "right", "left", "back"):
                        f *= 1.04 - 0.07 * (b / max(1, fh))
                    if c.part == "body" and Y < 1.5 and c.tag != "neck":
                        f *= 0.86 + 0.09 * max(0.0, Y)
                    f *= 1 + (hnoise(fu * S + i, fv * S + j, 77) - 0.5) * 0.05
                    px[fu * S + i, fv * S + j] = mul(col, f) + (255,)
    return img


# ------------------------------------------------------------------ java
def java_model(model):
    lines = []
    lines.append("\tpublic static LayerDefinition %s() {" % model.name)
    lines.append("\t\tMeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, %s);" % ("true" if model.slim else "false"))
    lines.append("\t\tPartDefinition root = mesh.getRoot();")
    lines.append("\t\troot.clearRecursively();")
    parts = {}
    for c in model.cubes:
        parts.setdefault(c.part, []).append(c)
    for part, cubes in parts.items():
        lines.append("\t\troot.getChild(\"%s\").addOrReplaceChild(\"hd\", CubeListBuilder.create()" % part)
        for c in cubes:
            lines.append("\t\t\t\t.texOffs(%d, %d).addBox(%sF, %sF, %sF, %d.0F, %d.0F, %d.0F)" % (c.u, c.v, fmt(c.x0), fmt(c.y0), fmt(c.z0), c.w, c.h, c.d))
        lines.append("\t\t\t\t, HALF);")
    lines.append("\t\treturn LayerDefinition.create(mesh, %d, %d);" % (GRID, GRID))
    lines.append("\t}")
    return "\n".join(lines)


def fmt(v):
    return ("%.2f" % v).rstrip("0").rstrip(".") if v != int(v) else "%d.0" % int(v)


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    models = []
    jf = jason_face(JASON_C)
    lf = lucia_face(LUCIA_C)
    for o in ("everyday", "heist", "swim", "casual"):
        m = jason_model(o)
        pack(m.cubes)
        paint(m, lambda part, tag, X, Y, Z, face, F, o=o: jason_mat(o, part, tag, X, Y, Z, face, F), jf).save(os.path.join(TEX_DIR, m.name + ".png"))
        models.append(m)
        print(m.name, len(m.cubes), "cubes")
    for o in ("everyday", "heist", "swim", "jumpsuit"):
        m = lucia_model(o)
        pack(m.cubes)
        paint(m, lambda part, tag, X, Y, Z, face, F, o=o: lucia_mat(o, part, tag, X, Y, Z, face, F), lf).save(os.path.join(TEX_DIR, m.name + ".png"))
        models.append(m)
        print(m.name, len(m.cubes), "cubes")
    Image.new("RGBA", (64, 64), (0, 0, 0, 0)).save(os.path.join(TEX_DIR, "blank.png"))
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as f:
        f.write("package dev.leonida.character.model;\n\n")
        f.write("import net.minecraft.client.model.geom.PartPose;\n")
        f.write("import net.minecraft.client.model.geom.builders.CubeDeformation;\n")
        f.write("import net.minecraft.client.model.geom.builders.CubeListBuilder;\n")
        f.write("import net.minecraft.client.model.geom.builders.LayerDefinition;\n")
        f.write("import net.minecraft.client.model.geom.builders.MeshDefinition;\n")
        f.write("import net.minecraft.client.model.geom.builders.PartDefinition;\n")
        f.write("import net.minecraft.client.model.player.PlayerModel;\n\n")
        f.write("/** High detail Jason and Lucia meshes. Generated by tools/character/hdmodel.py; do not edit by hand. */\n")
        f.write("public final class HdModels {\n")
        f.write("\tprivate HdModels() {}\n\n")
        f.write("\t/** Detail cubes are authored in half units inside a child scaled by one half. */\n")
        f.write("\tprivate static final PartPose HALF = PartPose.ZERO.scaled(0.5F);\n\n")
        f.write("\n\n".join(java_model(m) for m in models))
        f.write("\n}\n")
    print("ok", len(models), "models")


if __name__ == "__main__":
    main()
