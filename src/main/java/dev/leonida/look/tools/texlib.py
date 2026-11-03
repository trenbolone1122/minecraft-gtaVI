"""Procedural texture toolkit for the Leonida look module.

Everything tiles: noise is made in the frequency domain (periodic by construction), shapes use wrapped coordinates.
Colours are linear RGB floats; writers convert to sRGB. Height maps are 0..1 (1 = highest).
labPBR 1.3: _n = DirectX normal XY, AO in blue, height in alpha; _s = smoothness, F0/metal id, porosity/SSS, emissive.
"""
import numpy as np
from PIL import Image

# ------------------------------------------------------------------ colour

def to_srgb(c):
    c = np.clip(c, 0.0, 1.0)
    return np.where(c <= 0.0031308, 12.92 * c, 1.055 * np.power(c, 1 / 2.4) - 0.055)


def from_srgb(c):
    c = np.asarray(c, dtype=np.float64)
    return np.where(c <= 0.04045, c / 12.92, np.power((c + 0.055) / 1.055, 2.4))


def lin(r, g, b):
    return np.array([r, g, b], dtype=np.float64)


def hexlin(h):
    return from_srgb(np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255]) / 255.0)

# ------------------------------------------------------------------ noise


def fnoise(n, beta=2.0, seed=0, ax=1.0, ay=1.0, kmin=1.0, kmax=None):
    """Periodic 1/f^beta noise, zero mean unit std. ax/ay < 1 keep more fine detail along that axis, so
    (ax=0.1, ay=5) gives vertical streaks and (ax=5, ay=0.1) horizontal ones."""
    rng = np.random.default_rng(seed)
    kx = np.fft.fftfreq(n) * n
    ky = np.fft.fftfreq(n) * n
    KX, KY = np.meshgrid(kx, ky)
    k = np.sqrt((KX * ax) ** 2 + (KY * ay) ** 2)
    amp = np.zeros_like(k)
    m = k >= kmin
    if kmax is not None:
        m &= k <= kmax
    amp[m] = k[m] ** (-beta / 2.0)
    ph = rng.uniform(0, 2 * np.pi, k.shape)
    spec = amp * np.exp(1j * ph)
    x = np.real(np.fft.ifft2(spec))
    x -= x.mean()
    s = x.std()
    return x / s if s > 0 else x


def white(n, seed):
    return np.random.default_rng(seed).standard_normal((n, n))


def blur(a, r):
    """Periodic gaussian blur (radius r px) via FFT."""
    n = a.shape[0]
    k = np.fft.fftfreq(n)
    KX, KY = np.meshgrid(k, k)
    g = np.exp(-2 * (np.pi * r) ** 2 * (KX ** 2 + KY ** 2))
    if a.ndim == 2:
        return np.real(np.fft.ifft2(np.fft.fft2(a) * g))
    return np.stack([blur(a[..., i], r) for i in range(a.shape[2])], -1)


def grid(n):
    """u, v in [0,1): u = column (x), v = row (top = 0)."""
    t = (np.arange(n) + 0.5) / n
    return np.meshgrid(t, t)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def band(x, a, b, aa):
    """1 inside [a,b] with soft edges of width aa."""
    return smoothstep(a - aa, a + aa, x) * (1 - smoothstep(b - aa, b + aa, x))


def cells(n, count, seed, jitter=1.0):
    """Periodic Worley: returns (F1 distance in px, F2, cell id) for `count` random points."""
    rng = np.random.default_rng(seed)
    pts = rng.uniform(0, n, (count, 2))
    ids = rng.uniform(0, 1, count)
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + 0.5
    best = np.full((n, n), 1e9)
    second = np.full((n, n), 1e9)
    bid = np.zeros((n, n))
    for (px, py), cid in zip(pts, ids):
        dx = np.abs(xx - px)
        dx = np.minimum(dx, n - dx)
        dy = np.abs(yy - py)
        dy = np.minimum(dy, n - dy)
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        bid = np.where(closer, cid, bid)
        best = np.where(closer, d, best)
    return best, second, bid


