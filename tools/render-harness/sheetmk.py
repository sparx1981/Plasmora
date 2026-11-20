import sys
from PIL import Image
names=sys.argv[2:]; out=sys.argv[1]
ims=[Image.open(f"/home/claude/harness/{x}.png") for x in names]
w,h=ims[0].size
cols=min(3,len(ims)); rows=(len(ims)+cols-1)//cols
sh=Image.new("RGB",(w*cols,h*rows))
for i,im in enumerate(ims): sh.paste(im,((i%cols)*w,(i//cols)*h))
s=0.5
sh.resize((int(w*cols*s),int(h*rows*s))).save(out)
