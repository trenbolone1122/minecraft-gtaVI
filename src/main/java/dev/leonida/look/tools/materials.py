"""Material recipes. Each returns a texlib.Mat. Palettes are LOOK.md linear RGB."""
import zlib
import numpy as np
from texlib import (Mat, lin, hexlin, fnoise, white, blur, grid, smoothstep, band, cells, speckle, tint, mix)

HI = 128   # road, glass, stucco, sand, walls
MID = 64   # props


def sd(name):
    return zlib.crc32(name.encode()) & 0x7FFFFFFF

# ------------------------------------------------------------------ palettes (LOOK.md)
PASTEL = dict(pink=(0.98, 0.70, 0.73), mint=(0.60, 0.89, 0.79), butter=(0.99, 0.88, 0.56), sky=(0.58, 0.81, 0.96),
              lavender=(0.84, 0.73, 0.94), cream=(0.97, 0.95, 0.90), peach=(0.99, 0.75, 0.57))
ACCENT = dict(teal=(0.06, 0.60, 0.62), coral=(0.95, 0.40, 0.44), gold=(0.99, 0.77, 0.22), blue=(0.18, 0.44, 0.86),
              white=(0.97, 0.97, 0.96))
AWNING = dict(teal=(0.06, 0.60, 0.62), coral=(0.95, 0.40, 0.44), gold=(0.99, 0.77, 0.22), blue=(0.18, 0.44, 0.86),
              pink=(1.0, 0.22, 0.72))
CONDO = dict(warm=(0.95, 0.94, 0.91), cream=(0.94, 0.90, 0.81), cool=(0.87, 0.88, 0.89), aqua=(0.86, 0.92, 0.92),
             sand=(0.93, 0.87, 0.77))
EARTH = dict(sand=(0.93, 0.85, 0.70), off_white=(0.96, 0.94, 0.89), terracotta=(0.91, 0.71, 0.58),
             ochre=(0.97, 0.88, 0.62), taupe=(0.83, 0.79, 0.71), sage=(0.80, 0.89, 0.80))
GREYS = dict(grey=(0.71, 0.71, 0.69), warm=(0.79, 0.75, 0.68), blue=(0.63, 0.64, 0.67), light=(0.85, 0.84, 0.81))
METAL = dict(silver=(0.89, 0.90, 0.90), light=(0.77, 0.79, 0.81), blue_grey=(0.56, 0.66, 0.76),
             beige=(0.83, 0.79, 0.69), green_grey=(0.67, 0.73, 0.67))
GLASS = dict(teal=(0.13, 0.27, 0.31), navy=(0.08, 0.16, 0.28), graphite=(0.18, 0.19, 0.21), green=(0.14, 0.24, 0.19),
             slate=(0.24, 0.28, 0.34))
FRAME = dict(dark=(0.22, 0.24, 0.27), steel=(0.45, 0.47, 0.50), silver=(0.75, 0.76, 0.78))
NEON = dict(pink=(1.0, 0.22, 0.72), cyan=(0.15, 0.95, 1.0), violet=(0.62, 0.28, 1.0), blue=(0.25, 0.55, 1.0))
CONTAINER = dict(red=(0.45, 0.06, 0.04), blue=(0.04, 0.12, 0.35), green=(0.06, 0.24, 0.10), orange=(0.80, 0.25, 0.03),
                 white=(0.80, 0.80, 0.78), grey=(0.30, 0.32, 0.33))
WHITE_TRIM = (0.97, 0.97, 0.95)
ASPHALT = (0.045, 0.045, 0.05)
SIDEWALK = (0.42, 0.41, 0.39)
KERB = (0.56, 0.55, 0.52)
BARRIER = (0.62, 0.62, 0.60)
PAINT_WHITE = (0.80, 0.80, 0.78)
PAINT_YELLOW = (0.85, 0.55, 0.06)

# ------------------------------------------------------------------ walls


def stucco(name, col, n=HI, grimy=False, smooth=0.22):
    s = sd(name)
    c = lin(*col)
    trowel = fnoise(n, 2.6, s, kmin=2)          # broad trowel undulation
    grain = white(n, s + 1)
    fine = blur(grain, 0.7)
    sand = fnoise(n, 0.6, s + 2, kmin=20)
    v = 1 + 0.035 * trowel + 0.03 * fine + 0.012 * sand
    alb = tint(c, n) * v[..., None]
    h = 0.5 + 0.10 * trowel / 3 + 0.05 * fine
    if grimy:
        u, vv = grid(n)
        streak = fnoise(n, 2.0, s + 3, ax=0.12, ay=6.0)   # long vertical streaks
        blot = fnoise(n, 2.4, s + 4)
        dirt = np.clip(0.18 + 0.10 * streak + 0.07 * blot + 0.10 * vv, 0, 0.45)
        grime = lin(0.30, 0.27, 0.22)
        alb = mix(alb, alb * grime / max(c.max(), 0.3) * 1.6, dirt)
        alb *= (1 - 0.15 * dirt)[..., None]
    return Mat(n, alb, h, smooth=smooth, f0=10, porosity=38, relief=0.35, normal_strength=1.2)


def condo(name, col, n=HI, grimy=False):
    m = stucco(name, col, n, grimy, smooth=0.32)
    m.porosity = 25
    return m


def panel(name, col, n=HI):
    """Office cladding: two panels per block (1.6 m bays), recessed joints, faint fastener dots."""
    s = sd(name)
    c = lin(*col)
    u, v = grid(n)
    j = 1.5 / n
    joint = np.maximum(band(u, -0.01, j * 1.5, j * 0.5) + band(u, 1 - j * 1.5, 1.01, j * 0.5),
                       band(v, -0.01, j * 1.5, j * 0.5) + band(v, 1 - j * 1.5, 1.01, j * 0.5))
    joint = np.maximum(joint, band(v, 0.5 - j, 0.5 + j, j * 0.5))
    tex = fnoise(n, 1.5, s, kmin=4)
    alb = tint(c, n) * (1 + 0.025 * tex)[..., None]
    alb = mix(alb, alb * 0.45, joint)
    h = 0.75 - 0.6 * joint + 0.02 * tex
    return Mat(n, alb, h, smooth=0.45, f0=12, porosity=10, relief=0.5)


def corrugated(name, col, n=HI, ribs=5, rust=0.0):
    """Corrugated metal, vertical ribs every 0.2 m (5 per block)."""
    s = sd(name)
    c = lin(*col)
    u, v = grid(n)
    prof = 0.5 + 0.5 * np.cos(2 * np.pi * u * ribs)
    streak = fnoise(n, 2.0, s, ax=0.15, ay=5)
    alb = tint(c, n) * (1 + 0.04 * streak)[..., None]
    h = 0.15 + 0.8 * prof
    m = Mat(n, alb, h, smooth=0.55 + 0.05 * streak / 3, f0=232, porosity=0, relief=0.8, normal_strength=3.0,
            light=(-0.8, -0.3, 0.55))
    return m


def roll_up_door(name, col, n=HI):
    s = sd(name)
    c = lin(*col)
    u, v = grid(n)
    slats = 12
    f = (v * slats) % 1.0
    prof = smoothstep(0.0, 0.25, f) * (1 - smoothstep(0.85, 1.0, f))
    streak = fnoise(n, 2.0, s, ax=6, ay=0.15)
    alb = tint(c, n) * (1 + 0.03 * streak)[..., None]
    h = 0.2 + 0.7 * prof
    return Mat(n, alb, h, smooth=0.5, f0=232, relief=0.8, normal_strength=2.5)


def trim(name, col=WHITE_TRIM, n=HI):
    m = stucco(name, col, n, smooth=0.35)
    m.porosity = 20
    return m


def concrete(name, col=(0.50, 0.49, 0.46), n=HI):
    s = sd(name)
    c = lin(*col)
    big = fnoise(n, 2.2, s, kmin=1)
    mid = fnoise(n, 1.4, s + 1, kmin=6)
    pores, _ = speckle(n, 0.004, s + 2, 0.4, 1.0)
    alb = tint(c, n) * (1 + 0.05 * big + 0.03 * mid)[..., None]
    alb *= (1 - 0.35 * pores)[..., None]
    h = 0.6 + 0.05 * mid - 0.4 * pores
    return Mat(n, alb, h, smooth=0.25, f0=10, porosity=45, relief=0.4)

# ------------------------------------------------------------------ glass


def _mullion_mask(n, w_px, vertical=True, horizontal=False):
    u, v = grid(n)
    w = w_px / n
    m = np.zeros((n, n))
    if vertical:
        m = np.maximum(m, (u < w) | (u > 1 - w))
    if horizontal:
        m = np.maximum(m, (v < w) | (v > 1 - w))
    return m.astype(np.float64)


def curtain_glass(name, col, n=HI, frame=(0.62, 0.64, 0.66), mullion_px=3, spandrel=False):
    """Reflective tinted curtain wall pane: soft sky reflection mottling, faint distortion banding, thin mullions."""
    s = sd(name)
    c = lin(*col)
    u, v = grid(n)
    refl = fnoise(n, 3.2, s, ax=0.7, ay=1.4, kmax=6)  # broad soft reflections, slightly vertical
    wave = fnoise(n, 2.0, s + 1, ax=3.0, ay=0.3, kmax=10)  # faint horizontal roller wave bands
    sky = lin(0.50, 0.70, 0.88)
    alb = tint(c, n) * (1.0 + 0.10 * refl + 0.025 * wave)[..., None]
    # top of the pane catches more sky, bottom reflects the street (reads as reflective without shaders)
    alb = mix(alb, sky * 0.5 + c * 0.5, 0.18 + 0.16 * (1 - v))
    if spandrel:
        alb *= 0.62
        alb = mix(alb, alb * 0.8, band(v, 0.0, 0.08, 0.01))
    mull = _mullion_mask(n, mullion_px, True, spandrel)
    fr = lin(*frame)
    edge = band(u, mullion_px / n * 0.3, mullion_px / n * 0.7, 0.5 / n) + band(u, 1 - mullion_px / n * 0.7, 1 - mullion_px / n * 0.3, 0.5 / n)
    malb = tint(fr, n) * (1 + 0.15 * edge)[..., None]
    alb = mix(alb, malb, mull)
    h = 0.45 + 0.5 * mull
    smooth = np.where(mull > 0.5, 0.65, 0.96)
    f0 = np.where(mull > 0.5, 232, 11)
    return Mat(n, alb, h, smooth=smooth, f0=f0, relief=0.6, normal_strength=2.5)


