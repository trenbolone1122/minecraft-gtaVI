# Stage 2: from the classified map build the final per-metre surface layer (with road markings, sidewalks,
# street lights, bridges, runways), the height field, building footprints, the GPS road grid and the
# place list, and pack them into compact binary files for the mod jar.
# Map art: YANIS community map (CC BY 4.0, YANIS / map.gtadb.org).
import sys, os, time, json, struct, zlib, numpy as np, cv2
from scipy import ndimage as ndi

T0 = time.time()
def log(*a):
    print("[%6.1fs]" % (time.time() - T0), *a, flush=True)

IN = sys.argv[1] if len(sys.argv) > 1 else "/root/worlddata/cls"
OUTDIR = sys.argv[2] if len(sys.argv) > 2 else "/root/worlddata/out"
OX = int(sys.argv[3]) if len(sys.argv) > 3 else 0     # mosaic offset of the input (for crops)
OY = int(sys.argv[4]) if len(sys.argv) > 4 else 0
os.makedirs(OUTDIR, exist_ok=True)
ORIGIN = -11008          # block x/z of mosaic pixel 0 (game origin is pixel 16384 of the 32768 world)

# classifier ids
U, WATER, BEACH, IND, URB, PARK, WET, LAND, ROAD, HWY, ALLEY, BLD, BLDI, RAIL, DIRT, FOOT, FARM, HATCH, OUTL, RAILBED, METRO = range(21)
# shipped ids (keep in sync with dev.leonida.world.map.Cls)
S_OCEAN, S_WATER, S_BEACH, S_LAND, S_PARK, S_SWAMP, S_URBAN, S_INDUSTRIAL, S_ROAD, S_HIGHWAY, S_ALLEY, S_BUILDING, \
    S_RAIL, S_DIRT, S_FOOTPATH, S_FARM, S_RUNWAY, S_SIDEWALK, S_BRIDGE_ROAD, S_BRIDGE_HWY, S_BRIDGE_RAIL, S_SCRUB = range(22)
M_CENTER, M_DASH, M_EDGE, M_LIGHT, M_PALM, M_CROSS = 1, 2, 3, 4, 5, 6

def disk(r):
    return cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * r + 1, 2 * r + 1))

cls = np.load(IN + "_cls.npy")
G = np.load(IN + "_G.npy")
elev = np.load(IN + "_elev.npy")
H, W = cls.shape
log("loaded", W, H)

# ---------------------------------------------------------------- surface classes
water = cls == WATER
# ocean = water connected to the map border (or big); the rest is inland water
n, lab = cv2.connectedComponents(water.astype(np.uint8), connectivity=4)
border = np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
sizes = np.bincount(lab.ravel(), minlength=n)
oceanlab = np.zeros(n, bool); oceanlab[border] = True; oceanlab |= sizes > 3_000_000; oceanlab[0] = False
ocean = oceanlab[lab]
del lab
log("ocean split", int(ocean.sum()), int((water & ~ocean).sum()))

S = np.full(cls.shape, S_LAND, np.uint8)
mapping = {WATER: S_WATER, BEACH: S_BEACH, IND: S_INDUSTRIAL, URB: S_URBAN, PARK: S_PARK, WET: S_SWAMP, LAND: S_LAND,
           ROAD: S_ROAD, HWY: S_HIGHWAY, ALLEY: S_ALLEY, BLD: S_BUILDING, BLDI: S_BUILDING, RAIL: S_RAIL, DIRT: S_DIRT,
           FOOT: S_FOOTPATH, FARM: S_FARM, HATCH: S_SCRUB, METRO: S_RAIL}
for a, b in mapping.items():
    S[cls == a] = b
S[ocean] = S_OCEAN

m = np.load("/root/worlddata/mosaic.npy", mmap_mode="r")
img = np.asarray(m[OY:OY + H, OX:OX + W])

