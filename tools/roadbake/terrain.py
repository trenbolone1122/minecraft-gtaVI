"""Minimal reader for data/leonida/world/terrain.bin (class per 2 m cell, block coords)."""
import struct, zlib

class Terrain:
    def __init__(self, path):
        self.f = open(path, 'rb').read()
        magic, self.ox, self.oz, self.cell, self.r, self.rw, self.rh = struct.unpack_from('>7i', self.f, 0)
        n = self.rw * self.rh
        t = struct.unpack_from('>%di' % (2 * n), self.f, 28)
        self.off, self.len = t[0::2], t[1::2]
        self.start = 28 + 8 * n
        self.cache = {}

    def region(self, k):
        if k not in self.cache:
            raw = zlib.decompress(self.f[self.start + self.off[k]:self.start + self.off[k] + self.len[k]])
            self.cache[k] = raw
        return self.cache[k]

    def cls(self, x, z):
        ci, cj = (x - self.ox) // self.cell, (z - self.oz) // self.cell
        if ci < 0 or cj < 0 or ci >= self.rw * self.r or cj >= self.rh * self.r: return 8 + 3
        raw = self.region((cj // self.r) * self.rw + ci // self.r)
        return raw[(cj % self.r) * self.r + ci % self.r]