def window(name, kind, n=HI):
    """Windows for styles 1..5 and shopfronts."""
    s = sd(name)
    u, v = grid(n)
    refl = fnoise(n, 3.0, s, ax=0.6, ay=1.6)
    px = 1.0 / n
    if kind == "condo":
        g = lin(0.22, 0.32, 0.40)
        refl = refl * 0.45
        fr = lin(0.85, 0.88, 0.88)
        frame = ((u < 3 * px) | (u > 1 - 3 * px)).astype(float)
    elif kind == "deco":
        g = lin(0.05, 0.07, 0.08)
        fr = lin(0.97, 0.97, 0.95)
        frame = ((u < 6 * px) | (u > 1 - 6 * px) | (v < 4 * px) | (v > 1 - 4 * px)).astype(float)
    elif kind == "house":
        g = lin(0.06, 0.07, 0.08)
        fr = lin(0.95, 0.95, 0.93)
        frame = ((u < 7 * px) | (u > 1 - 7 * px) | (v < 6 * px) | (v > 1 - 6 * px) |
                 (np.abs(v - 0.5) < 3 * px) | (np.abs(u - 0.5) < 2 * px)).astype(float)
    elif kind == "ribbon":
        g = lin(0.20, 0.25, 0.30) * 0.8
        fr = lin(0.10, 0.10, 0.11)
        frame = (u < 5 * px).astype(float)
    elif kind == "clerestory":
        g = lin(0.55, 0.62, 0.60)
        fr = lin(0.72, 0.78, 0.74)
        frame = ((u < 5 * px) | (u > 1 - 5 * px) | (v < 5 * px) | (v > 1 - 5 * px) | (np.abs(u - 0.5) < 2 * px)).astype(float)
    else:  # shopfront
        g = lin(0.14, 0.13, 0.11)
        fr = lin(0.08, 0.08, 0.09)
        frame = ((u < 3 * px) | (u > 1 - 3 * px)).astype(float)
    alb = tint(g, n) * (1 + 0.12 * refl)[..., None]
    if kind == "clerestory":
        frost = fnoise(n, 0.8, s + 2, kmin=10)
        alb *= (1 + 0.06 * frost)[..., None]
    if kind == "shopfront":
        # hint of a lit-looking interior even by day: shelves and warm back wall
        shelves = band((v * 4) % 1.0, 0.0, 0.08, 0.01) * smoothstep(0.2, 0.3, v) * (1 - smoothstep(0.9, 0.95, v))
        alb = mix(alb, lin(0.35, 0.25, 0.15), 0.15 * (1 - v))
        alb = mix(alb, alb * 0.5, shelves * 0.6)
    alb = mix(alb, alb + lin(0.30, 0.36, 0.42) * 0.25, 0.25 * (1 - v))
    alb = mix(alb, tint(fr, n), frame)
    h = 0.45 + 0.5 * frame
    smooth = np.where(frame > 0.5, 0.45, 0.94 if kind != "clerestory" else 0.6)
    f0 = np.where(frame > 0.5, 12 if kind in ("deco", "house", "condo") else 232, 11)
    m = Mat(n, alb, h, smooth=smooth, f0=f0, relief=0.6, normal_strength=2.5)
    m.frame_mask = frame
    return m


def glow(name, n, mask, blinds=False):
    """Night window overlay: near-white interior light (tinted per window at render time), cutout where the frame
    is so mullions stay visible. Emissive in _s alpha."""
    s = sd(name)
    u, v = grid(n)
    # ceiling light falloff: brightest upper middle, darker floor
    lum = 0.78 + 0.22 * np.exp(-((u - 0.5) ** 2) / 0.18) * (1 - 0.55 * v)
    if blinds:
        slat = 0.82 + 0.18 * np.cos(2 * np.pi * v * 18) ** 2
        lum = lum * slat * 0.95
    else:
        # silhouettes: a dark sofa / desk line and a lamp
        furn = band(v, 0.72, 0.9, 0.01) * band(u, 0.15, 0.6, 0.01) * 0.35
        lum = lum * (1 - furn)
        lum *= 1 + 0.05 * fnoise(n, 2.0, s, kmin=2)
    alb = np.stack([lum, lum, lum], -1)
    alpha = (mask < 0.5).astype(np.float64)
    return Mat(n, alb, None, alpha=alpha, smooth=0.9, f0=11, emissive=alpha * 1.0, relief=0.0)


def clear_glass(name, n=MID):
    u, v = grid(n)
    px = 1.0 / n
    frame = ((u < 2 * px) | (u > 1 - 2 * px) | (v < 2 * px) | (v > 1 - 2 * px)).astype(float)
    alb = tint(lin(0.55, 0.72, 0.74), n)
    alb = mix(alb, tint(lin(0.70, 0.72, 0.74), n), frame)
    streak = fnoise(n, 2.0, sd(name), ax=0.3, ay=2)
    alpha = np.clip(0.22 + 0.04 * streak + frame, 0, 1)
    return Mat(n, alb, 0.5 + 0.4 * frame, alpha=alpha, smooth=np.where(frame > 0.5, 0.6, 0.97), f0=np.where(frame > 0.5, 232, 11),
               relief=0.0)


def glass_railing(name, n=MID):
    u, v = grid(n)
    px = 1.0 / n
    rail = (v < 5 * px).astype(float)
    post = ((u < 2 * px) | (u > 1 - 2 * px)).astype(float) * (1 - rail)
    alb = tint(lin(0.60, 0.78, 0.80), n)
    alb = mix(alb, tint(lin(0.80, 0.82, 0.84), n), np.maximum(rail, post))
    alpha = np.clip(0.30 + 0.06 * (1 - v) + rail + post, 0, 1)
    return Mat(n, alb, 0.5 + 0.4 * rail, alpha=alpha, smooth=0.9, f0=np.where(rail + post > 0.5, 232, 11), relief=0.0)


def frame_metal(name, col, n=HI):
    s = sd(name)
    brush = fnoise(n, 1.0, s, ax=0.05, ay=8, kmin=1)
    alb = tint(lin(*col), n) * (1 + 0.05 * brush)[..., None]
    metal = col[0] > 0.4
    return Mat(n, alb, 0.6 + 0.02 * brush, smooth=0.6 if metal else 0.45, f0=232 if metal else 14, relief=0.3)

# ------------------------------------------------------------------ deco details


def speed_line(name, col, n=MID):
    u, v = grid(n)
    px = 1.0 / n
    lines = np.zeros((n, n))
    for cy in (0.22, 0.5, 0.78):
        lines = np.maximum(lines, (np.abs(v - cy) < 4 * px).astype(float))
    alb = tint(lin(*col), n) * (1 + 0.02 * fnoise(n, 1.5, sd(name)))[..., None]
    h = 0.4 + 0.5 * lines
    return Mat(n, alb, h, alpha=lines, smooth=0.3, f0=10, porosity=30, relief=0.2)


def awning(name, col, n=HI):
    s = sd(name)
    u, v = grid(n)
    stripe = ((u * 4) % 1.0 < 0.5).astype(float)  # 4 colour + 4 white stripes per block (12.5 cm)
    c = mix(tint(lin(*col), n), tint(lin(0.93, 0.92, 0.88), n), stripe)
    weave = 0.5 + 0.25 * np.cos(2 * np.pi * u * 64) * np.cos(2 * np.pi * v * 64)
    fade = fnoise(n, 2.2, s)
    alb = c * (1 + 0.05 * (weave - 0.5) + 0.04 * fade)[..., None]
    h = 0.5 + 0.1 * weave
    return Mat(n, alb, h, smooth=0.1, f0=8, porosity=55, relief=0.3)


def neon(name, col, n=MID):
    u, v = grid(n)
    c = lin(*col) ** 2  # deeper, saturated tube colour
    core = np.exp(-np.minimum((v - 0.5) ** 2, (u - 0.5) ** 2) / 0.02)
    # saturated tube colour (the shader takes the emissive hue from the albedo), only a faint lighter core
    alb = tint(c, n) * (0.82 + 0.18 * core)[..., None]
    alb = mix(alb, np.minimum(c * 0.85 + 0.15, 1.0) * np.ones((n, n, 3)), core * 0.15)
    return Mat(n, alb, 0.5 + 0.3 * core, smooth=0.9, f0=11, emissive=1.0, relief=0.0)

# ------------------------------------------------------------------ roofs


def barrel_tile(name, base, n=HI, aged=False):
    """Clay barrel tiles: 5 columns of half-round tiles per block, 3 courses, per-tile colour."""
    s = sd(name)
    rng = np.random.default_rng(s)
    u, v = grid(n)
    cols, rows = 5, 3
    cu = u * cols
    rv = v * rows
    ci = np.floor(cu).astype(int)
    ri = np.floor(rv).astype(int)
    fu = cu - ci
    fv = rv - ri
    # alternate cap / pan tiles: caps are humps, pans valleys
    hump = np.sin(np.pi * fu)
    cap = (ci % 2 == 0)
    prof = np.where(cap, 0.45 + 0.55 * hump, 0.45 - 0.25 * hump)
    # each course overlaps the next: lower edge raised (tile thickness), slope down
    prof = prof * (0.75 + 0.25 * (1 - fv)) + 0.08 * smoothstep(0.9, 1.0, fv)
    tile_id = (ci * 7 + ri * 13) % 23
    rnd = rng.uniform(0, 1, 23)[tile_id]
    c0 = lin(0.72, 0.36, 0.22) if not aged else lin(0.58, 0.36, 0.26)
    c1 = lin(0.80, 0.50, 0.32) if not aged else lin(0.66, 0.46, 0.34)
    alb = mix(tint(c0, n), tint(c1, n), rnd)
    grain = fnoise(n, 1.2, s + 1, kmin=8)
    alb *= (1 + 0.05 * grain)[..., None]
    shadow = smoothstep(0.85, 1.0, fv) * 0.35
    alb *= (1 - shadow)[..., None]
    lightside = np.sin(np.pi * np.clip(fu - 0.15, 0, 1))  # sun from the left: lit left flank, dark right flank
    cyl = np.where(cap, 0.72 + 0.38 * lightside - 0.25 * smoothstep(0.75, 1.0, fu), 0.62 + 0.18 * hump)
    alb *= cyl[..., None]
    if aged:
        lich = np.clip(fnoise(n, 2.2, s + 2) - 1.0, 0, 1)
        alb = mix(alb, tint(lin(0.35, 0.36, 0.30), n), lich * 0.6)
        alb *= (1 - 0.12 * np.clip(fnoise(n, 2.0, s + 3, ax=0.2, ay=4), 0, 2))[..., None]
    return Mat(n, alb, prof, smooth=0.3 if not aged else 0.2, f0=10, porosity=40, relief=0.7, normal_strength=3.0,
               light=(-0.6, -0.4, 0.7))


def shingle(name, n=HI):
    s = sd(name)
    rng = np.random.default_rng(s)
    u, v = grid(n)
    rows = 6
    rv = v * rows
    ri = np.floor(rv).astype(int)
    fv = rv - ri
    off = (ri % 2) * 0.5 / 3
    cu = (u + off) * 3
    ci = np.floor(cu).astype(int)
    fu = cu - ci
    tab = (ci * 5 + ri * 11) % 17
    rnd = rng.uniform(0.8, 1.15, 17)[tab]
    gran, _ = speckle(n, 0.08, s + 1, 0.3, 0.8)
    alb = tint(lin(0.10, 0.10, 0.105), n) * rnd[..., None] * (1 + 0.6 * gran)[..., None]
    gap = (fu < 0.03).astype(float) * (fv > 0.4)
    alb *= (1 - 0.5 * gap)[..., None]
    alb *= (1 - 0.35 * smoothstep(0.85, 1.0, fv))[..., None]
    h = 0.4 + 0.4 * (1 - fv) - 0.3 * gap + 0.05 * gran
    return Mat(n, alb, h, smooth=0.2, f0=10, porosity=50, relief=0.6)


