import sys, os
exec(open('/home/claude/harness/gl_common.py').read())
PAL = [0.5,0.5,0.5, 0.5,0.5,0.5, 1,1,1, 0.0,0.33,0.67]
def audio_frame(t, beat):
    x = np.arange(1024) / 1024.0
    f0 = 3.0
    L = 0.35 * np.sin(2 * np.pi * (x * 3 + t * 2)) + 0.2 * np.sin(2 * np.pi * (x * 7 + t * 5)) * (0.6 + 0.4 * beat) + 0.12 * np.sin(2 * np.pi * (x * 19 - t * 3))
    R = 0.35 * np.sin(2 * np.pi * (x * 3 + t * 2 + 0.1)) + 0.2 * np.sin(2 * np.pi * (x * 5 + t * 3)) + 0.1 * np.sin(2 * np.pi * (x * 23 + t))
    sp = np.clip(0.75 - 0.55 * x + 0.12 * np.sin(2 * np.pi * (x * 14 + t)) + 0.25 * beat * np.exp(-x * 6), 0, 1.2)
    return np.stack([L, R, sp, sp]).astype(np.float32)


barsP = Prog("bars.vert", "bars.frag"); ridgeP = Prog("fullscreen.vert", "warp.frag")
ridgeP = Prog("ridge.vert", "ridge.frag")
print("new shaders compiled OK")
hist = C.c_uint(); gl.glGenTextures(1, C.byref(hist)); gl.glActiveTexture(0x84C3); gl.glBindTexture(0xDE1, hist)
ROWS=48
gl.glTexImage2D(0xDE1, 0, 0x822D, 256, ROWS, 0, 0x1903, 0x1406, None)
for p, v in ((0x2801, 0x2600), (0x2800, 0x2600), (0x2802, 0x812F), (0x2803, 0x812F)): gl.glTexParameteri(0xDE1, p, v)
import numpy as np, math
def spec_at(t, beat, k):
    x = np.arange(1024) / 1024.0
    return np.clip(0.8 - 0.6 * x + 0.18 * np.sin(2*np.pi*(x*13 + t*1.7 + k)) + 0.12*np.sin(2*np.pi*(x*31 - t*2.9)) + 0.25*beat*np.exp(-x*5), 0, 1.2).astype(np.float32)
def pal_unis(p, shift=0):
    for nm, v in (("pa", PAL[0:3]), ("pb", PAL[3:6]), ("pc", PAL[6:9])): p.u3f(nm, *v)
    p.u3f("pd", PAL[9]+shift, PAL[10]+shift, PAL[11]+shift)

def frame_common(P, dst, src, fcount):
    gl.glBindFramebuffer(0x8D40, dst[1]); gl.glViewport(0, 0, dst[2], dst[3]); gl.glDisable(0xBE2)
    gl.glUseProgram(warpP.id); gl.glActiveTexture(0x84C0); gl.glBindTexture(0xDE1, src[0])
    w = warpP; w.u1i("uPrev", 0); w.u2f("uTexel", 1/dst[2], 1/dst[3]); w.u1f("uAspect", W/H)
    w.u1f("uZoom", P.get('zoom',1)); w.u1f("uRot", 0); w.u1f("uSwirl", 0); w.u1f("uSwirlR", 3); w.u2f("uTrans", 0, P.get('ty',0))
    w.u1f("uRipA", 0); w.u1f("uRipF", 10); w.u1f("uRipP", 0); w.u1f("uRipR", 1); w.u1f("uFlowA", 0); w.u1f("uFlowS", 2); w.u1f("uFlowT", 0)
    w.u1f("uKal", 0); w.u1f("uTwist", 0); w.u1f("uDecay", P['decay']); w.u3f("uTint", 1, 1, 1); w.u1f("uHue", P.get('hue',0)); w.u1f("uSoft", P.get('soft',0))
    gl.glDrawArrays(4, 0, 3)

def bars_draw(**k):
    d = dict(style=0, n=56, mirror=0, cap=0, base=-0.85, height=1.5, halfw=(W/H)*0.92, gap=0.22, pw=1.2, capH=0.012, inten=0.4, phase=0.0, spread=0.9, hue=0.15)
    d.update(k); b = barsP
    gl.glUseProgram(b.id); b.u1i("uAudio", 1); b.u1i("uStyle", d['style']); b.u1i("uBars", d['n']); b.u1i("uMirror", d['mirror']); b.u1i("uCap", d['cap'])
    b.u1f("uAspect", W/H); b.u1f("uBase", d['base']); b.u1f("uHeight", d['height']); b.u1f("uHalfW", d['halfw']); b.u1f("uGap", d['gap']); b.u1f("uPow", d['pw']); b.u1f("uCapH", d['capH'])
    b.u1f("uIntensity", d['inten']); b.u1f("uPhase", d['phase']); b.u1f("uSpread", d['spread']); b.u1f("uHueLevel", d['hue']); pal_unis(b)
    halves = 1 if d['mirror']==0 else 2
    b.u1f('uRot', 0.0)
    if d['style']==2: gl.glDrawArraysInstanced(5, 0, 4, d['n'])
    elif d['style']==0: gl.glDrawArraysInstanced(5, 0, 4, d['n']*halves)
    else: gl.glDrawArraysInstanced(5, 0, 2*d['n'], halves)

