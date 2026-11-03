"""Packs community-world-v1 terrain into data/leonida/world/terrain.bin.

Inputs: rasters sampled by cw_terrain.mjs (2 m cells), ground-mask PNGs, shore-distance.png.
Per 2 m cell: class byte (1 grass, 2 sand, 3 paving, 4 dirt; 8+kind water), depth byte (water blocks),
height int16 dm (land ground or water surface). Regions of 256x256 cells, zlib, row-delta heights.
"""
import sys, zlib, struct
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

cw, tout, outfile = sys.argv[1:4]
W, H = 8192, 10240
g = np.fromfile(f'{tout}/ground-2.i16', np.int16).reshape(H, W)
lv = np.fromfile(f'{tout}/level-2.i16', np.int16).reshape(H, W)
water = np.fromfile(f'{tout}/water-2.u8', np.uint8).reshape(H, W).astype(bool)

mask = np.zeros((H, W, 4), np.uint8)
for i in range(8):
    for j in range(10):
        im = np.array(Image.open(f'{cw}/ground-mask-{i}_{j}.png').convert('RGBA'))
        mask[j*1024:(j+1)*1024, i*1024:(i+1)*1024] = im
land_cls = np.argmax(mask[..., :3], axis=2).astype(np.uint8) + 1  # 1 grass 2 sand 3 paving
low = mask[..., :3].max(axis=2) < 60
land_cls[low] = 4
del mask

shore = np.array(Image.open(f'{cw}/shore-distance.png').convert('L')).astype(np.float32) * 2.0  # 4 m px
shore = np.repeat(np.repeat(shore, 2, 0), 2, 1)[:H, :W]
# exact distance on 2 m grid for small bodies (shore png saturates at 510 m)
dist = ndi.distance_transform_edt(water) * 2.0
dist = np.maximum(dist, np.where(shore >= 500, shore, 0))

def frac(a, r):
    return ndi.uniform_filter(a.astype(np.float32), size=r, mode='nearest')

landf = frac(~water, 32)       # 64 m window
landf_big = frac(~water, 128)  # 256 m window
sand = (land_cls == 2) & ~water
sandf = frac(sand, 64)
pavef_big = frac((land_cls == 3) & ~water, 128)
openf = frac(water[::8, ::8], 250)  # ~4 km window on 16 m grid
openf = np.repeat(np.repeat(openf, 8, 0), 8, 1)[:H, :W]

lake = water & (lv > 5)
kind = np.full((H, W), 6, np.uint8)  # 6 open ocean mid blue
kind[openf < 0.8] = 1  # bay grey-teal
kind[dist > 300] = 3   # deep
kind[(dist < 70) & (sandf > 0.03)] = 0  # shallow turquoise beach water
chan = landf > 0.3
kind[chan] = 2
swamp = (landf_big > 0.45) & (pavef_big < 0.04)
kind[swamp] = 4
kind[lake] = 5

depth = np.zeros((H, W), np.float32)
d = dist
depth = np.select(
    [kind == 0, kind == 1, kind == 2, kind == 3, kind == 4, kind == 5, kind == 6],
    [1 + d / 25, 2 + d / 50, 2 + d / 12, 6 + d / 40, 1 + d / 30, 2 + d / 25, 3 + d / 30])
cap = np.select([kind == 0, kind == 1, kind == 2, kind == 3, kind == 4, kind == 5, kind == 6], [3, 7, 5, 30, 2, 6, 12])
depth = np.clip(np.minimum(depth, cap), 1, 40).astype(np.uint8)

cls = np.where(water, 8 + kind, land_cls).astype(np.uint8)
depth[~water] = 0
height = np.where(water, lv, g).astype(np.int16)
print('kinds', np.bincount(kind[water].ravel(), minlength=7) / water.sum())
print('land cls', np.bincount(land_cls[~water].ravel(), minlength=5) / (~water).sum())

R = 256
rw, rh = W // R, H // R
blobs = []
for rj in range(rh):
    for ri in range(rw):
        sl = (slice(rj*R, (rj+1)*R), slice(ri*R, (ri+1)*R))
        c = cls[sl]; dp = depth[sl]; h = height[sl].astype(np.int32)
        hd = np.diff(h, axis=1, prepend=0).astype(np.int16)
        raw = c.tobytes() + dp.tobytes() + hd.astype('>i2').tobytes()
        blobs.append(zlib.compress(raw, 9))
with open(outfile, 'wb') as f:
    f.write(b'LTR1'); f.write(struct.pack('>iiiii', -11264, -11264, 2, R, rw)); f.write(struct.pack('>i', rh))
    off = 0
    for b in blobs:
        f.write(struct.pack('>ii', off, len(b))); off += len(b)
    for b in blobs:
        f.write(b)
print('wrote', outfile, sum(len(b) for b in blobs) / 1e6, 'MB')
np.save(f'{tout}/cls.npy', cls); np.save(f'{tout}/height.npy', height); np.save(f'{tout}/depth.npy', depth)
