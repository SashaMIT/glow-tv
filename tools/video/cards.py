"""Draws the promo video's captions, title card and end card (1280x720 PNGs) in the guide's own style.
Usage: python3 cards.py OUTDIR   (needs Pillow; macOS system fonts)"""
import sys, pathlib
from PIL import Image, ImageDraw, ImageFont

OUT = pathlib.Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
W, H = 1280, 720
COND = "/System/Library/Fonts/Avenir Next Condensed.ttc"
YELLOW, ORANGE, INK, BLUE, EDGE, NAVY = "#F5D33C", "#E3A81B", "#14306F", "#1C3A8E", "#4C78D0", "#070B18"

def font(size, index=8):  # 8 = Heavy, 0 = Bold, 5 = Medium
    return ImageFont.truetype(COND, size, index=index)

def caption(n, text, name):
    """Guide-style row at the bottom left: orange number cell + yellow label."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    f = font(40); tw = d.textlength(text.upper(), font=f)
    x, y, h = 48, H - 120, 64
    d.rectangle([x, y, x + 64, y + h], fill=ORANGE)
    d.text((x + 32, y + h / 2), str(n), font=font(40), fill=INK, anchor="mm")
    d.rectangle([x + 68, y, x + 68 + tw + 44, y + h], fill=YELLOW)
    d.text((x + 90, y + h / 2 + 2), text.upper(), font=f, fill=INK, anchor="lm")
    im.save(OUT / f"{name}.png")

def gradient(top, bottom):
    im = Image.new("RGB", (W, H), top); d = ImageDraw.Draw(im)
    t = Image.new("RGB", (1, 1), top).getpixel((0, 0)); b = Image.new("RGB", (1, 1), bottom).getpixel((0, 0))
    for yy in range(H):
        k = yy / (H - 1)
        d.line([(0, yy), (W, yy)], fill=tuple(int(t[i] + (b[i] - t[i]) * k) for i in range(3)))
    return im

def title():
    im = gradient("#6FA8E8", "#2C5FB8"); d = ImageDraw.Draw(im)
    d.text((W / 2, 290), "glow", font=font(210, 9), fill="white", anchor="mm")
    d.text((W / 2 + 150, 395), "guide", font=font(54, 5), fill="white", anchor="mm")
    sub = "A CHEAP ANDROID TV BOX, REBUILT"
    f = font(40); tw = d.textlength(sub, font=f)
    d.rectangle([W / 2 - tw / 2 - 24, 480, W / 2 + tw / 2 + 24, 540], fill=YELLOW)
    d.text((W / 2, 512), sub, font=f, fill=INK, anchor="mm")
    im.save(OUT / "title.png")

def end():
    im = gradient("#6FA8E8", "#2C5FB8"); d = ImageDraw.Draw(im)
    d.text((W / 2, 120), "OPEN SOURCE", font=font(84), fill="white", anchor="mm")
    url = "github.com/SashaMIT/glow-tv"
    f = font(50, 0); tw = d.textlength(url, font=f)
    d.rectangle([W / 2 - tw / 2 - 28, 190, W / 2 + tw / 2 + 28, 262], fill=YELLOW)
    d.text((W / 2, 227), url, font=f, fill=INK, anchor="mm")
    rows = [("70 ms", "to load 2,300 free channels"), ("0.15 s", "to open the TV guide"),
            ("15 / 15", "top sports & news picks play"), ("828 KB", "the whole app")]
    y = 318
    for big, small in rows:
        d.rectangle([250, y, 450, y + 70], fill="#3A66CC")
        d.text((350, y + 36), big, font=font(42), fill="white", anchor="mm")
        d.rectangle([454, y, 1030, y + 70], fill=BLUE)
        d.rectangle([454, y, 1030, y + 4], fill=EDGE)
        d.text((480, y + 37), small.upper(), font=font(34, 0), fill="white", anchor="lm")
        y += 82
    im.save(OUT / "end.png")

CAPS = ["New home screen", "Classic TV guide", "Sports: action & fight first", "Live in one press",
        "Type 502: Al Jazeera", "Thai TV with real listings", "World headlines", "Search that ranks"]
for i, c in enumerate(CAPS, 1):
    caption(i, c, f"cap{i}")
title(); end()
print("ok")
