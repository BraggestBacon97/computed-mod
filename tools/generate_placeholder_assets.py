#!/usr/bin/env python3
"""Regenerates the CC0 placeholder assets of this fork.

Every texture under assets/computed/textures is rebuilt deterministically from its
path (flat colours / checker / frames), and the authored model/geometry files are
reduced to plain cubes. This keeps the fork free of upstream artwork: the original
Propulsion Team release shipped its assets under All Rights Reserved; everything
this script writes is dedicated to the public domain under CC0 1.0.

Usage:  python3 tools/generate_placeholder_assets.py [project-root]
"""

import hashlib
import sys
from pathlib import Path

from PIL import Image, ImageFile

ImageFile.LOAD_TRUNCATED_IMAGES = True


def colors(rel: str):
    digest = hashlib.md5(("computed-placeholder:" + rel).encode()).digest()
    r, g, b = 80 + digest[0] % 150, 80 + digest[1] % 150, 80 + digest[2] % 150
    base = (r, g, b, 255)
    dark = (r * 3 // 4, g * 3 // 4, b * 3 // 4, 255)
    light = (min(255, r + 40), min(255, g + 40), min(255, b + 40), 255)
    return base, dark, light


def placeholder(path: Path, root: Path):
    rel = str(path.relative_to(root))
    orig = Image.open(path).convert("RGBA")
    width, height = orig.size
    base, dark, light = colors(rel)
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    pixels = out.load()
    if (width, height) == (1, 1):
        pixels[0, 0] = orig.getpixel((0, 0))
    elif width == 16 and height % 16 == 0 and height > 16:
        frames = height // 16
        step = 120 // max(1, frames - 1) if frames > 1 else 0
        for frame in range(frames):
            shade = tuple(min(255, c + frame * step) for c in base[:3]) + (255,)
            for y in range(frame * 16, (frame + 1) * 16):
                for x in range(16):
                    pixels[x, y] = shade if 1 <= x <= 14 and 1 <= y <= 14 else dark
    elif orig.getbbox() is None or orig.getextrema()[3][0] < 255:
        inset = 1 if min(width, height) <= 8 else 2
        for y in range(inset, height - inset):
            for x in range(inset, width - inset):
                pixels[x, y] = base
    else:
        for y in range(height):
            for x in range(width):
                if x in (0, width - 1) or y in (0, height - 1):
                    pixels[x, y] = dark
                elif (x + y) % 2 == 0:
                    pixels[x, y] = base
                else:
                    pixels[x, y] = light
    out.save(path)


# Files created by this fork itself (not derived from upstream) — never touched.
OWN_ASSET_MARKERS = (
    "portable_monitor",
    "program_disk",
    "link_card",
    "textures/block/computer/",
    "textures/block/monitor/",
    "textures/block/speaker/",
)


def is_own_asset(rel: str) -> bool:
    return any(marker in rel for marker in OWN_ASSET_MARKERS)


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    assets = root / "src" / "main" / "resources" / "assets" / "computed"
    count = 0
    for path in sorted(assets.joinpath("textures").rglob("*.png")):
        rel = str(path.relative_to(assets))
        if is_own_asset(rel):
            continue
        placeholder(path, assets)
        count += 1
    print(f"{root.name}: {count} placeholder textures regenerated (own assets skipped)")


if __name__ == "__main__":
    main()