def metal_roof(name, n=HI):
    s = sd(name)
    u, v = grid(n)
    seam = np.exp(-(((u * 2.5) % 1.0 - 0.5) ** 2) / 0.002)
    streak = fnoise(n, 2.0, s, ax=0.2, ay=5)
    alb = tint(lin(0.42, 0.46, 0.49), n) * (1 + 0.05 * streak + 0.1 * seam)[..., None]
    return Mat(n, alb, 0.3 + 0.6 * seam, smooth=0.55, f0=232, relief=0.7, normal_strength=3.0)


def gravel_roof(name, n=HI):
    s = sd(name)
    m1, v1 = speckle(n, 0.05, s, 0.8, 2.2)
    base = fnoise(n, 1.0, s + 1)
    c = tint(lin(0.25, 0.24, 0.22), n) * (1 + 0.1 * base)[..., None]
    stone = mix(lin(0.18, 0.17, 0.16), lin(0.50, 0.47, 0.42), v1)
    alb = mix(c, stone, m1)
    return Mat(n, alb, 0.3 + 0.6 * m1, smooth=0.15, f0=10, porosity=60, relief=0.8)


def membrane_roof(name, n=HI):
    s = sd(name)
    u, v = grid(n)
    seam = band(v, 0.0, 2.0 / n, 0.5 / n)
    dirt = np.clip(fnoise(n, 2.4, s) * 0.5, -1, 1)
    alb = tint(lin(0.72, 0.74, 0.72), n) * (1 - 0.06 * dirt - 0.15 * seam)[..., None]
    return Mat(n, alb, 0.5 + 0.2 * seam, smooth=0.4, f0=10, porosity=5, relief=0.4)


def ac_unit(name, part, n=MID):
    s = sd(name + part)
    u, v = grid(n)
    base = lin(0.62, 0.64, 0.63)
    alb = tint(base, n)
    if part == "side":
        louv = ((v * 14) % 1.0 < 0.45) * band(u, 0.08, 0.92, 0.01) * band(v, 0.1, 0.9, 0.01)
        alb = mix(alb, alb * 0.35, louv)
        h = 0.6 - 0.4 * louv
    else:
        r = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2)
        grill = (r < 0.38) * (((r * 30) % 1.0) < 0.35)
        rim = band(r, 0.38, 0.41, 0.005)
        alb = mix(alb, alb * 0.25, grill)
        alb = mix(alb, alb * 1.05, rim)
        h = 0.6 - 0.4 * grill + 0.2 * rim
    alb *= (1 + 0.03 * fnoise(n, 2, s))[..., None]
    return Mat(n, alb, h, smooth=0.45, f0=232, relief=0.6)

# ------------------------------------------------------------------ roads


def asphalt(name, variant="fresh", n=HI):
    s = sd("asphalt_" + variant)
    big = fnoise(n, 2.4, s, kmin=1)
    mid = fnoise(n, 1.2, s + 1, kmin=6)
    stones, sv = speckle(n, 0.10, s + 2, 0.35, 1.1)
    base = lin(*ASPHALT)
    lighten = {"fresh": 1.0, "worn": 1.3, "patched": 1.15, "cracked": 1.25, "wheelpath": 1.18}[variant]
    stone_amt = {"fresh": 0.55, "worn": 0.9, "patched": 0.7, "cracked": 0.9, "wheelpath": 0.8}[variant]
    alb = tint(base * lighten, n) * (1 + 0.10 * big + 0.08 * mid)[..., None]
    stonec = mix(lin(0.06, 0.06, 0.06), lin(0.22, 0.21, 0.20), sv)
    alb = mix(alb, stonec * (lighten ** 0.5), stones * stone_amt * 0.8)
    h = 0.5 + 0.03 * mid + 0.25 * stones
    smooth = 0.22
    poro = 50
    if variant == "wheelpath":
        smooth = 0.32
        alb = mix(alb, alb * 1.05, 0.5)
    if variant == "patched":
        u, v = grid(n)
        # a smoother darker tar patch with a sealed edge, plus a sealant squiggle
        d = np.maximum(np.abs(u - 0.55) / 0.33, np.abs(v - 0.45) / 0.28)
        d += 0.08 * fnoise(n, 2.0, s + 5)
        patch = 1 - smoothstep(0.95, 1.0, d)
        edge = band(d, 0.93, 1.02, 0.01)
        alb = mix(alb, tint(base * 0.75, n) * (1 + 0.05 * mid)[..., None], patch)
        alb = mix(alb, tint(base * 0.4, n), edge * 0.9)
        squig = band(u + 0.04 * np.sin(v * 2 * np.pi * 3) + 0.03 * fnoise(n, 2.0, s + 6), 0.12, 0.15, 0.006)
        alb = mix(alb, tint(base * 0.35, n), squig)
        h = h * (1 - patch) + (0.48 + 0.01 * mid) * patch
        smooth = np.where(patch + squig > 0.5, 0.45, 0.22)
    if variant == "cracked":
        f1, f2, _ = cells(n, 9, s + 7)
        crack = 1 - smoothstep(0.0, 1.4, f2 - f1)
        crack *= np.clip(fnoise(n, 1.6, s + 8) + 0.6, 0, 1)
        alb = mix(alb, tint(base * 0.3, n), crack * 0.9)
        h = h - 0.4 * crack
    return Mat(n, alb, h, smooth=smooth, f0=10, porosity=poro, relief=0.5)


def _paint(m, mask, col, seed):
    """Paint road marking `mask` over asphalt mat m (worn: aggregate peeks through)."""
    n = m.n
    wear = np.clip(fnoise(n, 1.8, seed, kmin=3) * 0.35 + 1.05, 0, 1) * np.clip(1.15 - 0.5 * speckle(n, 0.02, seed + 3, 0.3, 0.8)[0], 0, 1)
    mask = mask * wear
    paint = tint(lin(*col), n) * (1 + 0.04 * fnoise(n, 1.5, seed + 1))[..., None]
    m.albedo = mix(m.albedo, paint, mask)
    m.height = m.height * (1 - 0.85 * mask) + (0.5 + 0.12) * mask * 0.85 + 0.0
    sm = np.broadcast_to(np.asarray(m.smooth, dtype=float), (n, n))
    m.smooth = sm * (1 - mask) + 0.45 * mask
    m.porosity = 50 * (1 - mask) + 5 * mask
    return m


def marking(name, kind, n=HI):
    m = asphalt(name, "fresh", n)
    u, v = grid(n)
    aa = 0.6 / n
    if kind == "line_white":
        mask = band(u, 0.5 - 0.075, 0.5 + 0.075, aa); col = PAINT_WHITE
    elif kind == "line_white_edge":
        mask = band(v, 0.2, 0.35, aa); col = PAINT_WHITE
    elif kind == "line_yellow":
        mask = band(u, 0.5 - 0.06, 0.5 + 0.06, aa); col = PAINT_YELLOW
    elif kind == "line_yellow_double":
        mask = band(u, 0.5 - 0.15, 0.5 - 0.05, aa) + band(u, 0.5 + 0.05, 0.5 + 0.15, aa); col = PAINT_YELLOW
    elif kind == "stop_bar":
        mask = band(v, 0.25, 0.75, aa); col = PAINT_WHITE
    elif kind == "zebra":
        mask = band(u, 0.2, 0.8, aa); col = PAINT_WHITE
    else:
        mask = _arrow(n, kind); col = PAINT_WHITE
    return _paint(m, np.clip(mask, 0, 1), col, sd(name))


def _arrow(n, kind):
    """Arrow pointing north (up in the image)."""
    u, v = grid(n)
    aa = 0.8 / n
    shaft_w = 0.07

    def head(cx, cy, dirx, diry, size=0.24):
        # triangle pointing along (dirx, diry); local coords
        px, py = u - cx, v - cy
        a = px * dirx + py * diry        # along
        b = -px * diry + py * dirx       # across
        return smoothstep(-aa, aa, a + size) * (1 - smoothstep(-aa, aa, a)) * smoothstep(-aa, aa, -a * 0.8 - np.abs(b))
    if kind == "arrow_straight":
        shaft = band(u, 0.5 - shaft_w, 0.5 + shaft_w, aa) * band(v, 0.38, 0.95, aa)
        return np.maximum(shaft, head(0.5, 0.08, 0, -1, 0.32))
    # turn arrow: stem up from the bottom, turning left with the head at the left
    stem = band(u, 0.62 - shaft_w, 0.62 + shaft_w, aa) * band(v, 0.40, 0.95, aa)
    bar = band(v, 0.40 - shaft_w, 0.40 + shaft_w, aa) * band(u, 0.30, 0.62 + shaft_w, aa)
    hd = head(0.08, 0.40, -1, 0, 0.26)
    m = np.maximum(np.maximum(stem, bar), hd)
    return m if kind == "arrow_left" else m[:, ::-1]


def sidewalk(name, jx=None, jz=None, n=HI, side=False):
    s = sd("sidewalk")
    u, v = grid(n)
    big = fnoise(n, 2.2, s, kmin=1)
    broom = fnoise(n, 1.0, s + 1, ax=6.0, ay=0.08, kmin=4)  # broom finish across the walk
    stain = np.clip(fnoise(n, 2.6, s + 2 + (jx or 0) * 3 + (jz or 0) * 7) - 0.8, 0, 2)
    pores, _ = speckle(n, 0.003, s + 3, 0.4, 0.9)
    alb = tint(lin(*SIDEWALK), n) * (1 + 0.05 * big + 0.03 * broom - 0.08 * stain)[..., None]
    alb *= (1 - 0.3 * pores)[..., None]
    h = 0.6 + 0.03 * broom - 0.3 * pores
    if not side:
        j = np.zeros((n, n))
        w = 1.2 / n
        if jx == 0:
            j = np.maximum(j, band(u, -1, w, 0.5 / n) + band(u, 1 - 0.2 / n, 2, 0.5 / n) * 0)
        if jx == 1:
            j = np.maximum(j, band(u, 0.5 - w, 0.5 + w, 0.5 / n))
        if jz == 0:
            j = np.maximum(j, band(v, -1, w, 0.5 / n))
        if jz == 1:
            j = np.maximum(j, band(v, 0.5 - w, 0.5 + w, 0.5 / n))
        # tooled edge: slightly smoother margin beside each joint
        margin = np.clip(blur(j, 2.5) * 3, 0, 1) * (1 - j)
        alb = mix(alb, alb * 1.06, margin * 0.5)
        alb = mix(alb, alb * 0.35, j)
        h = h - 0.5 * j
    return Mat(n, alb, h, smooth=0.22, f0=10, porosity=45, relief=0.55)


def kerb(name, part, n=HI):
    s = sd("kerb_" + part)
    u, v = grid(n)
    big = fnoise(n, 2.2, s)
    alb = tint(lin(*KERB), n) * (1 + 0.05 * big)[..., None]
    h = 0.6 + 0.02 * big
    if part == "top":
        # road side (north, top of image): 15 cm lighter rounded nose
        nose = 1 - smoothstep(0.13, 0.16, v)
        alb = mix(alb, alb * 1.12, nose)
        h = h + 0.2 * nose * np.cos(np.clip(v / 0.15, 0, 1) * np.pi / 2)
    if part == "face":
        alb *= (1 + 0.04 * fnoise(n, 1.0, s + 1, ax=4, ay=0.2))[..., None]
        grime = smoothstep(0.6, 1.0, v) * 0.25
        alb *= (1 - grime)[..., None]
    return Mat(n, alb, h, smooth=0.25, f0=10, porosity=40, relief=0.5)


