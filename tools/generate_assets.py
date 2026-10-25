#!/usr/bin/env python3
"""
Génère TOUTES les textures et tous les sons du mod The Black Parasite (aucun fichier externe, aucune licence tierce).

Usage :   python3 tools/generate_assets.py          (depuis la racine du projet)
Dépend de : Pillow, numpy, ffmpeg (avec libvorbis) pour convertir les sons en .ogg.

Les fichiers sont écrits dans src/main/resources/assets/blackparasite/{textures,sounds}.
Le script est déterministe (graine fixe) : le relancer reproduit exactement les mêmes fichiers.
"""
import math, os, subprocess, sys, tempfile, wave, json, zlib
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "blackparasite")
TEX = os.path.join(ASSETS, "textures")
SND = os.path.join(ASSETS, "sounds")
for d in ("entity", "gui", "item"):
    os.makedirs(os.path.join(TEX, d), exist_ok=True)
os.makedirs(SND, exist_ok=True)
rng = np.random.default_rng(1337)

# ----------------------------------------------------------------------------------------------------------------
# Outils de bruit
# ----------------------------------------------------------------------------------------------------------------
def fbm(h, w, octaves=5, base=4, persistence=0.55, seed_rng=None):
    """Bruit fractal 0..1 (valeurs lissées par interpolation bicubique, tuilable non requis)."""
    r = seed_rng or rng
    out = np.zeros((h, w), dtype=np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        n = base * (2 ** o)
        grid = r.random((n + 1, n + 1)).astype(np.float32)
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += amp * (np.asarray(img, dtype=np.float32) / 255.0)
        total += amp
        amp *= persistence
    out /= total
    out -= out.min()
    return out / max(1e-6, out.max())

def clamp8(a):
    return np.clip(a, 0, 255).astype(np.uint8)

def vein_layer(h, w, count, color, length=40, width=1, seed_rng=None, glow=True):
    """Calque RGBA de veines sinueuses."""
    r = seed_rng or rng
    big = 4
    img = Image.new("RGBA", (w * big, h * big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _ in range(count):
        x, y = r.random() * w, r.random() * h
        ang = r.random() * math.tau
        pts = [(x * big, y * big)]
        for _ in range(length):
            ang += (r.random() - 0.5) * 0.9
            x += math.cos(ang) * 1.2
            y += math.sin(ang) * 1.2
            pts.append((x * big, y * big))
            if r.random() < 0.04:  # ramification
                bx, by, ba = x, y, ang + (r.random() - 0.5) * 1.8
                bp = [(bx * big, by * big)]
                for _ in range(length // 3):
                    ba += (r.random() - 0.5) * 0.8
                    bx += math.cos(ba) * 1.1
                    by += math.sin(ba) * 1.1
                    bp.append((bx * big, by * big))
                d.line(bp, fill=color, width=max(1, (width * big) // 2))
        d.line(pts, fill=color, width=width * big)
    if glow:
        halo = img.filter(ImageFilter.GaussianBlur(big * 1.3))
        img = Image.alpha_composite(halo, img)
    return img.resize((w, h), Image.LANCZOS)

def save(img, *path):
    p = os.path.join(TEX, *path)
    img.save(p)
    return p

# ----------------------------------------------------------------------------------------------------------------
# Textures de peau (atlas 128x128 : 4 zones de 64x64)
#   A (0,0)   : tronc          B (64,0)  : tête et membres
#   C (0,64)  : tentacules     D (64,64) : dents, griffes, pointes
# ----------------------------------------------------------------------------------------------------------------
def skin(kind, veined):
    W = H = 128
    r = np.random.default_rng(zlib.crc32(kind.encode()) % (2 ** 32) + (7 if veined else 0))
    palette = {
        "predator":    dict(base=(16, 12, 18), hi=(70, 78, 98),  vein=(190, 30, 30, 255),  alpha=False),
        "ghost":       dict(base=(10, 10, 16), hi=(80, 100, 120), vein=(90, 220, 200, 200), alpha=True),
        "colossus":    dict(base=(20, 18, 20), hi=(96, 92, 100),  vein=(220, 40, 20, 255),  alpha=False),
        "abomination": dict(base=(14, 8, 20),  hi=(104, 60, 120), vein=(255, 30, 70, 255),  alpha=False),
    }[kind]
    n1 = fbm(H, W, 5, 4, seed_rng=r)
    n2 = fbm(H, W, 4, 10, seed_rng=r)
    base = np.array(palette["base"], dtype=np.float32)
    hi = np.array(palette["hi"], dtype=np.float32)
    # Peau noire luisante : le bruit fin donne des reflets humides localisés.
    gloss = np.clip((n2 - 0.62) * 4.0, 0, 1) ** 1.5
    mott = (n1 - 0.5) * 22
    rgb = base[None, None, :] + mott[..., None] + gloss[..., None] * (hi - base)[None, None, :] * 0.8
    # Plaques / écailles (surtout colosse)
    if kind == "colossus":
        for y in range(0, H, 8):
            for x in range(0, W, 8):
                rgb[y, x:x + 8] -= 14
                rgb[y:y + 8, x] -= 14
        rgb += (fbm(H, W, 3, 3, seed_rng=r)[..., None] - 0.5) * 30
    img = Image.fromarray(clamp8(rgb)).convert("RGBA")
    # Zone C : tentacules, rainures transversales luisantes + ventouses sombres
    pix = np.asarray(img).copy()
    for y in range(64, 128):
        if (y // 3) % 4 == 0:
            pix[y, 0:64, :3] = clamp8(pix[y, 0:64, :3].astype(np.float32) * 1.7 + 14)
    img = Image.fromarray(pix)
    d = ImageDraw.Draw(img)
    for _ in range(60):
        cx, cy = int(r.integers(0, 64)), int(r.integers(64, 128))
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=(4, 2, 6, 255))
    # Zone D : os / dents / griffes
    bone = fbm(64, 64, 4, 6, seed_rng=r)
    dpix = np.zeros((64, 64, 4), dtype=np.uint8)
    dpix[..., 0] = clamp8(120 + bone * 60)
    dpix[..., 1] = clamp8(112 + bone * 50)
    dpix[..., 2] = clamp8(92 + bone * 40)
    dpix[..., 3] = 255
    dimg = Image.fromarray(dpix)
    dd = ImageDraw.Draw(dimg)
    for _ in range(30):  # fissures
        x, y = int(r.integers(0, 64)), int(r.integers(0, 64))
        dd.line([(x, y), (x + int(r.integers(-6, 7)), y + int(r.integers(2, 9)))], fill=(30, 22, 20, 255), width=1)
    for y in range(0, 64, 16):  # extrémité rouge sombre
        dd.rectangle([0, y + 12, 63, y + 15], fill=(110, 18, 22, 255))
    img.paste(dimg, (64, 64))
    # Veines
    count = 34 if veined else 14
    veins = vein_layer(H, W, count, palette["vein"], length=45, width=1, seed_rng=r)
    if kind == "ghost":
        veins = veins.point(lambda v: int(v * 0.7))
    img = Image.alpha_composite(img, veins)
    if veined:  # stade avancé : fines lignes de lumière le long du corps
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        for y in range(2, H, 9):
            gd.line([(0, y), (W, y + int(r.integers(-2, 3)))], fill=palette["vein"][:3] + (60,), width=1)
        img = Image.alpha_composite(img, glow)
    if palette["alpha"]:  # fantôme : contours en fumée, alpha variable
        a = 150 + (fbm(H, W, 4, 5, seed_rng=r) * 105).astype(np.uint8)
        arr = np.asarray(img).copy()
        arr[..., 3] = np.clip(a, 120, 255)
        img = Image.fromarray(arr)
    return img

for kind in ("predator", "ghost", "colossus", "abomination"):
    save(skin(kind, False), "entity", f"parasite_{kind}.png")
    save(skin(kind, True), "entity", f"parasite_{kind}_veined.png")

# ----------------------------------------------------------------------------------------------------------------
# Calque du Porteur (disposition UV d'un joueur 64x64) : taches noires, veines, zones suintantes
# ----------------------------------------------------------------------------------------------------------------
def porteur_overlay():
    r = np.random.default_rng(99)
    n = fbm(64, 64, 4, 4, seed_rng=r)
    patches = np.clip((n - 0.52) * 6, 0, 1)
    arr = np.zeros((64, 64, 4), dtype=np.uint8)
    arr[..., 0] = clamp8(10 + fbm(64, 64, 3, 8, seed_rng=r) * 18)
    arr[..., 1] = clamp8(6 + fbm(64, 64, 3, 8, seed_rng=r) * 10)
    arr[..., 2] = clamp8(12 + fbm(64, 64, 3, 8, seed_rng=r) * 16)
    arr[..., 3] = clamp8(patches * 235)
    img = Image.fromarray(arr)
    veins = vein_layer(64, 64, 22, (170, 20, 24, 235), length=20, width=1, seed_rng=r, glow=False)
    return Image.alpha_composite(img, veins)

save(porteur_overlay(), "entity", "porteur_overlay.png")

# ----------------------------------------------------------------------------------------------------------------
# Oeil émissif (cube 8x8x2 sur texture 32x16 : face avant = (2,2)-(10,10)). Teinté par la couleur de la forme.
# ----------------------------------------------------------------------------------------------------------------
def eye_texture():
    img = Image.new("RGBA", (32, 16), (255, 255, 255, 255))
    px = img.load()
    # Fond (côtés, dessus) : blanc doux pour un halo uniforme
    for y in range(16):
        for x in range(32):
            px[x, y] = (230, 230, 230, 255)
    # Face avant : iris dégradé, fente verticale noire
    cx, cy = 5.5 + 0.5, 5.5 + 0.5  # centre en coordonnées de la face (2..10)
    for y in range(2, 10):
        for x in range(2, 10):
            dx, dy = (x - 5.5) / 4.0, (y - 5.5) / 4.0
            dist = math.sqrt(dx * dx + dy * dy)
            v = max(0.25, 1.0 - dist * 0.75)
            c = int(255 * v)
            px[x, y] = (c, c, c, 255)
            if abs(x - 5.5) < 0.9 and abs(y - 5.5) < 3.4:  # pupille fendue
                px[x, y] = (0, 0, 0, 255)
    return img

save(eye_texture(), "entity", "eye.png")

# ----------------------------------------------------------------------------------------------------------------
# Icônes (64x64, dessinées en 256x256 puis réduites)
# ----------------------------------------------------------------------------------------------------------------
INK = (12, 8, 14, 255)
BLOOD = (170, 18, 32, 255)
RED = (235, 60, 52, 255)
GLOW = (255, 140, 100, 255)
STEEL = (170, 176, 188, 255)
BONE = (225, 215, 195, 255)

def canvas():
    return Image.new("RGBA", (256, 256), (0, 0, 0, 0))

def finish(img, halo=(200, 30, 40)):
    h = img.filter(ImageFilter.GaussianBlur(10))
    ha = np.asarray(h).copy()
    ha[..., 0], ha[..., 1], ha[..., 2] = halo
    ha[..., 3] = (ha[..., 3] * 0.55).astype(np.uint8)
    out = Image.alpha_composite(Image.fromarray(ha), img)
    return out.resize((64, 64), Image.LANCZOS)

def poly(d, pts, fill, outline=None):
    d.polygon(pts, fill=fill)
    if outline:
        d.line(pts + [pts[0]], fill=outline, width=5)

def icon_absorb():
    im = canvas(); d = ImageDraw.Draw(im)
    d.ellipse([40, 40, 216, 216], fill=INK, outline=BLOOD, width=8)
    for i in range(10):
        a = math.tau * i / 10
        p1 = (128 + math.cos(a - 0.17) * 84, 128 + math.sin(a - 0.17) * 84)
        p2 = (128 + math.cos(a + 0.17) * 84, 128 + math.sin(a + 0.17) * 84)
        tip = (128 + math.cos(a) * 36, 128 + math.sin(a) * 36)
        poly(d, [p1, p2, tip], BONE)
    d.ellipse([108, 108, 148, 148], fill=RED)
    return finish(im)

def icon_claws():
    im = canvas(); d = ImageDraw.Draw(im)
    for k, off in enumerate((-56, 0, 56)):
        pts = [(70 + off, 24), (96 + off, 24), (160 + off * 0.2 + 20, 232), (120 + off * 0.2, 232)]
        pts = [(70 + off, 20), (100 + off, 30), (150 + off * 0.3, 232), (118 + off * 0.3, 200)]
        poly(d, pts, BONE if k != 1 else GLOW, BLOOD)
    return finish(im)

def icon_whip():
    im = canvas(); d = ImageDraw.Draw(im)
    pts = []
    for t in range(0, 101):
        u = t / 100
        x = 30 + u * 190
        y = 190 - math.sin(u * math.pi * 1.6) * 110 - u * 40
        pts.append((x, y))
    for i in range(len(pts) - 1):
        w = int(22 * (1 - i / 100)) + 3
        d.line([pts[i], pts[i + 1]], fill=INK, width=w + 6)
    for i in range(len(pts) - 1):
        w = int(22 * (1 - i / 100)) + 3
        d.line([pts[i], pts[i + 1]], fill=RED if i > 60 else BLOOD, width=w)
    tx, ty = pts[-1]
    poly(d, [(tx, ty - 22), (tx + 30, ty + 6), (tx - 4, ty + 4)], BONE)
    return finish(im)

def icon_leap():
    im = canvas(); d = ImageDraw.Draw(im)
    poly(d, [(128, 20), (210, 120), (156, 120), (156, 190), (100, 190), (100, 120), (46, 120)], RED, BONE)
    for i in range(3):
        d.arc([60 + i * 12, 170 + i * 14, 196 - i * 12, 250], 200, 340, fill=BLOOD, width=7)
    return finish(im)

def icon_roar():
    im = canvas(); d = ImageDraw.Draw(im)
    d.pieslice([40, 70, 180, 210], 0, 180, fill=INK, outline=BLOOD, width=8)
    d.pieslice([40, 50, 180, 190], 180, 360, fill=INK, outline=BLOOD, width=8)
    for i in range(5):
        x = 62 + i * 24
        poly(d, [(x, 128), (x + 18, 128), (x + 9, 160)], BONE)
        poly(d, [(x, 128), (x + 18, 128), (x + 9, 100)], BONE)
    for r_ in (0, 1, 2):
        d.arc([150 + r_ * 22, 50 + r_ * 6 - r_ * 14, 230 + r_ * 12, 206 - r_ * 6 + r_ * 14], -50, 50, fill=GLOW, width=8)
    return finish(im)

def icon_carapace():
    im = canvas(); d = ImageDraw.Draw(im)
    pts = [(128, 18), (220, 54), (206, 150), (128, 238), (50, 150), (36, 54)]
    poly(d, pts, (36, 30, 40, 255), STEEL)
    d.line([(128, 18), (128, 238)], fill=STEEL, width=6)
    for y in (70, 112, 154):
        d.line([(60, y), (196, y)], fill=BLOOD, width=6)
    poly(d, [(128, 90), (160, 128), (128, 170), (96, 128)], RED)
    return finish(im)

def eye_shape(d, cx, cy, w, h, iris=RED, pupil=INK):
    pts = []
    for i in range(0, 41):
        u = i / 40
        x = cx - w + 2 * w * u
        y = cy - math.sin(u * math.pi) * h
        pts.append((x, y))
    for i in range(40, -1, -1):
        u = i / 40
        pts.append((cx - w + 2 * w * u, cy + math.sin(u * math.pi) * h))
    d.polygon(pts, fill=(30, 20, 26, 255), outline=BLOOD)
    d.ellipse([cx - h * 0.9, cy - h * 0.9, cx + h * 0.9, cy + h * 0.9], fill=iris)
    d.ellipse([cx - 9, cy - h * 0.8, cx + 9, cy + h * 0.8], fill=pupil)

def icon_vision():
    im = canvas(); d = ImageDraw.Draw(im)
    eye_shape(d, 128, 128, 104, 64)
    return finish(im)

def icon_regen():
    im = canvas(); d = ImageDraw.Draw(im)
    poly(d, [(104, 30), (152, 30), (152, 104), (226, 104), (226, 152), (152, 152), (152, 226), (104, 226), (104, 152), (30, 152), (30, 104), (104, 104)], BLOOD, GLOW)
    d.ellipse([112, 112, 144, 144], fill=INK)
    return finish(im)

def icon_camouflage():
    im = canvas(); d = ImageDraw.Draw(im)
    eye_shape(d, 128, 128, 104, 64, iris=(90, 40, 60, 255))
    for i in range(0, 256, 24):  # trait en pointillés : l'œil s'efface
        d.line([(24 + i, 232 - i), (40 + i, 216 - i)], fill=STEEL, width=8)
    return finish(im, halo=(120, 90, 150))

def icon_burst():
    im = canvas(); d = ImageDraw.Draw(im)
    for i in range(9):
        a = math.tau * i / 9
        pts = []
        for t in range(0, 31):
            u = t / 30
            rr = 24 + u * 100
            aa = a + math.sin(u * 3.2 + i) * 0.35
            pts.append((128 + math.cos(aa) * rr, 128 + math.sin(aa) * rr))
        for j in range(len(pts) - 1):
            d.line([pts[j], pts[j + 1]], fill=RED if j > 10 else BLOOD, width=max(3, int(18 * (1 - j / 30))))
    d.ellipse([98, 98, 158, 158], fill=INK, outline=GLOW, width=6)
    return finish(im)

def icon_ultimate():
    im = canvas(); d = ImageDraw.Draw(im)
    pts = []
    for t in range(0, 181):
        u = t / 180
        a = u * math.tau * 2.6
        rr = 14 + u * 104
        pts.append((128 + math.cos(a) * rr, 128 + math.sin(a) * rr))
    for j in range(len(pts) - 1):
        d.line([pts[j], pts[j + 1]], fill=RED if j % 18 < 12 else BLOOD, width=9)
    for k in range(3):
        a = k * math.tau / 3 - math.pi / 2
        cx, cy = 128 + math.cos(a) * 100, 128 + math.sin(a) * 100
        d.ellipse([cx - 16, cy - 16, cx + 16, cy + 16], fill=GLOW)
        d.ellipse([cx - 5, cy - 13, cx + 5, cy + 13], fill=INK)
    return finish(im)

def head_icon(kind):
    im = canvas(); d = ImageDraw.Draw(im)
    if kind == "porteur":      # silhouette humaine, yeux brûlants
        d.ellipse([70, 40, 186, 176], fill=(22, 18, 24, 255), outline=STEEL, width=5)
        poly(d, [(60, 250), (70, 180), (186, 180), (196, 250)], (22, 18, 24, 255), STEEL)
        d.ellipse([98, 98, 120, 120], fill=GLOW); d.ellipse([136, 98, 158, 120], fill=GLOW)
        d.line([(110, 150), (146, 150)], fill=BLOOD, width=5)
    elif kind == "predateur":  # crâne allongé, grands yeux ambrés, mâchoire ouverte
        poly(d, [(128, 16), (200, 60), (190, 150), (150, 240), (106, 240), (66, 150), (56, 60)], (24, 18, 26, 255), STEEL)
        eye_shape(d, 96, 92, 34, 24, iris=(255, 150, 20, 255)); eye_shape(d, 160, 92, 34, 24, iris=(255, 150, 20, 255))
        for i in range(5):
            poly(d, [(98 + i * 15, 190), (110 + i * 15, 190), (104 + i * 15, 218)], BONE)
    elif kind == "fantome":    # silhouette de fumée
        poly(d, [(128, 20), (200, 70), (210, 250), (170, 210), (128, 250), (86, 210), (46, 250), (56, 70)], (30, 40, 50, 200), (120, 220, 200, 255))
        d.ellipse([86, 90, 118, 126], fill=(150, 255, 200, 255)); d.ellipse([138, 90, 170, 126], fill=(150, 255, 200, 255))
    elif kind == "colosse":    # bloc massif
        poly(d, [(48, 70), (208, 70), (230, 250), (26, 250)], (34, 30, 36, 255), STEEL)
        poly(d, [(86, 30), (170, 30), (186, 100), (70, 100)], (44, 38, 44, 255), STEEL)
        d.rectangle([88, 60, 120, 84], fill=RED); d.rectangle([136, 60, 168, 84], fill=RED)
        d.line([(60, 160), (196, 160)], fill=BLOOD, width=8)
    else:                      # abomination : yeux multiples
        d.ellipse([30, 30, 226, 226], fill=(26, 12, 30, 255), outline=(255, 50, 90, 255), width=7)
        for cx, cy, s in ((128, 110, 34), (74, 80, 20), (182, 80, 20), (84, 150, 18), (172, 150, 18), (128, 178, 14), (128, 54, 12)):
            d.ellipse([cx - s, cy - s, cx + s, cy + s], fill=(255, 40, 80, 255))
            d.ellipse([cx - s * 0.2, cy - s * 0.8, cx + s * 0.2, cy + s * 0.8], fill=INK)
    return finish(im)

def icon_health():
    im = canvas(); d = ImageDraw.Draw(im)
    d.ellipse([30, 50, 132, 152], fill=BLOOD); d.ellipse([124, 50, 226, 152], fill=BLOOD)
    poly(d, [(36, 120), (220, 120), (128, 232)], BLOOD)
    d.line([(60, 90), (90, 70)], fill=GLOW, width=8)
    return finish(im)

def icon_hunger():
    im = canvas(); d = ImageDraw.Draw(im)
    d.pieslice([30, 30, 226, 226], 200, 340, fill=INK, outline=BLOOD, width=8)
    d.pieslice([30, 30, 226, 226], 20, 160, fill=INK, outline=BLOOD, width=8)
    for i in range(5):
        x = 62 + i * 30
        poly(d, [(x, 92), (x + 24, 92), (x + 12, 128)], BONE)
        poly(d, [(x, 164), (x + 24, 164), (x + 12, 128)], BONE)
    return finish(im)

def icon_energy():
    im = canvas(); d = ImageDraw.Draw(im)
    poly(d, [(150, 16), (60, 140), (118, 140), (92, 240), (200, 100), (140, 100)], (255, 190, 60, 255), (255, 240, 180, 255))
    return finish(im, halo=(255, 140, 20))

def icon_biomass():
    im = canvas(); d = ImageDraw.Draw(im)
    for cx, cy, s in ((128, 150, 70), (80, 120, 40), (180, 116, 44), (128, 80, 34)):
        d.ellipse([cx - s, cy - s, cx + s, cy + s], fill=(24, 14, 28, 255), outline=(150, 40, 170, 255), width=6)
    d.ellipse([110, 120, 140, 150], fill=RED)
    d.line([(100, 110), (118, 98)], fill=STEEL, width=7)
    return finish(im, halo=(120, 40, 160))

ICONS = {
    "icon_absorb": icon_absorb, "icon_claws": icon_claws, "icon_whip": icon_whip, "icon_leap": icon_leap,
    "icon_roar": icon_roar, "icon_carapace": icon_carapace, "icon_vision": icon_vision, "icon_regen": icon_regen,
    "icon_camouflage": icon_camouflage, "icon_burst": icon_burst, "icon_ultimate": icon_ultimate,
    "icon_health": icon_health, "icon_hunger": icon_hunger, "icon_energy": icon_energy, "icon_biomass": icon_biomass,
}
for name, fn in ICONS.items():
    save(fn(), "gui", name + ".png")
for form in ("porteur", "predateur", "fantome", "colosse", "abomination"):
    save(head_icon(form), "gui", f"icon_form_{form}.png")

# Oeil de menu : globe + pupille séparée (la pupille suit le curseur)
def menu_eye():
    S = 4
    im = Image.new("RGBA", (96 * S, 64 * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    pts = []
    for i in range(0, 61):
        u = i / 60
        pts.append((8 * S + u * 80 * S, 32 * S - math.sin(u * math.pi) ** 0.8 * 28 * S))
    for i in range(60, -1, -1):
        u = i / 60
        pts.append((8 * S + u * 80 * S, 32 * S + math.sin(u * math.pi) ** 0.8 * 28 * S))
    d.polygon(pts, fill=(210, 190, 185, 255), outline=(10, 4, 8, 255))
    im = im.resize((96, 64), Image.LANCZOS)
    arr = np.asarray(im).copy()
    n = fbm(64, 96, 3, 6)
    arr[..., 1] = clamp8(arr[..., 1].astype(np.float32) * (0.75 + 0.2 * n))
    arr[..., 2] = clamp8(arr[..., 2].astype(np.float32) * (0.7 + 0.2 * n))
    im = Image.fromarray(arr)
    ve = vein_layer(64, 96, 14, (170, 20, 30, 220), length=12, width=1, glow=False)
    # veines uniquement sur le globe
    mask = Image.fromarray(np.asarray(Image.fromarray(arr))[..., 3])
    ve.putalpha(Image.fromarray((np.asarray(ve)[..., 3] * (np.asarray(mask) > 0)).astype(np.uint8)))
    return Image.alpha_composite(im, ve)

def menu_pupil():
    S = 4
    im = Image.new("RGBA", (32 * S, 32 * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.ellipse([2 * S, 2 * S, 30 * S, 30 * S], fill=(150, 10, 20, 255), outline=(60, 0, 8, 255), width=S)
    d.ellipse([7 * S, 7 * S, 25 * S, 25 * S], fill=(230, 40, 30, 255))
    d.ellipse([13 * S, 3 * S, 19 * S, 29 * S], fill=(0, 0, 0, 255))
    d.ellipse([9 * S, 9 * S, 13 * S, 13 * S], fill=(255, 220, 200, 200))
    return im.resize((32, 32), Image.LANCZOS)

save(menu_eye(), "gui", "menu_eye.png")
save(menu_pupil(), "gui", "menu_pupil.png")

# Fond de menu : métal sombre de laboratoire avec rivets (tuile 64x64)
def lab_panel():
    r = np.random.default_rng(5)
    n = fbm(64, 64, 4, 8, seed_rng=r)
    arr = np.zeros((64, 64, 4), dtype=np.uint8)
    base = 22 + n * 14
    arr[..., 0] = clamp8(base); arr[..., 1] = clamp8(base * 0.95); arr[..., 2] = clamp8(base * 1.1); arr[..., 3] = 255
    img = Image.fromarray(arr); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 63, 63], outline=(8, 6, 10, 255))
    d.line([(1, 1), (62, 1)], fill=(54, 54, 62, 255)); d.line([(1, 1), (1, 62)], fill=(54, 54, 62, 255))
    for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
        d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(46, 46, 54, 255), outline=(8, 6, 10, 255))
    return img

save(lab_panel(), "gui", "lab_panel.png")

# Item : échantillon de parasite (16x16)
def sample_item():
    S = 16
    im = Image.new("RGBA", (16 * S, 16 * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([6 * S, 1 * S, 10 * S, 3 * S], fill=(110, 110, 120, 255), outline=(30, 30, 36, 255), width=S // 4)  # bouchon
    d.rounded_rectangle([4 * S, 3 * S, 12 * S, 15 * S], radius=S, fill=(180, 190, 205, 130), outline=(70, 76, 90, 255), width=S // 3)
    d.rounded_rectangle([5 * S, 7 * S, 11 * S, 14 * S], radius=S, fill=(10, 6, 12, 255))
    d.ellipse([7 * S, 9 * S, 10 * S, 12 * S], fill=(230, 40, 40, 255))
    d.ellipse([6 * S + 2, 8 * S, 7 * S + 6, 9 * S + 4], fill=(130, 130, 160, 255))
    return im.resize((16, 16), Image.LANCZOS)

save(sample_item(), "item", "parasite_sample.png")

# ----------------------------------------------------------------------------------------------------------------
# Sons : synthèse (numpy) → WAV → OGG Vorbis via ffmpeg. 100 % original.
# ----------------------------------------------------------------------------------------------------------------
SR = 44100

def t_(sec):
    return np.arange(int(SR * sec)) / SR

def env(n, a=0.01, r=0.2):
    e = np.ones(n)
    na, nr = max(1, int(a * SR)), max(1, int(r * SR))
    e[:na] = np.linspace(0, 1, na)
    e[-nr:] *= np.linspace(1, 0, nr)
    return e

def noise(n):
    return rng.standard_normal(n)

def lowpass(x, cutoff):
    # filtre RC simple (suffisant pour des textures sonores)
    a = 1 - math.exp(-2 * math.pi * cutoff / SR)
    y = np.zeros_like(x); acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc); y[i] = acc
    return y

def lp_fast(x, cutoff):
    # version vectorisée : moyenne glissante (approximation passe-bas)
    k = max(1, int(SR / (2 * cutoff)))
    return np.convolve(x, np.ones(k) / k, mode="same")

def norm(x, peak=0.85):
    m = np.max(np.abs(x))
    return x / m * peak if m > 0 else x

def write_ogg(name, samples):
    samples = np.clip(samples, -1, 1)
    pcm = (samples * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
        out = os.path.join(SND, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", out], check=True)

def s_ui_hover():
    t = t_(0.09); f = 200 - 700 * t
    x = np.sin(2 * math.pi * np.cumsum(f) / SR) * 0.6 + lp_fast(noise(len(t)), 3000) * 0.3
    return norm(x * env(len(t), 0.004, 0.07), 0.5)

def s_ui_click():
    t = t_(0.18); f = 120 * np.exp(-t * 8) + 50
    x = np.sin(2 * math.pi * np.cumsum(f) / SR) * np.exp(-t * 18) + lp_fast(noise(len(t)), 1800) * np.exp(-t * 30) * 0.8
    return norm(x, 0.7)

def s_roar():
    t = t_(1.6)
    f0 = 70 + 50 * np.sin(math.pi * np.minimum(1, t / 1.4)) + 6 * np.sin(2 * math.pi * 11 * t)
    ph = 2 * math.pi * np.cumsum(f0) / SR
    x = np.sign(np.sin(ph)) * 0.5 + np.sin(ph * 2.01) * 0.35 + np.sin(ph * 0.5) * 0.5
    x = np.tanh(x * 2.6)
    x += lp_fast(noise(len(t)), 2500) * (0.5 + 0.4 * np.sin(2 * math.pi * 23 * t))
    e = np.minimum(1, t / 0.12) * np.exp(-np.maximum(0, t - 0.9) * 2.4)
    return norm(x * e, 0.9)

def s_tentacle():
    t = t_(0.4)
    n = lp_fast(noise(len(t)), 4000) - lp_fast(noise(len(t)), 600)
    sweep = np.sin(2 * math.pi * np.cumsum(900 * np.exp(-t * 7) + 120) / SR)
    x = n * 0.9 * np.exp(-((t - 0.1) ** 2) / 0.006) + sweep * 0.3 * np.exp(-t * 9)
    x += lp_fast(noise(len(t)), 900) * np.exp(-np.maximum(0, t - 0.2) * 22) * (t > 0.2) * 0.9  # claquement humide
    return norm(x, 0.8)

def s_absorb():
    t = t_(1.1)
    f = 90 + 60 * np.sin(2 * math.pi * 7 * t) - 40 * t
    x = np.sin(2 * math.pi * np.cumsum(f) / SR + 2.2 * np.sin(2 * math.pi * 31 * t))
    x += lp_fast(noise(len(t)), 700) * (1.4 + np.sin(2 * math.pi * 13 * t))
    gurgle = np.sin(2 * math.pi * np.cumsum(260 + 180 * np.sin(2 * math.pi * 17 * t)) / SR) * 0.25
    return norm((x + gurgle) * env(len(t), 0.03, 0.4), 0.85)

def s_evolve():
    t = t_(2.6)
    x = np.zeros(len(t))
    for f, a in ((55, 1.0), (82.4, 0.7), (110.7, 0.6), (116.5, 0.4), (164, 0.35)):  # accord dissonant
        x += a * np.sin(2 * math.pi * f * t * (1 + 0.06 * t / 2.6) + 1.3 * np.sin(2 * math.pi * 0.7 * t))
    for k in range(5):  # battements de cœur de plus en plus rapprochés
        c = 0.1 + k * 0.45 - k * k * 0.03
        x += np.sin(2 * math.pi * 52 * (t - c)) * np.exp(-np.abs(t - c) * 28) * (t > c) * 1.6
    x += lp_fast(noise(len(t)), 2000) * 0.25 * np.minimum(1, t / 1.5)
    return norm(x * env(len(t), 0.05, 0.9), 0.9)

def s_whisper():
    t = t_(2.2)
    n = noise(len(t))
    f1 = lp_fast(n, 3200) - lp_fast(n, 1400)
    f2 = lp_fast(n, 1200) - lp_fast(n, 500)
    form = 0.5 + 0.5 * np.sin(2 * math.pi * (3.1 * t + 0.6 * np.sin(2 * math.pi * 0.9 * t)))
    syl = np.clip(np.sin(2 * math.pi * 2.3 * t) * 1.5, 0, 1)
    x = (f1 * form + f2 * (1 - form)) * syl
    return norm(x * env(len(t), 0.2, 0.8), 0.55)

def s_heartbeat():
    t = t_(1.3)
    x = np.zeros(len(t))
    for c, a in ((0.05, 1.0), (0.32, 0.75)):
        x += a * np.sin(2 * math.pi * 48 * (t - c)) * np.exp(-np.abs(t - c) * 22) * (t > c)
    return norm(x, 0.9)

def s_ambient():
    dur = 24.0
    t = t_(dur)
    x = np.zeros(len(t))
    for f, a, lf in ((41.2, 1.0, 0.07), (55.0, 0.8, 0.05), (61.7, 0.5, 0.11), (82.4, 0.4, 0.09)):
        x += a * np.sin(2 * math.pi * f * t + 1.8 * np.sin(2 * math.pi * lf * t)) * (0.6 + 0.4 * np.sin(2 * math.pi * lf * 0.7 * t + f))
    wind = lp_fast(noise(len(t)), 500) * (0.5 + 0.5 * np.sin(2 * math.pi * 0.09 * t)) * 0.9
    x += wind
    for c in (3.0, 9.5, 15.2, 20.7):  # coups sourds lointains
        x += np.sin(2 * math.pi * 40 * (t - c)) * np.exp(-np.abs(t - c) * 6) * (t > c) * 1.2
    return norm(x * env(len(t), 2.5, 3.5), 0.6)

SOUNDS = {
    "ui_hover": s_ui_hover, "ui_click": s_ui_click, "roar": s_roar, "tentacle": s_tentacle, "absorb": s_absorb,
    "evolve": s_evolve, "whisper": s_whisper, "heartbeat": s_heartbeat, "ambient_menu": s_ambient,
}
for name, fn in SOUNDS.items():
    write_ogg(name, fn())

sounds_json = {name: {"sounds": [{"name": f"blackparasite:{name}", "stream": name == "ambient_menu"}]} for name in SOUNDS}
with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as f:
    json.dump(sounds_json, f, indent=2)

print("OK : textures et sons générés dans", ASSETS)