def run2(name, layers_fn, decay, frames, **wp):
    cur=0; histbuf=np.zeros((ROWS,256),np.float32); acc=0.0; dt=1/60
    for f in range(frames):
        t=f*dt; beat=max(0.0,1.0-((t%0.5)*4.2))
        au = audio_frame(t, beat); sp = spec_at(t, beat, 0); au[2]=sp; au[3]=np.maximum(sp, np.roll(sp,0)*1.0+0.05)
        gl.glActiveTexture(0x84C1); gl.glBindTexture(0xDE1, audio)
        gl.glTexSubImage2D(0xDE1,0,0,0,1024,4,0x1903,0x1406,au.ctypes.data_as(C.c_void_p))
        acc+=dt
        while acc>1/20:
            histbuf[1:]=histbuf[:-1]; histbuf[0]=au[2].reshape(256,4).max(axis=1); acc-=1/20
            gl.glActiveTexture(0x84C3); gl.glBindTexture(0xDE1,hist)
            gl.glTexSubImage2D(0xDE1,0,0,0,256,ROWS,0x1903,0x1406,np.ascontiguousarray(histbuf).ctypes.data_as(C.c_void_p))
        P=dict(decay=decay); P.update(wp)
        src,dst=sim[cur],sim[1-cur]
        frame_common(P,dst,src,f)
        gl.glEnable(0xBE2); gl.glBlendFunc(1,1)
        layers_fn(t, beat, acc)
        gl.glDisable(0xBE2); cur=1-cur
    res=sim[cur]
    # bloom + final (same as gl_test)
    gl.glActiveTexture(0x84C0); gl.glUseProgram(downP.id); downP.u1i("uSrc", 0)
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
    outT=C.c_uint(); gl.glGenTextures(1,C.byref(outT)); gl.glBindTexture(0xDE1,outT)
    gl.glTexImage2D(0xDE1,0,0x8058,W,H,0,0x1908,0x1401,None)
    gl.glTexParameteri(0xDE1,0x2801,0x2601); gl.glTexParameteri(0xDE1,0x2800,0x2601)
    outF=C.c_uint(); gl.glGenFramebuffers(1,C.byref(outF)); gl.glBindFramebuffer(0x8D40,outF)
    gl.glFramebufferTexture2D(0x8D40,0x8CE0,0xDE1,outT,0); gl.glViewport(0,0,W,H); gl.glUseProgram(finalP.id)
    gl.glActiveTexture(0x84C0); gl.glBindTexture(0xDE1,res[0]); gl.glActiveTexture(0x84C2); gl.glBindTexture(0xDE1,bloom[0][0])
    fp=finalP; fp.u1i("uScene",0); fp.u1i("uBloom",2); fp.u2f("uSSOff",0,0); fp.u1f("uBloomAmt",0.72); fp.u1f("uExposure",1.0); fp.u1f("uSat",1.1); fp.u1f("uGrad",0.2)
    fp.u1f("uVig",0.35); fp.u1f("uChroma",0.35); fp.u1f("uGrain",0.12); fp.u1f("uTime",frames/60.0); fp.u1f("uPhase",0); pal_unis(fp)
    gl.glDrawArrays(4,0,3)
    buf=(C.c_ubyte*(W*H*4))(); gl.glReadPixels(0,0,W,H,0x1908,0x1401,buf)
    img=np.frombuffer(buf,dtype=np.uint8).reshape(H,W,4)[::-1,:,:3]
    Image.fromarray(img).save(f"/home/claude/harness/{name}.png"); print(name,"mean",img.mean().round(1),"max",img.max())

def ridge_draw(t, beat, acc):
    r=ridgeP; gl.glUseProgram(r.id); r.u1i("uHist",3); r.u1i("uRows",ROWS)
    r.u1f("uFrac",acc*20); r.u1f("uAspect",W/H); r.u1f("uAmp",0.42); r.u1f("uWidth",0.011); r.u1f("uTop",0.62); r.u1f("uBot",-0.72)
    r.u1f("uSpan",(W/H)*0.9); r.u1f("uFar",0.62); r.u1f("uDrop",0.14); r.u1f("uAlpha",1.0); r.u1f("uPhase",0.1); r.u1f("uSpread",0.5); r.u1f("uIntensity",1.1); r.u1f("uDepthHue",0.5)
    pal_unis(r)
    gl.glBlendFuncSeparate(0x302,0x303,0,1)
    for row in range(ROWS-1,-1,-1):
        r.u1i("uRow",row)
        for ps in (0,1):
            r.u1i("uPass",ps); gl.glDrawArrays(5,0,1024)
    gl.glBlendFunc(1,1)

which=sys.argv[1:]
F=int(os.environ.get("FRAMES",150))
if "bars" in which: run2("bars", lambda t,b,a: bars_draw(cap=0) or bars_draw(cap=1,inten=0.9), 0.62, F, ty=-0.0015)
if "mirror" in which: run2("mirror", lambda t,b,a: bars_draw(mirror=1,base=0.0,height=0.85,n=64,gap=0.3) or bars_draw(mirror=1,base=0.0,height=0.85,n=64,gap=0.3,cap=1,inten=0.9), 0.68, F)
if "valley" in which: run2("valley", lambda t,b,a: bars_draw(mirror=2,height=1.05,n=48,gap=0.25,spread=1.3) or bars_draw(mirror=2,height=1.05,n=48,gap=0.25,cap=1,inten=0.9,spread=1.3), 0.75, F, hue=0.004)
if "wave" in which: run2("wave", lambda t,b,a: bars_draw(style=1,n=512,base=-0.85,height=1.5,halfw=(W/H)*1.0,inten=0.5,spread=0.7), 0.6, F, ty=-0.001)
if "mwave" in which: run2("mwave", lambda t,b,a: bars_draw(style=1,n=512,mirror=1,base=0.0,height=0.8,halfw=(W/H)*1.0,inten=0.5,spread=0.7), 0.65, F)
if "ridge" in which: run2("ridge", ridge_draw, 0.0, F)
