"""Convierte las TTF pixel-art a fuentes bitmap de Minecraft (nítidas, sin desenfoque). Ejecutar desde la raíz.
Cada TTF se dibuja a un tamaño donde sus píxeles caen justos en la rejilla y luego se reduce 1:1."""
from PIL import Image, ImageDraw, ImageFont
import json
D='src/main/resources/assets/yogurines'
CHARS=[chr(c) for c in range(33,127)]+list('ÁÉÍÓÚÑÜáéíóúñü¡¿ÀÈÒàèòÇç·€')
# clave: (ttf, tamaño nítido, píxeles por punto de diseño, ancho del espacio, escala final en el juego)
# cartel y medieval se dibujan a la mitad para que midan como antes (nítidas con escala de GUI par).
FONTS={'tech':('silkscreen.ttf',16,2,3,1),'cartel':('jersey10.ttf',56,3,3,2),'medieval':('jacquard12.ttf',21,1,3,2)}
PER=16
for key,(ttf,size,u,space,div) in FONTS.items():
    f=ImageFont.truetype(f'tools/fonts/{ttf}',size)
    glyphs={}
    for c in CHARS:
        im=Image.new('L',(size*3,size*3),0); d=ImageDraw.Draw(im); d.fontmode='1'
        d.text((size,size*2),c,font=f,fill=255,anchor='ls')
        if not im.getbbox(): continue
        sm=im.resize((im.width//u,im.height//u),Image.NEAREST)
        glyphs[c]=sm
    base=(size*2)//u
    boxes=[g.getbbox() for g in glyphs.values()]
    top=min(b[1] for b in boxes); bot=max(b[3] for b in boxes)
    left=size//u
    cw=max(b[2] for b in boxes)-left+1
    ch=bot-top
    if ch%div: ch+=div-ch%div; bot=top+ch
    asc=base-top
    have=list(glyphs)
    rows=[have[i:i+PER] for i in range(0,len(have),PER)]
    rows[-1]=rows[-1]+['\u0000']*(PER-len(rows[-1]))
    out=Image.new('RGBA',(cw*PER,ch*len(rows)),(0,0,0,0))
    for j,r in enumerate(rows):
        for i,c in enumerate(r):
            if c=='\u0000': continue
            g=glyphs[c].crop((left,top,left+cw,bot))
            white=Image.new('RGBA',g.size,(255,255,255,255))
            out.paste(white,(i*cw,j*ch),g)
    out.save(f'{D}/textures/font/{key}.png')
    js={'providers':[{'type':'space','advances':{' ':space}},
        {'type':'bitmap','file':f'yogurines:font/{key}.png','height':ch//div,'ascent':min(round(asc/div),ch//div),'chars':[''.join(r) for r in rows]},
        {'type':'reference','id':'minecraft:default'}]}
    json.dump(js,open(f'{D}/font/{key}.json','w'),ensure_ascii=False,indent=1)
    print(key,'cell',cw,ch,'asc',asc,'chars',len(have))
