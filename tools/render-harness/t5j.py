exec(open('/home/claude/harness/gl_test5.py').read())
import math
D=dict(taa=1,mb=0.6,streak=0.5,flare=0.35,halo=0.25,dof=0.6,god=0.6,refl=0.6,lut=0.6,sharp=0.4,beat=0.5)
def shk(f):
    if f<0: return (0,0,0,0)
    e=math.exp(-f*0.4); return (0.006*math.sin(f*2.1)*e,0.005*math.cos(f*1.6)*e,0.012*math.sin(f*1.3)*e,0.012*e)
cases={}
def bars_s(t,b,a): bars2(style=0,n=56,mirror=0,base=-0.85,height=1.45,inten=0.4); bars2(style=0,n=56,mirror=0,base=-0.85,height=1.45,cap=1,inten=0.9)
cases["bars"]=(bars_s,0.62,{})
cases["valleyridge"]=(ridge_draw,0.0,{})
cases["ribbon"]=(lambda t,b,a: bars2(style=1,n=512,base=-0.15,height=1.0,fill=0.25,edge=0.012,halfw=(W/H)*1.0,inten=0.5,spread=1.2,hue=0.25),0.82,dict(soft=0.3))
cases["spike"]=(lambda t,b,a: bars2(style=3,n=180,base=0.8,height=0.7,gap=0.35,tilt=0.38,cy=-0.1,inten=0.5,spread=1.0,hue=0.0,rot=t*0.1),0.6,dict(soft=0.05))
cases["dots"]=(terrain(0,1,1.0,3.0,1.3,0.45,1.4,line=2.2),0.55,dict(soft=0.1))
cases["ripple"]=(terrain(2,0,0.5,2.2,2.2,0.6,0.9),0.5,dict(soft=0.05))
imgs=[];ok=True
for n,(fn,dec,kw) in cases.items():
    res=scene(fn,dec,160,**kw)
    img=post(res,"r_"+n,dict(D,dofCam=(1.3,0.45,3.4)),frames=10,shakes=shk)
    mean=float(img.mean()); finite=np.isfinite(img).all(); mx=int(img.max())
    status = "OK" if (5<mean<150 and mx>100) else "CHECK"
    print(n,"mean",round(mean,1),"max",mx,status); imgs.append(img)
Image.fromarray(np.concatenate(imgs,1)).resize((int(W*3),int(H/2))).save("/home/claude/harness/sheet13.png")