def barrier(name, n=HI):
    s = sd(name)
    u, v = grid(n)
    big = fnoise(n, 2.2, s)
    streak = fnoise(n, 2.0, s + 1, ax=0.15, ay=5)
    alb = tint(lin(*BARRIER), n) * (1 + 0.04 * big + 0.04 * streak)[..., None]
    alb *= (1 - 0.18 * smoothstep(0.75, 1.0, v))[..., None]  # road spray at the foot
    joint = band(u, -1, 1.2 / n, 0.4 / n)
    alb = mix(alb, alb * 0.5, joint)
    return Mat(n, alb, 0.6 - 0.4 * joint, smooth=0.3, f0=10, porosity=35, relief=0.4)

# ------------------------------------------------------------------ ground


def sand(name, kind, n=HI):
    s = sd(name)
    u, v = grid(n)
    grain = white(n, s)
    g2 = blur(grain, 0.6)
    big = fnoise(n, 2.4, s + 1)
    shells, sv = speckle(n, 0.0015, s + 2, 0.5, 1.4)
    if kind == "beach":
        c = lin(0.86, 0.80, 0.66)
        alb = tint(c, n) * (1 + 0.05 * g2 + 0.03 * big)[..., None]
        dark, dv = speckle(n, 0.02, s + 3, 0.3, 0.6)
        alb = mix(alb, alb * 0.55, dark * 0.6)
        alb = mix(alb, tint(lin(0.95, 0.92, 0.88), n), shells)
        h = 0.5 + 0.06 * g2 + 0.08 * big / 3 + 0.2 * shells
        return Mat(n, alb, h, smooth=0.12, f0=10, porosity=60, relief=0.45)
    if kind == "wet":
        c = lin(0.52, 0.45, 0.33)
        alb = tint(c, n) * (1 + 0.04 * g2 + 0.05 * big)[..., None]
        foam = np.clip(fnoise(n, 2.0, s + 4, ax=0.5, ay=2) - 1.4, 0, 1)
        alb = mix(alb, tint(lin(0.70, 0.66, 0.58), n), foam * 0.5)
        h = 0.5 + 0.03 * g2
        return Mat(n, alb, h, smooth=0.62, f0=12, porosity=64, relief=0.3)
    # seabed: ripples
    warp = 0.03 * fnoise(n, 2.5, s + 5)
    rip = 0.5 + 0.5 * np.sin(2 * np.pi * (v * 6 + warp * 6 + 0.3 * np.sin(2 * np.pi * u)))
    c = lin(0.70, 0.66, 0.50)
    alb = tint(c, n) * (1 + 0.04 * g2 + 0.06 * (rip - 0.5) + 0.04 * big)[..., None]
    alb = mix(alb, alb * lin(0.85, 0.95, 0.85), 0.3)
    h = 0.3 + 0.4 * rip + 0.04 * g2
    return Mat(n, alb, h, smooth=0.2, f0=10, porosity=64, relief=0.6, light=(-0.2, -0.7, 0.68))


def coral_stone(name, n=HI):
    s = sd(name)
    holes, hv = speckle(n, 0.012, s, 0.6, 2.6)
    f1, f2, cid = cells(n, 40, s + 1)
    big = fnoise(n, 2.0, s + 2)
    alb = tint(lin(0.80, 0.74, 0.58), n) * (1 + 0.06 * big + 0.05 * (cid - 0.5))[..., None]
    alb = mix(alb, alb * 0.4, holes * 0.85)
    h = 0.6 + 0.06 * big - 0.5 * holes + 0.05 * smoothstep(0, 3, f2 - f1)
    return Mat(n, alb, h, smooth=0.18, f0=10, porosity=58, relief=0.7)


def terrazzo(name, n=HI):
    s = sd(name)
    chips, cv = speckle(n, 0.03, s, 0.5, 2.0)
    pal = [lin(0.25, 0.25, 0.26), lin(0.55, 0.30, 0.22), lin(0.35, 0.45, 0.38), lin(0.65, 0.62, 0.58), lin(0.08, 0.08, 0.09)]
    idx = np.clip((cv * len(pal)).astype(int), 0, len(pal) - 1)
    chipc = np.stack([pal[i] for i in range(len(pal))])[idx]
    alb = tint(lin(0.80, 0.77, 0.72), n) * (1 + 0.02 * fnoise(n, 1.5, s + 1))[..., None]
    alb = mix(alb, chipc, chips)
    return Mat(n, alb, 0.6, smooth=0.82, f0=11, porosity=4, relief=0.0)


def tile_floor(name, n=HI):
    s = sd(name)
    rng = np.random.default_rng(s)
    u, v = grid(n)
    k = 4
    fu, fv = (u * k) % 1.0, (v * k) % 1.0
    grout = np.maximum(band(fu, -1, 0.03, 0.01) + band(fu, 0.97, 2, 0.01), band(fv, -1, 0.03, 0.01) + band(fv, 0.97, 2, 0.01))
    tid = (np.floor(u * k) * 5 + np.floor(v * k) * 3).astype(int) % 16
    rnd = rng.uniform(0.95, 1.04, 16)[tid]
    alb = tint(lin(0.86, 0.84, 0.80), n) * rnd[..., None]
    alb = mix(alb, tint(lin(0.45, 0.43, 0.40), n), np.clip(grout, 0, 1))
    return Mat(n, alb, 0.65 - 0.4 * np.clip(grout, 0, 1), smooth=np.where(grout > 0.5, 0.2, 0.86), f0=11,
               porosity=np.where(grout > 0.5, 40, 2), relief=0.4)


def marble(name, n=HI):
    s = sd(name)
    u, v = grid(n)
    w = fnoise(n, 2.6, s) * 0.8
    veins = np.abs(np.sin(2 * np.pi * (u + v * 0.6 + w * 0.25)))
    vein = 1 - smoothstep(0.0, 0.06, veins)
    fine = 1 - smoothstep(0.0, 0.02, np.abs(np.sin(2 * np.pi * (u * 2 - v + fnoise(n, 2.2, s + 1) * 0.3))))
    alb = tint(lin(0.88, 0.87, 0.85), n)
    alb = mix(alb, tint(lin(0.45, 0.45, 0.46), n), vein * 0.6 + fine * 0.25)
    return Mat(n, alb, 0.6, smooth=0.9, f0=11, porosity=2, relief=0.0)


def wood_deck(name, n=HI, side=False):
    s = sd(name)
    rng = np.random.default_rng(s)
    u, v = grid(n)
    boards = 7
    bi = np.floor(v * boards).astype(int)
    fv = (v * boards) % 1.0
    gap = (fv > 0.9).astype(float)
    grain = fnoise(n, 1.4, s, ax=10, ay=0.12)
    tone = rng.uniform(0.85, 1.12, boards)[bi % boards]
    ends = band(((u + rng.uniform(0, 1, boards)[bi % boards]) % 1.0), 0.0, 0.012, 0.004)
    base = lin(0.30, 0.24, 0.17)
    weather = lin(0.40, 0.38, 0.34)
    alb = mix(tint(base, n), tint(weather, n), 0.5 + 0.2 * fnoise(n, 2, s + 1))
    alb *= (tone * (1 + 0.10 * grain))[..., None]
    alb = mix(alb, alb * 0.25, np.maximum(gap, ends))
    h = 0.6 + 0.04 * grain - 0.5 * np.maximum(gap, ends)
    return Mat(n, alb, h, smooth=0.2, f0=10, porosity=45, relief=0.6)


def door(name, half, n=HI):
    s = sd(name)
    u, v = grid(n)
    base = lin(0.20, 0.10, 0.05)
    grain = fnoise(n, 1.4, s, ax=0.12, ay=10)
    alb = tint(base, n) * (1 + 0.12 * grain)[..., None]
    px = 1.0 / n
    if half == "upper":
        p1 = band(u, 0.15, 0.45, px) * band(v, 0.12, 0.85, px)
        p2 = band(u, 0.55, 0.85, px) * band(v, 0.12, 0.85, px)
    else:
        p1 = band(u, 0.15, 0.45, px) * band(v, 0.15, 0.8, px)
        p2 = band(u, 0.55, 0.85, px) * band(v, 0.15, 0.8, px)
    panel = np.clip(p1 + p2, 0, 1)
    rim = np.clip(blur(panel, 1.5) - panel, 0, 1) * 3
    h = 0.6 - 0.2 * panel + 0.0 * rim
    frame = ((u < 4 * px) | (u > 1 - 4 * px)).astype(float)
    alb = mix(alb, tint(lin(0.95, 0.95, 0.93), n), frame)
    if half == "lower":
        knob = ((u - 0.85) ** 2 + (v - 0.1) ** 2) < (0.03 ** 2)
        alb = mix(alb, tint(lin(0.8, 0.6, 0.25), n), knob.astype(float))
    return Mat(n, alb, h + 0.3 * frame, smooth=0.4, f0=12, relief=0.8)

# ------------------------------------------------------------------ props


def chain_link(name, n=MID):
    u, v = grid(n)
    k = 6
    a = ((u + v) * k) % 1.0
    b = ((u - v) * k) % 1.0
    w = 0.09
    wire = np.maximum((np.minimum(a, 1 - a) < w), (np.minimum(b, 1 - b) < w)).astype(float)
    alb = tint(lin(0.45, 0.47, 0.47), n) * (1 + 0.1 * fnoise(n, 1.5, sd(name)))[..., None]
    return Mat(n, alb, 0.5 + 0.3 * wire, alpha=wire, smooth=0.5, f0=232, relief=0.0)


def pole(name, n=MID):
    s = sd(name)
    grain = fnoise(n, 1.3, s, ax=0.1, ay=10)
    alb = tint(lin(0.09, 0.065, 0.045), n) * (1 + 0.18 * grain)[..., None]
    cracks = np.clip(fnoise(n, 1.0, s + 1, ax=0.05, ay=12) - 1.6, 0, 1)
    alb = mix(alb, alb * 0.4, cracks)
    return Mat(n, alb, 0.6 + 0.05 * grain - 0.3 * cracks, smooth=0.15, f0=10, porosity=50, relief=0.6)


def container(name, col, part, n=HI):
    s = sd(name + part)
    u, v = grid(n)
    c = lin(*col)
    if part == "side":
        f = (u * 3.5) % 1.0
        prof = smoothstep(0.05, 0.2, f) * (1 - smoothstep(0.55, 0.7, f))  # trapezoid corrugation
    else:
        f = (v * 4) % 1.0
        prof = 0.5 + 0.2 * np.cos(2 * np.pi * f)
    scratch = np.clip(fnoise(n, 1.0, s, ax=4, ay=0.3) - 1.8, 0, 1)
    rust = np.clip(fnoise(n, 2.2, s + 1) - 1.2, 0, 1) * 0.7
    streak = fnoise(n, 2.0, s + 2, ax=0.12, ay=5)
    alb = tint(c, n) * (1 + 0.05 * streak)[..., None]
    alb = mix(alb, tint(lin(0.28, 0.12, 0.05), n), rust)
    alb = mix(alb, tint(lin(0.5, 0.5, 0.5), n), scratch * 0.5)
    return Mat(n, alb, 0.3 + 0.6 * prof, smooth=0.4 - 0.2 * rust, f0=12, relief=0.8, normal_strength=3.0,
               light=(-0.8, -0.3, 0.55))