# road repair: labels, route lines and metro lines drawn over streets and causeways broke the road mask.
# Grow the roads back through raw white road paint, dark overlay ink and metro lines, but only close to
# existing road (fills gaps, never invents label-shaped roads far from streets).
drive0 = (S == S_ROAD) | (S == S_HIGHWAY)
white = (img[..., 0] >= 235) & (img[..., 1] >= 235) & (img[..., 2] >= 200)
# over open water the causeway paint is noisy light grey (jpeg), so accept any light neutral there
wz = cv2.morphologyEx(((S == S_OCEAN) | (S == S_WATER)).astype(np.uint8), cv2.MORPH_CLOSE, disk(22)) == 1
mn_ = img.min(axis=2); mx_ = img.max(axis=2)
white |= wz & (mn_ >= 200) & (mx_.astype(np.int16) - mn_ < 32)
del mn_, mx_
dark = img.max(axis=2) < 150
cand = (white | dark | drive0 | (cls == METRO) | (cls == U)).astype(np.uint8)
n, lab = cv2.connectedComponents(cand, connectivity=8)
hit = np.zeros(n, bool); hit[np.unique(lab[drive0])] = True; hit[0] = False
recon = hit[lab]
del lab, cand
recon = cv2.morphologyEx(recon.astype(np.uint8), cv2.MORPH_OPEN, disk(3)) == 1
near = cv2.distanceTransform((~drive0).astype(np.uint8), cv2.DIST_L2, 5) <= 14
add = recon & near & ~drive0 & (cls != BLD) & (cls != BLDI) & (cls != RAIL) & (cls != RAILBED)
# causeways: white road paint crossing open water, connected to the street network, may be far from any
# surviving road pixel
add |= recon & white & wz & ~drive0 & (cls != BLD) & (cls != BLDI)
del wz
# highway paint is yellowish: added pixels take the class of the nearest original road pixel
_, idx = ndi.distance_transform_edt(~drive0, return_indices=True)
S[add] = S[idx[0][add], idx[1][add]]
del idx, recon, near, white, dark
drive1 = ((S == S_ROAD) | (S == S_HIGHWAY)).astype(np.uint8)
drive1 = cv2.morphologyEx(drive1, cv2.MORPH_CLOSE, disk(3))
gap = (drive1 == 1) & ~((S == S_ROAD) | (S == S_HIGHWAY)) & (cls != BLD) & (cls != BLDI)
_, idx = ndi.distance_transform_edt(~((S == S_ROAD) | (S == S_HIGHWAY)), return_indices=True)
S[gap] = S[idx[0][gap], idx[1][gap]]
del idx, gap
# causeway gaps: close the road mask along straight lines (12 directions, 61 m) inside the water zone
wz = cv2.morphologyEx(((S == S_OCEAN) | (S == S_WATER)).astype(np.uint8), cv2.MORPH_CLOSE, disk(22)) == 1
drv = ((S == S_ROAD) | (S == S_HIGHWAY)).astype(np.uint8)
L = 61
kernels = []
for k in range(12):
    a = np.pi * k / 12
    ker = np.zeros((L, L), np.uint8)
    dx, dy = np.cos(a) * (L // 2), np.sin(a) * (L // 2)
    cv2.line(ker, (int(round(L // 2 - dx)), int(round(L // 2 - dy))), (int(round(L // 2 + dx)), int(round(L // 2 + dy))), 1, 1)
    kernels.append(ker)
TS, P = 512, L
link = np.zeros(S.shape, bool)
for ty in range(0, H, TS):
    for tx in range(0, W, TS):
        if not (wz[ty:ty + TS, tx:tx + TS].any() and drv[ty:ty + TS, tx:tx + TS].any()):
            continue
        y0, x0 = max(0, ty - P), max(0, tx - P)
        sub = drv[y0:ty + TS + P, x0:tx + TS + P]
        acc = np.zeros(sub.shape, np.uint8)
        for ker in kernels:
            acc |= cv2.morphologyEx(sub, cv2.MORPH_CLOSE, ker)
        link[ty:ty + TS, tx:tx + TS] = acc[ty - y0:ty - y0 + TS, tx - x0:tx - x0 + TS] == 1
lk = link & wz & (drv == 0) & (cls != BLD) & (cls != BLDI)
_, idx = ndi.distance_transform_edt(drv == 0, return_indices=True)
S[lk] = S[idx[0][lk], idx[1][lk]]
log("causeway links", int(lk.sum()))
del link, lk, idx, wz, drv, kernels
# drop road specks floating in water (map label text): components with no land around them
drv = ((S == S_ROAD) | (S == S_HIGHWAY)).astype(np.uint8)
n, lab, st, _ = cv2.connectedComponentsWithStats(drv, connectivity=8)
landish = ~((S == S_OCEAN) | (S == S_WATER) | (drv == 1))
ring = cv2.dilate(drv, disk(4)) == 1
# a component touches land if any land pixel lies in its dilation: map land pixels to the nearest comp
_, idx = ndi.distance_transform_edt(drv == 0, return_indices=True)
lm = ring & landish
touch = np.zeros(n, bool); touch[np.unique(lab[idx[0][lm], idx[1][lm]])] = True
touch[0] = False
kill = ~touch[lab] & (drv == 1)
S[kill] = S_WATER
S[kill & ocean] = S_OCEAN
del lab, idx, ring, landish, lm, kill, drv
log("road repair", int(add.sum()), "added")
del add

# scrub: land with the vertical hatching texture of the original art (recomputed from the mosaic)
hatch = ((np.abs(img[..., 0].astype(np.int16) - 0x9A) < 10) & (np.abs(img[..., 1].astype(np.int16) - 0xB2) < 10) &
         (np.abs(img[..., 2].astype(np.int16) - 0x66) < 12)).astype(np.float32)
hd = cv2.boxFilter(hatch, -1, (41, 41))
S[(hd > 0.06) & (S == S_LAND)] = S_SCRUB
del img, hatch, hd
log("scrub")

# bridges: road/highway/rail crossing water (water closes over the deck)
wat = (S == S_OCEAN) | (S == S_WATER)
wclose = cv2.morphologyEx(wat.astype(np.uint8), cv2.MORPH_CLOSE, disk(22)) == 1
S[wclose & (S == S_ROAD)] = S_BRIDGE_ROAD
S[wclose & (S == S_HIGHWAY)] = S_BRIDGE_HWY
S[wclose & (S == S_RAIL)] = S_BRIDGE_RAIL
del wclose
# bridge pieces not connected to any street on land are label text or broken fragments: back to water
drv = ((S == S_ROAD) | (S == S_HIGHWAY) | (S == S_BRIDGE_ROAD) | (S == S_BRIDGE_HWY)).astype(np.uint8)
n, lab = cv2.connectedComponents(drv, connectivity=8)
keep = np.zeros(n, bool); keep[np.unique(lab[(S == S_ROAD) | (S == S_HIGHWAY)])] = True; keep[0] = True
lone = ~keep[lab]
S[lone] = S_WATER; S[lone & ocean] = S_OCEAN
log("lone bridge pixels removed", int(lone.sum()))
del lab, lone, drv
log("bridges", int(((S >= S_BRIDGE_ROAD) & (S <= S_BRIDGE_RAIL)).sum()))

# runways: very wide white road areas with a long extent
roadm = (S == S_ROAD).astype(np.uint8)
d = cv2.distanceTransform(roadm, cv2.DIST_L2, 5)
wide = (d >= 15).astype(np.uint8)
n, lab, st, _ = cv2.connectedComponentsWithStats(wide, connectivity=8)
longc = np.maximum(st[:, 2], st[:, 3]) >= 350
longc[0] = False
rw = cv2.dilate(longc[lab].astype(np.uint8), disk(16))
S[(rw == 1) & (roadm == 1)] = S_RUNWAY
del lab, rw, d, wide
log("runways", int(longc.sum()))

# sidewalks: paved urban ground next to streets
streets = ((S == S_ROAD) | (S == S_ALLEY)).astype(np.uint8)
dst = cv2.distanceTransform(1 - streets, cv2.DIST_L2, 3)
side = (dst <= 3.2) & ((S == S_URBAN) | (S == S_INDUSTRIAL) | (S == S_LAND) | (S == S_PARK))
S[side] = S_SIDEWALK
log("sidewalks", int(side.sum()))

# ---------------------------------------------------------------- markings (top 3 bits)
mark = np.zeros(cls.shape, np.uint8)
carr = ((S == S_ROAD) | (S == S_BRIDGE_ROAD)).astype(np.uint8)
hw = ((S == S_HIGHWAY) | (S == S_BRIDGE_HWY)).astype(np.uint8)

def coords_sum():
    # (x + z) per pixel without allocating two full int arrays at once
    return (np.arange(W, dtype=np.int32)[None, :] + np.arange(H, dtype=np.int32)[:, None])

xz = coords_sum()
dash = ((xz >> 2) & 1) == 0
# streets: yellow centre line where the street is at least 8 m wide
d = cv2.distanceTransform(carr, cv2.DIST_L2, 5)
mx = ndi.maximum_filter(d, size=3)
ridge = (carr == 1) & (d >= 4.0) & (d >= mx - 0.01)
mark[ridge] = M_CENTER
# wide streets (>= 14 m): dashed lane line 3.5 m from the kerb
wloc = ndi.maximum_filter(d, size=21)
lane = (carr == 1) & (wloc >= 7) & (np.abs(d - 3.6) < 0.5) & dash
mark[lane & (mark == 0)] = M_DASH
del d, mx, ridge, wloc, lane
# highways: solid edge line, dashed lanes every 3.7 m, solid line in the middle of narrow carriageways
d = cv2.distanceTransform(hw, cv2.DIST_L2, 5)
wloc = ndi.maximum_filter(d, size=25)
edge = (hw == 1) & (d >= 1.0) & (d < 2.0) & (wloc >= 4)
mark[edge] = M_EDGE
for k in (1, 2, 3, 4):
    ln = (hw == 1) & (np.abs(d - (1.5 + 3.7 * k)) < 0.5) & (d < wloc - 2.0) & dash
    mark[ln & (mark == 0)] = M_DASH
del d, wloc, edge
log("markings")

# street lights: one per 22 m cell on the kerb side of sidewalks; palms in between
kerb = (S == S_SIDEWALK) & (dst >= 0.9) & (dst <= 1.6)
ky, kx = np.nonzero(kerb)
def pick(cell, off):
    cx = (kx + off) // cell; cy = (ky + off) // cell
    key = cy.astype(np.int64) * 100000 + cx
    sc = (np.abs((kx + off) % cell - cell // 2) + np.abs((ky + off) % cell - cell // 2)).astype(np.int64)
    order = np.lexsort((sc, key))
    keys = key[order]
    first = np.ones(len(keys), bool); first[1:] = keys[1:] != keys[:-1]
    sel = order[first]
    return ky[sel], kx[sel]
ly, lx = pick(22, 0)
mark[ly, lx] = M_LIGHT
py_, px_ = pick(22, 11)
ok = mark[py_, px_] == 0
mark[py_[ok], px_[ok]] = M_PALM
del dst, kerb, ky, kx
log("lights", len(ly), "palms", int(ok.sum()))

final = (S | (mark << 5)).astype(np.uint8)

# ---------------------------------------------------------------- heights (4 m grid, decimetres)
C = 4
H4, W4 = (H + C - 1) // C, (W + C - 1) // C
def pool_mean(a):
    p = np.full((H4 * C, W4 * C), np.nan, np.float32); p[:H, :W] = a
    return np.nanmean(p.reshape(H4, C, W4, C), axis=(1, 3))
import warnings; warnings.filterwarnings("ignore")
e = elev.copy(); e[e <= 5] = 2.0
B = 16
Hb, Wb = (H + B - 1) // B, (W + B - 1) // B
pe = np.full((Hb * B, Wb * B), np.nan, np.float32); pe[:H, :W] = e
blk = pe.reshape(Hb, B, Wb, B).transpose(0, 2, 1, 3).reshape(Hb, Wb, B * B)
frac = np.mean(~np.isnan(blk), axis=2)
Eb = np.nanmedian(blk, axis=2)
Eb[frac < 0.6] = np.nan
Eb[np.isnan(Eb) & (frac < 0.6)] = np.nan
if np.all(np.isnan(Eb)): Eb[:] = 2.0
idx = ndi.distance_transform_edt(np.isnan(Eb), return_distances=False, return_indices=True)
Eb = Eb[idx[0], idx[1]]
Eb = cv2.GaussianBlur(Eb, (0, 0), 1.2)
E4 = cv2.resize(Eb, (Wb * (B // C), Hb * (B // C)), interpolation=cv2.INTER_LINEAR)[:H4, :W4]
E4 = cv2.GaussianBlur(E4, (0, 0), 3)
del pe, blk
wat4 = pool_mean(np.where(wat, 1.0, 0.0).astype(np.float32)) > 0.5
Gf = np.where(wat, G.astype(np.float32), np.nan)
G4 = pool_mean(Gf)
idx = ndi.distance_transform_edt(np.isnan(G4), return_distances=False, return_indices=True)
G4 = G4[idx[0], idx[1]]
depth = np.interp(G4, [100, 115, 140, 160, 175], [24, 16, 8, 3, 1.5]).astype(np.float32)
depth = cv2.GaussianBlur(depth, (0, 0), 1.5)
# distance to shore keeps the sea floor sloping from the beach
dshore = ndi.distance_transform_edt(wat4) * C
depth = np.minimum(depth, 1.0 + dshore * 0.25)
Hm = np.where(wat4, -depth, np.maximum(E4, 1.0))
H16 = np.clip(np.round(Hm * 10), -32000, 32000).astype(np.int16)
log("heights", float(Hm.min()), float(Hm.max()))

# ---------------------------------------------------------------- buildings
places = json.load(open("/root/worlddata/places.json"))
P = []
for p in places:
    if not p.get("g"):
        continue
    gx, gy = p["g"]
    bx, bz = gx, -gy                 # block coordinates
    P.append((bx, bz, p))
pbx = np.array([q[0] for q in P]); pbz = np.array([q[1] for q in P])

LANDMARKS = {"L266": "jason_house", "L475": "lucia_home", "L553": "el_gas_station", "L556": "club_vice",
             "L1869": "gym", "L221": "office_floor", "L304": "motel_room"}
CORES = [(-804, 441, 700), (-1114, 1479, 650), (1450, -1000, 500), (-6300, -3200, 350)]   # downtown, Tequesta, Vice Beach, Port Gellhorn

KINDS = ["HOUSE", "APARTMENT", "TOWER", "SHOP", "GAS_STATION", "MOTEL", "CLUB", "OFFICE", "WAREHOUSE", "BANK", "HOTEL",
         "MALL", "POLICE", "HOSPITAL", "MARINA", "LANDMARK"]
K = {k: i for i, k in enumerate(KINDS)}

bm = (cls == BLD) | (cls == BLDI)
n, lab, st, cen = cv2.connectedComponentsWithStats(bm.astype(np.uint8), connectivity=4)
log("building comps", n)
# dominant ground around each building
beachd = cv2.distanceTransform((S != S_BEACH).astype(np.uint8), cv2.DIST_L2, 3)
roadmask = ((S == S_ROAD) | (S == S_ALLEY) | (S == S_HIGHWAY)).astype(np.uint8)
_, ridx = ndi.distance_transform_edt(roadmask == 0, return_indices=True)
indus = (S == S_INDUSTRIAL)

def h01(i, salt):
    v = (i * 2654435761 + salt * 40503) & 0xFFFFFFFF
    v ^= v >> 13; v = (v * 1274126177) & 0xFFFFFFFF; v ^= v >> 16
    return v / 4294967296.0

buildings = []
contours_all = {}
for i in range(1, n):
    x, y, w, h, area = st[i]
    if area < 30:
        continue
    sub = (lab[y:y + h, x:x + w] == i).astype(np.uint8)
    cs, _ = cv2.findContours(sub, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    c = max(cs, key=cv2.contourArea)
    eps = 1.2 if area < 2000 else 2.0
    poly = cv2.approxPolyDP(c, eps, True).reshape(-1, 2)
    if len(poly) < 3:
        continue
    if len(poly) > 40:
        poly = cv2.approxPolyDP(c, 4.0, True).reshape(-1, 2)
    # convert to block coords; CCW in x/z (z down) means clockwise in image space -> keep orientation consistent
    pts = [(int(px + x + OX + ORIGIN), int(py + y + OY + ORIGIN)) for px, py in poly]
    a2 = sum(pts[k][0] * pts[(k + 1) % len(pts)][1] - pts[(k + 1) % len(pts)][0] * pts[k][1] for k in range(len(pts)))
    if a2 < 0:
        pts = pts[::-1]
    bx0, bz0 = x + OX + ORIGIN, y + OY + ORIGIN
    cxb, czb = cen[i][0] + OX + ORIGIN, cen[i][1] + OY + ORIGIN
    # landmarks inside or within 6 m of the bbox
    sel = np.nonzero((pbx >= bx0 - 6) & (pbx <= bx0 + w + 6) & (pbz >= bz0 - 6) & (pbz <= bz0 + h + 6))[0]
    tags, names, lmkey, lmid = set(), [], None, None
    for j in sel:
        p = P[j][2]
        tags.update(p["t"]); names.append(p["n"])
        if p["id"] in LANDMARKS:
            lmkey = LANDMARKS[p["id"]]; lmid = p["id"]
    nm = " ".join(names).lower()
    ccx, ccy = int(cen[i][0]), int(cen[i][1])
    win = indus[max(0, y - 6):y + h + 6, max(0, x - 6):x + w + 6]
    indfrac = win.mean() if win.size else 0
    nearbeach = beachd[ccy, ccx] < 160
    core = max([np.exp(-np.hypot(cxb - a, czb - b) / r) for a, b, r in CORES])
    r1, r2 = h01(i, 1), h01(i, 2)
    if "#gas" in tags: kind = "GAS_STATION"
    elif "#police" in tags: kind = "POLICE"
    elif "#hospital" in tags: kind = "HOSPITAL"
    elif "#marina" in tags: kind = "MARINA"
    elif "motel" in nm or " inn" in nm: kind = "MOTEL"
    elif "hotel" in tags: kind = "HOTEL"
    elif "bank" in nm or "lombank" in nm: kind = "BANK"
    elif tags & {"#nightclub", "#stripclub", "#bar"}: kind = "CLUB"
    elif "#gym" in tags: kind = "SHOP"
    elif "office" in tags: kind = "TOWER" if area > 900 and core > 0.2 else "OFFICE"
    elif tags & {"retail", "restaurant", "#supermarket"}: kind = "MALL" if area > 8000 else "SHOP"
    elif "industrial" in tags: kind = "WAREHOUSE"
    elif "residential" in tags: kind = "HOUSE" if area < 350 else ("TOWER" if area > 900 and core > 0.3 else "APARTMENT")
    elif indfrac > 0.25: kind = "WAREHOUSE"
    elif area < 260: kind = "HOUSE"
    elif area < 1100:
        kind = "SHOP" if r1 < 0.45 else ("APARTMENT" if core < 0.25 else "OFFICE")
        if nearbeach and area > 500: kind = "HOTEL"
    else:
        if core > 0.25: kind = "TOWER"
        elif nearbeach: kind = "HOTEL"
        elif area > 9000: kind = "MALL" if r1 < 0.5 else "WAREHOUSE"
        else: kind = "APARTMENT" if r1 < 0.6 else "OFFICE"
    fl = {"HOUSE": 1 + (r2 < 0.35), "SHOP": 1 + (r2 < 0.3), "WAREHOUSE": 1 + (r2 < 0.4), "GAS_STATION": 1, "MOTEL": 2,
          "MARINA": 1, "CLUB": 2, "MALL": 2 + (r2 < 0.5), "POLICE": 3, "HOSPITAL": 5 + int(r2 * 4), "BANK": 3 + int(r2 * 3),
          "APARTMENT": 3 + int(r2 * 6), "OFFICE": 4 + int(r2 * 8 + core * 10)}.get(kind)
    if kind == "HOTEL": fl = 4 + int(r2 * 10 + core * 12) if area > 500 else 3
    if kind == "TOWER": fl = int(14 + r2 * 20 + core * 30)
    fl = int(min(max(fl, 1), 60))
    # entrance: nearest road pixel to the centroid, then the building edge facing it
    ry, rx = ridx[0][ccy, ccx], ridx[1][ccy, ccx]
    dirx, diry = rx - ccx, ry - ccy
    L = max(1e-6, np.hypot(dirx, diry)); dirx /= L; diry /= L
    ex, ey = float(ccx), float(ccy)
    for step in range(0, 400):
        tx, ty = int(round(ccx + dirx * step)), int(round(ccy + diry * step))
        if not (0 <= tx < W and 0 <= ty < H) or lab[ty, tx] != i:
            ex, ey = ccx + dirx * (step + 1.5), ccy + diry * (step + 1.5)
            break
    ent = (int(round(ex)) + OX + ORIGIN, int(round(ey)) + OY + ORIGIN)
    # base height: median ground inside
    hy0, hx0 = y // C, x // C
    hb = np.median(H16[hy0:(y + h) // C + 1, hx0:(x + w) // C + 1]) / 10.0
    if kind in ("HOUSE", "MOTEL", "GAS_STATION"):
        # lot: the outline grown by 2 m (axis-aligned growth of each vertex away from the centroid)
        pts = [(int(px + np.sign(px - cxb) * 2), int(pz + np.sign(pz - czb) * 2)) for px, pz in pts]
    buildings.append(dict(id=len(buildings) + 1, kind=K[kind], floors=fl, lm=lmkey or "", poly=pts,
                          ent=ent, base=max(1.0, hb), area=int(area), lmid=lmid))
    if lmkey:
        LANDMARKS.pop(lmid, None)
log("buildings", len(buildings), "unplaced landmarks", LANDMARKS)
# landmarks without a traced footprint get a synthetic lot at the marker
LOT = {"jason_house": (24, 20, "HOUSE", 1), "lucia_home": (18, 14, "APARTMENT", 3), "el_gas_station": (36, 28, "GAS_STATION", 1),
       "club_vice": (30, 24, "CLUB", 2), "gym": (24, 18, "SHOP", 2), "office_floor": (20, 20, "TOWER", 30), "motel_room": (40, 16, "MOTEL", 2)}
for lid, key in list(LANDMARKS.items()):
    p = next(q for q in places if q["id"] == lid)
    if not p.get("g"): continue
    bx, bz = int(p["g"][0]), int(-p["g"][1])
    w_, h_, kd, fl = LOT[key]
    pts = [(bx - w_ // 2, bz - h_ // 2), (bx - w_ // 2, bz + h_ // 2), (bx + w_ // 2, bz + h_ // 2), (bx + w_ // 2, bz - h_ // 2)]
    mx_, my_ = bx - ORIGIN - OX, bz - ORIGIN - OY
    hb = 2.0
    if 0 <= mx_ < W and 0 <= my_ < H: hb = max(1.0, H16[my_ // C, mx_ // C] / 10.0)
    buildings.append(dict(id=len(buildings) + 1, kind=K[kd], floors=fl, lm=key, poly=pts, ent=(bx, bz + h_ // 2 + 2), base=hb,
                          area=w_ * h_, lmid=lid))
    log("synthetic landmark lot", key, bx, bz)
del lab, ridx

# ---------------------------------------------------------------- GPS road grid (4 m cells, skeleton)
from skimage.morphology import skeletonize
drive = ((S == S_ROAD) | (S == S_HIGHWAY) | (S == S_BRIDGE_ROAD) | (S == S_BRIDGE_HWY) | (S == S_ALLEY)).astype(np.uint8)
p = np.zeros((H4 * C, W4 * C), np.uint8); p[:H, :W] = drive
R4 = p.reshape(H4, C, W4, C).max(axis=(1, 3))
R4 = cv2.morphologyEx(R4, cv2.MORPH_CLOSE, disk(1))
SK = skeletonize(R4.astype(bool)).astype(np.uint8)
log("road skeleton cells", int(SK.sum()))

# ---------------------------------------------------------------- write
def write_map():
    R = 256
    nx, ny = (W + R - 1) // R, (H + R - 1) // R
    blobs = []
    for ry in range(ny):
        for rx in range(nx):
            t = np.full((R, R), S_OCEAN, np.uint8)
            blk = final[ry * R:(ry + 1) * R, rx * R:(rx + 1) * R]
            t[:blk.shape[0], :blk.shape[1]] = blk
            blobs.append(zlib.compress(t.tobytes(), 9))
    with open(OUTDIR + "/surface.bin", "wb") as f:
        f.write(b"LMS1"); f.write(struct.pack(">iiiiii", W, H, ORIGIN + OX, ORIGIN + OY, R, nx * ny))
        off = 0
        for b in blobs:
            f.write(struct.pack(">ii", off, len(b))); off += len(b)
        for b in blobs: f.write(b)
    with open(OUTDIR + "/height.bin", "wb") as f:
        f.write(b"LMH1"); f.write(struct.pack(">iiiii", W4, H4, ORIGIN + OX, ORIGIN + OY, C))
        f.write(zlib.compress(H16.astype(">i2").tobytes(), 9))
    with open(OUTDIR + "/roads.bin", "wb") as f:
        f.write(b"LMR1"); f.write(struct.pack(">iiiii", W4, H4, ORIGIN + OX, ORIGIN + OY, C))
        f.write(zlib.compress(np.packbits(SK.ravel()).tobytes(), 9))
    import io
    bio = io.BytesIO()
    bio.write(struct.pack(">i", len(buildings)))
    for b in buildings:
        lm = b["lm"].encode()
        bio.write(struct.pack(">iBBhiiH", b["id"], b["kind"], b["floors"], int(round(b["base"])), b["ent"][0], b["ent"][1], len(b["poly"])))
        for px, pz in b["poly"]: bio.write(struct.pack(">ii", px, pz))
        bio.write(struct.pack(">B", len(lm))); bio.write(lm)
    with open(OUTDIR + "/buildings.bin", "wb") as f:
        f.write(b"LMB1"); f.write(zlib.compress(bio.getvalue(), 9))
    # places: name, area, block x/z, flags
    bio = io.BytesIO()
    sel = [q for q in P]
    bio.write(struct.pack(">i", len(sel)))
    for bx, bz, p in sel:
        nm = p["n"].encode()[:250]; ar = p["a"].encode()[:250]
        generic = 1 if p.get("ns") == "unknown" else 0
        bio.write(struct.pack(">iiB", int(round(bx)), int(round(bz)), generic))
        bio.write(struct.pack(">B", len(nm))); bio.write(nm); bio.write(struct.pack(">B", len(ar))); bio.write(ar)
    with open(OUTDIR + "/places.bin", "wb") as f:
        f.write(b"LMP1"); f.write(zlib.compress(bio.getvalue(), 9))
write_map()
for fn in sorted(os.listdir(OUTDIR)):
    log(fn, os.path.getsize(OUTDIR + "/" + fn))
json.dump([{k: v for k, v in b.items() if k != "poly"} | {"n": len(b["poly"])} for b in buildings if b["lm"]], open(OUTDIR + "/landmarks.json", "w"))
np.save(OUTDIR + "/final.npy", final)
log("done")
