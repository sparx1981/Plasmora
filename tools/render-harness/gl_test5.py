import sys, os
os.environ["ONLY"]="none"
exec(open('/home/claude/harness/gl_test4.py').read())
os.environ.pop("ONLY")
import numpy as np, math
def u4f(p,n,a,b,c,d): gl.glUniform4f(p.loc(n),C.c_float(a),C.c_float(b),C.c_float(c),C.c_float(d))
postP=Prog("fullscreen.vert","post.frag"); taaP=Prog("fullscreen.vert","taa.frag"); gradeP=Prog("fullscreen.vert","grade.frag")
print("post/taa/grade compiled OK")
sphP=Prog("fullscreen.vert","sphere.frag")
LUTN=24
def make_lut(palv, n=LUTN):
    a=np.array(palv[0:3]);b=np.array(palv[3:6]);c=np.array(palv[6:9]);d=np.array(palv[9:12])
    pl=lambda t: np.clip(a+b*np.cos(2*np.pi*(c*t+d)),0,1)
    sh=pl(0.12); hi=pl(0.62)
    lw=np.array([.2126,.7152,.0722])
    g=np.linspace(0,1,n)
    B,G,R=np.meshgrid(g,g,g,indexing='ij')   # [b][g][r]
    cc=np.stack([R,G,B],-1)
    lum=(cc*lw).sum(-1,keepdims=True)
    shc=sh-sh@lw; hic=hi-hi@lw
    o=cc+shc*((1-lum)**2)*0.07+hic*(lum**2)*0.14
    o=np.clip(o,0,1); o=o+((o*o*(3-2*o))-o)*0.45
    l2=(o*lw).sum(-1,keepdims=True); o=np.clip(l2+(o-l2)*1.08,0,1)
    return (o*255+0.5).astype(np.uint8)
lutTex=C.c_uint(); gl.glGenTextures(1,C.byref(lutTex)); gl.glActiveTexture(0x84C7); gl.glBindTexture(0x806F,lutTex)
def upload_lut(palv):
    data=np.ascontiguousarray(make_lut(palv))
    gl.glPixelStorei(0xCF5,1)
    gl.glTexImage3D(0x806F,0,0x8051,LUTN,LUTN,LUTN,0,0x1907,0x1401,data.ctypes.data_as(C.c_void_p))
    for p,v in ((0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812F),(0x2803,0x812F),(0x8072,0x812F)): gl.glTexParameteri(0x806F,p,v)
upload_lut(PAL)
T0=make_target(W,H,0x812F); H0=make_target(W,H,0x812F); H1=make_target(W,H,0x812F)
outT=C.c_uint(); gl.glGenTextures(1,C.byref(outT)); gl.glBindTexture(0xDE1,outT)
gl.glTexImage2D(0xDE1,0,0x8058,W,H,0,0x1908,0x1401,None)
gl.glTexParameteri(0xDE1,0x2801,0x2601); gl.glTexParameteri(0xDE1,0x2800,0x2601)
outF=C.c_uint(); gl.glGenFramebuffers(1,C.byref(outF)); gl.glBindFramebuffer(0x8D40,outF); gl.glFramebufferTexture2D(0x8D40,0x8CE0,0xDE1,outT,0)

def scene(layers_fn, decay, frames, **wp):
    cur=0; histbuf=np.zeros((ROWS,256),np.float32); acc=0.0; dt=1/60; globals()['_beat']=0
    for f in range(frames):
        t=f*dt; beat=max(0.0,1.0-((t%0.5)*4.2)); globals()['_beat']=beat
        au=audio_frame(t,beat); sp=spec_at(t,beat,0); au[2]=sp; au[3]=np.maximum(sp,sp+0.05)
        gl.glActiveTexture(0x84C1); gl.glBindTexture(0xDE1,audio)
        gl.glTexSubImage2D(0xDE1,0,0,0,1024,4,0x1903,0x1406,au.ctypes.data_as(C.c_void_p))
        acc+=dt
        while acc>1/20:
            histbuf[1:]=histbuf[:-1]; histbuf[0]=au[2].reshape(256,4).max(axis=1); acc-=1/20
            gl.glActiveTexture(0x84C3); gl.glBindTexture(0xDE1,hist)
            gl.glTexSubImage2D(0xDE1,0,0,0,256,ROWS,0x1903,0x1406,np.ascontiguousarray(histbuf).ctypes.data_as(C.c_void_p))
        P=dict(decay=decay); P.update(wp); src,dst=sim[cur],sim[1-cur]
        frame_common(P,dst,src,f); gl.glEnable(0xBE2); gl.glBlendFunc(1,1); layers_fn(t,beat,acc); gl.glDisable(0xBE2); cur=1-cur
    res=sim[cur]
    gl.glActiveTexture(0x84C0); gl.glUseProgram(downP.id); downP.u1i("uSrc",0)
    st,sw_,sh2=res[0],res[2],res[3]
    for i,b in enumerate(bloom):
        gl.glBindFramebuffer(0x8D40,b[1]); gl.glViewport(0,0,b[2],b[3]); gl.glBindTexture(0xDE1,st)
        downP.u2f("uTexel",1/sw_,1/sh2); downP.u1f("uThreshold",0.45 if i==0 else -1.0); downP.u1f("uKnee",0.35)
        gl.glDrawArrays(4,0,3); st,sw_,sh2=b[0],b[2],b[3]
    gl.glEnable(0xBE2); gl.glBlendFunc(1,1); gl.glUseProgram(upP.id); upP.u1i("uSrc",0)
    for i in range(len(bloom)-2,-1,-1):
        sm,bg=bloom[i+1],bloom[i]
        gl.glBindFramebuffer(0x8D40,bg[1]); gl.glViewport(0,0,bg[2],bg[3]); gl.glBindTexture(0xDE1,sm[0])
        upP.u2f("uTexel",1/sm[2],1/sm[3]); upP.u1f("uWeight",0.85); gl.glDrawArrays(4,0,3)
    gl.glDisable(0xBE2)
    return res