# ------------------------------------------------------------------ batch 2: arenas, stadiums, Keys houses
SEAT = dict(teal=(0.03, 0.42, 0.45), navy=(0.03, 0.06, 0.18), burgundy=(0.25, 0.03, 0.06), red=(0.55, 0.04, 0.04),
            grey=(0.30, 0.31, 0.32))
LED = dict(cyan=(0.15, 0.95, 1.0), gold=(1.0, 0.70, 0.20), warm_white=(1.0, 0.85, 0.64))
NEON.update(gold=(1.0, 0.72, 0.18), white=(0.92, 0.94, 1.0), mint=(0.30, 1.0, 0.70))
TRUSS = dict(white=(0.85, 0.86, 0.86), grey=(0.40, 0.42, 0.44))


def stadium_seat(name, col, n=MID):
    """Moulded plastic seats seen on a stair step: two seats per block, seat back highlight, dark gaps."""
    s = sd(name)
    u, v = grid(n)
    c = lin(*col)
    su = (u * 2) % 1.0
    gap = 1 - band(su, 0.06, 0.94, 0.02)
    # each seat: lighter rounded top edge of the back, darker pan
    back = np.exp(-((v - 0.15) ** 2) / 0.006) * (1 - gap)
    shade = 0.9 + 0.25 * back - 0.25 * smoothstep(0.55, 1.0, v)
    alb = tint(c, n) * (shade * (1 + 0.03 * fnoise(n, 2.0, s)))[..., None]
    alb = mix(alb, alb * 0.25, gap)
    h = 0.55 + 0.3 * back - 0.4 * gap
    return Mat(n, alb, h, smooth=0.55, f0=10, relief=0.5)


def standing_seam(name, col, n=HI):
    """White standing-seam roof membrane: raised seams 4 per block, faint dirt streaks along the seams."""
    s = sd(name)
    u, v = grid(n)
    seam = band((u * 4) % 1.0, 0.0, 0.035, 0.012) + band((u * 4) % 1.0, 0.965, 1.0, 0.012)
    streak = fnoise(n, 1.4, s, ax=0.08, ay=6)
    alb = tint(lin(*col), n) * (1 + 0.025 * streak + 0.015 * fnoise(n, 1.0, s + 1, kmin=20))[..., None]
    alb = mix(alb, alb * 0.86, np.clip(blur(seam, 2.0) * 1.5 - seam, 0, 1))
    h = 0.4 + 0.5 * seam
    return Mat(n, alb, h, smooth=0.45, f0=10, porosity=8, relief=0.7)


def arena_panel(name, col, n=HI, metal=True):
    """Large smooth metal cladding with faint horizontal ribs (2 per block) and a panel joint on the block edge."""
    s = sd(name)
    u, v = grid(n)
    px = 1.0 / n
    rib = band((v * 2) % 1.0, 0.47, 0.53, 0.02)
    joint = ((u < 1.5 * px) | (v < 1.5 * px)).astype(float)
    brush = fnoise(n, 1.0, s, ax=8, ay=0.05)
    alb = tint(lin(*col), n) * (1 + 0.03 * brush + 0.06 * rib)[..., None]
    alb = mix(alb, alb * 0.55, joint)
    h = 0.55 + 0.15 * rib - 0.4 * joint
    return Mat(n, alb, h, smooth=0.62 if metal else 0.55, f0=232 if metal else 10, relief=0.4)


def led(name, col, n=MID, ribbon=True):
    """LED ribbon / sign panel: diffuser with a row (or grid) of brighter LED dots; fully emissive."""
    u, v = grid(n)
    c = lin(*col)
    if ribbon:
        dots = np.exp(-(((u * 8) % 1.0 - 0.5) ** 2 + (v - 0.5) ** 2 * 4) / 0.02)
    else:
        dots = np.exp(-(((u * 8) % 1.0 - 0.5) ** 2 + ((v * 8) % 1.0 - 0.5) ** 2) / 0.03)
    alb = tint(c, n) * (0.8 + 0.2 * dots)[..., None]
    alb = mix(alb, np.minimum(c * 0.3 + 0.7, 1.0) * np.ones((n, n, 3)), dots * 0.6)
    return Mat(n, alb, 0.5 + 0.2 * dots, smooth=0.85, f0=11, emissive=1.0, relief=0.0)


def steel_truss(name, col, n=MID):
    """Open lattice: chords on the block edges, one diagonal per face; cutout elsewhere."""
    s = sd(name)
    u, v = grid(n)
    px = 1.0 / n
    w = 4 * px
    chord = ((u < w) | (u > 1 - w) | (v < w) | (v > 1 - w)).astype(float)
    diag = (np.abs(u - v) < 2.2 * px).astype(float)
    bolt = 0.0
    m = np.maximum(chord, diag)
    flange = ((u < 1.2 * px) | (u > 1 - 1.2 * px) | (v < 1.2 * px) | (v > 1 - 1.2 * px)).astype(float)
    alb = tint(lin(*col), n) * (1 + 0.06 * fnoise(n, 1.5, s) - 0.25 * flange)[..., None]
    return Mat(n, alb, 0.5 + 0.3 * m + bolt, alpha=m, smooth=0.45, f0=10 if col[0] > 0.6 else 230, relief=0.3)


def court_wood(name, n=HI):
    """Maple parquet: narrow light strips (10 per block) along u, staggered butt joints, satin finish."""
    s = sd(name)
    rng = np.random.default_rng(s)
    u, v = grid(n)
    k = 10
    bi = np.floor(v * k).astype(int) % k
    off = rng.uniform(0, 1, k)[bi]
    tone = rng.uniform(0.9, 1.08, k)[bi]
    fu = (u * 0.5 + off) % 1.0
    joint = band(fu, 0.0, 0.006, 0.003) + band((v * k) % 1.0, 0.0, 0.04, 0.02)
    grain = fnoise(n, 1.3, s, ax=12, ay=0.1)
    alb = tint(lin(0.62, 0.42, 0.22), n) * (tone * (1 + 0.07 * grain))[..., None]
    alb = mix(alb, alb * 0.6, np.clip(joint, 0, 1))
    return Mat(n, alb, 0.6 + 0.02 * grain - 0.2 * joint, smooth=0.78, f0=10, relief=0.4)


def turf(name, n=HI, dark=False):
    """Manicured ballpark grass: fine blades, slight mowing sheen (dark = the other mowing direction)."""
    s = sd(name)
    blades = fnoise(n, 0.6, s, kmin=16, ax=0.5, ay=2)
    clump = fnoise(n, 2.0, s + 1)
    c = lin(0.10, 0.30, 0.05) * (0.78 if dark else 1.0)
    alb = tint(c, n) * (1 + 0.16 * blades + 0.05 * clump)[..., None]
    alb[..., 0] *= 1 + 0.06 * clump
    return Mat(n, alb, 0.5 + 0.2 * blades, smooth=0.15, f0=10, porosity=200, relief=0.4)


def siding(name, n=HI, variant=0, dark=False, faded=False):
    """Board-and-batten siding, sea-green paint over grey wood: 4 boards per block, raised battens on the joints,
    chalky worn patches and peeling showing bare wood. Variants change the wear pattern so walls do not tile."""
    s = sd(name) + 17 * variant
    u, v = grid(n)
    paint = from_hex_mix(0x7FA095, 0x96B2A8, n, s) if faded else from_hex_mix(0x5E9C8C, 0x7FB5A3, n, s)
    wood = lin(0.30, 0.29, 0.26)
    grain = fnoise(n, 1.3, s + 2, ax=0.1, ay=12)
    bu = (u * 4) % 1.0
    batten = band(bu, 0.0, 0.07, 0.01) + band(bu, 0.93, 1.0, 0.01)
    edge = np.clip(blur(batten, 1.5) - batten, 0, 1)
    wear = fnoise(n, 2.2, s + 3) + 0.6 * fnoise(n, 1.4, s + 4, ax=0.2, ay=4)
    chalk = smoothstep(0.7, 1.7, wear)
    peel = smoothstep(1.8, 1.95, wear + 0.3 * batten)
    alb = paint * (1 + 0.05 * grain)[..., None]
    alb = mix(alb, mix(alb, tint(lin(0.62, 0.68, 0.64), n), 0.6), chalk * 0.5)
    alb = mix(alb, tint(wood, n) * (1 + 0.15 * grain)[..., None], peel)
    alb = mix(alb, alb * 0.7, edge * 0.8)
    if faded:
        bleach = smoothstep(0.0, 1.2, fnoise(n, 1.5, s + 6, ax=0.15, ay=5))
        alb = mix(alb, tint(lin(0.70, 0.74, 0.70), n), bleach * 0.35)
        scuff = smoothstep(1.75, 1.95, fnoise(n, 2.4, s + 7))
        alb = mix(alb, tint(wood, n) * (1 + 0.15 * grain)[..., None], scuff)
    if dark:
        dirt = smoothstep(0.2, 1.0, v) * (0.6 + 0.3 * fnoise(n, 1.5, s + 5, ax=0.2, ay=3))
        alb = alb * (1 - 0.35 * np.clip(dirt, 0, 1))[..., None]
    h = 0.45 + 0.35 * batten + 0.03 * grain - 0.06 * peel
    return Mat(n, alb, h, smooth=0.18, f0=10, porosity=40, relief=0.7)


def cream_panel(name, n=HI):
    """Interior cream painted tongue-and-groove boards: 5 vertical boards per block with V-groove joints, brush streaks
    and light scuffs showing grey wood at the edges."""
    s = sd(name)
    u, v = grid(n)
    paint = from_hex_mix(0xE8E0CC, 0xDCD2BA, n, s)
    bu = (u * 5) % 1.0
    groove = band(bu, 0.0, 0.035, 0.01) + band(bu, 0.965, 1.0, 0.01)
    edge = np.clip(blur(groove, 1.2) - groove, 0, 1)
    brush = fnoise(n, 1.2, s + 2, ax=0.08, ay=14)
    wear = fnoise(n, 2.3, s + 3) + 0.8 * groove
    scuff = smoothstep(1.9, 2.1, wear)
    alb = paint * (1 + 0.025 * brush)[..., None]
    alb = mix(alb, alb * 0.6, np.clip(groove, 0, 1))
    alb = mix(alb, alb * 0.85, edge * 0.6)
    alb = mix(alb, tint(lin(0.30, 0.28, 0.24), n), scuff * 0.7)
    grime = smoothstep(0.75, 1.0, v) * 0.08
    alb = alb * (1 - grime)[..., None]
    h = 0.55 - 0.4 * np.clip(groove, 0, 1) + 0.02 * brush
    return Mat(n, alb, h, smooth=0.32, f0=10, porosity=20, relief=0.6)


