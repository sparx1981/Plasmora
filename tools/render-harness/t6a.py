import sys, os, time
exec(open('/home/claude/harness/gl_common.py').read())
PAL = [0.5,0.5,0.5, 0.5,0.5,0.5, 1,1,1, 0.0,0.33,0.67]
if os.environ.get("PALV"): PAL=[float(x) for x in os.environ["PALV"].split(",")]
def pal_unis(p):
    for nm, v in (("pa", PAL[0:3]), ("pb", PAL[3:6]), ("pc", PAL[6:9]), ("pd", PAL[9:12])): p.u3f(nm, *v)
def upload_audio(t, beat):
    x = np.arange(1024)/1024.0
    sp = np.clip(0.8-0.6*x+0.18*np.sin(2*np.pi*(x*13+t*1.7))+0.12*np.sin(2*np.pi*(x*31-t*2.9))+0.25*beat*np.exp(-x*5),0,1.2).astype(np.float32)
    au = np.stack([np.zeros(1024),np.zeros(1024),sp,sp]).astype(np.float32)
    gl.glActiveTexture(0x84C1); gl.glBindTexture(0xDE1, audio)
    gl.glTexSubImage2D(0xDE1,0,0,0,1024,4,0x1903,0x1406,au.ctypes.data_as(C.c_void_p))
def tone(a):
    a = a/(1+a); return (np.clip(a,0,1)**(1/2.2)*255).astype(np.uint8)
def readback(t):
    gl.glBindFramebuffer(0x8D40, t[1]); buf=(C.c_float*(t[2]*t[3]*4))()
    gl.glReadPixels(0,0,t[2],t[3],0x1908,0x1406,buf)
    a=np.frombuffer(buf,dtype=np.float32).reshape(t[3],t[2],4)[::-1,:,:3].copy()
    return a
def save(a,name): Image.fromarray(tone(a)).save(f"/home/claude/harness/{name}.png")
scn = make_target(W,H,0x812F)
marchP = Prog("fullscreen.vert","march.frag"); partP = Prog("particles.vert","particles.frag"); fluidP = Prog("fullscreen.vert","fluid.frag")
print("new shaders compiled OK")
def begin(t):
    gl.glBindFramebuffer(0x8D40,t[1]); gl.glViewport(0,0,t[2],t[3]); gl.glClearColor(C.c_float(0),C.c_float(0),C.c_float(0),C.c_float(1)); gl.glClear(0x4000)

def draw_march(scene_i, steps, scale, cy, rot, tilt, t, kick=0.5, snare=0.3, vocal=0.5, beat=0.4, amp=0.9, inten=0.8):
    p=marchP; gl.glUseProgram(p.id)
    p.u1i("uAudio",1); p.u1i("uScene",scene_i); p.u1i("uSteps",steps)
    p.u1f("uAspect",W/H); p.u1f("uPix",1/H); p.u1f("uScale",scale); p.u1f("uCy",cy); p.u1f("uRot",rot); p.u1f("uTilt",tilt); p.u1f("uTime",t)
    for nm,v in (("uBass",beat*0.5),("uBeat",beat),("uKick",kick),("uSnare",snare),("uVocal",vocal),("uMelody",0.4),("uPitch",0.3),("uBright",0.5),("uAmp",amp),("uIntensity",inten),("uPhase",0.0),("uSpread",0.9)): p.u1f(nm,v)
    pal_unis(p); gl.glDrawArrays(4,0,3)

if "march" in sys.argv:
    upload_audio(1.0,0.4)
    for i,(nm,steps,sc,cy) in enumerate((("bulb",72,0.31,0.0),("blobs",64,0.34,0.02),("crystal",80,0.30,0.0))):
        begin(scn); gl.glDisable(0xBE2)
        t0=time.time(); draw_march(i,steps,sc,cy,0.6,0.3,3.0); gl.glFinish(); dt=time.time()-t0
        a=readback(scn); save(a,"m6_"+nm); print(nm,"mean",round(float(a.mean()),3),"max",round(float(a.max()),2),"finite",bool(np.isfinite(a).all()),"sec",round(dt,2))
if "part" in sys.argv:
    upload_audio(1.0,0.4)
    for mode,n,size,inten,scale in ((3,140000,0.0022,0.030,0.95),(4,120000,0.0020,0.034,1.0),(5,90000,0.0026,0.045,0.95)):
        begin(scn); gl.glEnable(0xBE2); gl.glBlendFunc(1,1)
        p=partP; gl.glUseProgram(p.id); p.u1i("uAudio",1); p.u1i("uMode",mode); p.u1f("uAspect",W/H); p.u1f("uTravel",8.0)
        p.u1f("uSize",size*H); p.u1f("uScale",scale); p.u1f("uArms",3); p.u1f("uSwirl",0); p.u1f("uBass",0.3); p.u1f("uBeat",0.4)
        for nm,v in (("uKick",0.6),("uSnare",0.3),("uVocal",0.5),("uPitch",0.3),("uBright",0.5),("uRot",0.7),("uTilt",0.35),("uPhase",0.0),("uSpread",0.9),("uIntensity",inten)): p.u1f(nm,v)
        pal_unis(p); gl.glDrawArrays(0,0,n); gl.glDisable(0xBE2)
        a=readback(scn); save(a,f"pt6_{mode}"); print("particles mode",mode,"mean",round(float(a.mean()),4),"max",round(float(a.max()),2),"finite",bool(np.isfinite(a).all()))

