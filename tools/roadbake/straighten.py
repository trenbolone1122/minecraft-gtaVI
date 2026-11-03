#!/usr/bin/env python3
"""Straighten the baked road network: the traced community polylines wobble 1 to 4 m on streets
that are straight in the game, and grid streets kink at every junction. This chains segments
straight through junctions, finds long straight runs with Douglas-Peucker, projects their samples
onto exact lines (snapped to N-S / E-W when within a few degrees), moves junction nodes onto the
lines (intersection of two crossing runs), and blends curved pieces to the moved ends.

  python3 tools/roadbake/straighten.py tools/roadbake/roads-raw.bin.gz src/main/resources/data/leonida/road/roads.bin.gz
"""
import gzip, math, struct, sys

TOL_STREET, TOL_HIGHWAY = 2.6, 1.2       # max lateral move when straightening (m)
MIN_RUN = 36.0                           # shortest piece treated as a straight run (m)
NEAR_NODE, TOL_NEAR = 14.0, 4.5             # looser fit within this distance of a junction node
SNAP_DEG = 3.5                           # runs this close to an axis become exactly axis aligned
SNAP_MAX_MOVE = 3.0                      # unless that would move a sample further than this


def read(path):
    d = gzip.open(path).read()
    o = 0
    magic, ver, nn = struct.unpack_from('>iii', d, o); o += 12
    nodes = [list(struct.unpack_from('>fff', d, o + 12 * i)) for i in range(nn)]; o += 12 * nn
    ns, = struct.unpack_from('>i', d, o); o += 4
    segs = []
    for _ in range(ns):
        k, w, a, b, n = struct.unpack_from('>Bfiii', d, o); o += 17
        smp = []
        for j in range(n):
            smp.append(list(struct.unpack_from('>ffffB', d, o))); o += 17
        segs.append(dict(k=k, w=w, a=a, b=b, s=smp))
    return magic, ver, nodes, segs


def write(path, magic, ver, nodes, segs):
    out = bytearray(struct.pack('>iii', magic, ver, len(nodes)))
    for n in nodes: out += struct.pack('>fff', *n)
    out += struct.pack('>i', len(segs))
    for s in segs:
        out += struct.pack('>Bfiii', s['k'], s['w'], s['a'], s['b'], len(s['s']))
        for p in s['s']: out += struct.pack('>ffffB', p[0], p[1], p[2], p[3], int(p[4]))
    with gzip.open(path, 'wb', compresslevel=9) as f: f.write(bytes(out))


def heading_out(seg, at_start, look=15.0):
    p = seg['s'] if at_start else seg['s'][::-1]
    x0, y0 = p[0][0], p[0][1]
    acc = 0.0
    for j in range(1, len(p)):
        acc += math.hypot(p[j][0] - p[j - 1][0], p[j][1] - p[j - 1][1])
        if acc >= look or j == len(p) - 1:
            return math.atan2(p[j][1] - y0, p[j][0] - x0)
    return 0.0


def build_chains(nodes, segs):
    arms = {}
    for i, s in enumerate(segs):
        if s['a'] >= 0: arms.setdefault(s['a'], []).append((i, True))
        if s['b'] >= 0: arms.setdefault(s['b'], []).append((i, False))
    link = {}  # (seg, end_is_start) -> (seg, end_is_start)
    for n, lst in arms.items():
        cand = []
        for x in range(len(lst)):
            for y in range(x + 1, len(lst)):
                (i, ai), (j, aj) = lst[x], lst[y]
                if i == j: continue
                si, sj = segs[i], segs[j]
                if si['k'] != sj['k'] or min(si['w'], sj['w']) / max(si['w'], sj['w']) < 0.7: continue
                d = abs(((heading_out(si, ai) - heading_out(sj, aj)) + math.pi) % (2 * math.pi) - math.pi)
                if d > math.radians(160): cand.append((-d, x, y))
        cand.sort()
        used = set()
        for _, x, y in cand:
            if x in used or y in used: continue
            used |= {x, y}
            link[lst[x]] = lst[y]; link[lst[y]] = lst[x]
    seen, chains = set(), []
    for i in range(len(segs)):
        if i in seen: continue
        # walk back to a chain start
        cur, fwd = i, True
        guard = 0
        while True:
            back = link.get((cur, fwd))  # the end we would enter from: start of cur when walking forward
            if back is None or back[0] == i or guard > len(segs): break
            j, j_at_start = back
            cur, fwd = j, not j_at_start
            guard += 1
            if cur == i: break
        chain = []
        while cur not in seen:
            seen.add(cur); chain.append((cur, fwd))
            nxt = link.get((cur, not fwd))
            if nxt is None: break
            cur, fwd = nxt[0], nxt[1]
        chains.append(chain)
    return chains


