"""Packs community-world-v1 buildings, infill, trees, landmarks and the road graph into compact
resources for the world module (data/leonida/world/). MC x = mapX, z = -mapY.
Usage: python cw_features_pack.py <community-world-v1> <out dir>
"""
import sys, json, math, struct, gzip, io
cw, out = sys.argv[1:3]
J = lambda n: json.load(open(f'{cw}/{n}'))

def area(p):
    return sum(p[i][0] * p[(i + 1) % len(p)][1] - p[(i + 1) % len(p)][0] * p[i][1] for i in range(len(p))) / 2

def ring(p, ccw):
    q = []
    for x, y in p:
        v = (int(round(x)), int(round(-y)))
        if not q or q[-1] != v:
            q.append(v)
    if len(q) > 1 and q[0] == q[-1]:
        q.pop()
    # orientation in map space (x, y=-z): CCW = positive map area
    a = area([(x, -z) for x, z in q])
    if (a > 0) != ccw:
        q.reverse()
    return q

def inside(pt, p):
    x, y = pt; c = False
    for i in range(len(p)):
        x1, y1 = p[i]; x2, y2 = p[i - 1]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            c = not c
    return c

lms = [l for l in J('landmarks.json') if len(l.get('position') or []) == 2]
def tagset(l):
    return set(l.get('tags') or [])

KIND = ['HOUSE', 'APARTMENT', 'TOWER', 'SHOP', 'GAS_STATION', 'MOTEL', 'CLUB', 'OFFICE', 'WAREHOUSE', 'BANK', 'HOTEL', 'MALL',
        'POLICE', 'HOSPITAL', 'MARINA', 'LANDMARK']
K = {k: i for i, k in enumerate(KIND)}

def kind_for(style, h, area_m, tags, name):
    n = (name or '').lower()
    if '#police' in tags or 'police' in n: return K['POLICE']
    if '#hospital' in tags or 'hospital' in n or 'medical center' in n: return K['HOSPITAL']
    if '#gas' in tags: return K['GAS_STATION']
    if 'motel' in n: return K['MOTEL']
    if 'hotel' in tags or 'hotel' in n: return K['HOTEL']
    if '#nightclub' in tags or '#stripclub' in tags or '#bar' in tags: return K['CLUB']
    if 'bank' in n: return K['BANK']
    if '#marina' in tags: return K['MARINA']
    if 'mall' in n: return K['MALL']
    if 'retail' in tags or '#supermarket' in tags or 'restaurant' in tags or '#food' in tags or '#clothes' in tags or '#guns' in tags: return K['SHOP']
    if 'office' in tags: return K['OFFICE']
    if 'landmark' in tags: return K['LANDMARK']
    if style == 5: return K['WAREHOUSE']
    if style == 0: return K['TOWER'] if h > 40 else K['OFFICE']
    if style == 4: return K['OFFICE']
    if style == 1: return K['APARTMENT'] if h > 9 else K['HOUSE']
    if style == 2: return K['HOTEL'] if h > 14 else K['SHOP']
    if style == 3: return K['HOUSE'] if (h < 9 and area_m < 400) else K['SHOP']
    return K['HOUSE']

# landmark spatial hash for point-in-building tagging
grid = {}
for l in lms:
    x, y = l['position']
    grid.setdefault((int(x // 64), int(y // 64)), []).append(l)

recs = []  # (id, kind, style, height, groundZdm, outer, holes, landmarkName)
for idx, b in enumerate(J('buildings.json')):
    p = b['p']
    xs = [v[0] for v in p]; ys = [v[1] for v in p]
    tags = set(); name = None
    for gx in range(int(min(xs) // 64), int(max(xs) // 64) + 1):
        for gy in range(int(min(ys) // 64), int(max(ys) // 64) + 1):
            for l in grid.get((gx, gy), []):
                if inside(l['position'], p):
                    tags |= tagset(l); name = name or l.get('name')
    k = kind_for(b['style'], b['h'], b['area'], tags, name)
    outer = ring(p, True)
    if len(outer) < 3: continue
    holes = [h for h in (ring(hh, False) for hh in b.get('holes') or []) if len(h) >= 3]
    recs.append((idx + 1, k, b['style'], max(3, int(round(b['h']))), int(round(b['z'] * 10)), outer, holes, name or ''))
nb = len(recs)
for idx, f in enumerate(J('infill.json')):
    x, y, z, w, d, h, yaw, style = f
    c, s = math.cos(yaw), math.sin(yaw)
    pts = [(x + c * u - s * v, y + s * u + c * v) for u, v in ((-w / 2, -d / 2), (w / 2, -d / 2), (w / 2, d / 2), (-w / 2, d / 2))]
    outer = ring(pts, True)
    if len(outer) < 3: continue
    k = kind_for(int(style), h, w * d, set(), '')
    recs.append((1_000_000 + idx, k, int(style), max(3, int(round(h))), int(round(z * 10)), outer, [], ''))
print('buildings', nb, 'infill', len(recs) - nb)

bio = io.BytesIO(); W = lambda fmt, *v: bio.write(struct.pack('>' + fmt, *v))
W('i', len(recs))
for rid, k, st, h, zdm, outer, holes, name in recs:
    W('ibbhh', rid, k, st, h, zdm)
    nm = name.encode('utf-8')[:255]; W('B', len(nm)); bio.write(nm)
    W('h', len(outer))
    for x, z in outer: W('ii', x, z)
    W('b', len(holes))
    for hh in holes:
        W('h', len(hh))
        for x, z in hh: W('ii', x, z)
open(f'{out}/buildings.bin.gz', 'wb').write(gzip.compress(bio.getvalue(), 9))

trees = [(t[0], t[1], t[2], t[3], int(t[4])) for t in J('trees.json')]
coast = [(r[0], r[1], r[2], r[3], 4) for r in J('coastal-tree-restoration.json')['records']]  # 4 = coconut palm
bio = io.BytesIO()
allt = trees + coast
W('i', len(allt))
for x, y, z, h, k in allt:
    W('iihbb', int(round(x)), int(round(-y)), int(round(z * 10)), min(127, int(round(h))), k)
open(f'{out}/trees.bin.gz', 'wb').write(gzip.compress(bio.getvalue(), 9))
print('trees', len(allt))

L = []
for l in lms:
    x, y = l['position']
    L.append({'n': l.get('name'), 'x': int(round(x)), 'z': int(round(-y)), 'a': l.get('gameAddress') or '',
              't': [t for t in (l.get('tags') or []) if not t.startswith('//')]})
open(f'{out}/landmarks.json', 'w').write(json.dumps(L, separators=(',', ':')))

roads = J('roads.json')
ex = set()  # excludedAnnotationSegments index the raw input; roads.json output is already clean
bio = io.BytesIO()
segs = [s for i, s in enumerate(roads['segments']) if i not in ex]
W('i', len(roads['junctions']))
for jx, jy, arms in roads['junctions']:
    W('iib', int(round(jx)), int(round(-jy)), arms)
W('i', len(segs))
for s in segs:
    W('iibh', s['a'], s['b'], s['k'], int(round(s['w'] * 10)))
    W('h', len(s['p']))
    for x, y in s['p']: W('ff', x, -y)
open(f'{out}/roads.bin.gz', 'wb').write(gzip.compress(bio.getvalue(), 9))
print('roads', len(segs), 'excluded', len(ex))
