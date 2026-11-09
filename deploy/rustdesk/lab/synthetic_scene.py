#!/usr/bin/env python3
"""Synthetic RustDesk remote framebuffer and mouse input probe (R2-02).

Owned entirely by disposable CI. Distinct red/green/blue rectangles must
arrive through the actual RustDesk remote display. Only clicking the blue
rectangle in THIS peer's X11 window can create /tmp/r2-remote-input-ok.
Never contains or captures personal data.
"""
from __future__ import annotations

import os
import pathlib
import tkinter as tk

PROOF = pathlib.Path("/tmp/r2-remote-input-ok")
root = tk.Tk()
root.title("LANVERIQ_R2_REMOTE_ONLY_CANVAS")
root.geometry("940x510+90+85")
root.resizable(False, False)

canvas = tk.Canvas(root, width=930, height=475, background="#111a25", highlightthickness=0)
canvas.pack()
canvas.create_text(465, 42, text="LANVERIQ SYNTHETIC REMOTE-ONLY TEST",
                   fill="#ffffff", font=("DejaVu Sans", 19, "bold"))

for left, color in ((55, "#ec2030"), (315, "#1ce33d"), (575, "#2065f4")):
    canvas.create_rectangle(left, 105, left + 210, 330, fill=color, outline=color, width=0)
canvas.create_text(165, 220, text="R2-RED", fill="#ffffff", font=("DejaVu Sans", 17, "bold"))
canvas.create_text(420, 220, text="R2-GREEN", fill="#101010", font=("DejaVu Sans", 17, "bold"))
canvas.create_text(680, 220, text="CLICK", fill="#ffffff", font=("DejaVu Sans", 19, "bold"))
canvas.create_text(465, 410, text="Mouse must arrive via real RustDesk remote control.",
                   fill="#ffffff", font=("DejaVu Sans", 12))


def on_click(event: tk.Event) -> None:
    if 575 <= event.x <= 785 and 105 <= event.y <= 330:
        old_umask = os.umask(0o077)
        try:
            PROOF.write_text("R2_SYNTHETIC_REMOTE_CLICK_CONFIRMED\n", encoding="utf-8")
        finally:
            os.umask(old_umask)
        canvas.create_rectangle(575, 105, 785, 330, fill="#973fe8",
                                outline="#973fe8", width=0)
        canvas.create_text(680, 220, text="INPUT OK", fill="#ffffff",
                           font=("DejaVu Sans", 19, "bold"))


canvas.bind("<Button-1>", on_click)
root.mainloop()
