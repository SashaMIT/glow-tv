"""Captions, title and end cards for the "What's new" video (1280x720 PNGs), in the same guide style as cards.py.
Usage: python3 whatsnew_cards.py OUTDIR VERSION   (needs Pillow; macOS system fonts)"""
import sys, pathlib
from PIL import Image, ImageDraw

sys.argv, OUT_ARG, VERSION = sys.argv[:2], sys.argv[1], sys.argv[2]
import cards  # noqa: E402  (draws its own cards into OUTDIR on import; we add ours next to them)
from cards import W, H, YELLOW, INK, BLUE, EDGE, font, gradient, caption

OUT = pathlib.Path(OUT_ARG)

CAPS = ["Tonight on free TV", "Everything at a glance", "Zoom into the world", "Markets: crypto, gold, baht",
        "F1 timing, tennis & golf", "Rewind for minutes", "Radio with album covers", "ภาษาไทย  Thai menus",
        "Kids mode with a PIN", "Speed test & data use"]
for i, c in enumerate(CAPS, 1):
    if any("\u0e00" <= ch <= "\u0e7f" for ch in c):
        continue
    caption(i, c, f"new{i}")


def thai_caption(n, thai, english, name):
    """The Thai menus caption: Avenir has no Thai letters, so the Thai word is drawn in Sukhumvit (macOS)."""
    from PIL import ImageFont
    th = ImageFont.truetype("/System/Library/Fonts/Supplemental/SukhumvitSet.ttc", 40, index=4)
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    f = font(40); x, y, h = 48, H - 120, 64
    w1 = d.textlength(thai, font=th); w2 = d.textlength(english.upper(), font=f)
    d.rectangle([x, y, x + 64, y + h], fill=cards.ORANGE)
    d.text((x + 32, y + h / 2), str(n), font=font(40), fill=INK, anchor="mm")
    d.rectangle([x + 68, y, x + 68 + w1 + w2 + 64, y + h], fill=YELLOW)
    d.text((x + 90, y + h / 2), thai, font=th, fill=INK, anchor="lm")
    d.text((x + 90 + w1 + 20, y + h / 2 + 2), english.upper(), font=f, fill=INK, anchor="lm")
    im.save(OUT / f"{name}.png")


thai_caption(8, "ภาษาไทย", "Thai menus", "new8")


def title():
    im = gradient("#6FA8E8", "#2C5FB8"); d = ImageDraw.Draw(im)
    d.text((W / 2, 260), "glow", font=font(210, 9), fill="white", anchor="mm")
    d.text((W / 2, 410), "WHAT'S NEW IN " + VERSION, font=font(64), fill="white", anchor="mm")
    sub = "FREE TV, THE WORLD, YOUR HOME - ON A CHEAP ANDROID BOX"
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
