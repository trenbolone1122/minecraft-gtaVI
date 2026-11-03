import os, sys, random
from PIL import Image
OUT = sys.argv[1]
def hexc(s,a=255): s=s.lstrip('#'); return (int(s[0:2],16),int(s[2:4],16),int(s[4:6],16),a)
def shade(c,f): return tuple(max(0,min(255,int(c[i]*f))) for i in range(3))+(255,)
R = random.Random(7)
def box_fill(img,u,v,w,h,d,fn):
    faces={"top":(u+d,v,w,d),"bottom":(u+d+w,v,w,d),"right":(u,v+d,d,h),"front":(u+d,v+d,w,h),"left":(u+d+w,v+d,d,h),"back":(u+d+w+d,v+d,w,h)}
    for f,(x0,y0,fw,fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                img.putpixel((x0+x,y0+y), fn(f,x,y))
acc = os.path.join(OUT,"assets/leonida/textures/entity/character/accessory"); os.makedirs(acc,exist_ok=True)
hair=hexc("2b1a12"); hl=hexc("4a2e1e")
img=Image.new("RGBA",(32,32),(0,0,0,0))
def hairfn(f,x,y):
    c = hl if (x*3+y)%5==0 else hair
    return shade(c, 1+R.uniform(-0.08,0.08))
box_fill(img,0,0,3,5,2,hairfn); box_fill(img,12,0,2,5,2,hairfn)
box_fill(img,0,16,3,2,2,lambda f,x,y: hexc("1a1a1a") if y==0 else hexc("e6c050"))
box_fill(img,0,22,4,2,5,lambda f,x,y: shade(hl if (x+y)%4==0 else hair, 1.12 if f=="top" else 1.0))
img.save(os.path.join(acc,"ponytail.png"))
img=Image.new("RGBA",(32,32),(0,0,0,0))
cap=hexc("2e2e30")
box_fill(img,0,0,8,1,4,lambda f,x,y: shade(cap, 1.15 if f=="top" else (0.7 if f=="bottom" else 0.95)))
img.save(os.path.join(acc,"cap_back.png"))
# wardrobe block textures: white louvered Florida wardrobe
blk = os.path.join(OUT,"assets/leonida/textures/block/character"); os.makedirs(blk,exist_ok=True)
paint=hexc("ece6d8"); frame=hexc("cfc6b2"); dark=hexc("8f8672"); brass=hexc("d4a840")
f=Image.new("RGBA",(16,16))
for y in range(16):
    for x in range(16):
        c=paint
        if x in (0,15) or y in (0,15): c=frame
        elif x in (7,8): c=dark if x==7 else frame
        elif 2<=y<=13 and (x in (1,6,9,14)): c=frame
        elif 2<=y<=13 and y%2==0: c=shade(paint,0.82)
        elif y in (1,14): c=frame
        f.putpixel((x,y),shade(c,1+R.uniform(-0.03,0.03)))
for (x,y) in [(6,7),(6,8),(9,7),(9,8)]: f.putpixel((x,y),brass)
f.save(os.path.join(blk,"wardrobe_front.png"))
s=Image.new("RGBA",(16,16))
for y in range(16):
    for x in range(16):
        c=frame if (x in (0,15) or y in (0,15)) else (shade(paint,0.93) if x%5==0 else paint)
        s.putpixel((x,y),shade(c,1+R.uniform(-0.03,0.03)))
s.save(os.path.join(blk,"wardrobe_side.png"))
t=Image.new("RGBA",(16,16))
for y in range(16):
    for x in range(16):
        c=frame if (x in (0,15) or y in (0,15)) else shade(paint,0.97 if y%4 else 0.9)
        t.putpixel((x,y),shade(c,1+R.uniform(-0.03,0.03)))
t.save(os.path.join(blk,"wardrobe_top.png"))
