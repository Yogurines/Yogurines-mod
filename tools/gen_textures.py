"""Genera las texturas del mod (objetos y cajero) a partir de los diseños aprobados.
Uso: python3 tools/gen_textures.py  (escribe en src/main/resources/assets/yogurines/textures)"""
from PIL import Image
import os, json, math
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'yogurines')
TEX = os.path.join(ROOT, 'textures')

def hx(c):
    c = c.lstrip('#'); return tuple(int(c[i:i+2], 16) for i in (0, 2, 4)) + ((int(c[6:8], 16),) if len(c) == 8 else (255,))
def shade(c, f):
    r, g, b, a = hx(c)
    if f < 0: r, g, b = r*(1+f), g*(1+f), b*(1+f)
    else: r, g, b = r+(255-r)*f, g+(255-g)*f, b+(255-b)*f
    return '#%02x%02x%02x' % (round(r), round(g), round(b))

class Canvas:
    def __init__(self, w, h): self.im = Image.new('RGBA', (w, h), (0, 0, 0, 0)); self.d = self.im.load(); self.w, self.h = w, h
    def px(self, x, y, c, w=1, h=1):
        col = hx(c)
        for j in range(int(y), int(y)+int(h)):
            for i in range(int(x), int(x)+int(w)):
                if 0 <= i < self.w and 0 <= j < self.h: self.d[i, j] = col
    def map(self, rows, ox, oy, cols):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in cols: self.px(ox+x, oy+y, cols[ch])

Y_B2 = ["#.....#", ".#...#.", "..#.#..", "...#...", ".#####.", "...#...", "..###.."]
NOTES = [(5, '#8f9a92'), (10, '#c8574d'), (20, '#4a73b5'), (50, '#e08a3c'), (100, '#4f9d5b'), (200, '#c9a03a'), (500, '#8a5aa8')]

def note_item(col):
    c = Canvas(16, 16); d, m, l, l2 = shade(col, -.5), shade(col, -.2), shade(col, .4), shade(col, .65)
    c.px(2, 2, shade(col, -.6), 14, 10); c.px(3, 3, shade(col, -.3), 12, 8)
    c.px(0, 5, d, 15, 10); c.px(1, 6, col, 13, 8); c.px(1, 6, l, 13, 1)
    c.px(2, 9, l2, 3, 4); c.px(3, 8, l2, 1, 1); c.px(3, 10, l, 1, 3)
    for y, h in [(6, '#e6c8ff'), (8, '#c8e8ff'), (10, '#c9ffd9'), (12, '#fff6c4')]: c.px(6, y, h, 1, 2)
    c.px(10, 8, m, 2, 2); c.px(9, 10, m, 4, 4); c.px(10, 8, d, 2, 1)
    c.px(2, 7, '#ffffff', 2, 1)
    return c

def coin_item():
    c = Canvas(16, 16)
    for y in range(15):
        for x in range(15):
            r = math.hypot(x-7, y-7)
            if r <= 7.2:
                lit = x+y < 14
                c.px(x, y, '#7a5f18' if r > 6.4 else ('#ebc95e' if lit else '#c99f39') if r > 4.4 else ('#e2e5ea' if lit else '#b5bac2'))
    c.map(["#...#", ".#.#.", "..#..", ".###.", "..#..", ".###."], 5, 4, {'#': '#4d535c'})
    return c

def phone_item():
    c = Canvas(16, 16)
    def rrect(x, y, w, h, col, r):
        for j in range(h):
            ins = r-j if j < r else (j-(h-r)+1 if j >= h-r else 0); ins = min(ins, r)
            c.px(x+ins, y+j, col, w-2*ins, 1)
    rrect(4, 1, 8, 14, '#000000', 1); rrect(5, 1, 6, 14, '#17171c', 1)
    for y, col, h in [(3, '#3b2163', 1), (4, '#5a2a73', 2), (6, '#8a356f', 2), (8, '#c44e63', 2), (10, '#e8795a', 2)]: c.px(5, y, col, 6, h)
    c.px(7, 13, '#3a3a44', 2, 1); c.px(5, 3, '#ffffff40', 1, 8)
    return c

