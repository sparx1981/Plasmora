import sys
exec(open('/home/claude/harness/t8.py').read().split('cfg={')[0])
cfg={
 'sring':dict(frag="smokering.frag",uScale=0.17,uP1=90,uAmp=0.7,uWidth=0.55,uP2=0.8,uP3=1.2,uCore=1.0,uSpread=0.8),
 'dfloor':dict(frag="dotfloor.frag",uScale=0.1,uP1=0.8,uAmp=1.0,uP2=0.5,uP3=2.2,uSpread=0.9,uCore=1.0),
 'ltun':dict(frag="ledtunnel.frag",uScale=0.55,uP1=72,uP2=0.8,uP3=6.0,uAmp=1.0,uSpread=0.6,uCore=1.0),
 'rbars':dict(frag="ringbars.frag",uScale=0.22,uP1=5,uAmp=0.5,uWidth=0.62,uP2=1.0,uP3=1.0,uCore=1.0,uSpread=1.0,uCy=0.12),
 'dbars':dict(frag="dotbars.frag",uScale=0.95,uP1=34,uAmp=1.0,uP2=1,uP3=1.0,uCore=1.0,uSpread=1.0,uCy=-0.15),
 'hori':dict(frag="horizon.frag",uScale=0.7,uP1=220,uAmp=1.0,uP2=0.5,uP3=1.0,uCore=1.0,uSpread=0.6,uCy=-0.1),
}
for k in (sys.argv[1:] or cfg):
    c=dict(cfg[k]); f=c.pop('frag'); full(f,"v12_"+k,**c)
