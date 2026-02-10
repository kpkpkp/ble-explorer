#!/usr/bin/env python3
"""Auto-detect P2XL screen position in a calibration photo."""
import sys
from PIL import Image, ImageOps

photo = sys.argv[1] if len(sys.argv) > 1 else "captures/calibration_20260207_211636/PXL_20260208_043448136.jpg"

img = ImageOps.exif_transpose(Image.open(photo)).convert("RGB")
w, h = img.size
print(f"Image size: {w}x{h}")

# Search only in the left 40% of image (where P2XL is)
search_w = int(w * 0.40)
# Exclude top 15% (strip/gutter area)
search_y_start = int(h * 0.15)

bright = []
for y in range(search_y_start, h):
    for x in range(search_w):
        idx = y * w + x
        p = img.getpixel((x, y))
        b = p[0] + p[1] + p[2]
        if b > 100:  # brightness threshold
            bright.append((b, x, y))

bright.sort(reverse=True)
n_bright = max(50, len(bright) // 10)  # top 10% of bright pixels
top = bright[:n_bright]
xs = [t[1] for t in top]
ys = [t[2] for t in top]

x1, x2 = min(xs), max(xs)
y1, y2 = min(ys), max(ys)

print(f"Bright cluster pixel box: ({x1},{y1}) to ({x2},{y2})")
print(f"As fractions: [{x1/w:.3f}, {y1/h:.3f}, {x2/w:.3f}, {y2/h:.3f}]")

# Check what the current ref region samples
rx1, ry1, rx2, ry2 = 0.05, 0.58, 0.22, 0.78
crop = img.crop((int(rx1 * w), int(ry1 * h), int(rx2 * w), int(ry2 * h)))
cpx = list(crop.getdata())
avg = (sum(p[0] for p in cpx) // len(cpx),
       sum(p[1] for p in cpx) // len(cpx),
       sum(p[2] for p in cpx) // len(cpx))
print(f"Current ref region [{rx1},{ry1},{rx2},{ry2}] avg: {avg}")

# Sample the detected bright region
crop2 = img.crop((x1, y1, x2, y2))
cpx2 = list(crop2.getdata())
avg2 = (sum(p[0] for p in cpx2) // len(cpx2),
        sum(p[1] for p in cpx2) // len(cpx2),
        sum(p[2] for p in cpx2) // len(cpx2))
print(f"Detected bright region avg: {avg2}")
