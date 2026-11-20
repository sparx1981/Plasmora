import sys
sys.argv=['x']
exec(open('/home/claude/harness/gl_test2.py').read())
tunnelP=Prog("fullscreen.vert","tunnel.frag"); partP=Prog("particles.vert","particles.frag")
print("pack shaders compiled OK")
def tunnel_draw(sides, ribs, twist, inten):
    def f(t,beat,acc):
        p=tunnelP; gl.glUseProgram(p.id); p.u1i("uAudio",1); p.u1f("uAspect",W/H); p.u1f("uSides",sides); p.u1f("uRibs",ribs)
        p.u1f("uTravel",t*0.6); p.u1f("uTwist",twist); p.u1f("uBeat",beat); p.u1f("uBass",beat*0.5); p.u1f("uIntensity",inten*(1+0.25*beat))
        p.u1f("uPhase",t*0.02); p.u1f("uSpread",1.0); pal_unis(p); gl.glDrawArrays(4,0,3)
    return f
def part_draw(mode, n, arms, swirl, size, inten, scale):
    def f(t,beat,acc):
        p=partP; gl.glUseProgram(p.id); p.u1i("uAudio",1); p.u1i("uMode",mode); p.u1f("uAspect",W/H); p.u1f("uTravel",t*1.0)
        p.u1f("uSize",size*H); p.u1f("uScale",scale); p.u1f("uArms",arms); p.u1f("uSwirl",swirl); p.u1f("uBass",beat*0.5); p.u1f("uBeat",beat)
        p.u1f("uPhase",t*0.02); p.u1f("uSpread",0.9); p.u1f("uIntensity",inten); pal_unis(p); gl.glDrawArrays(0,0,n)
    return f
which=sys.argv_orig if hasattr(sys,'argv_orig') else None
import os
F=int(os.environ.get("FRAMES",240))
run2("p_tunnel", tunnel_draw(0,2.6,0.5,0.55), 0.55, F, soft=0.12)
run2("p_hex", tunnel_draw(6,2.2,-1.0,0.34), 0.86, F, zoom=1.012, soft=0.2)
run2("p_galaxy", part_draw(0,26000,3,5.2,0.0042,0.045,0.92), 0.86, F, soft=0.3)
run2("p_star", part_draw(1,22000,3,0.6,0.0038,0.06,0.85), 0.7, F, zoom=1.006, soft=0.1)
run2("p_fountain", part_draw(2,20000,3,0,0.0035,0.04,0.8), 0.88, F, soft=0.35)
def radial(t,beat,acc):
    bars_draw(style=2,n=120,base=0.27,height=0.5,gap=0.35,inten=0.42*(1+0.25*beat)); bars_draw(style=2,n=120,base=0.27,height=0.5,gap=0.35,cap=1,inten=0.9)
run2("p_radial", radial, 0.74, F, zoom=1.004, soft=0.12)
