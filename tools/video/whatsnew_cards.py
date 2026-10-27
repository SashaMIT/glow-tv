"""Captions, title and end cards for the "What's new" video (1280x720 PNGs), in the same guide style as cards.py.
Usage: python3 whatsnew_cards.py OUTDIR VERSION   (needs Pillow; macOS system fonts)"""
import sys, pathlib
from PIL import Image, ImageDraw

sys.argv, OUT_ARG, VERSION = sys.argv[:2], sys.argv[1], sys.argv[2]
import cards  # noqa: E402  (draws its own cards into OUTDIR on import; we add ours next to them)
from cards import W, H, YELLOW, INK, BLUE, EDGE, font, gradient, caption

OUT = pathlib.Path(OUT_ARG)

CAPS = ["Tonight on free TV", "Everything at a glance", "A live map of the world", "Live cams around the world",
        "Weather & rain radar", "What the world expects", "Sports now & F1 timing", "Score banners over TV",
        "Markets: crypto, gold, the baht", "Rewind for minutes", "Radio with album covers", "Kids mode with a PIN",
        "The doorbell on your TV", "Speed test & data use"]
for i, c in enumerate(CAPS, 1):
    caption(i, c, f"new{i}")


def title():
    im = gradient("#6FA8E8", "#2C5FB8"); d = ImageDraw.Draw(im)
    d.text((W / 2, 260), "glow", font=font(210, 9), fill="white", anchor="mm")
    d.text((W / 2, 410), "WHAT'S NEW IN " + VERSION, font=font(64), fill="white", anchor="mm")
    sub = "FREE TV, THE WORLD AND YOUR HOME, ON A CHEAP ANDROID BOX"
    f = font(36); tw = d.textlength(sub, font=f)
    d.rectangle([W / 2 - tw / 2 - 24, 480, W / 2 + tw / 2 + 24, 538], fill=YELLOW)
    d.text((W / 2, 510), sub, font=f, fill=INK, anchor="mm")
    im.save(OUT / "newtitle.png")


def end():
    im = gradient("#6FA8E8", "#2C5FB8"); d = ImageDraw.Draw(im)
    d.text((W / 2, 120), "OPEN SOURCE  ·  MIT", font=font(80), fill="white", anchor="mm")
    url = "github.com/SashaMIT/glow-tv"
    f = font(50, 0); tw = d.textlength(url, font=f)
    d.rectangle([W / 2 - tw / 2 - 28, 190, W / 2 + tw / 2 + 28, 262], fill=YELLOW)
    d.text((W / 2, 227), url, font=f, fill=INK, anchor="mm")
    rows = [("0.28 s", "to change channel"), ("1.0 s", "home screen from cold"),
            ("2,300", "free channels, ~1,000 with listings"), ("1.3 MB", "the whole app")]
    y = 318
    for big, small in rows:
        d.rectangle([250, y, 450, y + 70], fill="#3A66CC")
        d.text((350, y + 36), big, font=font(42), fill="white", anchor="mm")
        d.rectangle([454, y, 1030, y + 70], fill=BLUE)
        d.rectangle([454, y, 1030, y + 4], fill=EDGE)
        d.text((480, y + 37), small.upper(), font=font(34, 0), fill="white", anchor="lm")
        y += 82
    im.save(OUT / "newend.png")


title(); end()
print("ok")
