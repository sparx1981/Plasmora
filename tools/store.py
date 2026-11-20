from PIL import Image, ImageDraw, ImageFont, ImageFilter
import numpy as np
H='/home/claude/harness/'; O='/home/claude/deliver/playstore/'
F='/usr/share/fonts/opentype/inter/'
bold=lambda s: ImageFont.truetype(F+'Inter-Bold.otf',s)
reg=lambda s: ImageFont.truetype(F+'Inter-Medium.otf',s)
def logo(sz):
    S=sz*4; y,x=np.mgrid[0:S,0:S].astype(np.float32); c=S/2; k=S/64
    r=np.hypot(x-c,y-c)/k; a=(np.arctan2(y-c,x-c)/(2*np.pi))%1; t=1-np.abs(a*2-1)
    col=np.array([0,229,255],np.float32)*(1-t[...,None])+np.array([177,76,255],np.float32)*t[...,None]
    img=np.zeros((S,S,4),np.float32); ring=(r<=30)&(r>=17); img[ring,:3]=col[ring]; img[ring,3]=255
    d=r<=8; img[d]=[255,79,154,255]
    return Image.fromarray(img.astype(np.uint8),'RGBA').resize((sz,sz),Image.LANCZOS)
def shade_top(im,h):
    g=Image.new('L',(1,h)); 
    for i in range(h): g.putpixel((0,i),int(235*(1-i/h)**1.4))
    g=g.resize((im.width,h)); blk=Image.new('RGB',(im.width,h),(6,3,14))
    top=im.crop((0,0,im.width,h)); im.paste(Image.composite(blk,top,g),(0,0))
shots=[("Smoke_Crown","Your music,\nturned into light","A live light show for every song"),
       ("Vortex_Halo","90+ live effects","Tunnels, smoke, glass, LED and 3D"),
       ("Dot_Equaliser","Reacts to every beat","Bass, kicks and melody drive the visuals"),
       ("Hex_Portal","Works with your\nmusic apps","YouTube Music, Spotify and more"),
       ("Ring_Bars","Build your own mix","Like or dislike effects as they play"),
       ("Neon_Tiles","Make it yours","Colour, glow, motion and zoom controls"),
       ("Dot_Ripples","Always on","Live wallpaper and always-on mode"),
       ("Smoke_Bloom","Smooth transitions","Effects change with the music")]
for i,(fn,t1,t2) in enumerate(shots,1):
    im=Image.open(H+'hi_'+fn+'.png').convert('RGB')
    shade_top(im,760)
    d=ImageDraw.Draw(im); y=150
    for line in t1.split('\n'):
        w=d.textlength(line,font=bold(84)); d.text(((1080-w)/2,y),line,font=bold(84),fill='white'); y+=104
    w=d.textlength(t2,font=reg(42)); d.text(((1080-w)/2,y+18),t2,font=reg(42),fill=(207,194,238))
    im.save(O+f'phone-screenshot-{i}.png',optimize=True)
# feature graphic 1024x500
bg=Image.open(H+'fg_Smoke_Crown.png').convert('RGB').resize((1024,500),Image.LANCZOS)
fg=Image.new('RGB',(1024,500),(6,3,14)); fg.paste(bg,(210,0))
m=Image.new('L',(1024,1))
for x in range(1024): m.putpixel((x,0),int(255*min(1,max(0,(x-250)/260))))
fg=Image.composite(fg,Image.new('RGB',(1024,500),(6,3,14)),m.resize((1024,500)))
L=logo(96); fg.paste(L,(64,150),L)
d=ImageDraw.Draw(fg)
d.text((178,152),"Plasmora",font=bold(70),fill='white')
d.text((66,272),"3D music visualiser",font=reg(36),fill=(207,194,238))
d.text((66,322),"Turn any song into a live light show",font=reg(26),fill=(150,140,180))
fg.save(O+'feature-graphic-1024x500.png',optimize=True)
