exec(open('/home/claude/harness/gl_test5.py').read())
import math
D=dict(taa=1,mb=0.6,streak=0.5,flare=0.35,halo=0.25,dof=0.6,god=0.6,refl=0.6,lut=0.6,sharp=0.4)
fit=min(W/H,1.0); out=[]
# synth
pitch=-0.12; hz=0.5+fit*1.15*math.tan(pitch); sun=(0.5,hz+fit*0.2)
res=scene(terrain(1,0,0.8,1.3,0.75,pitch,0.9,hue=0.2),0.5,200,soft=0.1)
out.append(post(res,"h_synth",dict(D,beat=0.3,dofCam=(0.75,pitch,2.8),sun=sun,reflY=hz)))
out.append(post(res,"h_synth_beat",dict(D,beat=1.0,dofCam=(0.75,pitch,2.8),sun=sun,reflY=hz)))
# sphere
def sphere2(t,b,a):
    p=sphP; gl.glUseProgram(p.id); p.u1i("uAudio",1); p.u1f("uAspect",W/H); p.u1f("uPix",1.0/H); p.u1f("uScale",0.36); p.u1f("uAmp",0.2); p.u1f("uCy",0.22)
    p.u1f("uRot",t*0.35); p.u1f("uTilt",0.45); p.u1f("uTime",t); p.u1f("uBeat",b); p.u1f("uBass",b*0.5); p.u1f("uLon",36); p.u1f("uLineW",1.0)
    p.u1f("uIntensity",0.9); p.u1f("uPhase",0.0); p.u1f("uSpread",1.0); pal_unis(p); gl.glDrawArrays(4,0,3)
res=scene(sphere2,0.6,200,soft=0.1)
bottom=0.5+0.22*fit-0.36*fit*1.06
out.append(post(res,"h_sphere",dict(D,beat=0.3,reflY=bottom,dof=0,god=0)))
# wire terrain
res=scene(terrain(0,0,1.0,2.2,1.3,0.45,0.9),0.45,200,soft=0.05)
out.append(post(res,"h_terrain",dict(D,beat=0.3,dofCam=(1.3,0.45,1.3/math.sin(0.45)),refl=0,god=0)))
Image.fromarray(np.concatenate(out,1)).resize((int(W*2),int(H/2))).save("/home/claude/harness/sheet12.png")
print([round(x.mean(),1) for x in out])
