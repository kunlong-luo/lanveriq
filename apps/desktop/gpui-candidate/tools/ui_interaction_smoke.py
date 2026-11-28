#!/usr/bin/env python3
"""Actual synthetic keyboard/pixel smoke; not semantic accessibility or physical L3."""
import itertools
import json
import os
import pathlib
import subprocess
import time

from package_audit import ROOT, NAME, digest
from window_smoke import main as smoke, command, wait_for

W, H = 96, 64
PIXELS = W * H * 3

def distance(first, second):
    if len(first) != PIXELS or len(second) != PIXELS:
        raise ValueError("unexpected RGB sample size")
    return sum(abs(a-b) for a,b in zip(first,second)) / PIXELS

def brightness(image):
    if len(image) != PIXELS:
        raise ValueError("unexpected RGB sample size")
    return sum(image) / PIXELS

def read_pixels(wid, env, crop):
    # Exact synthetic child client window only; no desktop capture, no image files.
    image = subprocess.check_output(["/usr/bin/import", "-window", wid,
        "-crop", crop, "+repage", "-resize", f"{W}x{H}!",
        "-colorspace", "sRGB", "-depth", "8", "rgb:-"], env=env,
        stderr=subprocess.DEVNULL, timeout=5)
    if len(image) != PIXELS:
        raise RuntimeError("unexpected synthetic window pixel length")
    return image

def stable_pixels(wid, env, crop):
    previous = None
    def settled():
        nonlocal previous
        current = read_pixels(wid, env, crop)
        stable = previous is not None and distance(current, previous) <= 0.25
        previous = current
        return current if stable else None
    time.sleep(0.35)
    return wait_for(settled, seconds=8)

def exercise(app, wid, env, geometry, result):
    def live():
        if app.poll() is not None:
            raise RuntimeError("candidate exited during UI exercise")
    def resize(width, height):
        command(["/usr/bin/xdotool", "windowsize", wid, str(width), str(height)], env)
        wait_for(lambda: geometry() == (width,height))
        time.sleep(0.6)
        live()
    def key(*keys):
        live()
        command(["/usr/bin/xdotool", "key", "--clearmodifiers", "--delay", "160", *keys], env)
    def content():
        live()
        return stable_pixels(wid, env, "620x580+260+20")
    resize(900,620)
    command(["/usr/bin/xdotool","windowactivate","--sync",wid],env)
    # Move pointer outside the candidate so hover/tooltip changes do not count.
    command(["/usr/bin/xdotool","mousemove","5","5"],env)
    overview = content()
    # From the initial no-focus state, Tab/Enter should activate Overview first.
    # Observe content only, not the focus outline in the sidebar.
    key("Tab","Return")
    first = content()
    if distance(overview,first) > 1.0:
        raise RuntimeError("first keyboard activation did not preserve initial view")
    views = [first]
    for index in range(1,7):
        key("Tab","Return")
        views.append(content())
        if min(distance(views[-1],other) for other in views[:-1]) <= 1.0:
            raise RuntimeError(f"navigation did not produce distinct content at step {index+1}")
    minimum = min(distance(a,b) for a,b in itertools.combinations(views,2))
    result["distinct_keyboard_views"] = len(views)
    result["minimum_view_rgb_delta"] = round(minimum,3)
    print("PASS: seven distinct main-content views via sequential Tab/Return",flush=True)

    # About follows the seventh navigation item. Its first three enabled
    # controls are System, Light, Dark. Compare global content brightness,
    # not localized text, button colors, or raw screenshots.
    key("Tab","Return")  # System
    key("Tab","Return")  # Light
    light = content()
    key("Tab","Return")  # Dark
    dark = content()
    if brightness(light)-brightness(dark) < 40:
        raise RuntimeError("explicit Light/Dark keyboard activation did not change content brightness")
    key("shift+Tab","space")  # return to Light by reverse traversal
    restored = content()
    if abs(brightness(restored)-brightness(light)) > 5 or brightness(restored)-brightness(dark) < 40:
        raise RuntimeError("reverse Tab/Space did not restore light appearance")
    result["theme_brightness"] = {name:round(brightness(image),3)
        for name,image in (("light",light),("dark",dark),("restored",restored))}
    print("PASS: Light/Dark and reverse Tab/Space brightness transition",flush=True)

    # Exercise resize through both sides of the logical threshold.
    # Geometry and pixel change are not a measurement of the rail width.
    wide = stable_pixels(wid,env,"200x580+12+20")
    resize(680,620)
    narrow = stable_pixels(wid,env,"200x580+12+20")
    if distance(wide,narrow) <= 1.0:
        raise RuntimeError("narrow window did not change left content region")
    resize(900,620)
    expanded = stable_pixels(wid,env,"200x580+12+20")
    if distance(narrow,expanded) <= 1.0:
        raise RuntimeError("wide window did not restore a different left region")
    result["resize_observed"] = [[900,620],[680,620],[900,620]]
    result["left_region_rgb_delta"] = [round(distance(wide,narrow),3),round(distance(narrow,expanded),3)]
    print("PASS: narrow/wide window geometry and left-region pixel changes",flush=True)

def main():
    binary = ROOT/"target/debug"/NAME
    source = os.environ["GITHUB_SHA"]
    result = {"schema":"lanveriq.gpui.synthetic-x11-interaction-v1",
        "source_sha":source,"lock_sha256":digest(ROOT/"Cargo.lock"),"elf_sha256":digest(binary),
        "profile":"debug","scope":"SYNTHETIC_X11_KEYBOARD_AND_WINDOW_PIXELS_NOT_SEMANTIC_ATTESTATION",
        "status":"FAIL","physical_cosmic":"NOT_RUN","chinese_ime":"NOT_RUN",
        "system_appearance_event":"NOT_RUN","accessibility_tree":"NOT_RUN",
        "install_authorized":False,"release_approved":False}
    try:
        smoke(binary=binary,evidence=False,interaction=lambda *args:exercise(*args,result))
        if digest(binary) != result["elf_sha256"]:
            raise RuntimeError("candidate ELF changed")
        result["status"]="PASS"
    finally:
        with (ROOT/"candidate-ui-interaction.json").open("x",encoding="utf-8") as out:
            json.dump(result,out,indent=2,sort_keys=True)
        print("UI interaction",json.dumps(result,sort_keys=True),flush=True)

if __name__ == "__main__":
    main()