def from_hex_mix(a, b, n, s):
    t = 0.5 + 0.5 * np.clip(fnoise(n, 2.4, s + 9), -1, 1)
    return mix(tint(hexlin(a), n), tint(hexlin(b), n), t * 0.6)


def weathered_wood(name, n=MID, end=False):
    """Sun-bleached grey timber (pilings, posts): long checks along the grain; end grain shows rings."""
    s = sd(name)
    u, v = grid(n)
    base = lin(0.25, 0.23, 0.19)
    if end:
        r = np.sqrt((u - 0.47) ** 2 + (v - 0.52) ** 2)
        rings = 0.5 + 0.5 * np.sin(r * 2 * np.pi * 9 + 2 * fnoise(n, 2.5, s))
        alb = tint(base * 0.9, n) * (0.88 + 0.16 * rings)[..., None]
        crack = band(np.arctan2(v - 0.52, u - 0.47), 0.3, 0.33, 0.01) * smoothstep(0.05, 0.4, r)
        alb = mix(alb, alb * 0.35, crack)
        rim = smoothstep(0.42, 0.5, np.maximum(np.abs(u - 0.5), np.abs(v - 0.5)))
        alb = mix(alb, alb * 0.8, rim)
        return Mat(n, alb, 0.5 + 0.05 * rings - 0.3 * crack, smooth=0.12, f0=10, porosity=55, relief=0.6)
    grain = fnoise(n, 1.2, s, ax=0.08, ay=10)
    fine = fnoise(n, 1.0, s + 3, ax=0.03, ay=16)
    checks = np.clip(fnoise(n, 1.0, s + 1, ax=0.04, ay=14) - 1.5, 0, 1) * 2
    streak = smoothstep(0.3, 1.4, fnoise(n, 1.6, s + 4, ax=0.15, ay=6))
    tan = mix(tint(base, n), tint(base * np.array([1.25, 1.12, 0.95]), n), smoothstep(-0.8, 0.8, fnoise(n, 2.2, s + 5)))
    alb = tan * (1 + 0.28 * grain + 0.14 * fine)[..., None]
    alb = mix(alb, alb * 0.55, streak * 0.6)
    alb = mix(alb, alb * 0.3, np.clip(checks, 0, 1))
    return Mat(n, alb, 0.6 + 0.1 * grain + 0.05 * fine - 0.35 * np.clip(checks, 0, 1), smooth=0.12, f0=10, porosity=55,
               relief=0.8, normal_strength=3.0)


def wood_brown(name, n=MID):
    """Brown stained deck timber for railings and handrails (#8A5E3C)."""
    s = sd(name)
    grain = fnoise(n, 1.3, s, ax=0.1, ay=10)
    alb = tint(hexlin(0x8A5E3C), n) * (1 + 0.12 * grain + 0.04 * fnoise(n, 2.0, s + 1))[..., None]
    return Mat(n, alb, 0.6 + 0.04 * grain, smooth=0.3, f0=10, porosity=35, relief=0.5)


def shutter(name, n=MID):
    """Louvered wooden shutter in weathered grey-blue paint, stile frame and 12 slats."""
    s = sd(name)
    u, v = grid(n)
    px = 1.0 / n
    stile = ((u < 6 * px) | (u > 1 - 6 * px) | (v < 5 * px) | (v > 1 - 5 * px)).astype(float)
    sl = (v * 12) % 1.0
    slat = 0.55 + 0.45 * sl - 0.6 * band(sl, 0.85, 1.0, 0.03)
    paint = lin(0.30, 0.38, 0.44)
    alb = tint(paint, n) * (1 + 0.06 * fnoise(n, 1.4, s, ax=0.2, ay=4))[..., None]
    alb = alb * np.where(stile > 0.5, 1.0, slat)[..., None]
    wear = smoothstep(1.2, 1.5, fnoise(n, 2.0, s + 1))
    alb = mix(alb, tint(lin(0.36, 0.34, 0.31), n), wear * 0.8)
    h = np.where(stile > 0.5, 0.8, 0.35 + 0.35 * sl)
    return Mat(n, alb, h, smooth=0.2, f0=10, porosity=40, relief=0.8)


def lattice(name, n=MID):
    """White-grey diagonal wood lattice skirt (cutout)."""
    s = sd(name)
    u, v = grid(n)
    k = 4
    a = ((u + v) * k) % 1.0
    b = ((u - v) * k) % 1.0
    w = 0.16
    s1 = (np.minimum(a, 1 - a) < w).astype(float)
    s2 = (np.minimum(b, 1 - b) < w).astype(float)
    m = np.maximum(s1, s2)
    over = s1 * (1 - s2) + s1 * s2 * ((np.floor((u + v) * k) % 2) == 0)
    alb = tint(lin(0.72, 0.72, 0.70), n) * (1 + 0.06 * fnoise(n, 1.4, s) - 0.15 * (1 - over) * s2)[..., None]
    return Mat(n, alb, 0.4 + 0.3 * m + 0.1 * over, alpha=m, smooth=0.15, f0=10, porosity=40, relief=0.4)


def bamboo_blind(name, n=MID):
    """Rolled-down reed blind: thin horizontal reeds with tiny gaps and two vertical tie cords."""
    s = sd(name)
    rng = np.random.default_rng(s)
    u, v = grid(n)
    k = 24
    ri = np.floor(v * k).astype(int) % k
    fv = (v * k) % 1.0
    reed = 0.75 + 0.25 * np.sin(fv * np.pi)
    tone = rng.uniform(0.85, 1.1, k)[ri]
    gap = band(fv, 0.9, 1.0, 0.02)
    cord = band(u, 0.2, 0.23, 0.005) + band(u, 0.77, 0.8, 0.005)
    alb = tint(lin(0.55, 0.40, 0.20), n) * (reed * tone * (1 + 0.08 * fnoise(n, 1.3, s, ax=8, ay=0.1)))[..., None]
    alb = mix(alb, tint(lin(0.25, 0.18, 0.10), n), np.clip(cord, 0, 1))
    alpha = np.clip(1 - gap * 0.85, 0, 1)
    return Mat(n, alb, 0.4 + 0.3 * reed, alpha=alpha, smooth=0.25, f0=10, porosity=40, relief=0.5)


def tarp(name, n=MID):
    """Crinkled blue polyethylene tarp with woven grid and a grommet edge."""
    s = sd(name)
    u, v = grid(n)
    crinkle = fnoise(n, 2.6, s, kmin=1)
    weave = 0.5 + 0.5 * np.cos(2 * np.pi * u * 32) * np.cos(2 * np.pi * v * 32)
    alb = tint(lin(0.03, 0.18, 0.55), n) * (1 + 0.08 * crinkle + 0.03 * weave)[..., None]
    return Mat(n, alb, 0.5 + 0.2 * crinkle, smooth=0.55, f0=10, relief=0.8, normal_strength=3.0)


def tropical_fabric(name, n=HI):
    """Green banana-leaf print on cream upholstery."""
    s = sd(name)
    u, v = grid(n)
    rng = np.random.default_rng(s)
    leaf = np.zeros((n, n))
    vein = np.zeros((n, n))
    for _ in range(9):
        cx, cy = rng.uniform(0, 1, 2)
        ang = rng.uniform(0, np.pi)
        L, W = rng.uniform(0.25, 0.4), rng.uniform(0.07, 0.11)
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                dx, dy = u - cx + ox, v - cy + oy
                a = dx * np.cos(ang) + dy * np.sin(ang)
                b = -dx * np.sin(ang) + dy * np.cos(ang)
                shape = (a / L) ** 2 + (b / (W * (1 - (a / L) ** 2).clip(0.05))) ** 2
                inside = (np.abs(a) < L) & (shape < 1)
                leaf = np.maximum(leaf, inside.astype(float))
                vein = np.maximum(vein, (inside & (np.abs(b) < 0.006)).astype(float))
                rib = inside & (np.abs(((a * 30) % 1.0) - 0.5 + b * 12) < 0.06)
                vein = np.maximum(vein, rib.astype(float) * 0.5)
    base = tint(lin(0.80, 0.75, 0.62), n)
    green = tint(lin(0.05, 0.25, 0.08), n) * (1 + 0.15 * fnoise(n, 1.6, s + 1))[..., None]
    alb = mix(base, green, leaf)
    alb = mix(alb, tint(lin(0.20, 0.42, 0.15), n), vein * leaf)
    weave = 0.5 + 0.5 * np.cos(2 * np.pi * u * 48) * np.cos(2 * np.pi * v * 48)
    alb *= (1 + 0.05 * (weave - 0.5))[..., None]
    return Mat(n, alb, 0.5 + 0.1 * weave, smooth=0.1, f0=10, porosity=60, relief=0.3)


def oyster_gravel(name, n=HI):
    """White crushed oyster shell drive: bright angular chips, grey-brown fines between."""
    s = sd(name)
    chips, cv = speckle(n, 0.07, s, 1.2, 3.2)
    small, sv = speckle(n, 0.12, s + 1, 0.5, 1.2)
    base = tint(lin(0.45, 0.42, 0.37), n) * (1 + 0.1 * fnoise(n, 1.5, s + 2))[..., None]
    chipc = mix(lin(0.80, 0.78, 0.72), lin(0.95, 0.94, 0.90), cv)
    alb = mix(base, chipc, chips)
    alb = mix(alb, mix(lin(0.6, 0.58, 0.54), lin(0.85, 0.84, 0.80), sv), small * 0.8)
    pearl = fnoise(n, 1.0, s + 3, kmin=20)
    alb *= (1 + 0.04 * pearl * chips)[..., None]
    return Mat(n, alb, 0.3 + 0.5 * chips + 0.2 * small, smooth=np.where(chips > 0.5, 0.45, 0.1), f0=10, porosity=50,
               relief=0.8)


def porthole(name, n=HI):
    """Round Deco porthole: white trim surround, chrome ring, dark glass with a sky glint."""
    s = sd(name)
    u, v = grid(n)
    r = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2)
    glass = (r < 0.33).astype(float)
    ring = band(r, 0.33, 0.38, 0.006)
    wall = tint(lin(0.97, 0.97, 0.95), n) * (1 + 0.02 * fnoise(n, 1.0, s, kmin=10))[..., None]
    g = tint(lin(0.05, 0.07, 0.08), n) * (1 + 0.1 * fnoise(n, 3.0, s + 1))[..., None]
    g = mix(g, g + lin(0.30, 0.36, 0.42) * 0.3, 0.3 * (1 - v))
    chrome = tint(lin(0.6, 0.62, 0.64), n) * (0.8 + 0.4 * (1 - v))[..., None]
    alb = mix(mix(wall, g, glass), chrome, ring)
    frame = 1 - glass
    h = 0.45 + 0.35 * ring + 0.25 * (1 - glass - ring).clip(0)
    m = Mat(n, alb, h, smooth=np.where(glass > 0.5, 0.94, np.where(ring > 0.5, 0.8, 0.25)),
            f0=np.where(ring > 0.5, 232, np.where(glass > 0.5, 11, 10)), relief=0.6)
    m.frame_mask = frame
    return m