def speckle(n, density, seed, rmin=0.6, rmax=1.6):
    """Random round stones/chips: returns (mask 0..1, per-chip random value)."""
    rng = np.random.default_rng(seed)
    cnt = int(density * n * n)
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float64) + 0.5
    mask = np.zeros((n, n))
    val = np.zeros((n, n))
    for _ in range(cnt):
        px, py = rng.uniform(0, n, 2)
        r = rng.uniform(rmin, rmax)
        v = rng.uniform()
        x0, x1 = int(px - r - 2), int(px + r + 3)
        y0, y1 = int(py - r - 2), int(py + r + 3)
        ys = np.arange(y0, y1) % n
        xs = np.arange(x0, x1) % n
        sx = np.arange(x0, x1) + 0.5 - px
        sy = np.arange(y0, y1) + 0.5 - py
        SX, SY = np.meshgrid(sx, sy)
        m = np.clip(r + 0.5 - np.sqrt(SX * SX + SY * SY), 0, 1)
        sub = mask[np.ix_(ys, xs)]
        upd = m > sub
        mask[np.ix_(ys, xs)] = np.where(upd, m, sub)
        vs = val[np.ix_(ys, xs)]
        val[np.ix_(ys, xs)] = np.where(upd & (m > 0.3), v, vs)
    return mask, val

# ------------------------------------------------------------------ material


class Mat:
    """A texture: linear albedo (n,n,3), alpha (n,n) or None, height (n,n) 0..1, smoothness, f0 (0..255 int code),
    porosity (0..64) or sss (65..255), emissive (0..1) maps or scalars."""

    def __init__(self, n, albedo, height=None, alpha=None, smooth=0.3, f0=10, porosity=0, emissive=0.0,
                 relief=0.6, normal_strength=2.0, ao_strength=1.0, light=None):
        self.n = n
        self.albedo = albedo
        self.height = np.full((n, n), 0.5) if height is None else np.broadcast_to(np.asarray(height, dtype=np.float64), (n, n)).copy()
        self.alpha = alpha
        self.smooth = smooth
        self.f0 = f0
        self.porosity = porosity
        self.emissive = emissive
        self.relief = relief  # how much height shading is baked into albedo (vanilla look)
        self.normal_strength = normal_strength
        self.ao_strength = ao_strength
        self.light = light

    # derived ---------------------------------------------------------
    def gradients(self):
        h = self.height * self.normal_strength * self.n / 32.0
        dx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * 0.5
        dy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * 0.5
        return dx, dy

    def ao(self):
        h = self.height
        cav = np.clip(blur(h, self.n / 24.0) - h, 0, None)
        cav2 = np.clip(blur(h, self.n / 64.0) - h, 0, None)
        return np.clip(1.0 - (cav * 2.2 + cav2 * 1.5) * self.ao_strength, 0.25, 1.0)

    def baked_albedo(self):
        """Albedo with subtle relief lighting + AO so vanilla (no normal maps) still reads depth."""
        dx, dy = self.gradients()
        nx, ny = -dx, -dy  # ny: down
        nz = np.ones_like(nx)
        ln = np.sqrt(nx * nx + ny * ny + nz * nz)
        nx, ny, nz = nx / ln, ny / ln, nz / ln
        L = np.array([-0.45, -0.55, 0.70]) if self.light is None else np.array(self.light)
        L = L / np.linalg.norm(L)
        lam = nx * L[0] + ny * L[1] + nz * L[2]
        flat = L[2]
        shade = 1.0 + self.relief * (lam - flat) * 1.4
        shade *= 1.0 - self.relief * (1.0 - self.ao())
        return self.albedo * np.clip(shade, 0.3, 1.6)[..., None]

    def normal_png(self):
        dx, dy = self.gradients()
        nx, ny = -dx, -dy
        nz = np.ones_like(nx)
        ln = np.sqrt(nx * nx + ny * ny + nz * nz)
        r = (nx / ln * 0.5 + 0.5) * 255
        g = (ny / ln * 0.5 + 0.5) * 255
        b = self.ao() * 255
        a = 1 + self.height * 254
        img = np.stack([r, g, b, a], -1)
        return Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), "RGBA")

    def spec_png(self):
        n = self.n

        def full(v):
            return np.broadcast_to(np.asarray(v, dtype=np.float64), (n, n))
        r = full(self.smooth) * 255
        g = full(self.f0)
        b = full(self.porosity)
        a = full(self.emissive) * 254
        img = np.stack([r, g, b, a], -1)
        return Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), "RGBA")

    def albedo_png(self):
        c = to_srgb(self.baked_albedo()) * 255
        if self.alpha is None:
            a = np.full((self.n, self.n), 255.0)
        else:
            a = self.alpha * 255
        img = np.concatenate([c, a[..., None]], -1)
        return Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), "RGBA")

    def save(self, base):
        self.albedo_png().save(base + ".png", optimize=True)
        self.normal_png().save(base + "_n.png", optimize=True)
        self.spec_png().save(base + "_s.png", optimize=True)


def tint(c, n):
    return np.broadcast_to(np.asarray(c, dtype=np.float64), (n, n, 3)).copy()


def mix(a, b, t):
    t = np.asarray(t)
    if t.ndim == 2:
        t = t[..., None]
    return a * (1 - t) + b * t
