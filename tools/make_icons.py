"""Builds Kinerary's launcher icons from art/icon.png (a red K on black).

    python tools/make_icons.py
"""
from pathlib import Path

from PIL import Image

root = Path(__file__).resolve().parent.parent
art = Image.open(root / "art" / "icon.png").convert("RGB")
res = root / "app" / "src" / "main" / "res"

# Black becomes transparent: alpha from brightness, colour un-premultiplied, so the result over
# black looks exactly like the artwork. A small floor drops near-black noise.
FLOOR = 14
rgba = Image.new("RGBA", art.size)
px_in, px_out = art.load(), rgba.load()
for y in range(art.height):
    for x in range(art.width):
        r, g, b = px_in[x, y]
        m = max(r, g, b)
        a = 0 if m <= FLOOR else min(255, round((m - FLOOR) * 255 / (255 - FLOOR)))
        if a == 0:
            px_out[x, y] = (0, 0, 0, 0)
        else:
            k = 255 / max(m, 1)
            px_out[x, y] = (min(255, round(r * k)), min(255, round(g * k)), min(255, round(b * k)), a)

logo = rgba.crop(rgba.getbbox())
white = Image.new("RGBA", logo.size, (255, 255, 255, 255))
white.putalpha(logo.getchannel("A"))


def place(img: Image.Image, canvas_px: int, frac: float) -> Image.Image:
    """Centres img on a transparent square canvas, its longer side frac of the canvas."""
    scale = canvas_px * frac / max(img.width, img.height)
    w, h = round(img.width * scale), round(img.height * scale)
    scaled = img.resize((w, h), Image.LANCZOS)
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    canvas.paste(scaled, ((canvas_px - w) // 2, (canvas_px - h) // 2), scaled)
    return canvas


for name, scale in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    out = res / f"drawable-{name}"
    out.mkdir(exist_ok=True)
    size = round(108 * scale)
    # Adaptive icons show the middle 72dp of 108dp and masks cut that to a 66dp circle; 44% keeps the K inside it
    place(logo, size, 0.44).save(out / "ic_launcher_foreground.png", optimize=True)
    place(white, size, 0.44).save(out / "ic_launcher_monochrome.png", optimize=True)

preview = Image.new("RGBA", (432, 432), (0, 0, 0, 255))
fg = Image.open(res / "drawable-xxxhdpi" / "ic_launcher_foreground.png")
preview.paste(fg, (0, 0), fg)
preview.save(root / "art" / "icon_preview.png")
