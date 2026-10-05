"""Compares the port's pictures (out/port) with the original's (out/original), case by case.

Prints the mean and the worst difference in brightness (0-255) over a grid of points, and writes
out/compare/<case>.png with the original on top and the port below. The two are never identical:
the original breathes and ripples while its picture is taken, and it blurs where the port shades.
A mean of a few levels is a match; a mean above ten is worth a look.
"""
import glob
import os
import sys

from PIL import Image

here = os.path.dirname(os.path.abspath(__file__))
os.makedirs(os.path.join(here, 'out/compare'), exist_ok=True)
only = sys.argv[1] if len(sys.argv) > 1 else ''
for path in sorted(glob.glob(os.path.join(here, 'out/original/*.png'))):
    name = os.path.basename(path)
    port = os.path.join(here, 'out/port', name)
    if only not in name or not os.path.exists(port):
        continue
    a = Image.open(path).convert('RGB')
    b = Image.open(port).convert('RGB')
    if a.size != b.size:
        a = a.resize(b.size, Image.BILINEAR)
    total = worst = count = 0
    for x in range(4, a.width, max(1, a.width // 40)):
        for y in range(4, a.height, max(1, a.height // 60)):
            pa, pb = a.getpixel((x, y)), b.getpixel((x, y))
            d = abs(sum(pa) - sum(pb)) / 3
            total += d
            worst = max(worst, d)
            count += 1
    both = Image.new('RGB', (a.width, a.height * 2 + 6), (255, 0, 255))
    both.paste(a, (0, 0))
    both.paste(b, (0, a.height + 6))
    both.save(os.path.join(here, 'out/compare', name))
    print('%-28s mean %5.1f   worst %4.0f' % (name[:-4], total / count, worst))
