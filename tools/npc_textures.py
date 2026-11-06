"""Genera las texturas de NPCs y rascas (pixel art). Ejecutar desde la raíz del repo."""
from PIL import Image
import os
A='src/main/resources/assets/yogurines/textures'
os.makedirs(A+'/npc',exist_ok=True); os.makedirs(A+'/item',exist_ok=True)

def draw(rows,pal,w=16,h=16):
    im=Image.new('RGBA',(w,h),(0,0,0,0))
    for y,r in enumerate(rows):
        for x,c in enumerate(r):
            if c!='.': im.putpixel((x,y),pal[c])
    return im

# --- modificador: palo con cristal verde ---
wand=["............kgk.","...........kgGgk","...........gGGGg","............gGgk","...........kwk..","..........kwWk..",".........kwk....","........kwWk....",".......kwk......","......kwWk......",".....kwk........","....kwWk........","...kwk..........","..kwk...........","..kk............","................"]
pal={'k':(26,26,26,255),'g':(47,191,90,255),'G':(184,255,204,255),'w':(138,90,43,255),'W':(170,118,62,255)}
draw(wand,pal).save(A+'/item/npc_wand.png')

# --- rascas 16x16 ---
def ticket(c,c2,d):
    rows=["................",
          ".kkkkkkkkkkkkkk.",
          ".kccccccccccccck",
          ".kcCCCcccc*cccck",
          ".kcCccccc***ccck",
          ".kcccccccc*cccck",
          ".kccccccccccccck",
          ".kcssssssssssssk",
          ".kcsSsSsSsSsSsck",
          ".kcssssssssssssk",
          ".kcsSsSsSsSsSsck",
          ".kcssssssssssssk",
          ".kccccccccccccck",
          ".kdddddddddddddk",
          "..kkkkkkkkkkkkk.",
          "................"]
    rows=[r[:16].ljust(16,'.') for r in rows]
    pal={'k':(30,20,10,255),'c':c,'C':c2,'*':(255,255,255,255),'s':(190,190,196,255),'S':(222,222,228,255),'d':d}
    return draw(rows,pal)
ticket((224,123,42,255),(255,177,92,255),(122,58,12,255)).save(A+'/item/scratch_bronze.png')
ticket((143,163,191,255),(215,226,240,255),(59,74,96,255)).save(A+'/item/scratch_silver.png')
ticket((232,178,31,255),(255,240,154,255),(122,82,0,255)).save(A+'/item/scratch_gold.png')

# --- cinta (gris, se tiñe con el color elegido): 32x16, 12 izq | 8 centro | 12 der ---
K=(25,18,14,255); L=(255,255,255,255); B=(214,214,214,255); D=(140,140,140,255); DD=(92,92,92,255)
ST=(255,255,255,255); SD=(120,120,120,255)
rib=Image.new('RGBA',(32,16),(0,0,0,0))
def P(x,y,c): rib.putpixel((x,y),c)
for s in (0,1):
    for i in range(8):
        X= i if s==0 else 31-i
        d=i
        for y in range(5,15):
            if d<3 and abs(y-9.5)<3-d+0.5: continue
            P(X,y, L if y==5 else (DD if y>=13 else D))
        P(X,4,K); P(X,15,K)
    for y in range(5,15):
        X=0 if s==0 else 31
        if abs(y-9.5)>=3: P(X,y,K)
    # pliegue
    for X in ([8,9,10,11] if s==0 else [20,21,22,23]):
        P(X,13,DD); P(X,14,K)
for X in range(8,24):
    if X in (8,23):
        for y in range(0,14): P(X,y,K)
        continue
    for y in range(1,13): P(X,y, L if y<=2 else (D if y>=11 else B))
    P(X,0,K); P(X,13,K)
    if 9<X<22 and X%2==0: P(X,3,ST); P(X,10,SD)
# el centro (x 12..19) se repite: la costura queda alterna
rib.save(A+'/npc/ribbon.png')

# --- iconos holograma 5 x (9x9) en 48x9 ---
ic={'talk':[".xxxxxxx.","xooooooox","xoxoxoxox","xooooooox",".xxxxxxx.","..xx.....","..x......",".........","........."],
'cart':["x........","xxxxxxxxx",".xoooooox",".xoooooox","..xxxxxxx","..x....x.","..xxxxxx.","..xx..xx.","........."],
'excl':["...xxx...","...xox...","...xox...","...xox...","...xox...","...xxx...",".........","...xxx...","...xox..."],
'y':["xx.....xx","xox...xox",".xox.xox.","..xoxox..","...xox...","..xxoxx..","..xoooox.","...xox...","...xxx..."],
'info':["..xxxxx..",".xoooooxx","xoox.xoox","....xoox.","...xoox..","...xox...","...xxx...","...xox...","...xxx..."]}
icons=Image.new('RGBA',(48,9),(0,0,0,0))
for n,(k,rows) in enumerate(ic.items()):
    for y,r in enumerate(rows):
        for x,c in enumerate(r):
            if c=='x': icons.putpixel((n*9+x,y),(16,110,52,255))
            if c=='o': icons.putpixel((n*9+x,y),(170,255,200,255))
icons.save(A+'/npc/icons.png')
print('ok')
