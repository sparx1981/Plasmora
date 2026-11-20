exec(open('/home/claude/harness/gl_test5.py').read())
import math
res=scene(terrain(0,0,1.0,2.2,1.3,0.45,0.9),0.45,200,soft=0.05)
def shk(f):
    if f<0: return (0,0,0,0)
    e=math.exp(-f*0.35)
    return (0.004*math.sin(f*2.3)*e, 0.003*math.cos(f*1.7)*e, 0.01*math.sin(f*1.1)*e, 0.01*e)
off=post(res,"f_t_off",dict(),frames=1)
on_static=post(res,"f_t_taa_static",dict(taa=1),frames=12)
on_shake=post(res,"f_t_taa_shake",dict(taa=1),frames=12,shakes=shk)
nt_shake=post(res,"f_t_notaa_shake",dict(),frames=1,shakes=lambda f: shk(11) if f>=0 else shk(10))
def diff(a,b): return float(np.abs(a.astype(float)-b.astype(float)).mean())
print("static taa vs off mean abs diff",diff(off,on_static))
print("shake taa vs no-taa-shake",diff(on_shake,nt_shake))
# edge softness: gradient magnitude mean (aliasing/ghost indicator)
def grad(a): g=a.astype(float).mean(-1); return float(np.abs(np.diff(g,axis=0)).mean()+np.abs(np.diff(g,axis=1)).mean())
print("grad off/static/shake",grad(off),grad(on_static),grad(on_shake))
crop=lambda a:a[300:500,120:420]
Image.fromarray(np.concatenate([crop(off),crop(on_static),crop(on_shake)],1)).resize((1350,300),Image.NEAREST).save("/home/claude/harness/sheet10.png")
