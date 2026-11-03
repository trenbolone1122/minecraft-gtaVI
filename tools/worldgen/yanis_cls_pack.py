"""Re-derive the land class of data/leonida/world/terrain.bin from the YANIS map art (classify.py output),
keeping every height, water cell, water kind and depth as they are.

Usage (build machine): python3 yanis_cls_pack.py terrain.bin /root/worlddata/cls_cls.npy out.bin
YANIS mosaic pixel 0 is block -11008 on both axes (post.py ORIGIN), 1 px = 1 m, pixel y grows south, so
block x = mapX = px - 11008 and block z = -mapY = py - 11008. terrain.bin cells are 2 m from block -11264.
Mapping: URB, IND, ALLEY, ROAD, HWY, BLD, BLDI -> PAVING (cement lots, plazas, parking); PARK, FARM, HATCH,
WET, LAND -> GRASS; BEACH -> SAND; DIRT, FOOT, RAIL, METRO -> DIRT. YANIS water over terrain land and
cells outside the art keep their old class.
"""
import sys, zlib, struct
import numpy as np

U, WATER, BEACH, IND, URB, PARK, WET, LAND, ROAD, HWY, ALLEY, BLD, BLDI, RAIL, DIRT, FOOT, FARM, HATCH, OUTL, RAILBED, METRO = range(21)
GRASS, SAND, PAVING, DIRTC = 1, 2, 3, 4
MAP = np.zeros(21, np.uint8)  # 0 = keep old class
for c in (URB, IND, ALLEY, ROAD, HWY, BLD, BLDI, OUTL, RAILBED): MAP[c] = PAVING
for c in (PARK, FARM, HATCH, WET, LAND): MAP[c] = GRASS
MAP[BEACH] = SAND
for c in (DIRT, FOOT, RAIL, METRO): MAP[c] = DIRTC
ORIGIN = -11008


def read(path):
    f = open(path, 'rb').read()
    magic, ox, oz, cell, R, rw, rh = struct.unpack_from('>4s6i', f, 0)
    n = rw * rh
    t = struct.unpack_from('>%di' % (2 * n), f, 28)
    start = 28 + 8 * n
    W, H = rw * R, rh * R
    cls = np.zeros((H, W), np.uint8); depth = np.zeros((H, W), np.uint8); hd = np.zeros((H, W), np.int16)
    for k in range(n):
        raw = zlib.decompress(f[start + t[2 * k]:start + t[2 * k] + t[2 * k + 1]])
        rj, ri = divmod(k, rw)
        sl = (slice(rj * R, (rj + 1) * R), slice(ri * R, (ri + 1) * R))
        cls[sl] = np.frombuffer(raw[:R * R], np.uint8).reshape(R, R)
        depth[sl] = np.frombuffer(raw[R * R:2 * R * R], np.uint8).reshape(R, R)
        hd[sl] = np.frombuffer(raw[2 * R * R:], '>i2').reshape(R, R)
    return (ox, oz, cell, R, rw, rh), cls, depth, hd


def write(path, hdr, cls, depth, hd):
    ox, oz, cell, R, rw, rh = hdr
    blobs = []
    for rj in range(rh):
        for ri in range(rw):
            sl = (slice(rj * R, (rj + 1) * R), slice(ri * R, (ri + 1) * R))
            blobs.append(zlib.compress(cls[sl].tobytes() + depth[sl].tobytes() + hd[sl].astype('>i2').tobytes(), 9))
    with open(path, 'wb') as f:
        f.write(b'LTR1'); f.write(struct.pack('>6i', ox, oz, cell, R, rw, rh))
        off = 0
        for b in blobs:
            f.write(struct.pack('>ii', off, len(b))); off += len(b)
        for b in blobs: f.write(b)


def share(cls, hdr, name):
    ox, oz, cell = hdr[:3]
    # Vice City: blocks x -2000..2600, z -2500..1500 (downtown, Little Havana, Vice Beach)
    i0, i1 = (-2000 - ox) // cell, (2600 - ox) // cell
    j0, j1 = (-2500 - oz) // cell, (1500 - oz) // cell
    c = cls[j0:j1, i0:i1]; land = c[c < 8]
    b = np.bincount(land, minlength=5) / max(1, land.size)
    print('%s Vice City land: grass %.1f%%, sand %.1f%%, paving %.1f%%, dirt %.1f%%' % (name, b[1] * 100, b[2] * 100, b[3] * 100, b[4] * 100))
    return b


def main(tin, ycls, tout):
    hdr, cls, depth, hd = read(tin)
    ox, oz, cell, R, rw, rh = hdr
    share(cls, hdr, 'before')
    y = np.load(ycls, mmap_mode='r')
    YH, YW = y.shape
    H, W = cls.shape
    # cell (j, i) covers blocks ox + cell*i .. +cell; take the YANIS pixel at the cell centre
    px = ox + cell * np.arange(W) + cell // 2 - ORIGIN
    py = oz + cell * np.arange(H) + cell // 2 - ORIGIN
    vx = (px >= 0) & (px < YW); vy = (py >= 0) & (py < YH)
    new = np.zeros((H, W), np.uint8)
    sub = np.asarray(y[py[vy][:, None], px[vx][None, :]])
    new[np.ix_(vy, vx)] = MAP[sub]
    land = cls < 8
    upd = land & (new > 0)
    cls = np.where(upd, new, cls)
    print('land cells', int(land.sum()), 'reclassified', int(upd.sum()))
    share(cls, hdr, 'after')
    write(tout, hdr, cls, depth, hd)
    print('wrote', tout)


if __name__ == '__main__':
    main(*sys.argv[1:4])
