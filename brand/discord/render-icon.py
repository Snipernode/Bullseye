import math, struct, zlib, sys

# Headshot Lifesupport — icon palette
BG   = (0x0B, 0x0D, 0x10, 255)   # slate black
ECG  = (0x2B, 0xD9, 0x7C, 255)   # lifesupport green
RET  = (0xE5, 0x48, 0x4D, 255)   # headshot red
INK  = (0xF2, 0xF4, 0xF6, 255)   # impact white

# PQRST waveform; the R-spike apex lands exactly on the reticle centre (the headshot)
TRACE = [(0.180, 0.700), (0.330, 0.700), (0.395, 0.630), (0.455, 0.700),
         (0.478, 0.760), (0.500, 0.500), (0.525, 0.780), (0.580, 0.700),
         (0.645, 0.700), (0.710, 0.700), (0.765, 0.630), (0.820, 0.700)]

RADIUS, RING_HW, GAP_DEG = 0.300, 0.024, 14.0
ARC_LEN = 90.0 - GAP_DEG
TRACE_HW, DOT_R = 0.016, 0.036
TICK_IN, TICK_OUT, TICK_HW = RADIUS + RING_HW + 0.022, RADIUS + RING_HW + 0.098, 0.009


def dist_to_segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    if dx == 0.0 and dy == 0.0:
        return math.hypot(px - ax, py - ay)
    t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def render(size, ss):
    W = size * ss                     # supersampled grid edge
    u = float(W)                     # constants are fractions of canvas width
    cx = cy = W / 2.0

    ring_r, ring_hw = RADIUS * u, RING_HW * u
    dot_r, trace_hw = DOT_R * u, TRACE_HW * u
    t_in, t_out, t_hw = TICK_IN * u, TICK_OUT * u, TICK_HW * u

    grid = bytearray(W * W * 4)

    # base: background, reticle ring + ticks, impact dot
    for y in range(W):
        py = y + 0.5
        base = y * W * 4
        for x in range(W):
            dx, dy = x + 0.5 - cx, py - cy
            ax, ay = abs(dx), abs(dy)
            dist = math.hypot(dx, dy)
            col = BG
            if abs(dist - ring_r) <= ring_hw:
                ang = math.degrees(math.atan2(-dy, dx)) % 360.0
                for k in range(4):
                    if (ang - (GAP_DEG + k * 90.0)) % 360.0 <= ARC_LEN:
                        col = RET
                        break
            elif ((t_in <= ax <= t_out and ay <= t_hw)
                  or (t_in <= ay <= t_out and ax <= t_hw)):
                col = RET
            if dist <= dot_r:
                col = INK
            o = base + x * 4
            grid[o] = col[0]; grid[o + 1] = col[1]; grid[o + 2] = col[2]; grid[o + 3] = col[3]

    # ECG trace, stamped only within each segment's bounding box
    pts = [(ux * u, uy * u) for ux, uy in TRACE]
    for i in range(len(pts) - 1):
        (ax, ay), (bx, by) = pts[i], pts[i + 1]
        pad = trace_hw + 1.0
        x0, x1 = max(0, int(min(ax, bx) - pad)), min(W - 1, int(max(ax, bx) + pad) + 1)
        y0, y1 = max(0, int(min(ay, by) - pad)), min(W - 1, int(max(ay, by) + pad) + 1)
        for y in range(y0, y1 + 1):
            py = y + 0.5
            base = y * W * 4
            for x in range(x0, x1 + 1):
                if dist_to_segment(x + 0.5, py, ax, ay, bx, by) <= trace_hw:
                    o = base + x * 4
                    grid[o] = ECG[0]; grid[o + 1] = ECG[1]
                    grid[o + 2] = ECG[2]; grid[o + 3] = ECG[3]

    # downsample
    out = bytearray(size * size * 4)
    n = ss * ss
    for y in range(size):
        for x in range(size):
            r = g = b = a = 0
            for oy in range(ss):
                base = ((y * ss + oy) * W + x * ss) * 4
                for ox in range(ss):
                    o = base + ox * 4
                    r += grid[o]; g += grid[o + 1]; b += grid[o + 2]; a += grid[o + 3]
            o = (y * size + x) * 4
            out[o] = r // n; out[o + 1] = g // n
            out[o + 2] = b // n; out[o + 3] = a // n
    return bytes(out), size, size


def write_png(path, pixels, w, h):
    raw = b"".join(b"\x00" + pixels[y * w * 4:(y + 1) * w * 4] for y in range(h))
    def chunk(typ, data):
        return (struct.pack(">I", len(data)) + typ + data
                + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF))
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b""))


if __name__ == "__main__":
    write_png(sys.argv[1], *render(512, 2))
    write_png(sys.argv[2], *render(128, 3))
    print("done")