#!/usr/bin/env python3
"""Prove real RustDesk framebuffer without OCR or uploaded screenshots.

Runs INSIDE peer-a only; peer-b's synthetic scene is neither mounted nor
read by this process. Captures exclusively the control peer's own X11 root
window; three spatially ordered, saturated remote-only colored tiles must be
present in that real framebuffer. Emits only coarse counts and pixel target.
"""
from __future__ import annotations

import json
import pathlib
import subprocess
import sys
from PIL import Image


class FrameRejected(RuntimeError):
    pass


def capture() -> Image.Image:
    # Screenshots remain ephemeral inside synthetic peer-a container.
    src = pathlib.Path("/tmp/r2-remote-view.xwd")
    png = pathlib.Path("/tmp/r2-remote-view.png")
    for args in (
        ["xwd", "-root", "-silent", "-out", str(src)],
        ["convert", f"xwd:{src}", f"png:{png}"],
    ):
        p = subprocess.run(args, capture_output=True, timeout=15, check=False)
        if p.returncode:
            raise FrameRejected("Synthetic X11 framebuffer capture failed")
    try:
        image = Image.open(png).convert("RGB")
        image.load()
        return image
    finally:
        src.unlink(missing_ok=True)
        png.unlink(missing_ok=True)


def analyze(image: Image.Image) -> dict:
    width, height = image.size
    if not (500 <= width <= 3840 and 400 <= height <= 2160):
        raise FrameRejected("Unexpected synthetic X11 screen size")
    # Avoid depending on the video encoder's exact RGB values; require
    # distinctive red, green and blue rectangles in correct geometry.
    categories: dict[str, list[tuple[int, int]]] = {
        "red": [], "green": [], "blue": []
    }
    px = image.load()
    for y in range(0, height, 2):
        for x in range(0, width, 2):
            r, g, b = px[x, y]
            if r >= 170 and g <= 110 and b <= 130:
                categories["red"].append((x, y))
            if g >= 150 and r <= 130 and b <= 130:
                categories["green"].append((x, y))
            if b >= 160 and r <= 110 and 50 <= g <= 180:
                categories["blue"].append((x, y))
    for name in ("red", "green"):
        if len(categories[name]) < 450:
            raise FrameRejected(f"Missing real remote-only synthetic {name} pixels")

    def center(points: list[tuple[int, int]]) -> tuple[float, float]:
        return (sum(p[0] for p in points) / len(points),
                sum(p[1] for p in points) / len(points))

    red_x, red_y = center(categories["red"])
    green_x, green_y = center(categories["green"])
    distance = green_x - red_x
    if not (65 < distance < 420 and abs(red_y - green_y) < 0.35 * distance):
        raise FrameRejected("Synthetic remote tile geometry was not observed")
    target_x = round(green_x + distance)
    target_y = round((red_y + green_y) / 2)
    if not (20 < target_x < width - 20 and 20 < target_y < height - 20):
        raise FrameRejected("Projected remote input target outside screen")
    # The target MUST be inside the third, blue tile, not merely near UI chrome.
    radius = max(18, round(distance * 0.22))
    blue_in_target = sum(
        1 for x, y in categories["blue"]
        if abs(x - target_x) <= radius and abs(y - target_y) <= radius
    )
    if blue_in_target < 100:
        raise FrameRejected("No synthetic blue remote input target where expected")
    return {
        "visual_proof": "PASS_REAL_CONTROLLER_FRAMEBUFFER_SYNTHETIC_TILES",
        "red_pixels": len(categories["red"]),
        "green_pixels": len(categories["green"]),
        "blue_target_pixels": blue_in_target,
        "input_target_x": target_x,
        "input_target_y": target_y,
        "screenshots_exported": False,
        "remote_input_proven": False,
    }


def main() -> int:
    try:
        print(json.dumps(analyze(capture()), sort_keys=True))
        return 0
    except (OSError, subprocess.TimeoutExpired, FrameRejected) as exc:
        print(json.dumps({"visual_proof": "FAIL", "reason": str(exc)}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
