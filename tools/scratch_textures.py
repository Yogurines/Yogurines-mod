"""Genera los cartones de rasca (150x200) y los símbolos del rasca de oro. Ejecutar desde la raíz del repo."""
from PIL import Image, ImageDraw, ImageFont
import math, os
A='src/main/resources/assets/yogurines/textures/gui'
os.makedirs(A,exist_ok=True)
FONT='src/main/resources/assets/yogurines/font/silkscreen.ttf'
f8=ImageFont.truetype(FONT,8); f16=ImageFont.truetype(FONT,16)
TIERS=[('BRONCE','Y1',(224,123,42),(255,177,92),(122,58,12)),
       ('PLATA','Y10',(143,163,191),(215,226,240),(59,74,96)),
       ('ORO','Y100',(232,178,31),(255,240,154),(122,82,0))]
ZONES={0:[(11+i*44,92+j*40,40,36) for j in range(2) for i in range(3)],
       1:[(30+i*32,72,26,26) for i in range(3)]+[(9+i*34,116+j*36,31,32) for j in range(2) for i in range(4)],
       2:[(24+i*36,76+j*33,34,30) for j in range(3) for i in range(3)]}

def text(d,xy,s,font,fill,outline=None,anchor='mm'):
    d.fontmode='1'
    if outline:
        for dx,dy in [(-1,0),(1,0),(0,-1),(0,1),(1,1),(-1,1),(1,-1),(-1,-1),(2,2)]:
            d.text((xy[0]+dx,xy[1]+dy),s,font=font,fill=outline,anchor=anchor)
    d.text(xy,s,font=font,fill=fill,anchor=anchor)

def star(d,cx,cy,r,col):
    d.rectangle([cx,cy-r,cx,cy+r],fill=col); d.rectangle([cx-r,cy,cx+r,cy],fill=col); d.rectangle([cx-1,cy-1,cx+1,cy+1],fill=col)

for t,(name,price,c,c2,dk) in enumerate(TIERS):
    im=Image.new('RGBA',(150,200),c+(255,)); d=ImageDraw.Draw(im)
    # rayos desde arriba
    cx,cy=75,40
    for k in range(18):
        if k%2: continue
        a0=k*math.pi*2/18; a1=(k+1)*math.pi*2/18
        d.polygon([(cx,cy),(cx+300*math.cos(a0),cy+300*math.sin(a0)),(cx+300*math.cos(a1),cy+300*math.sin(a1))],fill=tuple(int(c[i]*0.6+c2[i]*0.4) for i in range(3))+(255,))
    # borde
    d.rectangle([0,0,149,199],outline=dk+(255,),width=3)
    d.rectangle([3,3,146,196],outline=c2+(255,),width=1)
    for (sx,sy) in [(16,14),(12,52),(138,40),(24,78),(128,70)]: star(d,sx,sy,2,(255,255,255,255))
    text(d,(75,20),'RASCA',f16,(255,255,255,255),dk+(255,))
    text(d,(75,40),name,f16,c2+(255,),dk+(255,))
    # etiqueta precio
    d.rectangle([108,7,143,19],fill=(255,255,255,255),outline=dk+(255,))
    text(d,(126,13),price,f8,dk+(255,))
    # panel de zonas
    zs=ZONES[t]
    x0=min(z[0] for z in zs)-4; y0=min(z[1] for z in zs)-4; x1=max(z[0]+z[2] for z in zs)+3; y1=max(z[1]+z[3] for z in zs)+3
    if t==1:
        d.rectangle([24,68,126,101],fill=tuple(int(v*0.55) for v in c)+(255,))
        text(d,(75,62),'NÚMEROS GANADORES',f8,(255,255,255,255),dk+(255,))
        d.rectangle([5,112,145,184],fill=tuple(int(v*0.55) for v in c)+(255,))
        text(d,(75,107),'TUS NÚMEROS',f8,(255,255,255,255),dk+(255,))
    else:
        d.rectangle([x0,y0,x1,y1],fill=tuple(int(v*0.55) for v in c)+(255,))
        if t==0: text(d,(75,y0-5),'3 IGUALES = PREMIO',f8,(255,255,255,255),dk+(255,))
        else: text(d,(75,y0-5),'3 EN RAYA = PREMIO',f8,(255,255,255,255),dk+(255,))
    for (x,y,w,h) in zs:
        d.rectangle([x,y,x+w-1,y+h-1],fill=(255,248,230,255))
    text(d,(75,192),'JUEGA CON CABEZA',f8,(0,0,0,150))
    im.save(f'{A}/scratch_{t}.png')

# símbolos 16x16: moneda, Y, lingote, diamante, corona, fundadores (estrella)
sym=Image.new('RGBA',(96,16),(0,0,0,0)); d=ImageDraw.Draw(sym)
# moneda
d.ellipse([1,1,14,14],fill=(242,194,48,255),outline=(122,90,0,255)); text(d,(8,8),'Y',f8,(122,90,0,255))
# Y verde
text(d,(24,8),'Y',f16,(63,154,46,255),(20,60,15,255))
# lingote
d.polygon([(34,5),(46,5),(47,12),(33,12)],fill=(232,178,31,255),outline=(122,82,0,255)); d.line([(36,7),(44,7)],fill=(255,240,154,255))
# diamante
d.polygon([(56,3),(60,3),(63,7),(58,13),(53,7)],fill=(74,237,217,255),outline=(27,111,116,255)); d.line([(56,5),(59,5)],fill=(213,255,250,255))
# corona
d.polygon([(66,12),(66,5),(69,8),(72,3),(75,8),(78,5),(78,12)],fill=(242,194,48,255),outline=(122,90,0,255)); d.point([(72,9)],fill=(176,46,38,255))
# estrella de los fundadores
pts=[]
for k in range(10):
    r=7 if k%2==0 else 3; a=-math.pi/2+k*math.pi/5
    pts.append((88+r*math.cos(a),8+r*math.sin(a)))
d.polygon(pts,fill=(200,80,200,255),outline=(90,20,90,255))
sym.save(f'{A}/scratch_symbols.png')
print('ok')
