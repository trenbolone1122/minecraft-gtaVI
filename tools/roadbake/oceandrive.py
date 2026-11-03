#!/usr/bin/env python3
"""Author Ocean Drive into the baked network. The community roads.json has only stubs of it, so the
beachfront street between the Deco hotel row (vicecity OceanDrive.ROW) and Lummus Park is laid as one
exact straight line along the row axis (about 9 degrees east of north), 12 m wide: two 3.6 m lanes and a
2.4 m kerb parking strip each side (segment kind 2). Its west sidewalk stays 1 m clear of every hotel
claim (building plus the 9 m porch and terrace). The three old stubs (1332, 1306, 1078) are reused as the
three pieces, so no segment id changes; cross streets 1317, 1098 and 1048 are extended east to meet it.
Run after straighten.py:

  python3 tools/roadbake/straighten.py tools/roadbake/roads-raw.bin.gz src/main/resources/data/leonida/road/roads.bin.gz
  python3 tools/roadbake/oceandrive.py src/main/resources/data/leonida/road/roads.bin.gz src/main/resources/data/leonida/road/roads.bin.gz
"""
import math, re, sys, os
sys.path.insert(0, os.path.dirname(__file__))
import straighten as S

KIND_PARKING = 2
WIDTH = 12.0
SIDEWALK = 3.0
CLEAR = 1.0
STEP = 6.0
THETA = math.pi / 2 - math.atan2(107, 674)  # DecoHotel.ROW_THETA
U = (math.cos(THETA), math.sin(THETA))     # along the drive, map frame (y north)
V = (-math.sin(THETA), math.cos(THETA))    # towards the hotels (west)
ROW = os.path.join(os.path.dirname(__file__), '../../src/main/java/dev/leonida/vicecity/site/OceanDrive.java')

# (old stub id, start node, end node) south to north; junction nodes get moved onto the line
PIECES = [(1332, 1269, 1246), (1306, 1246, 1037), (1078, 1037, 1009)]
CROSS = {1246: 1317, 1037: 1098, 1009: 1048}  # junction node -> cross street ending there
SOUTH_U = 547.0


def uv(x, y):
    return x * U[0] + y * U[1], x * V[0] + y * V[1]


def xy(u, v):
    return u * U[0] + v * V[0], u * U[1] + v * V[1]


def claim_edge():
    """Most ocean-ward v of any hotel claim rectangle (front - 9 m porch)."""
    src = open(ROW).read()
    edge = 1e9
    for m in re.finditer(r'new Spec\("L\d+", "[^"]*", "[^"]*", ([\d.]+), ([\d.]+), (\d+), (\d+)', src):
        x, y, w, d = float(m.group(1)), float(m.group(2)), int(m.group(3)), int(m.group(4))
        u, v = uv(x, y)
        edge = min(edge, v - d // 2 - 9)
    return edge


def main(src, dst):
    magic, ver, nodes, segs = S.read(src)
    vc = claim_edge() - CLEAR - SIDEWALK - WIDTH / 2
    # junctions: extend each cross street's last leg to the drive's centre line
    ju = {}
    for node, sid in CROSS.items():
        s = segs[sid]
        at_end = s['b'] == node
        pts = s['s'] if at_end else s['s'][::-1]
        p0, p1 = pts[-2], pts[-1]
        u0, v0 = uv(p0[0], p0[1]); u1, v1 = uv(p1[0], p1[1])
        t = (vc - v0) / (v1 - v0)
        uj = u0 + (u1 - u0) * t
        x, y = xy(uj, vc)
        h = p1[2]
        nodes[node] = [x, y, h]
        # replace the last sample by evenly spaced samples up to the new node
        base = pts[-2]
        L = math.hypot(x - base[0], y - base[1])
        k = max(1, int(math.ceil(L / STEP)))
        new = [[base[0] + (x - base[0]) * i / k, base[1] + (y - base[1]) * i / k,
                base[2] + (h - base[2]) * i / k, base[3] + (h - base[3]) * i / k, 0] for i in range(1, k + 1)]
        pts = pts[:-1] + new
        s['s'] = pts if at_end else pts[::-1]
        ju[node] = uj
    ju[1269] = SOUTH_U
    nodes[1269] = list(xy(SOUTH_U, vc)) + [nodes[1269][2]]
    for sid, a, b in PIECES:
        ua, ub = ju[a], ju[b]
        ha, hb = nodes[a][2], nodes[b][2]
        k = max(2, int(math.ceil((ub - ua) / STEP)))
        smp = []
        for i in range(k + 1):
            t = i / k
            x, y = xy(ua + (ub - ua) * t, vc)
            h = ha + (hb - ha) * t
            smp.append([x, y, h, h, 0])
        segs[sid] = {'k': KIND_PARKING, 'w': WIDTH, 'a': a, 'b': b, 's': smp}
    S.write(dst, magic, ver, nodes, segs)
    print('ocean drive: centre v %.2f, u %.1f..%.1f, junctions %s' % (vc, SOUTH_U, ju[1009],
          ', '.join('%d@u%.1f' % (n, ju[n]) for n in (1246, 1037, 1009))))
    for n in (1269, 1246, 1037, 1009):
        x, y, h = nodes[n]
        print('  node %d block (%.2f, %.2f) h %.2f' % (n, x, -y, h))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
