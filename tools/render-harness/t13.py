exec(open('/home/claude/harness/t8.py').read().split('cfg={')[0])
gp=Prog("glass3d.vert","glass3d.frag")
def draw(n=26,rings=2,rot=0.3,tilt=0.42,dist=3.4,R=1.0,amp=1.0,bw=0.55):
    begin(); gl.glBlendFunc(1,1); gl.glDisable(0xBE2)
    p=gp; gl.glUseProgram(p.id); p.u1i("uAudio",1); pal_unis(p)
    p.u1i("uN",n); p.u1i("uRings",rings)
    for k,v in dict(uFit=min(W/H,1.0)**0.55,uAspect=W/H,uRot=rot,uTilt=tilt,uDist=dist,uCy=0.0,uFocal=1.7,uR=0.8,uAmp=amp,uPow=1.1,uBeat=0.3,uBW=bw,uSpin=0.0,uHueLevel=0.9,uSpread=1.0,uPhase=0.0,uIntensity=1.0,uCore=1.0).items(): p.u1f(k,v)
    gl.glDrawArraysInstanced(4,0,36,n*rings*2+120)
shot("v13_glass",draw)
