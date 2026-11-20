exec(open('/home/claude/harness/gl_test5.py').read())
import math
fit=min(W/H,1.0); pitch=-0.12
horizon=0.5+fit*1.15*math.tan(pitch); sunuv=(0.5,0.5+fit*(1.15*math.tan(pitch)+0.2))
print("horizon",horizon,"sun",sunuv)
res=scene(terrain(1,0,0.8,1.3,0.75,pitch,0.9,hue=0.2),0.5,200,soft=0.1)
a=post(res,"f_synth_base",{})
b=post(res,"f_synth_full",dict(beat=0.6,streak=0.6,flare=0.5,halo=0.5,dof=0.8,dofCam=(0.75,pitch,2.8),god=0.7,sun=sunuv,refl=0.8,reflY=horizon,lut=0.7,sharp=0.5))
c=post(res,"f_synth_god",dict(beat=0.6,god=0.9,sun=sunuv))
d=post(res,"f_synth_refl",dict(refl=0.9,reflY=horizon))
Image.fromarray(np.concatenate([a,b,c,d],1)).resize((W*2,H//2)).save("/home/claude/harness/sheet7.png")
print("means",a.mean(),b.mean(),c.mean(),d.mean())
