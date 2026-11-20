exec(open('/home/claude/harness/gl_test5.py').read())
def grideq(t,b,a):
    bars2(style=0,n=44,mirror=1,base=-0.12,height=0.8,gap=0.12,seg=0.045,segGap=0.28,refl=0.30,sym=1,inten=0.55*(1+0.2*b),spread=1.0,hue=0.9,halfw=(W/H)*0.96)
res=scene(grideq,0.6,200,soft=0.05)
row1=[post(res,"f_g_base",{}),post(res,"f_g_streak",dict(streak=0.8,beat=1.0)),post(res,"f_g_flare",dict(flare=0.8,beat=1.0)),post(res,"f_g_halo",dict(halo=0.8))]
row2=[post(res,"f_g_all",dict(streak=0.5,flare=0.4,halo=0.4,beat=1.0,lut=0.6,sharp=0.5)),post(res,"f_g_all_nobeat",dict(streak=0.5,flare=0.4,halo=0.4,beat=0.0,lut=0.6,sharp=0.5)),
      post(res,"f_g_mb",dict(mb=1.0,warp=(0.03,0.01))),post(res,"f_g_sharp",dict(sharp=1.0))]
Image.fromarray(np.concatenate([np.concatenate(row1,1),np.concatenate(row2,1)],0)).resize((W*2,H)).save("/home/claude/harness/sheet8.png")
print([round(x.mean(),1) for x in row1+row2])
