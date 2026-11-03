"""Palm frond geometry for block models: arching fronds built from rotated zero-thickness planes.

A frond is a chain of segments pointing away from a start point. Each segment is two half planes (left and right of the
rachis) rolled down by `roll` degrees so the leaflets form a drooping V. Rotations use the model format's Euler x/y/z
(applied x, then y, then z) which can express any rotation, so we compose yaw * pitch * roll and decompose it.
"""
import math
import numpy as np


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler_zyx(m):
    """x, y, z degrees with Rz(z) @ Ry(y) @ Rx(x) == m (JOML rotationZYX order)."""
    y = -math.asin(max(-1.0, min(1.0, m[2, 0])))
    if abs(m[2, 0]) < 0.99999:
        x = math.atan2(m[2, 1], m[2, 2])
        z = math.atan2(m[1, 0], m[0, 0])
    else:
        x = math.atan2(-m[1, 2], m[1, 1])
        z = 0.0
    out = [math.degrees(x), math.degrees(y), math.degrees(z)]
    assert np.allclose(rz(math.radians(out[2])) @ ry(math.radians(out[1])) @ rx(math.radians(out[0])), m, atol=1e-6)
    return [round(v, 3) for v in out]


def r3(v):
    return [round(float(c), 3) for c in v]


def _axis(R):
    w, v = np.linalg.eig(R)
    i = int(np.argmin(np.abs(w - 1)))
    a = np.real(v[:, i])
    return a / np.linalg.norm(a)


def _place(P, R, box_rel, centre=(8.0, 8.0, 8.0)):
    """Element whose local box (relative to its anchor Q) is box_rel = (lo, hi), rotated by R so the anchor lands on P.
    Model files only bound from/to (not the origin), so slide Q toward the block centre along R's axis (the component
    along the axis is preserved by the rotation) and solve for the origin. Returns from, to, origin or None."""
    P = np.asarray(P, float)
    c = np.asarray(centre, float)
    if np.allclose(R, np.eye(3)):
        cands = [(P, P)]
    else:
        a = _axis(R)
        Q = c + (a @ P - a @ c) * a
        # (I - R) O = P - R Q, solvable in the plane perpendicular to a
        O, *_ = np.linalg.lstsq(np.eye(3) - R, P - R @ Q, rcond=None)
        O = O - (a @ O - a @ Q) * a  # any point on the line works; keep it near Q
        cands = [(Q, O), (P, P)]
    for Q, O in cands:
        lo, hi = Q + box_rel[0], Q + box_rel[1]
        if np.all(lo >= -16) and np.all(hi <= 32):
            assert np.allclose(O + R @ (Q - O), P, atol=1e-4)
            return lo, hi, O
    return None


def frond(start, yaw, pitches, lengths, width, roll, tex, widths=None, v0=0.0, v1=16.0):
    """Elements for one frond. start (x,y,z) px; yaw degrees (0 = north, positive turns toward west); pitches degrees per
    segment (positive = up); lengths px per segment; width px (or widths per segment); roll degrees (leaflet droop).
    Texture: frond along v (v0 base .. v1 tip), rachis at u = 8."""
    els = []
    p = np.array(start, dtype=float)
    total = sum(lengths)
    acc = 0.0
    for i, (pitch, L) in enumerate(zip(pitches, lengths)):
        w = (widths[i] if widths else width) / 2.0
        va = v0 + (v1 - v0) * acc / total
        vb = v0 + (v1 - v0) * (acc + L) / total
        acc += L
        Rbase = ry(math.radians(yaw)) @ rx(math.radians(pitch))
        for side in (-1, 1):
            R = Rbase @ rz(math.radians(-side * roll))
            placed = None
            for flip in (False, True):
                Rf = R @ ry(math.pi) if flip else R
                if not flip:
                    box = (np.array([-w if side == -1 else 0, 0, -L]), np.array([0 if side == -1 else w, 0, 0]))
                else:
                    box = (np.array([0 if side == -1 else -w, 0, 0]), np.array([w if side == -1 else 0, 0, L]))
                r = _place(p, Rf, box)
                if r:
                    placed = (flip, Rf, box, r)
                    break
            assert placed, (start, yaw, i)
            flip, Rf, box, (lo, hi, O) = placed
            u = [0, 8] if box[1][0] == 0 else [8, 16]  # rachis edge is the one at the anchor
            if not flip:
                up_uv, dn_uv = [u[0], vb, u[1], va], [u[0], va, u[1], vb]
            else:
                up_uv, dn_uv = [u[0], va, u[1], vb], [u[0], vb, u[1], va]
            ex, ey, ez = euler_zyx(Rf)
            els.append({"from": r3(lo), "to": r3(hi), "rotation": {"origin": r3(O), "x": ex, "y": ey, "z": ez},
                        "faces": {"up": {"texture": tex, "uv": r3(up_uv)}, "down": {"texture": tex, "uv": r3(dn_uv)}}})
        p = p + Rbase @ np.array([0.0, 0.0, -L])
    return els


def check_bounds(els):
    for e in els:
        for c in e["from"] + e["to"]:
            assert -16 <= c <= 32, e
