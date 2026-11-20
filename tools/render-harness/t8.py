import sys
exec(open('/home/claude/harness/t7.py').read().split('if __name__')[0])
def full(frag, name, t=3.0, beat=0.4, **k):
    p=Prog("fullscreen.vert",frag)
    def fn():
        begin(); gl.glBlendFunc(1,1); gl.glDisable(0xBE2)
        gl.glUseProgram(p.id); p.u1i("uAudio",1); p.u1i("uHist",3)
        base=dict(uZoom=1.12/min(W/H,1.0)**0.75,uAspect=W/H,uTime=t,uBeat=beat,uBass=0.5,uKick=0.3,uIntensity=1.0,uPhase=0.0,uSpread=1.0,uAmp=1.0,uScale=1.0,uWidth=0.01,uRot=0.0,uP1=0,uP2=0,uP3=0,uCore=0.5,uCy=0.0)
        base.update(k)
        for n,v in base.items(): p.u1f(n,float(v))
        pal_unis(p)
        gl.glDrawArrays(4,0,3)
    shot(name,fn)
cfg={
 'hex':dict(frag="hex.frag",uScale=7,uP1=0,uP2=0.1,uP3=1.0),
 'hex2':dict(frag="hex.frag",uScale=12,uP1=1,uP2=0.0,uP3=0.6),
 'frames':dict(frag="frames.frag",uScale=0.6,uP1=12,uP2=1.1,uP3=t_ if (t_:=2.0) else 0,uCy=0.05,uCore=0.6,uSpread=0.6),
 'maze':dict(frag="maze.frag",uScale=2.0,uP1=4,uP2=0.12,uP3=1.0,uSpread=0.7),
 'fire':dict(frag="firewave.frag",uScale=1.0,uP1=18,uP2=2.5,uP3=1.0,uWidth=0.006,uCore=1.0,uSpread=0.5),
 'vortex':dict(frag="vortex.frag",uScale=0.30,uAmp=1.0,uP1=128,uP2=1.0,uP3=0.8,uWidth=0.004,uCore=1.0,uSpread=1.0),
 'tfloor':dict(frag="tilefloor.frag",uScale=0.6,uP1=1.0,uP2=40,uP3=2.0,uCy=0.05,uWidth=0.02,uCore=1.0,uSpread=0.8),
}
for k in (sys.argv[1:] or cfg):
    c=dict(cfg[k]); f=c.pop('frag'); full(f,"v8_"+k,**c)