def cottage_window(name, n=HI):
    """White-framed two-pane wooden sash (upper and lower sash, meeting rail in the middle)."""
    s = sd(name)
    u, v = grid(n)
    px = 1.0 / n
    frame = ((u < 9 * px) | (u > 1 - 9 * px) | (v < 8 * px) | (v > 1 - 8 * px) | (np.abs(v - 0.5) < 5 * px)).astype(float)
    g = tint(lin(0.06, 0.07, 0.08), n) * (1 + 0.12 * fnoise(n, 3.0, s, ax=0.6, ay=1.6))[..., None]
    g = mix(g, g + lin(0.30, 0.36, 0.42) * 0.25, 0.25 * (1 - v))
    paint = tint(lin(0.90, 0.90, 0.87), n) * (1 + 0.05 * fnoise(n, 1.6, s + 1))[..., None]
    alb = mix(g, paint, frame)
    m = Mat(n, alb, 0.45 + 0.5 * frame, smooth=np.where(frame > 0.5, 0.3, 0.94), f0=np.where(frame > 0.5, 10, 11),
            relief=0.6, normal_strength=2.5)
    m.frame_mask = frame
    return m


def cottage_door(name, half, n=HI):
    """Green-teal painted wood door: lower half two raised panels, upper half 9-lite glazing (3 x 3)."""
    s = sd(name)
    u, v = grid(n)
    px = 1.0 / n
    paint = from_hex_mix(0x2F6F62, 0x3C8576, n, s)
    grain = fnoise(n, 1.4, s + 1, ax=0.12, ay=10)
    alb = paint * (1 + 0.06 * grain)[..., None]
    if half == "upper":
        gu, gv = (u - 0.16) / 0.68, (v - 0.12) / 0.76
        inside = (gu > 0) & (gu < 1) & (gv > 0) & (gv < 1)
        mun = (np.abs((gu * 3) % 1.0 - 0.5) > 0.44) | (np.abs((gv * 3) % 1.0 - 0.5) > 0.44)
        pane = (inside & ~mun).astype(float)
        g = tint(lin(0.05, 0.07, 0.08), n) * (1 + 0.1 * fnoise(n, 3.0, s + 2))[..., None]
        g = mix(g, g + lin(0.3, 0.36, 0.42) * 0.3, 0.3 * (1 - v))
        alb = mix(alb, g, pane)
        h = 0.65 - 0.3 * pane
        smooth = np.where(pane > 0.5, 0.94, 0.4)
    else:
        p = np.clip(band(u, 0.16, 0.84, px) * (band(v, 0.1, 0.45, px) + band(v, 0.55, 0.88, px)), 0, 1)
        alb = mix(alb, alb * 0.88, np.clip(blur(p, 1.5) - p, 0, 1) * 3)
        knob = (((u - 0.86) ** 2 + (v - 0.08) ** 2) < 0.03 ** 2).astype(float)
        alb = mix(alb, tint(lin(0.75, 0.6, 0.3), n), knob)
        h = 0.6 - 0.15 * p + 0.3 * knob
        smooth = np.where(knob > 0.5, 0.8, 0.4)
    wear = smoothstep(1.4, 1.7, fnoise(n, 2.0, s + 3))
    alb = mix(alb, tint(lin(0.36, 0.34, 0.31), n), wear * 0.7)
    return Mat(n, alb, h, smooth=smooth, f0=10, porosity=30, relief=0.8)


# ------------------------------------------------------------------ palms


def palm_trunk(name, kind, n=MID, end=False):
    """Palm trunk bark. royal: smooth pale grey concrete-like with faint growth rings; coconut: grey-brown with
    pronounced leaf-scar rings; sabal: criss-cross leaf boots (dark fibrous bases)."""
    s = sd(name)
    u, v = grid(n)
    if end:
        r = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2)
        fib = fnoise(n, 1.6, s)
        base = {"royal": lin(0.42, 0.41, 0.37), "coconut": lin(0.24, 0.19, 0.14), "sabal": lin(0.22, 0.18, 0.13)}[kind]
        alb = tint(base, n) * (0.85 + 0.1 * fib + 0.1 * smoothstep(0.2, 0.0, r))[..., None]
        return Mat(n, alb, 0.5 + 0.05 * fib, smooth=0.1, f0=10, porosity=50, relief=0.5)
    if kind == "royal":
        streak = fnoise(n, 2.2, s, ax=0.2, ay=4)
        rings = band((v * 6 + 0.04 * np.sin(u * 2 * np.pi + s % 7)) % 1.0, 0.0, 0.04, 0.03)
        alb = tint(lin(0.44, 0.43, 0.39), n) * (1 + 0.06 * streak + 0.04 * fnoise(n, 2.2, s + 2))[..., None]
        alb = mix(alb, alb * 0.85, rings * 0.6)
        return Mat(n, alb, 0.55 - 0.15 * rings + 0.03 * streak, smooth=0.2, f0=10, porosity=35, relief=0.6)
    if kind == "coconut":
        warp = 0.15 * fnoise(n, 2.0, s, ax=3, ay=0.4)
        rv = (v * 4 + warp) % 1.0
        ring = band(rv, 0.0, 0.16, 0.05)
        scar = smoothstep(0.16, 0.9, rv)
        fib = fnoise(n, 1.3, s + 1, ax=0.15, ay=6)
        alb = tint(lin(0.27, 0.22, 0.16), n) * (0.85 + 0.25 * scar + 0.1 * fib)[..., None]
        alb = mix(alb, tint(lin(0.12, 0.09, 0.06), n), ring * 0.8)
        alb = mix(alb, tint(lin(0.40, 0.38, 0.33), n), smoothstep(1.0, 2.0, fnoise(n, 2.0, s + 2)) * 0.35)
        return Mat(n, alb, 0.6 - 0.35 * ring + 0.05 * fib, smooth=0.1, f0=10, porosity=55, relief=0.8, normal_strength=3.0)
    # sabal: diamond lattice of split leaf bases
    wu = u + 0.03 * fnoise(n, 3.0, s + 5)
    wv = v + 0.03 * fnoise(n, 3.0, s + 6)
    a = ((wu + wv * 1.4) * 3) % 1.0
    b = ((wu - wv * 1.4) * 3) % 1.0
    boot = np.minimum(np.minimum(a, 1 - a), np.minimum(b, 1 - b))
    split = smoothstep(0.02, 0.12, boot)
    fib = fnoise(n, 1.2, s, ax=0.2, ay=5)
    alb = tint(lin(0.26, 0.21, 0.15), n) * (0.7 + 0.4 * split + 0.12 * fib)[..., None]
    alb = mix(alb, tint(lin(0.09, 0.07, 0.05), n), (1 - split) * 0.85)
    return Mat(n, alb, 0.2 + 0.6 * split + 0.05 * fib, smooth=0.08, f0=10, porosity=60, relief=0.8, normal_strength=3.0)


def crownshaft(name, n=MID):
    """Royal palm crownshaft: smooth waxy green sheath with fine vertical striations and a pale band at the base."""
    s = sd(name)
    u, v = grid(n)
    stri = fnoise(n, 1.2, s, ax=0.06, ay=10)
    alb = tint(lin(0.11, 0.21, 0.07), n) * (1 + 0.1 * stri + 0.06 * fnoise(n, 2.0, s + 1))[..., None]
    seam = band((u * 2 + 0.05 * fnoise(n, 2, s + 2)) % 1.0, 0.0, 0.03, 0.01)
    alb = mix(alb, alb * 0.6, seam)
    return Mat(n, alb, 0.55 + 0.03 * stri - 0.2 * seam, smooth=0.55, f0=10, porosity=10, relief=0.4)


def palm_frond(name, kind, n=HI):
    """Top view of a pinnate frond along v (v=0 base, v=1 tip), rachis at u=0.5. Leaflets leave the rachis angled toward
    the tip, longest mid-frond, tapering to the tip; tiny gaps between leaflets (cutout). Subsurface scattering in _s."""
    s = sd(name)
    u, v = grid(n)
    d = np.abs(u - 0.5) * 2  # 0 rachis .. 1 edge
    slope = 0.38 if kind == "royal" else 0.32
    sv = v - d * slope
    count = 30 if kind == "royal" else 26
    idx = np.floor(sv * count)
    f = sv * count - idx
    rng = np.random.default_rng(s)
    jitter = rng.uniform(0.85, 1.05, count + 40)[np.clip(idx.astype(int) + 20, 0, count + 39)]
    t = np.clip(sv, 0, 1)
    env = smoothstep(0.03, 0.22, t) * np.clip(1 - (t - 0.25) / 0.72, 0, 1) ** 0.8 * jitter
    env = np.minimum(env, np.clip((1.0 - sv) / slope * 0.9, 0, None))
    width = 0.72 * (1 - 0.7 * (d / np.maximum(env, 1e-3)))  # leaflets narrow toward their tips
    leaf = (f < width) & (d < env) & (sv > 0.05) & (sv < 1.0)
    rach = (d < 0.035 + 0.03 * (1 - v)) & (v < 0.985)
    alpha = np.maximum(leaf, rach).astype(float)
    if kind == "royal":
        g0, g1 = lin(0.045, 0.15, 0.03), lin(0.09, 0.24, 0.05)
    else:
        g0, g1 = lin(0.07, 0.15, 0.03), lin(0.16, 0.26, 0.05)
    tone = 0.5 + 0.5 * np.clip(fnoise(n, 2.0, s + 1), -1, 1)
    alb = mix(tint(g0, n), tint(g1, n), tone * 0.7 + 0.3 * (d / np.maximum(env, 1e-3)).clip(0, 1))
    alb = mix(alb, alb * 1.25, np.clip(1 - np.abs(f - width / 2) / np.maximum(width / 2, 1e-3), 0, 1) * 0.25)  # midrib sheen
    if kind == "coconut":
        brown = smoothstep(0.75, 1.0, d / np.maximum(env, 1e-3)) * smoothstep(1.2, 1.9, fnoise(n, 1.8, s + 2) + 0.8)
        alb = mix(alb, tint(lin(0.30, 0.22, 0.09), n), brown * 0.8)
    alb = np.where(rach[..., None], tint(lin(0.25, 0.30, 0.10), n), alb)
    h = 0.5 + 0.2 * leaf - 0.1 * np.abs(f - width / 2) * leaf + 0.15 * rach
    return Mat(n, alb, h, alpha=alpha, smooth=0.38, f0=10, porosity=140, relief=0.15)


def palm_fan(name, n=HI):
    """Sabal (cabbage palm) costapalmate fan leaf seen from above: about 40 folded segments radiating from the petiole at
    the bottom centre, split tips with threads, grey-green."""
    s = sd(name)
    u, v = grid(n)
    x, y = (u - 0.5) * 2, v * 1.05 - 0.05  # y=0 petiole at the base end (v=0)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(x, np.maximum(y, -0.2))
    inside = (r < 1.0) & (np.abs(ang) < 1.75)
    seg = ang * 40 / 3.5
    fs = seg % 1.0
    fold = np.abs(fs - 0.5) * 2
    split = (r > 0.62 + 0.1 * np.sin(seg * 2.3)) & (fold > 0.82)
    alpha = (inside & ~split & (r > 0.06)).astype(float)
    costa = (np.abs(x) < 0.025) & (y > 0) & (y < 0.55)
    alpha = np.maximum(alpha, costa.astype(float))
    rng = np.random.default_rng(s)
    tone = rng.uniform(0.85, 1.1, 64)[np.clip((seg + 32).astype(int), 0, 63)]
    alb = tint(lin(0.12, 0.21, 0.08), n) * (tone * (0.8 + 0.35 * fold))[..., None]
    tip = smoothstep(0.85, 1.0, r) * smoothstep(1.0, 1.8, fnoise(n, 1.8, s + 1) + 0.7)
    alb = mix(alb, tint(lin(0.32, 0.26, 0.12), n), tip * 0.7)
    alb = np.where(costa[..., None], tint(lin(0.20, 0.24, 0.10), n), alb)
    return Mat(n, alb, 0.4 + 0.3 * fold, alpha=alpha, smooth=0.32, f0=10, porosity=130, relief=0.3)


