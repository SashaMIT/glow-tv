"""Captions, title and end cards for Glow's one-minute promo video (1280x720 PNGs), in the guide's own style.
Usage: python3 promo_cards.py OUTDIR   (needs Pillow; macOS system fonts)"""
import sys, pathlib
from PIL import ImageDraw

OUT_ARG = sys.argv[1]
import cards  # noqa: E402  (draws its own cards into OUTDIR on import; we add ours next to them)
from cards import W, YELLOW, INK, BLUE, EDGE, font, gradient, caption

OUT = pathlib.Path(OUT_ARG)
CAPS = ["One home screen for everything", "2,400 free channels, a real TV guide", "Channels change in 0.28 s",
        "Tonight's best, picked for you", "Rewind live TV", "Live scores for your teams", "F1 timing",
        "Score banners over anything", "The world at a glance", "A live map of the world",
        "44 live cams, full screen", "Weather & rain radar", "What the world expects",
        "Crypto, gold & currencies", "Radio with album covers", "Kids mode with a PIN",
        "Your doorbell on the TV", "Phone remote & all your apps"]
for i, c in enumerate(CAPS, 1):
    caption(i, c, f"p{i}")

def banner(d, y, text, size=36):
    f = font(size); tw = d.textlength(text, font=f)
    d.rectangle([W / 2 - tw / 2 - 24, y, W / 2 + tw / 2 + 24, y + size + 22], fill=YELLOW)
    d.text((W / 2, y + (size + 22) / 2), text, font=f, fill=INK, anchor="mm")

im = gradient("#6FA8E8", "#2C5FB8"); d = ImageDraw.Draw(im)
d.text((W / 2, 262), "glow", font=font(210, 9), fill="white", anchor="mm")
d.text((W / 2, 412), "ONE BOX. EVERYTHING ON YOUR TV.", font=font(64), fill="white", anchor="mm")
banner(d, 482, "FREE TV, THE WORLD AND YOUR HOME, ON A CHEAP ANDROID BOX")
im.save(OUT / "ptitle.png")

im = gradient("#6FA8E8", "#2C5FB8"); d = ImageDraw.Draw(im)
d.text((W / 2, 72), "glow", font=font(110, 9), fill="white", anchor="mm")
d.text((W / 2, 164), "FREE  ·  OPEN SOURCE  ·  NO SUBSCRIPTION", font=font(54), fill="white", anchor="mm")
url = "github.com/SashaMIT/glow-tv"
f = font(50, 0); tw = d.textlength(url, font=f)
d.rectangle([W / 2 - tw / 2 - 28, 214, W / 2 + tw / 2 + 28, 286], fill=YELLOW)
d.text((W / 2, 251), url, font=f, fill=INK, anchor="mm")
rows = [("0.28 s", "to change channel"), ("1 s", "to the home screen, from cold"),
        ("2,400", "free channels, ~900 with listings"), ("1.4 MB", "the whole app")]
y = 330
for big, small in rows:
    d.rectangle([250, y, 450, y + 70], fill="#3A66CC")
    d.text((350, y + 36), big, font=font(42), fill="white", anchor="mm")
    d.rectangle([454, y, 1030, y + 70], fill=BLUE)
    d.rectangle([454, y, 1030, y + 4], fill=EDGE)
    d.text((480, y + 37), small.upper(), font=font(34, 0), fill="white", anchor="lm")
    y += 82
im.save(OUT / "pend.png")
print("ok")
