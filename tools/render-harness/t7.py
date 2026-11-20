import sys, os, time
exec(open('/home/claude/harness/gl_common.py').read())
FITP=float(os.environ.get('FITP',0.55))
PAL = [0.5,0.5,0.5, 0.5,0.5,0.5, 1,1,1, 0.0,0.33,0.67]
if os.environ.get("PALV"): PAL=[float(x) for x in os.environ["PALV"].split(",")]
def pal_unis(p, pv=None):
    pv = pv or PAL
    for nm, v in (("pa", pv[0:3]), ("pb", pv[3:6]), ("pc", pv[6:9]), ("pd", pv[9:12])): p.u3f(nm, *v)
def aces(x): return np.clip((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14),0,1)
def tone(a): return (aces(a*1.0)**(1/1.35)*255).astype(np.uint8)
def readback(t):
    gl.glBindFramebuffer(0x8D40, t[1]); buf=(C.c_float*(t[2]*t[3]*4))()
    gl.glReadPixels(0,0,t[2],t[3],0x1908,0x1406,buf)
    return np.frombuffer(buf,dtype=np.float32).reshape(t[3],t[2],4)[::-1,:,:3].copy()
def save(a,name): Image.fromarray(tone(a)).save(f"/home/claude/harness/{name}.png")
scn = make_target(W,H,0x812F)
bar3d=Prog("bar3d.vert","bar3d.frag"); wave3d=Prog("wave3d.vert","wave3d.frag"); surf=Prog("surf.vert","surf.frag")
print("new shaders compiled OK")
def upload_audio(t, beat):
    x = np.arange(1024)/1024.0
    sp = np.clip(0.8-0.6*x+0.18*np.sin(2*np.pi*(x*13+t*1.7))+0.12*np.sin(2*np.pi*(x*31-t*2.9))+0.25*beat*np.exp(-x*5),0,1.2).astype(np.float32)
    au = np.stack([np.zeros(1024),np.zeros(1024),sp,sp]).astype(np.float32)
    gl.glActiveTexture(0x84C1); gl.glBindTexture(0xDE1, audio)
    gl.glTexSubImage2D(0xDE1,0,0,0,1024,4,0x1903,0x1406,au.ctypes.data_as(C.c_void_p))
hist = C.c_uint(); gl.glGenTextures(1, C.byref(hist)); gl.glActiveTexture(0x84C3); gl.glBindTexture(0xDE1, hist)
gl.glTexImage2D(0xDE1,0,0x822D,256,40,0,0x1903,0x1406,None)
for p_,v in ((0x2801,0x2600),(0x2800,0x2600),(0x2802,0x812F),(0x2803,0x812F)): gl.glTexParameteri(0xDE1,p_,v)
def upload_hist(t):
    x=np.arange(256)/256.0
    rows=[]
    for r in range(40):
        tt=t-r*0.05
        h=np.clip(0.85-0.55*x+0.22*np.sin(2*np.pi*(x*9+tt*1.7))+0.15*np.sin(2*np.pi*(x*23-tt*2.9))+0.3*np.exp(-x*6)*(0.5+0.5*np.sin(tt*6)),0,1.2)
        rows.append(h)
    h=np.array(rows,dtype=np.float32)
    gl.glActiveTexture(0x84C3); gl.glBindTexture(0xDE1,hist)
    gl.glTexSubImage2D(0xDE1,0,0,0,256,40,0x1903,0x1406,h.ctypes.data_as(C.c_void_p))
def begin():
    gl.glBindFramebuffer(0x8D40,scn[1]); gl.glViewport(0,0,W,H); gl.glClearColor(C.c_float(0),C.c_float(0),C.c_float(0),C.c_float(1)); gl.glClear(0x4000)
    gl.glEnable(0xBE2)
def common(p, **k):
    gl.glUseProgram(p.id)
    p.u1i("uHist",3)
    try: p.u1i("uAudio",1)
    except Exception: pass
    p.u1f("uAspect",W/H); p.u1f("uFit",min(W/H,1.0)**FITP)
    for n,v in k.items():
        if isinstance(v,int) and not isinstance(v,bool): p.u1i(n,v)
        else: p.u1f(n,float(v))
    pal_unis(p, k.get("_pal") if False else None)

def draw_bars(nx=18,nz=14,rot=0.55,tilt=0.62,dist=4.2,cy=0.05,span=1.3,spanz=1.2,height=1.2,gap=0.3,pw=1.3,inten=1.0,refl=0.35,frac=0.4,far=0.8,spread=1.0,hue=0.4,phase=0.0,focal=1.7,beat=0.2):
    begin(); gl.glBlendFuncSeparate(0x302,0x303,0,1)
    p=bar3d; common(p,uFit=min(W/H,1.0),uFrac=frac,uRot=rot,uTilt=tilt,uDist=dist,uCy=cy,uFocal=focal,uSpanX=span,uSpanZ=spanz,uHeight=height,uGap=gap,uPow=pw,uFarFade=far,uBass=0.4,uBeat=beat,uNX=nx,uNZ=nz,uRows=40,uIntensity=inten,uPhase=phase,uSpread=spread,uHueLevel=hue,uAlpha=1.0,uReflect=refl)
    if refl>0:
        p.u1i("uPass",1); gl.glDrawArraysInstanced(4,0,36,nx*nz)
    p.u1i("uPass",0); gl.glDrawArraysInstanced(4,0,36,nx*nz)
    gl.glBlendFunc(1,1)

