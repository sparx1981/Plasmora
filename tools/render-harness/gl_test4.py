import sys
sys.argv=['x']
exec(open('/home/claude/harness/gl_test2.py').read())
import os
for p,v in ((0x2801,0x2601),(0x2800,0x2601)):
    gl.glActiveTexture(0x84C3); gl.glBindTexture(0xDE1,hist); gl.glTexParameteri(0xDE1,p,v)
terrP=Prog("fullscreen.vert","terrain.frag"); sphP=Prog("fullscreen.vert","sphere.frag")
print("terrain/sphere compiled OK")
F=int(os.environ.get("FRAMES",240))
def bars2(**k):
    d=dict(style=0,n=56,mirror=0,cap=0,base=-0.85,height=1.5,halfw=(W/H)*0.92,gap=0.22,pw=1.2,capH=0.012,inten=0.4,phase=0.0,spread=0.9,hue=0.15,
           sym=0,seg=0.0,segGap=0.2,refl=1.0,tilt=0.4,cy=0.0,fill=1.0,edge=0.0,rot=0.0)
    d.update(k); b=barsP
    gl.glUseProgram(b.id); b.u1i("uAudio",1); b.u1i("uStyle",d['style']); b.u1i("uBars",d['n']); b.u1i("uMirror",d['mirror']); b.u1i("uCap",d['cap'])
    b.u1f("uAspect",W/H); b.u1f("uBase",d['base']); b.u1f("uHeight",d['height']); b.u1f("uHalfW",d['halfw']); b.u1f("uGap",d['gap']); b.u1f("uPow",d['pw']); b.u1f("uCapH",d['capH'])
    b.u1f("uIntensity",d['inten']); b.u1f("uPhase",d['phase']); b.u1f("uSpread",d['spread']); b.u1f("uHueLevel",d['hue']); b.u1f("uRot",d['rot'])
    b.u1i("uSym",d['sym']); b.u1f("uSeg",d['seg']); b.u1f("uSegGap",d['segGap']); b.u1f("uReflect",d['refl']); b.u1f("uTilt",d['tilt']); b.u1f("uCy",d['cy']); b.u1f("uFill",d['fill']); b.u1f("uEdgeW",d['edge'])
    pal_unis(b)
    halves=1 if d['mirror']==0 else 2
    if d['style'] in (2,3): gl.glDrawArraysInstanced(5,0,4,d['n'])
    elif d['style']==0: gl.glDrawArraysInstanced(5,0,4,d['n']*halves)
    else: gl.glDrawArraysInstanced(5,0,2*d['n'],halves)
def terrain(mode,style,amp,cells,camh,pitch,inten,line=1.0,hue=0.4):
    def f(t,beat,acc):
        p=terrP; gl.glUseProgram(p.id); p.u1i("uAudio",1); p.u1i("uHist",3); p.u1f("uAspect",W/H); p.u1f("uPix",1.0/H)
        p.u1i("uMode",mode); p.u1i("uStyle",style); p.u1f("uTravel",t*0.8); p.u1f("uFrac",min(acc*20,1)); p.u1f("uAmp",amp); p.u1f("uCells",cells)
        p.u1f("uLineW",line); p.u1f("uCamH",camh); p.u1f("uPitch",pitch); p.u1f("uRot",t*0.1); p.u1f("uBeat",beat); p.u1f("uBass",beat*0.5)
        p.u1f("uIntensity",inten); p.u1f("uPhase",t*0.01); p.u1f("uSpread",1.0); p.u1f("uHueLevel",hue); pal_unis(p); gl.glDrawArrays(4,0,3)
    return f
def sphere(amp,lon,inten):
    def f(t,beat,acc):
        p=sphP; gl.glUseProgram(p.id); p.u1i("uAudio",1); p.u1f("uAspect",W/H); p.u1f("uPix",1.0/H); p.u1f("uScale",0.42); p.u1f("uAmp",amp)
        p.u1f("uRot",t*0.35); p.u1f("uTilt",0.35); p.u1f("uTime",t); p.u1f("uBeat",beat); p.u1f("uBass",beat*0.5); p.u1f("uLon",lon); p.u1f("uLineW",1.0)
        p.u1f("uIntensity",inten); p.u1f("uPhase",0.0); p.u1f("uSpread",1.0); pal_unis(p); gl.glDrawArrays(4,0,3)
    return f
only=os.environ.get("ONLY","").split(",")
def go(n,fn,dec,**kw):
    if only==[""] or n in only: run2(n,fn,dec,F,**kw)
go("q_terrain",terrain(0,0,1.0,2.2,1.3,0.45,0.9),0.55,soft=0.1)
go("q_dots",terrain(0,1,1.0,3.0,1.3,0.45,1.0),0.55,soft=0.1)
go("q_synth",terrain(1,0,0.8,1.3,0.75,-0.12,0.9,hue=0.2),0.5,soft=0.1)
go("q_ripple",terrain(2,0,1.1,3.0,2.2,0.6,0.9),0.55,soft=0.1)
go("q_sphere",sphere(0.2,36,0.8),0.55,soft=0.1)
def spike(t,b,a):
    bars2(style=3,n=180,base=0.8,height=0.7,gap=0.35,tilt=0.38,cy=-0.1,inten=0.5,spread=1.0,hue=0.0,rot=t*0.1)
go("q_spike",spike,0.6,soft=0.05)
def grideq(t,b,a):
    bars2(style=0,n=44,mirror=1,base=-0.12,height=0.8,gap=0.12,seg=0.045,segGap=0.28,refl=0.30,sym=1,inten=0.55*(1+0.2*b),spread=1.0,hue=0.9,halfw=(W/H)*0.96)
go("q_grideq",grideq,0.6,soft=0.05)
def pulse(t,b,a):
    bars2(style=0,n=150,mirror=1,base=0.0,height=0.9,gap=0.55,sym=1,inten=0.45,spread=0.6,hue=0.3,halfw=(W/H)*0.96,pw=1.0)
go("q_pulse",pulse,0.6,soft=0.05)
def ribbon(t,b,a):
    bars2(style=1,n=512,base=-0.15,height=1.0,fill=0.25,edge=0.012,halfw=(W/H)*1.0,inten=0.5,spread=1.2,hue=0.25)
go("q_ribbon",ribbon,0.82,soft=0.3,ty=0.0)
