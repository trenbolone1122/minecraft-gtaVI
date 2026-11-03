#!/usr/bin/env python3
"""Offline preview of the hdmodel.py meshes (standing pose): front and three-quarter views.
Usage: python3 preview_model.py REPO_ROOT OUT.png [model names...]"""
import math, os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hdmodel as H

ROOT, OUT = sys.argv[1], sys.argv[2]
NAMES = sys.argv[3:] or ["jason_everyday", "lucia_everyday"]
PIVOT = {"head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
         "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}


def build(name):
    ch, o = name.split("_", 1)
    m = H.jason_model(o) if ch == "jason" else H.lucia_model(o)
    H.pack(m.cubes)
    tex = Image.open(os.path.join(ROOT, "src/main/resources/assets/leonida/textures/entity/character/model", name + ".png")).convert("RGBA")
    return m, tex


def render(m, tex, yaw, size=360, zoom=1.0, focus_y=None):
    img = Image.new("RGBA", (size, size), (52, 56, 70, 255))
    dr = ImageDraw.Draw(img)
    t = tex.load()
    cy, sy = math.cos(yaw), math.sin(yaw)
    scale = size / 42.0 * zoom
    fy = 11 if focus_y is None else focus_y
    quads = []
    for c in m.cubes:
        px0, py0, pz0 = PIVOT[c.part]
        if m.slim and "arm" in c.part:
            py0 = 2.5
        for face, (fu, fv, fw, fh) in H.face_rects(c).items():
            _, n = H.point(c, face, 0.5, 0.5)
            # rotate normal around Y; camera looks along +z (sees faces with normal z < 0)
            nz = -n[0] * sy + n[2] * cy
            if nz >= -1e-6:
                continue
            step = 1.0 / H.S
            for j in range(fh * H.S):
                for i in range(fw * H.S):
                    corners = []
                    for (da, db) in ((0, 0), (1, 0), (1, 1), (0, 1)):
                        (X, Y, Z), _ = H.point(c, face, (i + da) * step, (j + db) * step)
                        X = X * 0.5 + px0; Y = Y * 0.5 + py0; Z = Z * 0.5 + pz0
                        rx = X * cy + Z * sy; rz = -X * sy + Z * cy
                        corners.append((size / 2 + rx * scale, size / 2 + (Y - fy) * scale, rz))
                    depth = sum(p[2] for p in corners) / 4
                    col = t[fu * H.S + i, fv * H.S + j]
                    # in-game entity lighting approximation
                    light = 0.75 + 0.25 * max(0.0, -nz) + (0.12 if face == "top" else 0) - (0.2 if face == "bottom" else 0)
                    col = tuple(min(255, int(v * light)) for v in col[:3])
                    quads.append((depth, [(p[0], p[1]) for p in corners], col))
    quads.sort(key=lambda q: -q[0])
    for _, pts, col in quads:
        dr.polygon(pts, fill=col)
    return img


def main():
    tiles = []
    for name in NAMES:
        m, tex = build(name)
        tiles.append(render(m, tex, 0.0))
        tiles.append(render(m, tex, math.radians(35)))
        tiles.append(render(m, tex, 0.0, zoom=3.2, focus_y=-4))
        tiles.append(render(m, tex, math.radians(35), zoom=3.2, focus_y=-4))
        tiles.append(render(m, tex, math.radians(180)))
    cols = 5
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 360, rows * 360), (30, 30, 30, 255))
    for k, t in enumerate(tiles):
        sheet.paste(t, ((k % cols) * 360, (k // cols) * 360))
    sheet.save(OUT)


if __name__ == "__main__":
    main()