def steel_region(c, ox, oy, w, h, steel='#7b8591', light='#9aa3ad', dark='#5c6570'):
    for y in range(h):
        for i in range(w):
            c.px(ox+i, oy+y, shade(steel, .06) if (i*7+y*13) % 11 == 0 else steel)
    c.px(ox, oy, light, w, 1); c.px(ox, oy, light, 1, h); c.px(ox, oy+h-1, dark, w, 1); c.px(ox+w-1, oy, dark, 1, h)

def atm_atlas():
    c = Canvas(64, 64); D, G, SD = '#2a2f36', '#3fdc7a', '#5c6570'
    # body front (0,0) 14x26
    steel_region(c, 0, 0, 14, 26)
    c.px(1, 2, SD, 12, 10); c.px(2, 3, '#1c2026', 10, 8)
    c.px(1, 13, SD, 12, 1); c.px(9, 14, '#1c2026', 4, 1); c.px(8, 14, G)
    c.px(1, 19, D, 12, 3); c.px(2, 20, '#11151a', 10, 1); c.px(3, 21, G, 8, 1)
    for i in (2, 5, 8, 11): c.px(i, 24, SD)
    # body side (14,0) 12x26
    steel_region(c, 14, 0, 12, 26)
    c.px(14+5, 0, SD, 1, 26)
    for y in (15, 17, 19, 21): c.px(14+1, y, '#3a4048', 3, 1)
    c.px(14+8, 4, SD, 3, 1); c.px(14+8, 4, SD, 1, 3)
    # body top (26,0) 14x12
    steel_region(c, 26, 0, 14, 12)
    # plinth top (40,0) 16x16
    c.px(40, 0, '#4a525c', 16, 16); c.px(41, 1, '#545c66', 14, 14)
    # sign top (0,26) 16x14
    c.px(0, 26, '#353b43', 16, 14); c.px(1, 27, D, 14, 12)
    # sign front (16,26) 32x8 (doble densidad para que quepa la Y)
    c.px(16, 26, D, 32, 8); c.px(16, 26, '#353b43', 32, 1)
    c.map(Y_B2, 16+12, 26+1, {'#': G}); c.px(16+3, 26+3, '#1d2228', 6, 2); c.px(16+23, 26+3, '#1d2228', 6, 2)
    # sign side (48,26) 14x4
    c.px(48, 26, D, 14, 4); c.px(48, 26, '#353b43', 14, 1); c.px(48+2, 28, G, 10, 1)
    # plinth side (48,30) 16x2
    c.px(48, 30, '#4a525c', 16, 2); c.px(48, 30, '#5c6570', 16, 1)
    for i in (2, 7, 13): c.px(48+i, 31, '#3a4048')
    # screen (16,34) 40x28
    sx, sy = 16, 34
    c.px(sx, sy, '#0f2a19', 40, 28); c.px(sx+1, sy+1, '#1f5d34', 38, 26); c.px(sx+1, sy+1, '#2b7a45', 38, 5)
    c.map(Y_B2, sx+3, sy+1, {'#': '#b8ffcb'})  # logo
    c.px(sx+12, sy+2, '#b8ffcb', 16, 1); c.px(sx+12, sy+4, '#7dff9e', 10, 1)
    c.px(sx+3, sy+10, '#7dff9e', 14, 2); c.px(sx+3, sy+15, '#4fd67a', 22, 1); c.px(sx+3, sy+18, '#4fd67a', 16, 1)
    c.px(sx+3, sy+22, '#7dff9e', 12, 3); c.px(sx+24, sy+22, '#7dff9e', 12, 3)
    # keypad (0,40) 12x5
    c.px(0, 40, SD, 12, 5); c.px(0, 40, '#9aa3ad', 12, 1)
    for r in range(2):
        for k in range(3): c.px(1+k*2, 41+r*2, D)
    c.px(8, 41, '#3fae5a', 3, 1); c.px(8, 42, '#e0a028', 3, 1); c.px(8, 43, '#c8574d', 3, 1)
    # steel 8x8 (0,46), dark (8,46), green (12,46)
    steel_region(c, 0, 46, 8, 8); c.px(8, 46, D, 4, 4); c.px(12, 46, G, 4, 4)
    return c

