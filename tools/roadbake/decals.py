"""Generate the angled road marking (decal) blocks: textures, models and blockstates.

Each decal block draws one marking at any angle on top of asphalt, so diagonal and curved roads
get straight, continuous lines instead of staircases. A block state picks a line angle phi in
[0, 90) degrees (16 steps of 5.625), a quarter turn r (model y rotation) and a signed
perpendicular offset of the line from the block centre (1/16 m steps, or 0.05 m pattern phase for
zebra). The Java side (RoadPalette.decal) computes the state from the exact road geometry per
block, so neighbouring blocks join into one straight line.

Top texture convention: u = +x (east), v = +z (south). Line direction t = (cos phi, sin phi) in
(x, z), normal n = (-sin phi, cos phi). A model rotated by y = 90 r adds 90 r degrees to phi.
  python3 tools/roadbake/decals.py
"""
import json, math, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'leonida')
LOOK_TEX = os.path.join(ROOT, 'textures', 'block', 'look', 'asphalt.png')
RES = 64
SS = 4
ANGLES = 16
WHITE = (214, 214, 212)
YELLOW = (238, 196, 68)

# name: (colour, kind, param, offsets, step)
TYPES = {
    'line': (WHITE, 'band', [(-0.075, 0.075)], 14, 1 / 16),
    'line_yellow': (YELLOW, 'band', [(-0.075, 0.075)], 14, 1 / 16),
    'line_yellow2': (YELLOW, 'band', [(-0.15, -0.05), (0.05, 0.15)], 15, 1 / 16),
    'stop_bar': (WHITE, 'band', [(-0.25, 0.25)], 17, 1 / 16),
    'zebra': (WHITE, 'zebra', (1.2, 0.6), 24, 0.05),
}


def asphalt_base():
    im = Image.open(LOOK_TEX).convert('RGB')
    return im.resize((RES, RES), Image.LANCZOS)


def coverage(kind, param, phi, d, x, z):
    # x, z in metres relative to block centre
    w = -math.sin(phi) * x + math.cos(phi) * z - d
    if kind == 'band':
        return any(a <= w <= b for a, b in param)
    period, on = param
    return ((w % period) + period) % period < on


def make_texture(base, colour, kind, param, phi, d, rng):
    im = base.copy()
    px = im.load()
    for j in range(RES):
        for i in range(RES):
            hits = 0
            for sj in range(SS):
                for si in range(SS):
                    x = (i + (si + 0.5) / SS) / RES - 0.5
                    z = (j + (sj + 0.5) / SS) / RES - 0.5
                    hits += coverage(kind, param, phi, d, x, z)
            if hits:
                a = hits / (SS * SS)
                # worn paint: a little speckle so lines are not flat plastic
                wear = 0.86 + 0.14 * rng.random()
                if rng.random() < 0.04:
                    wear *= 0.6
                a *= wear
                b = px[i, j]
                px[i, j] = tuple(int(b[k] * (1 - a) + colour[k] * a) for k in range(3))
    return im


def main():
    base = asphalt_base()
    rng = random.Random(1031)
    tex_dir = os.path.join(ROOT, 'textures', 'block', 'road', 'decal')
    mod_dir = os.path.join(ROOT, 'models', 'block', 'road', 'decal')
    bs_dir = os.path.join(ROOT, 'blockstates', 'road')
    for d in (tex_dir, mod_dir, bs_dir):
        os.makedirs(d, exist_ok=True)
    for name, (colour, kind, param, offsets, step) in TYPES.items():
        variants = {}
        for a in range(ANGLES):
            phi = math.radians(a * 90 / ANGLES)
            for o in range(offsets):
                tname = f'{name}_{a}_{o}'
                make_texture(base, colour, kind, param, phi, o * step, rng).save(os.path.join(tex_dir, tname + '.png'), optimize=True)
                tex = f'leonida:block/road/decal/{tname}'
                side = 'leonida:block/look/asphalt'
                full = {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'top': tex, 'side': side, 'bottom': side}}
                slab = {'parent': 'minecraft:block/slab', 'textures': {'top': tex, 'side': side, 'bottom': side}}
                json.dump(full, open(os.path.join(mod_dir, tname + '.json'), 'w'))
                json.dump(slab, open(os.path.join(mod_dir, tname + '_slab.json'), 'w'))
                for r in range(4):
                    for half in (False, True):
                        key = f'a={a},half={str(half).lower()},o={o},r={r}'
                        v = {'model': f'leonida:block/road/decal/{tname}' + ('_slab' if half else '')}
                        if r:
                            v['y'] = 90 * r
                        variants[key] = v
        json.dump({'variants': variants}, open(os.path.join(bs_dir, f'decal_{name}.json'), 'w'), separators=(',', ':'))
        print(name, len(variants))


if __name__ == '__main__':
    main()