def draw_ribbon(nl=72,rot=0.0,tilt=0.25,dist=3.0,cy=0.0,span=1.9,amp=0.55,width=0.004,twist=1.0,ribw=0.30,inten=0.07,pw=1.0,spread=0.9,hue=0.3,phase=0.0,t=1.0,focal=1.7):
    begin(); gl.glBlendFunc(1,1)
    p=wave3d; common(p,uKind=0,uLines=nl,uRowsN=1,uRot=rot,uTilt=tilt,uDist=dist,uCy=cy,uFocal=focal,uSpan=span,uAmp=amp,uWidth=width,uTwist=twist,uRibW=ribw,uTime=t,uTravel=0.0,uFrac=0.0,uPow=pw,uSize=1.0,uBass=0.4,uBeat=0.2,uKick=0.3,uFar=5.0,uIntensity=inten,uPhase=phase,uSpread=spread,uHueLevel=hue,uAlpha=1.0)
    gl.glDrawArraysInstanced(5,0,2*480,nl)

def draw_dots(nx=320,nz=44,rot=0.0,tilt=0.36,dist=3.2,cy=-0.1,span=1.9,amp=0.5,size=2.2,inten=0.9,pw=1.1,far=5.0,spread=0.9,hue=0.4,phase=0.0,t=1.0,travel=2.0,frac=0.3,focal=1.7):
    begin(); gl.glBlendFunc(1,1)
    p=wave3d; common(p,uKind=1,uLines=nx,uRowsN=nz,uRot=rot,uTilt=tilt,uDist=dist,uCy=cy,uFocal=focal,uSpan=span,uAmp=amp,uWidth=0.004,uTwist=1.0,uRibW=0.3,uTime=t,uTravel=travel,uFrac=frac,uPow=pw,uSize=size*H/1000.0,uBass=0.4,uBeat=0.2,uKick=0.3,uFar=far,uIntensity=inten,uPhase=phase,uSpread=spread,uHueLevel=hue,uAlpha=1.0)
    gl.glEnable(0x8642)
    gl.glDrawArrays(0,0,nx*nz)

def draw_surf(kind=0,style=0,rows=96,cols=170,rot=0.0,tilt=0.32,dist=3.0,cy=-0.05,span=2.6,amp=0.9,cells=2.0,lw=1.2,inten=1.0,far=6.0,travel=1.0,frac=0.3,spread=0.4,hue=0.5,phase=0.0,pw=1.2,spokes=48,inner=0.15,outer=1.6,focal=1.7):
    begin()
    if kind==0: gl.glBlendFuncSeparate(0x302,0x303,0,1)
    else: gl.glBlendFunc(1,1)
    p=surf; common(p,uFit=(min(W/H,1.0) if kind==1 else min(W/H,1.0)**FITP),uKind=kind,uStyle=style,uRowsN=rows,uColsN=cols,uRot=rot,uTilt=tilt,uDist=dist,uCy=cy,uFocal=focal,uAmp=amp,uCells=cells,uSpokes=spokes,uTravel=travel,uFrac=frac,uPow=pw,uBass=0.4,uBeat=0.2,uSpanX=span,uFar=far,uInner=inner,uOuter=outer,uIntensity=inten,uPhase=phase,uSpread=spread,uHueLevel=hue,uAlpha=1.0,uLineW=lw)
    gl.glDrawArraysInstanced(5,0,2*(cols+1),rows)
    gl.glBlendFunc(1,1)

def shot(name, fn, **kw):
    upload_audio(1.0,0.4); upload_hist(2.0)
    t0=time.time(); fn(**kw); gl.glFinish(); dt=time.time()-t0
    a=readback(scn); save(a,name)
    print(name,"mean",round(float(a.mean()),4),"max",round(float(a.max()),2),"finite",bool(np.isfinite(a).all()),"sec",round(dt,2))
    return a
if __name__=="__main__":
    which=sys.argv[1:] or ["bars","ribbon","dots","mountain","hills","radial"]
    if "bars" in which: shot("n7_bars",draw_bars)
    if "ribbon" in which: shot("n7_ribbon",draw_ribbon)
    if "dots" in which: shot("n7_dots",draw_dots)
    if "mountain" in which: shot("n7_mountain",draw_surf)
    if "hills" in which: shot("n7_hills",draw_surf,style=1)
    if "radial" in which: shot("n7_radial",draw_surf,kind=1,tilt=0.9,dist=3.2,cy=0.05,amp=0.5,cells=3.0,hue=0.5)