if "fluid" in sys.argv:
    import random
    asp=W/H; base=176
    vw,vh=(int(base*asp),base) if asp>=1 else (base,int(base/asp))
    T=[make_target(vw,vh,0x812F) for _ in range(6)]+[make_target(vw*2,vh*2,0x812F) for _ in range(2)]
    vel=0; prs=0; dye=0
    p=fluidP
    def setup():
        gl.glUseProgram(p.id); p.u1i("uSrc",9); p.u1i("uVel",10); p.u1i("uAux",11)
    def fpass(n,dst,src=None,v=None,aux=None):
        gl.glBindFramebuffer(0x8D40,dst[1]); gl.glViewport(0,0,dst[2],dst[3]); p.u1i("uPass",n); p.u2f("uTexel",1/dst[2],1/dst[3])
        for unit,tex in ((0x84C9,src),(0x84CA,v),(0x84CB,aux)):
            if tex is not None: gl.glActiveTexture(unit); gl.glBindTexture(0xDE1,tex[0])
        gl.glDrawArrays(4,0,3)
    def u4v(name,arr):
        gl.glUniform4fv(p.loc(name),8,(C.c_float*32)(*arr))
    def palrgb(t):
        return [min(2,max(0,PAL[c]+PAL[3+c]*math.cos(6.2831853*(PAL[6+c]*t+PAL[9+c])))) for c in range(3)]
    clock=0.0; dt=1/60; frames=int(os.environ.get("FF",150)); gl.glDisable(0xBE2)
    for f in range(frames):
        clock+=dt
        sp=[];sv=[];sc=[];n=0
        def add(x,y,r,vx,vy,h,a):
            global n
            if n>=8: return
            c=palrgb(h); sp.extend([x,y,r,0]); sv.extend([vx,vy,0,0]); sc.extend(c+[a]); n+=1
        vs=70.0
        if f%30==0:
            for i in range(5):
                ang=clock*0.7+i*6.2831853/5
                add(0.5+0.06*math.cos(ang)*min(1,1/asp)*0+0.06*math.cos(ang),0.5+0.06*math.sin(ang),0.0016+0.0012,math.cos(ang)*vs*2.4,math.sin(ang)*vs*2.4,i*0.07,0.22)
        if f%30==15:
            y=0.35+0.3*((clock*1.7)%1); add(0.04,y,0.0009,vs*3.2,(0.5-y)*vs,0.33,0.25); add(0.96,y,0.0009,-vs*3.2,(0.5-y)*vs,0.38,0.25)
        ang=clock*0.9; add(0.5+0.2*math.cos(ang),0.5+0.2*math.sin(ang),0.002,-math.sin(ang)*vs*0.9,math.cos(ang)*vs*0.9,0.15,0.05)
        add(0.5,0.06,0.0016,0,vs*1.1*0.8,0.6,0.024)
        while len(sp)<32: sp.extend([0,0,0,0]); sv.extend([0,0,0,0]); sc.extend([0,0,0,0])
        gl.glUseProgram(p.id); setup()
        p.u1f("uDt",dt); p.u1f("uAspect",asp); p.u2f("uVelTexel",1/T[0][2],1/T[0][3])
        p.u1i("uSplatMode",0); p.u1i("uCount",n); u4v("uSplat[0]",sp); u4v("uSplatVel[0]",sv); u4v("uSplatCol[0]",sc)
        V=lambda i:T[i]
        if n>0: fpass(6,V(1-vel),V(vel)); vel=1-vel
        fpass(4,T[5],None,V(vel))
        p.u1f("uVort",24.0); fpass(5,V(1-vel),V(vel),None,T[5]); vel=1-vel
        p.u1f("uDiss",math.exp(-0.45*dt)); fpass(0,V(1-vel),V(vel),V(vel)); vel=1-vel
        fpass(1,T[4],None,V(vel))
        gl.glBindFramebuffer(0x8D40,T[2][1]); gl.glClear(0x4000); prs=0
        for i in range(16): fpass(2,T[2+1-prs],T[2+prs],None,T[4]); prs=1-prs
        fpass(3,V(1-vel),None,V(vel),T[2+prs]); vel=1-vel
        p.u1i("uSplatMode",1)
        if n>0: fpass(6,T[6+1-dye],T[6+dye]); dye=1-dye
        p.u1f("uDiss",math.exp(-1.0*dt)); fpass(0,T[6+1-dye],T[6+dye],V(vel)); dye=1-dye
    d=T[6+dye]
    begin(scn); gl.glUseProgram(p.id); setup()
    gl.glEnable(0xBE2); gl.glBlendFunc(1,1)
    p.u2f("uTexel",1/d[2],1/d[3]); p.u1i("uPass",7); gl.glActiveTexture(0x84C9); gl.glBindTexture(0xDE1,d[0])
    pal_unis(p); p.u1f("uPhase",0.0); p.u1f("uSpread",0.7); p.u1f("uShade",32.0); p.u1f("uIntensity",0.95)
    gl.glDrawArrays(4,0,3); gl.glDisable(0xBE2)
    a=readback(scn); save(a,"fl6"); print("fluid mean",round(float(a.mean()),4),"max",round(float(a.max()),2),"finite",bool(np.isfinite(a).all()))
    dd=readback(d); print("dye max",float(dd.max()),"mean",float(dd.mean()))
