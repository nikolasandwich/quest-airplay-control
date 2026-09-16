"""Experimental per-frame arrow candidates; handles dark/light pointers.
The learned silhouette is from the user's 720-pixel video observation.
No device access or input generation. Shape similarity alone is not identity.
"""
import argparse,json
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
from track_pointer_sequence import components

SHAPE=np.array([[c=='#' for c in row] for row in
    ['##.....','###....','####...','#####..','######.','#######','#######','####...','###....','#......']])

def detect(image):
    gray=np.asarray(image.convert('L'),dtype=np.float32)
    h,w=gray.shape
    padded=np.pad(gray,8,mode='edge')
    integral=np.pad(padded,((1,0),(1,0))).cumsum(0).cumsum(1)
    average=(integral[17:,17:]-integral[:-17,17:]-integral[17:,:-17]+integral[:-17,:-17])/289
    found=[]
    for sign in (-1,1):
        difference=(gray-average)*sign
        for points in components(difference>18):
            if not 20<=len(points)<=120: continue
            xs,ys=zip(*points); left,right,top,bottom=min(xs),max(xs),min(ys),max(ys)
            bw,bh=right-left+1,bottom-top+1
            if not (5<=bw<=11 and 7<=bh<=15 and .45<=bw/bh<=.95): continue
            if left<2 or top<2 or right>=w-2 or bottom>=h-2: continue
            mask=np.zeros((bh,bw),dtype=np.uint8)
            mask[np.array(ys)-top,np.array(xs)-left]=255
            resized=np.asarray(Image.fromarray(mask).resize((7,10),Image.Resampling.NEAREST))>0
            score=float((resized&SHAPE).sum()/(resized|SHAPE).sum())
            if score<.70: continue
            found.append(dict(x=left,y=top,width=bw,height=bh,shape_score=round(score,4),polarity=sign))
    return sorted(found,key=lambda c:-c['shape_score'])

def main():
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
    a.output.mkdir(parents=True,exist_ok=True); result=[]
    for file in sorted(a.source.glob('frame-*.png')):
        image=Image.open(file).convert('RGB'); found=detect(image);result.append(dict(frame=file.name,candidates=found))
        draw=ImageDraw.Draw(image)
        for c in found: draw.rectangle((c['x']-2,c['y']-2,c['x']+c['width']+2,c['y']+c['height']+2),outline='red')
        image.save(a.output/file.name)
    (a.output/'matches.json').write_text(json.dumps(result,indent=2))
    print(json.dumps({'frames':len(result),'single':sum(len(x['candidates'])==1 for x in result),'empty':sum(not x['candidates'] for x in result),'ambiguous':sum(len(x['candidates'])>1 for x in result)}))
if __name__=='__main__': main()
