import sys, os, glob
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from skingen import parts
def view(img, slim, side):
    P = parts(slim); aw = 3 if slim else 4
    W = 8 + 2*aw; out = Image.new("RGBA", (W+2, 34), (60,60,70,255))
    def blit(part, face, dx, dy, flip=False):
        x0,y0,fw,fh = P[part][face]; c = img.crop((x0,y0,x0+fw,y0+fh))
        if flip: c = c.transpose(Image.FLIP_LEFT_RIGHT)
        out.alpha_composite(c, (dx+1, dy+1))
    f = side
    # front: viewer sees right arm on the left
    if f == "front":
        L=[("rarm",0),("larm",aw+8)]; LG=[("rleg",aw),("lleg",aw+4)]
    else:
        L=[("larm",0),("rarm",aw+8)]; LG=[("lleg",aw),("rleg",aw+4)]
    for base, ov in (("head","hat"),):
        blit(base,f,aw,0); blit(ov,f,aw,0)
    blit("body",f,aw,8); blit("jacket",f,aw,8)
    sl={"rarm":"rsleeve","larm":"lsleeve","rleg":"rpants","lleg":"lpants"}
    for p,x in L: blit(p,f,x,8); blit(sl[p],f,x,8)
    for p,x in LG: blit(p,f,x,20); blit(sl[p],f,x,20)
    return out
files = sys.argv[2:]; S=6
tiles=[]
for fn in files:
    im = Image.open(fn).convert("RGBA")
    slim = im.getpixel((54,20))[3]==0 and im.getpixel((50,16))[3]==0
    a = view(im, slim, "front"); b = view(im, slim, "back")
    t = Image.new("RGBA",(a.width*2+2, a.height),(30,30,30,255)); t.paste(a,(0,0)); t.paste(b,(a.width+2,0))
    tiles.append(t)
cols = 6; tw = max(t.width for t in tiles); th = tiles[0].height
sheet = Image.new("RGBA",(cols*(tw+2), ((len(tiles)+cols-1)//cols)*(th+2)),(20,20,20,255))
for i,t in enumerate(tiles): sheet.paste(t,((i%cols)*(tw+2),(i//cols)*(th+2)))
sheet = sheet.resize((sheet.width*S, sheet.height*S), Image.NEAREST); sheet.save(sys.argv[1])