def halton(i,b):
    f=1.0;r=0.0
    while i>0: f/=b; r+=f*(i%b); i//=b
    return r
CFG0=dict(taa=0,mb=0.0,streak=0.0,flare=0.0,halo=0.0,dof=0.0,god=0.0,refl=0.0,lut=0.0,sharp=0.0,shake=0.0,
          dofCam=(1.3,0.45,3.9),sun=(0.5,0.5),reflY=0.3,beat=0.0,bloomAmt=0.72,warp=(0.0,0.0))
def post(res,name,cfg,frames=12,shakes=None):
    c=dict(CFG0); c.update(cfg)
    hists=[H0,H1]; hi=0
    for fr in range(frames if c['taa'] else 1):
        jx=(halton(fr+1,2)-0.5)/W*0.9 if c['taa'] else 0.0; jy=(halton(fr+1,3)-0.5)/H*0.9 if c['taa'] else 0.0
        sh=shakes(fr) if shakes else (0,0,0,0); shp=shakes(fr-1) if shakes else (0,0,0,0)
        # pass A
        gl.glBindFramebuffer(0x8D40,T0[1]); gl.glViewport(0,0,W,H); gl.glUseProgram(postP.id)
        for u,tex in ((0x84C0,res[0]),(0x84C2,bloom[0][0]),(0x84C5,bloom[min(1,len(bloom)-1)][0]),(0x84C6,bloom[min(2,len(bloom)-1)][0]),(0x84C4,black.value)):
            gl.glActiveTexture(u); gl.glBindTexture(0xDE1,tex)
        p=postP
        for n,v in (("uScene",0),("uBloom",2),("uBloomMid",5),("uBloomWide",6),("uArt",4)): p.u1i(n,v)
        p.u2f("uSSOff",0,0); p.u2f("uJitter",jx,jy); p.u1f("uBloomAmt",c['bloomAmt']); p.u1f("uChroma",0.35); p.u1f("uAspect",W/H); p.u1f("uTime",3.0); p.u1f("uBeat",c['beat']); p.u1f("uPhase",0.0)
        u4f(p,"uShake",*sh); u4f(p,"uShakePrev",*shp); p.u2f("uWarpMV",*c['warp']); p.u1f("uMB",c['mb'])
        p.u1f("uDof",c['dof']); p.u1f("uDofCamH",c['dofCam'][0]); p.u1f("uDofPitch",c['dofCam'][1]); p.u1f("uDofFocus",c['dofCam'][2])
        p.u2f("uSun",*c['sun']); p.u1f("uGod",c['god']); p.u1f("uRefl",c['refl']); p.u1f("uReflY",c['reflY']); p.u1f("uReflRip",0.0015)
        p.u1f("uStreak",c['streak']); p.u1f("uFlare",c['flare']); p.u1f("uHalo",c['halo'])
        p.u1f("uArtAmt",0.0); p.u1f("uArtLod",0.0); p.u1f("uArtAspect",1.0); pal_unis(p)
        gl.glDrawArrays(4,0,3)
        src=T0
        if c['taa']:
            dst=hists[hi]; hist_in=hists[1-hi]
            gl.glBindFramebuffer(0x8D40,dst[1]); gl.glViewport(0,0,W,H); gl.glUseProgram(taaP.id)
            gl.glActiveTexture(0x84C0); gl.glBindTexture(0xDE1,T0[0]); gl.glActiveTexture(0x84C8); gl.glBindTexture(0xDE1,hist_in[0])
            q=taaP; q.u1i("uCur",0); q.u1i("uHist",8); q.u2f("uTexel",1/W,1/H); u4f(q,"uShake",*sh); u4f(q,"uShakePrev",*shp); q.u1f("uAspect",W/H); q.u1f("uAlpha",0.8 if fr>0 else 0.0)
            gl.glDrawArrays(4,0,3); src=dst; hi=1-hi
    # pass C
    gl.glBindFramebuffer(0x8D40,outF); gl.glViewport(0,0,W,H); gl.glUseProgram(gradeP.id)
    gl.glActiveTexture(0x84C0); gl.glBindTexture(0xDE1,src[0]); gl.glActiveTexture(0x84C7); gl.glBindTexture(0x806F,lutTex)
    g=gradeP; g.u1i("uTex",0); g.u1i("uLut",7); g.u2f("uTexel",1/W,1/H); g.u1f("uLutAmt",c['lut']); g.u1f("uLutN",float(LUTN)); g.u1f("uSharp",c['sharp'])
    g.u1f("uExposure",1.0); g.u1f("uSat",1.1); g.u1f("uGrad",0.2); g.u1f("uVig",0.35); g.u1f("uGrain",0.0); g.u1f("uTime",3.0); g.u1f("uPhase",0.0); g.u1f("uAspect",W/H); pal_unis(g)
    gl.glDrawArrays(4,0,3)
    buf=(C.c_ubyte*(W*H*4))(); gl.glReadPixels(0,0,W,H,0x1908,0x1401,buf)
    img=np.frombuffer(buf,dtype=np.uint8).reshape(H,W,4)[::-1,:,:3].copy()
    Image.fromarray(img).save(f"/home/claude/harness/{name}.png"); return img
