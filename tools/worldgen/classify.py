# Classifies the YANIS map art (CC BY 4.0, YANIS / map.gtadb.org) into terrain classes.
# Runs on the build machine. Input: mosaic.npy (full res, 1 px = 1 m, verified with the map scale bar).
# Output: classes.npy (uint8 class per metre), elev.npy (band height in metres, NaN where unknown),
# depth.npy (water depth estimate in metres, NaN on land).
import sys, time, numpy as np, cv2
from scipy import ndimage as ndi

T0 = time.time()
def log(*a):
    print("[%6.1fs]" % (time.time() - T0), *a, flush=True)

MAPW = 14950            # legend panel starts right of this
MAPH = 20224
X0, Y0, X1, Y1 = [int(v) for v in sys.argv[1:5]] if len(sys.argv) >= 5 else (0, 0, MAPW, MAPH)
OUT = sys.argv[5] if len(sys.argv) >= 6 else "/root/worlddata/cls"

U, WATER, BEACH, IND, URB, PARK, WET, LAND, ROAD, HWY, ALLEY, BLD, BLDI, RAIL, DIRT, FOOT, FARM, HATCH, OUTL, RAILBED, METRO = range(21)
PAL = {
    BEACH: ["F2E483"], IND: ["EEE393", "EBDF93"], URB: ["D5DAD6", "D8DEDA", "D5D9D8", "D7DED7"],
    PARK: ["A8CF7E", "A4CB7C", "A1C879"], WET: ["87CA9E"],
    ROAD: ["FBFFFF", "FFFFFF", "F4F8F8"], HWY: ["FBFCD4", "F8F9CE"], ALLEY: ["E4E6E5"],
    BLD: ["C3C3C3", "CBCBCB", "BBBBBB", "B4B4B4"], BLDI: ["9FB3CC", "A8BAD0"], OUTL: ["777777", "797A74", "888888"],
    RAIL: ["872386"], RAILBED: ["989898"], METRO: ["CD33A5", "DA79AC"], DIRT: ["B08C50"], FOOT: ["C4986B"],
    FARM: ["82AF48"], HATCH: ["9AB266", "99B165", "9FB769"],
}
# elevation bands (legend, metres above sea level): colour -> representative height
ELEV = [("C1DB84", 4), ("C1DB82", 4), ("BDD780", 14), ("BAD37F", 30), ("B8D17E", 45), ("B6CF7C", 55), ("B2CB7A", 70),
        ("B0C978", 80), ("B3CC7B", 90), ("ADC577", 120), ("ABC375", 140), ("A7BF73", 175), ("A6BE72", 225),
        ("A3BB71", 275), ("9CB16E", 325), ("9BB26C", 375), ("95AC68", 420)]

def hexrgb(h):
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], np.float32)

def build_lut():
    q = np.arange(64, dtype=np.float32) * 4 + 2
    r, g, b = np.meshgrid(q, q, q, indexing="ij")
    rgb = np.stack([r, g, b], -1).reshape(-1, 3)
    cols, ids = [], []
    for c, hs in PAL.items():
        for h in hs:
            cols.append(hexrgb(h)); ids.append(c)
    for h, _ in ELEV:
        cols.append(hexrgb(h)); ids.append(LAND)
    cols = np.stack(cols); ids = np.array(ids, np.uint8)
    d = np.linalg.norm(rgb[:, None, :] - cols[None, :, :], axis=2)
    best = np.argmin(d, 1); bd = d[np.arange(len(rgb)), best]
    lut = np.where(bd < 16, ids[best], U).astype(np.uint8)
    # water: strongly blue, saturated
    water = (rgb[:, 2] - rgb[:, 0] > 70) & (rgb[:, 2] > 140)
    lut[water] = WATER
    # elevation lut: nearest elevation colour among land colours
    ecols = np.stack([hexrgb(h) for h, _ in ELEV]); eh = np.array([v for _, v in ELEV], np.float32)
    de = np.linalg.norm(rgb[:, None, :] - ecols[None], axis=2)
    elut = eh[np.argmin(de, 1)]
    return lut.reshape(64, 64, 64), elut.reshape(64, 64, 64)

def disk(r):
    return cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * r + 1, 2 * r + 1))