def dp(pts, tol):
    keep = [0, len(pts) - 1]
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = pts[a]; bx, by = pts[b]
        L = math.hypot(bx - ax, by - ay) or 1e-9
        best, bi = 0, -1
        for k in range(a + 1, b):
            d = abs((pts[k][0] - ax) * (by - ay) - (pts[k][1] - ay) * (bx - ax)) / L
            if d > best: best, bi = d, k
        if best > tol:
            keep.append(bi); stack += [(a, bi), (bi, b)]
    return sorted(set(keep))


def fit_line(pts):
    n = len(pts)
    mx = sum(p[0] for p in pts) / n; my = sum(p[1] for p in pts) / n
    sxx = sum((p[0] - mx) ** 2 for p in pts); syy = sum((p[1] - my) ** 2 for p in pts)
    sxy = sum((p[0] - mx) * (p[1] - my) for p in pts)
    ang = 0.5 * math.atan2(2 * sxy, sxx - syy)
    return mx, my, ang


def main(src, dst):
    magic, ver, nodes, segs = read(src)
    chains = build_chains(nodes, segs)
    # per sample: target line (cx, cy, ang) or None
    lines = {}   # (seg, idx) -> line
    node_lines = {}
    nrun = 0
    for chain in chains:
        pts, refs = [], []
        for si, fwd in chain:
            sm = segs[si]['s'] if fwd else segs[si]['s'][::-1]
            idx = range(len(sm)) if fwd else range(len(sm) - 1, -1, -1)
            for q, k in zip(sm, idx):
                if pts and abs(pts[-1][0] - q[0]) < 1e-3 and abs(pts[-1][1] - q[1]) < 1e-3:
                    refs[-1].append((si, k)); continue
                pts.append((q[0], q[1])); refs.append([(si, k)])
        if len(pts) < 2: continue
        hw = segs[chain[0][0]]['k'] == 1
        tol = TOL_HIGHWAY if hw else TOL_STREET
        keep = dp(pts, tol)
        st = [0.0]
        for k in range(1, len(pts)): st.append(st[-1] + math.hypot(pts[k][0] - pts[k - 1][0], pts[k][1] - pts[k - 1][1]))
        # samples close to a junction node may sit further off the line (traced approach wobble)
        node_st = [st[j] for j in range(len(pts)) if any(k2 == 0 or k2 == len(segs[si]['s']) - 1 for si, k2 in refs[j])]
        near = [any(abs(st[j] - q) < NEAR_NODE for q in node_st) for j in range(len(pts))]
        # merge consecutive DP pieces into runs while one line still fits within tol
        k = 0
        while k < len(keep) - 1:
            a = keep[k]; m = k + 1
            line = None
            while m < len(keep):
                sub = pts[a:keep[m] + 1]
                core = [p for q, p in enumerate(sub) if not near[a + q]]
                cx, cy, ang = fit_line(core if len(core) >= 2 else sub)
                if any(abs(-(p[0] - cx) * math.sin(ang) + (p[1] - cy) * math.cos(ang)) > (TOL_NEAR if near[a + q] else tol)
                       for q, p in enumerate(sub)): break
                line = (cx, cy, ang, keep[m]); m += 1
            if line is None or st[line[3]] - st[a] < MIN_RUN:
                k += 1; continue
            cx, cy, ang, b = line
            sub = pts[a:b + 1]
            for ax in (0.0, math.pi / 2, math.pi, -math.pi / 2):
                dd = (ang - ax + math.pi) % (2 * math.pi) - math.pi
                if abs(dd) < math.radians(SNAP_DEG):
                    na = ax
                    mv = max(abs(-(p[0] - cx) * math.sin(na) + (p[1] - cy) * math.cos(na)) for p in sub)
                    if mv <= SNAP_MAX_MOVE: ang = na
                    break
            L = (cx, cy, ang)
            nrun += 1
            for j in range(a, b + 1):
                for r in refs[j]: lines.setdefault(r, L)
            k = keep.index(b)
    # node positions: intersection of two crossing lines, projection on one
    def proj(L, x, y):
        cx, cy, ang = L; c, s = math.cos(ang), math.sin(ang)
        t = (x - cx) * c + (y - cy) * s
        return cx + c * t, cy + s * t
    new_nodes = [list(n) for n in nodes]
    for i, s in enumerate(segs):
        for end, ni in ((0, s['a']), (len(s['s']) - 1, s['b'])):
            if ni < 0: continue
            L = lines.get((i, end))
            if L: node_lines.setdefault(ni, [])
            if L and all(abs(((L[2] - M[2]) + math.pi / 2) % math.pi - math.pi / 2) > 1e-6 or abs(L[0] - M[0]) + abs(L[1] - M[1]) > 1e-6 for M in node_lines[ni]):
                node_lines[ni].append(L)
    for ni, Ls in node_lines.items():
        x, y = nodes[ni][0], nodes[ni][1]
        best = None
        for p in range(len(Ls)):
            for q in range(p + 1, len(Ls)):
                (ax, ay, aa), (bx, by, ba) = Ls[p], Ls[q]
                den = math.cos(aa) * math.sin(ba) - math.sin(aa) * math.cos(ba)
                if abs(den) < math.sin(math.radians(25)): continue
                t = ((bx - ax) * math.sin(ba) - (by - ay) * math.cos(ba)) / den
                ix, iy = ax + math.cos(aa) * t, ay + math.sin(aa) * t
                if math.hypot(ix - x, iy - y) < 5 and (best is None or math.hypot(ix - x, iy - y) < best[2]):
                    best = (ix, iy, math.hypot(ix - x, iy - y))
        if best: new_nodes[ni][0], new_nodes[ni][1] = best[0], best[1]
        else:
            px, py = proj(Ls[0], x, y)
            if math.hypot(px - x, py - y) < 5: new_nodes[ni][0], new_nodes[ni][1] = px, py
    moved = []
    for i, s in enumerate(segs):
        sm = s['s']; n = len(sm)
        new = []
        for k, p in enumerate(sm):
            L = lines.get((i, k))
            new.append(list(proj(L, p[0], p[1])) if L else [p[0], p[1]])
        # pin the ends to the (moved) nodes, blending the correction along the segment
        acc = [0.0]
        for k in range(1, n): acc.append(acc[-1] + math.hypot(sm[k][0] - sm[k - 1][0], sm[k][1] - sm[k - 1][1]))
        tot = acc[-1] or 1.0
        def corr(ni, k):
            if ni < 0: return (0.0, 0.0)
            ox, oy = nodes[ni][0], nodes[ni][1]
            if math.hypot(sm[k][0] - ox, sm[k][1] - oy) > 1.0: return (0.0, 0.0)  # sample end is not at the node
            tx, ty = sm[k][0] + new_nodes[ni][0] - ox, sm[k][1] + new_nodes[ni][1] - oy
            return (tx - new[k][0], ty - new[k][1])
        da, db = corr(s['a'], 0), corr(s['b'], n - 1)
        for k in range(n):
            t = acc[k] / tot
            # corrections fade over 30 m so straight runs stay straight
            wa = max(0.0, 1 - acc[k] / 30.0) if t < 1 else 0.0
            wb = max(0.0, 1 - (tot - acc[k]) / 30.0)
            if (i, k) in lines and 0 < k < n - 1: wa = wb = 0.0  # straight runs stay exact; ends lie on the line
            new[k][0] += da[0] * wa + db[0] * wb
            new[k][1] += da[1] * wa + db[1] * wb
            moved.append(math.hypot(new[k][0] - sm[k][0], new[k][1] - sm[k][1]))
            sm[k][0], sm[k][1] = new[k][0], new[k][1]
    write(dst, magic, ver, new_nodes, segs)
    moved.sort()
    print(f'chains {len(chains)}, straight runs {nrun}, samples moved: median {moved[len(moved)//2]:.2f} m, p99 {moved[int(len(moved)*.99)]:.2f} m, max {moved[-1]:.2f} m')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
