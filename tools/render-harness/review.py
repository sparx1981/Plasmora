import sys, re, math
exec(open('/home/claude/harness/t8.py').read().split('cfg={')[0])
from PIL import Image
FR={24:"hex.frag",25:"frames.frag",26:"maze.frag",28:"vortex.frag",29:"tilefloor.frag",31:"pulse.frag",32:"polytunnel.frag",33:"smoke.frag",35:"smokebars.frag",37:"smokebloom.frag",38:"smokering.frag",39:"dotfloor.frag",40:"ledtunnel.frag",41:"ringbars.frag",42:"dotbars.frag",43:"horizon.frag"}
src=open('/home/claude/plasmora/app/src/main/java/app/plasmora/gl/Presets.kt').read()
body=src[src.index('val all: List<Preset>'):src.index('/** Browser categories')]
parts=re.split(r'(?=Preset\(")',body)[1:]
class A: beat=0.3; bass=0.5; mid=0.4; treb=0.3; level=0.5; kick=0.3
env=dict(mt=3.0,a=A,s=math.sin,c=math.cos,sin=math.sin,cos=math.cos,abs=abs,min=min,max=max)
names=[]
for part in parts:
    nm=re.match(r'Preset\("([^"]+)"',part).group(1)
    m=re.search(r'l\.mode = (\d+)',part)
    if not m or int(m.group(1)) not in FR: continue
    mode=int(m.group(1))
    lay=part[part.index('layers'):]
    vals={}
    for k,e in re.findall(r'l\.(\w+) = ([^;\n}]+)',lay):
        if k in vals: continue
        e=re.sub(r'(\d)f\b',r'\1',e).replace('true','1').replace('false','0')
        try: vals[k]=float(eval(e,env))
        except Exception as ex: vals[k]=0.0
    u=dict(uAmp=vals.get('amp',1),uScale=vals.get('scale',1),uWidth=vals.get('width',0.01),uRot=vals.get('rot',0),uP1=vals.get('p1',0),uP2=vals.get('p2',0),uP3=vals.get('p3',0),uPhase=0.0,uSpread=vals.get('spread',1),uCore=vals.get('core',0.5),uCy=vals.get('cy',0))
    fn="rv_"+re.sub(r'\W','_',nm)
    full(FR[mode],fn,**u); names.append((nm,fn))
cols=8; tw,th=(216,384) if W<H else (384,216)
rows=(len(names)+cols-1)//cols if W<H else (len(names)+3)//4
if W>=H: cols=4
sh=Image.new('RGB',(cols*tw,rows*th))
for i,(nm,fn) in enumerate(names): sh.paste(Image.open(fn+'.png').resize((tw,th)),((i%cols)*tw,(i//cols)*th))
sh.save(sys.argv[1] if len(sys.argv)>1 else 'review.png'); print([n for n,_ in names])