def main():
    lut, elut = build_lut()
    m = np.load("/root/worlddata/mosaic.npy", mmap_mode="r")
    img = np.ascontiguousarray(m[Y0:Y1, X0:X1])
    H, W = img.shape[:2]
    log("loaded", W, H)
    qi = (img >> 2)
    cls = lut[qi[..., 0], qi[..., 1], qi[..., 2]]
    G = img[..., 1].astype(np.float32)
    elev = np.where(cls == LAND, elut[qi[..., 0], qi[..., 1], qi[..., 2]], np.nan).astype(np.float32)
    del qi
    log("lut done", np.bincount(cls.ravel(), minlength=21))

    # remove grid lines (1 km grid at k*1000+7..8) and grid labels (bottom-left of each cell)
    def mask_rect(x0, y0, x1, y1, v=U):
        x0, x1 = max(0, x0 - X0), min(W, x1 - X0); y0, y1 = max(0, y0 - Y0), min(H, y1 - Y0)
        if x0 < x1 and y0 < y1: cls[y0:y1, x0:x1] = v
    for k in range(0, 22):
        g = k * 1000 + 7
        mask_rect(g - 1, 0, g + 3, 10 ** 6); mask_rect(0, g - 1, 10 ** 6, g + 3)
        for k2 in range(0, 22):
            gx = k2 * 1000 + 7
            mask_rect(gx + 3, g - 72, gx + 150, g - 1)
    # scale bar and anything right of the map frame -> ocean
    mask_rect(10800, 18600, 14600, 19450, WATER)
    mask_rect(MAPW, 0, 10 ** 6, 10 ** 6, WATER)
    log("grid masked")

    # map labels and icons have dark outlines (much darker than building outlines): find dense dark areas
    dark = (img.max(axis=2) < 108).astype(np.float32)
    dens = cv2.boxFilter(dark, -1, (15, 15))
    zone = cv2.dilate((dens > 0.10).astype(np.uint8), disk(4))
    lbl = (zone == 1) & np.isin(cls, [U, ROAD, HWY, ALLEY, OUTL, BLD, RAILBED])
    cls[lbl] = U
    del dark, dens, zone
    log("label areas removed", int(lbl.sum())); del lbl
    # roads: drop thin label halos and stray white text in the water
    water = cls == WATER
    for c in (ROAD, HWY, ALLEY):
        mk = (cls == c).astype(np.uint8)
        op = cv2.morphologyEx(mk, cv2.MORPH_OPEN, disk(2))
        cls[(mk == 1) & (op == 0)] = U
    rd = ((cls == ROAD) | (cls == HWY)).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(rd, connectivity=8)
    nearw = cv2.dilate(water.astype(np.uint8), disk(6))
    wfrac = ndi.mean(nearw, lab, index=np.arange(n))
    bad = (stats[:, cv2.CC_STAT_AREA] < 4000) & (wfrac > 0.5)
    bad |= stats[:, cv2.CC_STAT_AREA] < 60
    bad[0] = False
    cls[bad[lab] & (rd == 1)] = U
    del lab, nearw
    log("roads cleaned, removed comps", int(bad.sum()))

    # small water blobs are route shields, pools and icons, not lakes
    wm = (cls == WATER).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(wm, connectivity=4)
    small = stats[:, cv2.CC_STAT_AREA] < 1500
    small[0] = False
    cls[small[lab] & (wm == 1)] = U
    del lab
    log("small water removed", int(small.sum()))

    # buildings: outline, roof details and rail-bed grey join the building; holes are filled;
    # opening kills label strokes
    bmask = ((cls == BLD) | (cls == BLDI) | (cls == OUTL) | (cls == RAILBED)).astype(np.uint8)
    bmask = cv2.morphologyEx(bmask, cv2.MORPH_CLOSE, disk(2))
    bmask = ndi.binary_fill_holes(bmask).astype(np.uint8)
    bop = cv2.morphologyEx(bmask, cv2.MORPH_OPEN, disk(3))
    cls[(bmask == 1) & (bop == 0) & (cls != RAIL)] = U
    cls[(cls == OUTL) | (cls == RAILBED)] = U
    keep = (bop == 1) & (cls != ROAD) & (cls != HWY) & (cls != RAIL) & (cls != WATER)
    cls[keep] = BLD
    del bmask, bop, keep
    log("buildings cleaned")

    # fill unknown pixels with the nearest known class
    unk = cls == U
    log("unknown px", int(unk.sum()))
    idx = ndi.distance_transform_edt(unk, return_distances=False, return_indices=True)
    cls = cls[idx[0], idx[1]]
    del idx
    log("filled")
    # majority filter (7x7) to remove speckle; thin rail lines are restored afterwards
    rails = cv2.dilate(((cls == RAIL) | (cls == METRO)).astype(np.uint8), disk(1))
    best = np.zeros(cls.shape, np.uint16); bestc = cls.copy()
    for c in range(1, 21):
        mk = (cls == c)
        if not mk.any(): continue
        cnt = cv2.boxFilter(mk.astype(np.uint16), -1, (7, 7), normalize=False, borderType=cv2.BORDER_REPLICATE)
        upd = cnt > best
        best[upd] = cnt[upd]; bestc[upd] = c
    cls = bestc
    cls[(rails == 1) & (cls != WATER)] = RAIL
    del best, rails
    log("majority filtered")
    np.save(OUT + "_cls.npy", cls)
    np.save(OUT + "_G.npy", G.astype(np.uint8))
    np.save(OUT + "_elev.npy", elev)
    log("saved")

if __name__ == "__main__":
    main()
