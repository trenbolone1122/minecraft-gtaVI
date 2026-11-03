#!/usr/bin/env python3
"""Put the authored Ocean Drive (tools/roadbake/oceandrive.py) into the world module's roads.bin.gz
(CwFeatures / RoadNet / MapQuery / minimap). Same line as the road network: the raw community stubs
1359, 1333 and 1098 become the three pieces (south to north), the cross streets 1344, 1118 and 1068 are
extended to the junction points. No segment index changes. Idempotent:

  python3 tools/worldgen/oceandrive_world.py src/main/resources/data/leonida/road/roads.bin.gz src/main/resources/data/leonida/world/roads.bin.gz
"""
import gzip, io, struct, sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '../roadbake'))
import straighten as S

# world piece id, its (start node, end node) in the world file, road network piece id
PIECES = [(1359, 1985, 1940, 1332), (1333, 1940, 1647, 1306), (1098, 1647, 1560, 1078)]
# world cross street id -> road network node at its east end
CROSS = {1344: 1246, 1118: 1037, 1068: 1009}


def read(p):
    d = gzip.open(p).read(); o = 0
    nj, = struct.unpack_from('>i', d, o); o += 4
    J = [struct.unpack_from('>iib', d, o + 9 * i) for i in range(nj)]; o += 9 * nj
    ns, = struct.unpack_from('>i', d, o); o += 4
    segs = []
    for _ in range(ns):
        a, b, k, w = struct.unpack_from('>iibh', d, o); o += 11
        n, = struct.unpack_from('>h', d, o); o += 2
        xz = struct.unpack_from('>%df' % (2 * n), d, o); o += 8 * n
        segs.append({'a': a, 'b': b, 'k': k, 'w': w, 'p': [(xz[2 * j], xz[2 * j + 1]) for j in range(n)]})
    assert o == len(d)
    return J, segs


def write(p, J, segs):
    b = io.BytesIO()
    b.write(struct.pack('>i', len(J)))
    for j in J: b.write(struct.pack('>iib', *j))
    b.write(struct.pack('>i', len(segs)))
    for s in segs:
        b.write(struct.pack('>iibh', s['a'], s['b'], s['k'], s['w']))
        b.write(struct.pack('>h', len(s['p'])))
        for x, z in s['p']: b.write(struct.pack('>ff', x, z))
    open(p, 'wb').write(gzip.compress(b.getvalue(), 9))


def main(roadbin, worldbin):
    _, _, nodes, rsegs = S.read(roadbin)
    J, segs = read(worldbin)
    for wid, a, b, rid in PIECES:
        pts = [(p[0], -p[1]) for p in rsegs[rid]['s']]
        segs[wid] = {'a': a, 'b': b, 'k': 0, 'w': int(round(rsegs[rid]['w'] * 10)), 'p': pts}
    for wid, rn in CROSS.items():
        x, y, _ = nodes[rn]
        s = segs[wid]
        p = s['p']
        # raw cross streets end at their old stub node (b); move that end onto the drive's centre line
        p = p[:-1] + [(x, -y)] if len(p) > 1 else p + [(x, -y)]
        s['p'] = p
    write(worldbin, J, segs)
    for wid, *_ in PIECES:
        p = segs[wid]['p']
        print('world seg %d: (%.1f, %.1f) -> (%.1f, %.1f), %d points' % (wid, p[0][0], p[0][1], p[-1][0], p[-1][1], len(p)))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
