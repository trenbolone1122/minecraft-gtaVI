# Generates the vehicle texture atlas (128x128, 16px tiles). Original artwork for Leonida.
from PIL import Image, ImageDraw
import random, math
random.seed(3)
T=16; W=128
img=Image.new("RGBA",(W,W),(0,0,0,0))
px=img.load()
def tile(i): return (i%8)*T,(i//8)*T
def fill(i,f):
    ox,oy=tile(i)
    for y in range(T):
        for x in range(T):
            px[ox+x,oy+y]=f(x,y)
def clamp(v): return max(0,min(255,int(v)))
# 0 white with very subtle noise (paint, solid colours via vertex tint)
fill(0,lambda x,y:(lambda n:(n,n,n,255))(clamp(246+random.randint(-6,6))))
# 1 glass: light with diagonal reflection streaks (tinted by vertex colour, alpha in vertex)
def glass(x,y):
    s=(x+y)%16
    v=255 if s in (3,4,10) else 200
    return (v,v,v,255)
fill(1,glass)
# 2 tyre side: dark rubber ring, silver rim, dark hub
def tyre(x,y):
    cx=cy=7.5; d=math.hypot(x-cx,y-cy)
    if d>7.9: return (22,22,24,255)
    if d>5.2: return (30,30,32,255) if d<7.2 else (18,18,20,255)
    if d>4.6: return (120,120,126,255)
    ang=math.atan2(y-cy,x-cx)
    spoke=abs(math.sin(ang*2.5))<0.35
    if d<1.4: return (60,60,64,255)
    return (205,207,212,255) if spoke or d>3.9 else (70,70,76,255)
fill(2,tyre)
# 3 tread
fill(3,lambda x,y:(20,20,22,255) if (y//2+x//4)%2 else (36,36,38,255))
# 4 grille
fill(4,lambda x,y:(28,28,30,255) if y%3==0 or x in (0,15) else (70,70,74,255) if y%3==1 else (12,12,14,255))
# 5 licence plate: white plate, teal band, dark glyphs
def plate(x,y):
    if y<3: return (40,150,160,255)
    if 5<=y<=11 and 1<=x<=14 and (x%3!=0) and not (y==8 and x%2): return (30,40,70,255)
    return (238,236,228,255)
fill(5,plate)
# 6 headlight lens
def head(x,y):
    d=math.hypot(x-7.5,y-7.5)
    if d<3: return (255,255,250,255)
    if d<6: return (230,236,240,255)
    return (190,196,205,255)
fill(6,head)
# 7 taillight segments
fill(7,lambda x,y:(255,60,60,255) if (x%5!=0 and y%8!=0) else (120,10,12,255))
# 8 police door: white with gold-blue star badge
def police(x,y):
    cx,cy=7.5,7.5; ang=math.atan2(y-cy,x-cx); d=math.hypot(x-cx,y-cy)
    r=3.2+2.6*(0.5+0.5*math.cos(5*ang))
    if d<r and d<6.2: return (214,170,60,255) if d>2 else (30,50,110,255)
    return (240,240,240,255)
fill(8,police)
# 9 taxi sign: yellow box with black TAXI letters (3x5 font)
font={"T":["111","010","010","010","010"],"A":["010","101","111","101","101"],"X":["101","101","010","101","101"],"I":["111","010","010","010","111"]}
def taxi(x,y):
    return (245,200,40,255)
fill(9,taxi)
ox,oy=tile(9)
for k,ch in enumerate("TAXI"):
    for r,row in enumerate(font[ch]):
        for c,b in enumerate(row):
            if b=="1": px[ox+ 0+k*4+c, oy+5+r]=(20,20,20,255)
# 10 checker
fill(10,lambda x,y:(20,20,20,255) if (x//4+y//4)%2 else (245,245,245,255))
# 11 light bar housing
fill(11,lambda x,y:(40,40,44,255) if x%4==0 else (200,200,205,255))
# 12 brushed metal
fill(12,lambda x,y:(lambda n:(n,n,n+4,255))(clamp(190+20*math.sin(y*1.7)+random.randint(-8,8))))
# 13 seat fabric
fill(13,lambda x,y:(lambda n:(n,n,n,255))(200 if (x%4==0 or y%4==0) else 235))
# 14 face (front of head): skin, hair top, eyes, sunglasses
def face(x,y):
    if y<4: return (60,40,28,255)
    if 6<=y<=8 and (2<=x<=6 or 9<=x<=13): return (20,20,24,255)
    if y==12 and 5<=x<=10: return (150,80,70,255)
    return (205,150,115,255)
fill(14,face)
# 15 deck wood
fill(15,lambda x,y:(lambda n:(clamp(n),clamp(n*0.72),clamp(n*0.48),255))(170+random.randint(-15,15) - (30 if y%4==0 else 0)))
# 16 racing stripes (white stripes, alpha 0 elsewhere)
fill(16,lambda x,y:(245,245,245,255) if (3<=x<=6 or 9<=x<=12) else (0,0,0,0))
# 17 propeller cage mesh
fill(17,lambda x,y:(60,60,64,255) if (x%4==0 or y%4==0) else (0,0,0,0))
# 18 vent
fill(18,lambda x,y:(15,15,17,255) if y%2 else (50,50,54,255))
# 19 wreck char
fill(19,lambda x,y:(lambda n:(n,clamp(n*0.9),clamp(n*0.85),255))(25+random.randint(0,30)))
# 20 hair/back of head
fill(20,lambda x,y:(60,40,28,255) if y<10 else (205,150,115,255))
# 21 shirt print (floral, Leonida style) white base, tinted
def floral(x,y):
    v=255 if ((x*7+y*3)%11<3) else 210
    return (v,v,v,255)
fill(21,floral)
# 22 police lettering band (dark blue band with white stripe)
fill(22,lambda x,y:(30,50,110,255) if 4<=y<=11 else (240,240,240,255))
# 23 indicator amber lens
fill(23,lambda x,y:(255,170,40,255) if (x%5!=0) else (150,80,10,255))

# ---- lore fleet tiles (24+)
F5={"P":["111","101","111","100","100"],"O":["111","101","101","101","111"],"L":["100","100","100","100","111"],"I":["111","010","010","010","111"],
"C":["111","100","100","100","111"],"E":["111","100","110","100","111"],"V":["101","101","101","101","010"],"D":["110","101","101","101","110"],
"S":["111","100","111","001","111"],"H":["101","101","111","101","101"],"R":["110","101","110","101","101"],"F":["111","100","110","100","100"],
"G":["111","100","101","101","111"],"6":["111","100","111","101","111"],"0":["111","101","101","101","111"],"8":["111","101","111","101","111"],
"N":["101","111","111","111","101"],"A":["010","101","111","101","101"],"W":["101","101","111","111","101"],"T":["111","010","010","010","010"],
"U":["101","101","101","101","111"],"X":["101","101","010","101","101"]}
def text(i,s,x0,y0,col,step=4):
    ox,oy=tile(i)
    for k,ch in enumerate(s):
        for r,row in enumerate(F5[ch]):
            for c,b in enumerate(row):
                xx=x0+k*step+c
                if b=="1" and 0<=xx<16: px[ox+xx,oy+y0+r]=col
# 24 VCPD door: white door, blue POLICE lettering, small gold badge
fill(24,lambda x,y:(244,244,244,255))
text(24,"VCPD",0,3,(26,58,140,255))
def badge(i,cx,cy,r,col):
    ox,oy=tile(i)
    for y in range(16):
        for x in range(16):
            ang=math.atan2(y-cy,x-cx); d=math.hypot(x-cx,y-cy)
            rr=r*(0.55+0.45*(0.5+0.5*math.cos(5*ang)))
            if d<rr: px[ox+x,oy+y]=col
badge(24,7.5,11.5,3.6,(212,168,58,255))
# 25 Sheriff door: white door, gold star, green lettering
fill(25,lambda x,y:(244,244,240,255))
badge(25,7.5,6.5,5.2,(214,170,60,255))
text(25,"LCS",2,11,(30,80,44,255))
# 26 Leonida plate (replaces 5 look): white, pink-orange sunset band, dark glyphs
def lplate(x,y):
    if y<3: return (232,92,140,255) if x<8 else (247,150,60,255)
    if y>=14: return (40,150,160,255)
    return (240,238,230,255)
fill(26,lplate)
text(26,"8E0",2,6,(28,38,70,255))
# 27 chrome five spoke rim
def rim5(x,y,spokecol=(222,224,230,255),gap=(36,36,40,255),rim=(200,202,208,255)):
    cx=cy=7.5; d=math.hypot(x-cx,y-cy)
    if d>7.9: return (22,22,24,255)
    if d>6.0: return (30,30,32,255) if d<7.3 else (16,16,18,255)
    if d>5.2: return rim
    if d<1.3: return (90,90,96,255)
    ang=math.atan2(y-cy,x-cx)
    return spokecol if abs(math.sin(ang*2.5))<0.42 or d<2.2 else gap
fill(27,rim5)
# 28 black mesh sport rim
def mesh(x,y):
    cx=cy=7.5; d=math.hypot(x-cx,y-cy)
    if d>7.9: return (22,22,24,255)
    if d>6.2: return (30,30,32,255) if d<7.3 else (16,16,18,255)
    if d>5.6: return (60,60,66,255)
    if d<1.3: return (140,30,30,255)
    return (48,48,52,255) if (x+y)%3==0 or (x-y)%3==0 else (14,14,16,255)
fill(28,mesh)
# 29 gold wire spoke rim (lowrider), thin whitewall
def wire(x,y):
    cx=cy=7.5; d=math.hypot(x-cx,y-cy)
    if d>7.9: return (22,22,24,255)
    if d>6.8: return (24,24,26,255)
    if d>5.9: return (236,236,230,255)
    if d>5.2: return (226,186,70,255)
    if d<1.6: return (240,200,80,255)
    ang=math.atan2(y-cy,x-cx)
    return (232,192,74,255) if abs(math.sin(ang*9))<0.5 else (40,34,20,255)
fill(29,wire)
# 30 steel wheel with hubcap (police, trucks)
def steel(x,y):
    cx=cy=7.5; d=math.hypot(x-cx,y-cy)
    if d>7.9: return (22,22,24,255)
    if d>5.8: return (30,30,32,255) if d<7.3 else (16,16,18,255)
    if d>3.0: return (34,34,38,255) if int(math.degrees(math.atan2(y-cy,x-cx))+360)%60<12 else (58,58,62,255)
    return (190,192,198,255)
fill(30,steel)
# 31 wire spoke motorbike rim
def bspoke(x,y):
    cx=cy=7.5; d=math.hypot(x-cx,y-cy)
    if d>7.9: return (22,22,24,255)
    if d>6.6: return (30,30,32,255)
    if d>5.9: return (170,172,178,255)
    if d<1.5: return (120,120,126,255)
    ang=math.atan2(y-cy,x-cx)
    return (200,200,206,255) if abs(math.sin(ang*8))<0.25 else (0,0,0,0)
fill(31,bspoke)
# 32 LED strip headlight (thin bright bar over dark lens)
fill(32,lambda x,y:(250,252,255,255) if 6<=y<=8 else (60,64,72,255) if y<12 else (150,156,166,255))
# 33 round twin headlight
def twin(x,y):
    for cx in (3.5,11.5):
        d=math.hypot(x-cx,y-7.5)
        if d<1.6: return (255,255,250,255)
        if d<3.6: return (225,232,238,255)
    return (40,40,44,255)
fill(33,twin)
# 34 full width LED tail bar (racetrack)
fill(34,lambda x,y:(255,70,70,255) if (y in (3,4,11,12) or x in (0,1,14,15)) else (90,10,14,255))
# 35 spiderweb paint (tinted by paint colour)
def web(x,y):
    cx,cy=0,16; ang=math.atan2(cy-y,x-cx); d=math.hypot(x-cx,y-cy)
    if abs(math.sin(ang*6))<0.14 or abs((d%5)-2.5)<0.5: return (255,240,170,255)
    return (150,150,150,255)
fill(35,web)
# 36 Gruppe6 side panel: green with white G6
fill(36,lambda x,y:(38,120,70,255))
text(36,"G6",4,5,(245,245,245,255))
# 37 VCPD band: blue with white stripe
fill(37,lambda x,y:(26,58,140,255) if 3<=y<=12 else (244,244,244,255) if y in (2,13) else (26,58,140,255))
text(37,"POL",2,6,(244,244,244,255))
# 38 sheriff band: green with gold pinstripe
fill(38,lambda x,y:(212,168,58,255) if y in (3,12) else (30,84,48,255))
# 39 carbon fibre
fill(39,lambda x,y:(40,40,44,255) if ((x//2)+(y//2))%2 else (22,22,24,255))
# 40 race roundel 08 (white disc, black digits)
def roundel(x,y):
    d=math.hypot(x-7.5,y-7.5)
    return (245,245,245,255) if d<7 else (0,0,0,0)
fill(40,roundel)
text(40,"08",4,5,(20,20,20,255))
# 41 dashboard gauges
def dash(x,y):
    for cx in (4.5,11.5):
        d=math.hypot(x-cx,y-8)
        if d<3.2: return (230,240,255,255) if d<2.2 and abs(x-cx-(y-8))<0.8 else (20,24,30,255) if d<2.8 else (150,150,156,255)
    return (34,32,30,255)
fill(41,dash)
# 42 SWAT/RIOT side text on dark
fill(42,lambda x,y:(28,30,34,255))
text(42,"SWAT",0,5,(240,240,240,255))
# 43 wood grain light (kayak, deck trim) reuse: orange plastic with ribs
fill(43,lambda x,y:(240,120,30,255) if y%5 else (200,90,20,255))
# 44 Taxi Granger: yellow with checker strip
fill(44,lambda x,y:(20,20,20,255) if 6<=y<=9 and (x//2+y)%2 else (245,245,245,255) if 6<=y<=9 else (240,190,40,255))
# 45 round tail light (Corvette/Ferrari style quad)
def rtail(x,y):
    for cx in (3.5,11.5):
        d=math.hypot(x-cx,y-7.5)
        if d<1.5: return (255,140,140,255)
        if d<3.6: return (230,40,40,255)
    return (30,30,32,255)
fill(45,rtail)
# 46 brake disc + caliper behind spokes is skipped; 46 = tinted dark glass window film (unused) / chrome grille bars
fill(46,lambda x,y:(225,227,232,255) if y%4 in (0,1) else (20,20,22,255))
# 47 slats (Testarossa side strakes, painted)
fill(47,lambda x,y:(255,255,255,255) if y%3 else (90,90,90,255))

img.save("/home/user/workspace/leonida-wt/vehicle/src/main/resources/assets/leonida/textures/entity/vehicle/atlas.png")
img.resize((512,512),Image.NEAREST).save("/tmp/atlas_preview.png")
