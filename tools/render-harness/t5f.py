exec(open('/home/claude/harness/gl_test5.py').read())
import math
pals={"Spectrum":[0.5,0.5,0.5,0.5,0.5,0.5,1,1,1,0.0,0.33,0.67],
      "Synthwave":[0.74,0.40,0.54,0.49,0.53,0.28,1,1,1,0.50,0.36,-0.21],
      "Inferno":[0.5,0.35,0.3,0.5,0.4,0.3,1,0.7,0.4,0.0,0.15,0.2],
      "Cyan Glow":[0.19,0.59,0.83,0.36,0.46,0.28,1,1,1,0.33,0.43,0.48]}
def grideq(t,b,a):
    bars2(style=0,n=44,mirror=1,base=-0.12,height=0.8,gap=0.12,seg=0.045,segGap=0.28,refl=0.30,sym=1,inten=0.55*(1+0.2*b),spread=1.0,hue=0.9,halfw=(W/H)*0.96)
res=scene(grideq,0.6,160,soft=0.05)
row0=[];row1=[]
for n,pv in pals.items():
    upload_lut(pv)
    row0.append(post(res,"f_lut0_"+n.replace(' ','_'),dict(lut=0.0)))
    row1.append(post(res,"f_lut1_"+n.replace(' ','_'),dict(lut=1.0)))
Image.fromarray(np.concatenate([np.concatenate(row0,1),np.concatenate(row1,1)],0)).resize((W*2,H//1)).save("/home/claude/harness/sheet11.png")
print("lut diffs",[round(float(np.abs(a.astype(float)-b.astype(float)).mean()),2) for a,b in zip(row0,row1)])
