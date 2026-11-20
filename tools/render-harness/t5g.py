exec(open('/home/claude/harness/gl_test5.py').read())
def grideq(t,b,a):
    bars2(style=0,n=44,mirror=1,base=-0.12,height=0.8,gap=0.12,seg=0.045,segGap=0.28,refl=0.30,sym=1,inten=0.55*(1+0.2*b),spread=1.0,hue=0.9,halfw=(W/H)*0.96)
res=scene(grideq,0.6,100,soft=0.05)
a=post(res,"x0",dict(lut=0.0)); b=post(res,"x1",dict(lut=1.0)); c=post(res,"x2",dict(lut=1.0,sharp=1.0))
print(a.mean(),b.mean(),c.mean())
print(gl.glGetError())