def uv(x, y, w, h):
    return [x/4, y/4, (x+w)/4, (y+h)/4]

def atm_model():
    R = {'front': uv(0, 0, 14, 26), 'side': uv(14, 0, 12, 26), 'top': uv(26, 0, 14, 12), 'ptop': uv(40, 0, 16, 16),
         'stop': uv(0, 26, 16, 14), 'sfront': uv(16, 26, 32, 8), 'sside': uv(48, 26, 14, 4), 'pside': uv(48, 30, 16, 2),
         'screen': uv(16, 34, 40, 28), 'keys': uv(0, 40, 12, 5), 'steel': uv(0, 46, 8, 8), 'dark': uv(8, 46, 4, 4), 'green': uv(12, 46, 4, 4)}
    GLOW = {"block_light": 15, "sky_light": 15}
    def face(k, glow=False):
        f = {"uv": R[k], "texture": "#atm"}
        if glow: f["forge_data"] = GLOW; f["shade"] = False
        return f
    def box(frm, to, n, s, e, w, u, d, name, rot=None, glow_north=False):
        el = {"name": name, "from": frm, "to": to, "faces": {"north": face(n, glow_north), "south": face(s), "east": face(e), "west": face(w), "up": face(u), "down": face(d)}}
        if rot: el["rotation"] = rot
        return el
    els = [
        box([0, 0, 0], [16, 2, 16], 'pside', 'pside', 'pside', 'pside', 'ptop', 'ptop', 'zocalo'),
        box([1, 2, 2], [15, 28, 14], 'front', 'steel', 'side', 'side', 'top', 'top', 'cuerpo'),
        box([0, 28, 1], [16, 32, 15], 'sfront', 'sfront', 'sside', 'sside', 'stop', 'stop', 'letrero', glow_north=True),
        {"name": "pantalla", "from": [3, 17.5, 1.9], "to": [13, 24.5, 1.9], "faces": {"north": face('screen', True)}},
        box([2, 26, -0.25], [14, 27, 2], 'steel', 'steel', 'steel', 'steel', 'steel', 'steel', 'visera'),
        box([2, 17, -0.25], [3, 26, 2], 'steel', 'steel', 'steel', 'steel', 'steel', 'steel', 'visera_izq'),
        box([13, 17, -0.25], [14, 26, 2], 'steel', 'steel', 'steel', 'steel', 'steel', 'steel', 'visera_der'),
        box([2, 12.75, -2.1], [14, 14.25, 2.9], 'steel', 'steel', 'steel', 'steel', 'keys', 'steel', 'teclado',
            rot={"angle": -22.5, "axis": "x", "origin": [8, 13.5, 0.4]}),
        box([2.5, 7.8, 0.6], [13.5, 8.8, 2], 'dark', 'dark', 'dark', 'dark', 'dark', 'dark', 'ranura_billetes'),
        {"name": "led", "from": [4, 7.05, 1.9], "to": [12, 7.55, 1.9], "faces": {"north": face('green', True)}},
    ]
    return {"credit": "Yogurines", "textures": {"atm": "yogurines:block/atm", "particle": "yogurines:block/atm"}, "elements": els}

if __name__ == '__main__':
    os.makedirs(os.path.join(TEX, 'item'), exist_ok=True); os.makedirs(os.path.join(TEX, 'block'), exist_ok=True)
    for v, col in NOTES: note_item(col).im.save(os.path.join(TEX, 'item', f'note_{v}.png'))
    coin_item().im.save(os.path.join(TEX, 'item', 'coin_1.png'))
    phone_item().im.save(os.path.join(TEX, 'item', 'phone.png'))
    atm_atlas().im.save(os.path.join(TEX, 'block', 'atm.png'))
    with open(os.path.join(ROOT, 'models', 'block', 'atm.json'), 'w') as f: json.dump(atm_model(), f, indent=2)
    print('texturas generadas')
