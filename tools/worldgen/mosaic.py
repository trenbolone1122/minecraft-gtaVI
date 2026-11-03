# Builds the full-resolution mosaic of the YANIS map (zoom 5 = 32768 px world) as a numpy memmap.
import os, sys, numpy as np
from PIL import Image
T = "/root/worlddata/tiles/5"
X0, X1, Y0, Y1 = 21, 103, 21, 99
W, H = (X1 - X0 + 1) * 256, (Y1 - Y0 + 1) * 256
out = np.lib.format.open_memmap("/root/worlddata/mosaic.npy", mode="w+", dtype=np.uint8, shape=(H, W, 3))
miss = 0
for x in range(X0, X1 + 1):
    for y in range(Y0, Y1 + 1):
        p = f"{T}/5,{y},{x}.jpg"  # file names are z,row,col
        if not os.path.exists(p):
            miss += 1; continue
        out[(y - Y0) * 256:(y - Y0 + 1) * 256, (x - X0) * 256:(x - X0 + 1) * 256] = np.asarray(Image.open(p).convert("RGB"))
out.flush()
print("size", W, H, "missing", miss)
small = Image.fromarray(np.asarray(out[::8, ::8]))
small.save("/root/worlddata/mosaic8.png")
