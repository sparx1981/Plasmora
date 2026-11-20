exec(open('/home/claude/harness/gl_test5.py').read())
import math
def sphere2(amp,lon,inten,scale=0.36,cy=0.22):
    def f(t,beat,acc):
        p=sphP; gl.glUseProgram(p.id); p.u1i("uAudio",1); p.u1f("uAspect",W/H); p.u1f("uPix",1.0/H); p.u1f("uScale",scale); p.u1f("uAmp",amp); p.u1f("uCy",cy)
        p.u1f("uRot",t*0.35); p.u1f("uTilt",0.45); p.u1f("uTime",t); p.u1f("uBeat",beat); p.u1f("uBass",beat*0.5); p.u1f("uLon",lon); p.u1f("uLineW",1.0)
        p.u1f("uIntensity",inten); p.u1f("uPhase",0.0); p.u1f("uSpread",1.0); pal_unis(p); gl.glDrawArrays(4,0,3)
    return f
fit=min(W/H,1.0); scale=0.36; cy=0.22
cuv=0.5+cy*fit; bottom=cuv-scale*fit*1.06
print("sphere centre uv",cuv,"floor",bottom)
res=scene(sphere2(0.2,36,0.8),0.6,200,soft=0.1)
a=post(res,"f_sph_base",{})
b=post(res,"f_sph_refl",dict(refl=0.8,reflY=bottom))
c=post(res,"f_sph_full",dict(refl=0.8,reflY=bottom,beat=0.5,streak=0.5,flare=0.3,halo=0.5,lut=0.6,sharp=0.5))
res2=scene(terrain(0,0,1.0,2.2,1.3,0.45,0.9),0.45,200,soft=0.05)
d=post(res2,"f_ter_dof",dict(dof=0.0))
e=post(res2,"f_ter_dof1",dict(dof=1.0,dofCam=(1.3,0.45,1.3/math.sin(0.45))))
Image.fromarray(np.concatenate([a,b,c,d,e],1)).resize((int(W*2.5),H//2)).save("/home/claude/harness/sheet9.png")
print([round(x.mean(),1) for x in (a,b,c,d,e)])
