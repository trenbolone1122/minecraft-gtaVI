#!/usr/bin/env python3
"""Kerb straightness check on the baked network with the generator's zone rule (kerb column =
half < |u| <= half + 1, u measured to the nearest piece of the road's polyline, column centres).
For a road chain and a 100 block stretch along its main axis, take per column the kerb block nearest
the carriageway on each side and report the lateral deviation (max - min residual from the best
fitting straight line, in blocks).

  python3 tools/roadbake/kerbcheck.py roads.bin.gz <segment> <x or z start>   (one stretch)
  python3 tools/roadbake/kerbcheck.py roads.bin.gz --all                      (every street chain)"""
import math, sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import straighten as S


def chain_pts(segs, chain):
    pts = []
    for si, fwd in chain:
        sm = segs[si]['s'] if fwd else segs[si]['s'][::-1]
        for q in sm:
            p = (q[0], -q[1])
            if pts and abs(pts[-1][0] - p[0]) < 1e-3 and abs(pts[-1][1] - p[1]) < 1e-3: continue
            pts.append(p)
    return pts


def u_of(p, x, z):
    best = None
    for j in range(len(p) - 1):
        ax, az = p[j]; bx, bz = p[j + 1]; dx, dz = bx - ax, bz - az; L2 = dx * dx + dz * dz
        if L2 == 0: continue
        t = ((x - ax) * dx + (z - az) * dz) / L2
        if t < -1e-9 or t > 1 + 1e-9: continue
        u = (-(x - ax) * dz + (z - az) * dx) / math.sqrt(L2)
        if best is None or abs(u) < abs(best): best = u
    return best


def stretch(p, half, ew, a0, length=100):
    """Kerb inner-edge positions per column a in [a0, a0+length) for both sides; None if not found."""
    out = {1: [], -1: []}
    for a in range(int(a0), int(a0 + length)):
        # centre line crossing of this column
        c = None
        for j in range(len(p) - 1):
            (ax, az), (bx, bz) = p[j], p[j + 1]
            pa, pb = (ax, bx) if ew else (az, bz)
            if (pa - (a + 0.5)) * (pb - (a + 0.5)) <= 0 and pa != pb:
                t = (a + 0.5 - pa) / (pb - pa)
                c = (az + (bz - az) * t) if ew else (ax + (bx - ax) * t)
                break
        if c is None: continue
        cells = {}
        for b in range(int(math.floor(c - half - 3)), int(math.ceil(c + half + 3))):
            x, z = (a, b) if ew else (b, a)
            u = u_of(p, x + 0.5, z + 0.5)
            if u is not None and half < abs(u) <= half + 1:
                s = 1 if u > 0 else -1
                d = abs(b + 0.5 - c)
                if s not in cells or d < cells[s][1]: cells[s] = (b, d)
        for s in (1, -1):
            if s in cells: out[s].append((a, cells[s][0]))
    return out


def deviation(pts):
    if len(pts) < 50: return None
    n = len(pts); ma = sum(q[0] for q in pts) / n; mb = sum(q[1] for q in pts) / n
    saa = sum((q[0] - ma) ** 2 for q in pts) or 1
    k = sum((q[0] - ma) * (q[1] - mb) for q in pts) / saa
    res = [q[1] - (mb + k * (q[0] - ma)) for q in pts]
    return max(res) - min(res)


def find_chain(chains, seg):
    return next(c for c in chains if any(si == seg for si, _ in c))


if __name__ == '__main__':
    f = sys.argv[1]
    _, _, nodes, segs = S.read(f)
    raw = S.read(f)[3]
    chains = S.build_chains(nodes, raw)
    if sys.argv[2] != '--all':
        seg, a0 = int(sys.argv[2]), float(sys.argv[3])
        ch = find_chain(chains, seg)
        p = chain_pts(segs, ch)
        half = segs[seg]['w'] / 2
        ew = abs(p[-1][0] - p[0][0]) >= abs(p[-1][1] - p[0][1])
        st = stretch(p, half, ew, a0)
        for s in (1, -1):
            pts = st[s]
            print(f'segment {seg} side {s:+d}: {len(pts)} columns, kerb at {pts[0][1] if pts else None}..{pts[-1][1] if pts else None}, lateral deviation {deviation(pts)} blocks')


def straight_windows(p, length=100, step=50, tol=0.3):
    """100 block windows along the main axis where the polyline is one straight line (within tol)."""
    ew = abs(p[-1][0] - p[0][0]) >= abs(p[-1][1] - p[0][1])
    ax = [q[0] if ew else q[1] for q in p]
    lo, hi = min(ax), max(ax)
    out = []
    a = math.ceil(lo)
    while a + length <= hi:
        sub = [q for q in p if a - 1 <= (q[0] if ew else q[1]) <= a + length + 1]
        if len(sub) >= 3:
            (x0, z0), (x1, z1) = sub[0], sub[-1]
            L = math.hypot(x1 - x0, z1 - z0) or 1
            if max(abs(-(q[0] - x0) * (z1 - z0) + (q[1] - z0) * (x1 - x0)) / L for q in sub) <= tol:
                # monotonic along the axis
                vals = [q[0] if ew else q[1] for q in sub]
                if vals == sorted(vals) or vals == sorted(vals, reverse=True): out.append((ew, a))
        a += step
    return out


if __name__ == '__main__' and sys.argv[2] == '--all':
    rawf = sys.argv[3] if len(sys.argv) > 3 else None
    rsegs = S.read(rawf)[3] if rawf else None
    rows = []
    for ch in chains:
        s0 = segs[ch[0][0]]
        if s0['k'] != 0: continue
        p = chain_pts(segs, ch)
        for ew, a in straight_windows(p):
            st = stretch(p, s0['w'] / 2, ew, a)
            d = [deviation(st[s]) for s in (1, -1)]
            d = [x for x in d if x is not None]
            if not d: continue
            r = None
            if rsegs:
                rp = chain_pts(rsegs, ch)
                rs = stretch(rp, s0['w'] / 2, ew, a)
                r = [deviation(rs[s]) for s in (1, -1)]
                r = max([x for x in r if x is not None], default=None)
            rows.append((max(d), r, ch[0][0], ew, a))
    rows.sort()
    n = len(rows)
    print(f'{n} straight 100 block windows on street chains; kerb lateral deviation: median {rows[n//2][0]:.2f}, p95 {rows[int(n*.95)][0]:.2f}, max {rows[-1][0]:.2f}; within 1 block: {sum(1 for r in rows if r[0] <= 1.0)}')
    rr = sorted(r[1] for r in rows if r[1] is not None)
    if rr: print(f'same windows on the raw network: median {rr[len(rr)//2]:.2f}, p95 {rr[int(len(rr)*.95)]:.2f}, max {rr[-1]:.2f}; within 1 block: {sum(1 for x in rr if x <= 1.0)}')
    for r in rows[-5:]: print('worst', r)