def palm_boot(name, n=MID):
    """Fibrous frond bases at the heart of a palm crown."""
    s = sd(name)
    fib = fnoise(n, 1.1, s, ax=0.15, ay=8)
    alb = tint(lin(0.20, 0.16, 0.09), n) * (0.8 + 0.3 * fib)[..., None]
    return Mat(n, alb, 0.5 + 0.1 * fib, smooth=0.1, f0=10, porosity=60, relief=0.7)


def coconut(name, n=MID):
    s = sd(name)
    u, v = grid(n)
    mott = fnoise(n, 2.0, s)
    alb = mix(tint(lin(0.22, 0.30, 0.06), n), tint(lin(0.35, 0.25, 0.08), n), smoothstep(-0.5, 1.0, mott))
    return Mat(n, alb, 0.5 + 0.05 * mott, smooth=0.5, f0=10, porosity=10, relief=0.5)


# ------------------------------------------------------------------ Keys house pass 2


def trim_peeling(name, n=HI):
    """Off-white painted trim board with peeling flakes showing grey wood, shadowed flake edges."""
    s = sd(name)
    u, v = grid(n)
    grain = fnoise(n, 1.2, s, ax=0.08, ay=10)
    wear = fnoise(n, 2.3, s + 1) + 0.5 * fnoise(n, 1.5, s + 2, ax=0.3, ay=3)
    peel = smoothstep(1.35, 1.5, wear)
    rim = np.clip(blur(peel, 1.2) - peel, 0, 1)
    paint = tint(lin(0.80, 0.79, 0.74), n) * (1 + 0.03 * grain + 0.03 * fnoise(n, 2.0, s + 3))[..., None]
    wood = tint(lin(0.25, 0.23, 0.20), n) * (1 + 0.25 * grain)[..., None]
    alb = mix(paint, wood, peel)
    alb = mix(alb, alb * 0.65, rim * 0.8)
    alb *= (1 - 0.08 * smoothstep(0.6, 1.0, v))[..., None]
    return Mat(n, alb, 0.6 - 0.15 * peel + 0.03 * grain, smooth=np.where(peel > 0.5, 0.1, 0.35), f0=10, porosity=30, relief=0.6)


def wood_dark(name, n=MID):
    """Dark walnut furniture wood."""
    s = sd(name)
    grain = fnoise(n, 1.2, s, ax=0.08, ay=10)
    alb = tint(lin(0.07, 0.045, 0.03), n) * (1 + 0.25 * grain)[..., None]
    return Mat(n, alb, 0.6 + 0.05 * grain, smooth=0.55, f0=10, relief=0.4)


def fridge(name, part, n=HI):
    """White enamel fridge. part: front_lower, front_upper (freezer seam, memo board with notes and magnets), side, top."""
    s = sd(name + part)
    u, v = grid(n)
    base = lin(0.86, 0.86, 0.84)
    alb = tint(base, n) * (1 + 0.015 * fnoise(n, 2.0, s))[..., None]
    h = np.full((n, n), 0.6)
    smooth = np.full((n, n), 0.75)
    if part.startswith("front"):
        # door edge shadow all round and a vertical handle on the right
        edge = ((u < 0.03) | (u > 0.97)).astype(float)
        handle = band(u, 0.84, 0.9, 0.005) * band(v, 0.15 if part == "front_upper" else 0.0, 1.0 if part == "front_upper" else 0.6, 0.005)
        alb = mix(alb, alb * 0.75, edge)
        alb = mix(alb, tint(lin(0.62, 0.63, 0.64), n), handle)
        h += 0.3 * handle
        if part == "front_upper":
            seam = band(v, 0.28, 0.3, 0.004)
            alb = mix(alb, tint(lin(0.25, 0.25, 0.25), n), seam)
            h -= 0.3 * seam
            fh = band(u, 0.84, 0.9, 0.005) * band(v, 0.05, 0.25, 0.005)
            alb = mix(alb, tint(lin(0.62, 0.63, 0.64), n), fh)
            # cork memo board with notes, photos and magnets
            board = band(u, 0.14, 0.68, 0.004) * band(v, 0.40, 0.86, 0.004)
            cork = tint(lin(0.45, 0.30, 0.15), n) * (1 + 0.2 * fnoise(n, 1.0, s + 1))[..., None]
            frame = board * (1 - band(u, 0.16, 0.66, 0.004) * band(v, 0.42, 0.84, 0.004))
            alb = mix(alb, cork, board)
            alb = mix(alb, tint(lin(0.20, 0.13, 0.07), n), frame)
            rng = np.random.default_rng(s)
            cols = [(0.85, 0.80, 0.25), (0.90, 0.90, 0.88), (0.95, 0.55, 0.65), (0.40, 0.70, 0.90), (0.92, 0.92, 0.70)]
            for i in range(6):
                x0, y0 = rng.uniform(0.18, 0.52), rng.uniform(0.44, 0.72)
                w, hh = rng.uniform(0.08, 0.14), rng.uniform(0.08, 0.12)
                note = band(u, x0, x0 + w, 0.003) * band(v, y0, y0 + hh, 0.003)
                c = cols[i % len(cols)]
                alb = mix(alb, tint(lin(*c), n), note)
                lines = note * (np.abs(((v - y0) * 60) % 1.0 - 0.5) < 0.12) * band(u, x0 + 0.01, x0 + w - 0.01, 0.002)
                alb = mix(alb, alb * 0.55, lines * 0.6)
            for i in range(7):
                mx, my = rng.uniform(0.1, 0.75), rng.uniform(0.32, 0.95)
                r = np.sqrt((u - mx) ** 2 + (v - my) ** 2)
                mag = smoothstep(0.022, 0.016, r)
                c = [(0.9, 0.15, 0.15), (0.15, 0.4, 0.9), (0.95, 0.75, 0.1), (0.1, 0.65, 0.3)][i % 4]
                alb = mix(alb, tint(lin(*c), n), mag)
                h += 0.2 * mag
            smooth = np.where(board > 0.5, 0.15, smooth)
    elif part == "side":
        alb *= (0.94 + 0.04 * v)[..., None]
    else:
        vent = band(v, 0.8, 0.95, 0.005) * (np.abs((u * 30) % 1.0 - 0.5) < 0.2)
        alb = mix(alb, alb * 0.4, vent)
    return Mat(n, alb, h, smooth=smooth, f0=10, relief=0.4)


def cooler(name, part, n=MID):
    """Picnic cooler: blue body with white lid band (side), white lid with a recessed handle (top)."""
    s = sd(name + part)
    u, v = grid(n)
    white = lin(0.85, 0.86, 0.86)
    blue = lin(0.05, 0.22, 0.60)
    if part == "top":
        alb = tint(white, n) * (1 + 0.02 * fnoise(n, 2.0, s))[..., None]
        grip = band(u, 0.3, 0.7, 0.01) * band(v, 0.42, 0.58, 0.01)
        alb = mix(alb, tint(lin(0.6, 0.62, 0.63), n), grip)
        return Mat(n, alb, 0.6 - 0.2 * grip, smooth=0.5, f0=10, relief=0.5)
    lid = (v < 0.3).astype(float)
    alb = mix(tint(blue, n), tint(white, n), lid) * (1 + 0.03 * fnoise(n, 2.0, s))[..., None]
    gap = band(v, 0.29, 0.33, 0.005)
    alb = mix(alb, alb * 0.5, gap)
    ribs = (np.abs((u * 6) % 1.0 - 0.5) < 0.04) * (v > 0.4) * (v < 0.92)
    alb = mix(alb, alb * 0.85, ribs.astype(float))
    return Mat(n, alb, 0.6 - 0.2 * gap - 0.1 * ribs, smooth=0.5, f0=10, relief=0.5)


def bucket(name, part, n=MID):
    """5 gallon white HDPE bucket: horizontal stiffening ribs near the rim (side); dark interior with rim (top)."""
    s = sd(name + part)
    u, v = grid(n)
    white = lin(0.82, 0.82, 0.80)
    if part == "top":
        r = np.maximum(np.abs(u - 0.5), np.abs(v - 0.5))
        inner = (r < 0.4).astype(float)
        alb = mix(tint(white, n), tint(lin(0.45, 0.45, 0.43), n) * (1 + 0.1 * fnoise(n, 2.0, s))[..., None], inner)
        return Mat(n, alb, 0.6 - 0.3 * inner, smooth=0.45, f0=10, relief=0.5)
    ribs = sum(band(v, a, a + 0.03, 0.01) for a in (0.06, 0.12, 0.2))
    dirt = smoothstep(0.6, 1.0, v) * 0.1 * (1 + fnoise(n, 2.0, s))
    alb = tint(white, n) * (1 - 0.15 * ribs - np.clip(dirt, 0, 0.2))[..., None]
    return Mat(n, alb, 0.6 + 0.2 * ribs, smooth=0.45, f0=10, relief=0.5)


def hose(name, part, n=MID):
    """Green garden hose coiled on a reel: coil (front, concentric loops with round cutout), wrap (side, parallel
    loops), reel (dark grey plastic frame)."""
    s = sd(name + part)
    u, v = grid(n)
    green = lin(0.05, 0.30, 0.07)
    if part == "reel":
        alb = tint(lin(0.10, 0.10, 0.11), n) * (1 + 0.05 * fnoise(n, 2.0, s))[..., None]
        return Mat(n, alb, 0.5, smooth=0.4, f0=10, relief=0.3)
    if part == "coil":
        r = np.sqrt((u - 0.5) ** 2 + (v - 0.5) ** 2)
        loops = 0.5 + 0.5 * np.cos(r * 2 * np.pi * 7)
        hub = smoothstep(0.17, 0.15, r)
        alb = tint(green, n) * (0.6 + 0.5 * loops)[..., None]
        alb = mix(alb, tint(lin(0.10, 0.10, 0.11), n), hub)
        alpha = (r < 0.5).astype(float)
        return Mat(n, alb, 0.3 + 0.5 * loops * (1 - hub), alpha=alpha, smooth=0.55, f0=10, relief=0.7)
    loops = 0.5 + 0.5 * np.cos(u * 2 * np.pi * 8)
    stripe = band((u * 8) % 1.0, 0.45, 0.55, 0.02) * 0.4
    alb = tint(green, n) * (0.6 + 0.5 * loops)[..., None]
    alb = mix(alb, tint(lin(0.6, 0.6, 0.1), n), stripe * loops)
    return Mat(n, alb, 0.3 + 0.5 * loops, smooth=0.55, f0=10, relief=0.7)
