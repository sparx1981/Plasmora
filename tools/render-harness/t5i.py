exec(open('/home/claude/harness/gl_test5.py').read())
import math
fit=min(W/H,1.0); pitch=-0.12; hz=0.5+fit*1.15*math.tan(pitch); sun=(0.5,hz+fit*0.2)
res=scene(terrain(1,0,0.8,1.3,0.75,pitch,0.9,hue=0.2),0.5,200,soft=0.1)
base=dict(dofCam=(0.75,pitch,2.8),sun=sun,reflY=hz)
def m(name,cfg,beat=0.3): 
    a=post(res,"x_"+name,dict(base,beat=beat,**cfg)); return round(float(a.mean()),1)
print("none",m("n",{}))
for k,v in (("streak",0.5),("flare",0.35),("halo",0.25),("god",0.6),("refl",0.6),("dof",0.6),("lut",0.6),("sharp",0.4),("mb",0.6)):
    print(k,m(k,{k:v}), "beat1:",m(k+"b",{k:v},1.0))
