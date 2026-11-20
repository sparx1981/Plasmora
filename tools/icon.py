import numpy as np
from PIL import Image, ImageFilter
S=2048; N=512
y,x=np.mgrid[0:S,0:S].astype(np.float32)
c=S/2; k=S/108*1.12   # same design as launcher icon, slightly larger for store
dx=(x-c); dy=(y-c); r=np.hypot(dx,dy)/k
ang=(np.arctan2(dy,dx)/(2*np.pi))%1.0           # sweep from 3 o'clock, clockwise
t=1-np.abs(ang*2-1)                              # cyan -> purple -> cyan
cy=np.array([0,229,255],np.float32); pu=np.array([177,76,255],np.float32)
col=cy[None,None]*(1-t[...,None])+pu[None,None]*t[...,None]
bg=np.array([10,6,24],np.float32)
img=np.ones((S,S,3),np.float32)*bg
# soft glow
g=np.exp(-((r-30)/9.0)**2)*0.35
img=img*(1-g[...,None])+col*g[...,None]
ring=(r<=30)&(r>=17)
img[ring]=col[ring]
dot=r<=8
img[dot]=np.array([255,79,154],np.float32)
im=Image.fromarray(img.clip(0,255).astype(np.uint8)).resize((N,N),Image.LANCZOS).convert("RGB")
im.save("/home/claude/deliver/Plasmora-icon-512.png",optimize=True)
