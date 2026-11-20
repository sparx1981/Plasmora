import sys
exec(open('/home/claude/harness/t8.py').read().split('cfg={')[0])
tr=Prog("trails3d.vert","trails3d.frag")
def draw_tr(kind=0,n=22,seg=7,st=1,rot=0.4,tilt=0.35,dist=3.0,cy=0.0,size=1.0,width=0.012,amp=1.0,speed=0.3,gap=0.03,inten=1.0,spread=1.0,core=1.0,t=3.0,tumble=0.0):
    begin(); gl.glBlendFunc(1,1); gl.glDisable(0xBE2)
    p=tr; gl.glUseProgram(p.id); p.u1i("uAudio",1); pal_unis(p)
    for k,v in dict(uN=n,uSeg=seg,uStr=st,uKind=kind).items(): p.u1i(k,v)
    for k,v in dict(uFit=min(W/H,1.0)**0.55,uAspect=W/H,uRot=rot,uTilt=tilt,uDist=dist,uCy=cy,uFocal=1.7,uTime=t,uSpeed=speed,uSize=size,uWidth=width,uAmp=amp,uBeat=0.3,uBass=0.4,uGap=gap,uSpread=spread,uPhase=0.0,uTumble=tumble,uIntensity=inten,uCore=core).items(): p.u1f(k,v)
    gl.glDrawArraysInstanced(4,0,6,n*seg*st)
shot("v11_circ",draw_tr)
shot("v11_bund",draw_tr,kind=1,n=10,seg=6,st=6,gap=0.025,width=0.006,rot=0.7)
shot("v11_rib",draw_tr,kind=2,n=7,seg=26,st=1,width=0.014,size=0.9)
full("smokebars.frag","v11_sbar",uScale=0.55,uP1=30,uAmp=1.0,uCy=-0.2,uP2=0.8,uP3=1.0,uCore=1.0,uSpread=0.8,uWidth=0.25)
full("smokebloom.frag","v11_bloom",uScale=0.07,uP1=7,uAmp=1.0,uP2=0.7,uP3=0.6,uCore=1.0,uSpread=1.0)
