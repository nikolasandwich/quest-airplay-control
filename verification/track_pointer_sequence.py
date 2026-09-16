"""Offline moving dark-pointer candidates against a temporal median background.
This is a static-scene experiment, not a live cursor identity guarantee.
"""
import argparse
import csv
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw


def components(mask):
    height,width=mask.shape
    seen=np.zeros_like(mask)
    for sy,sx in zip(*np.nonzero(mask)):
        if seen[sy,sx]: continue
        todo=[(int(sx),int(sy))]; seen[sy,sx]=True; points=[]
        while todo:
            x,y=todo.pop(); points.append((x,y))
            for ny in range(max(0,y-1),min(height,y+2)):
                for nx in range(max(0,x-1),min(width,x+2)):
                    if mask[ny,nx] and not seen[ny,nx]:
                        seen[ny,nx]=True; todo.append((nx,ny))
        yield points


def detect(frame,background):
    darkening=(background-frame.astype(np.float32)).mean(axis=2)
    mask=darkening>22
    if mask.mean()>.03: return [],'scene_changed'
    found=[]
    height,width=mask.shape
    for points in components(mask):
        if not 8<=len(points)<=700: continue
        xs,ys=zip(*points); left,right,top,bottom=min(xs),max(xs),min(ys),max(ys)
        w,h=right-left+1,bottom-top+1
        if not (3<=w<=30 and 4<=h<=35): continue
        if left<2 or top<2 or right>=width-2 or bottom>=height-2: continue
        found.append(dict(x=(left+right)/2,y=(top+bottom)/2,left=left,top=top,width=w,height=h,
                          area=len(points),contrast=round(float(darkening[np.array(ys),np.array(xs)].mean()),2)))
    return found,'single_candidate' if len(found)==1 else 'ambiguous' if found else 'no_candidate'


def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('source',type=Path); p.add_argument('output',type=Path); a=p.parse_args()
    paths=sorted(a.source.glob('frame-*.png'))
    frames=[np.asarray(Image.open(f).convert('RGB')) for f in paths]
    background=np.median(np.stack(frames),axis=0).astype(np.float32)
    a.output.mkdir(parents=True,exist_ok=True)
    rows=[]
    for path,frame in zip(paths,frames):
        found,state=detect(frame,background)
        rows.append(dict(frame=path.name,state=state,candidates=found))
        image=Image.fromarray(frame); draw=ImageDraw.Draw(image)
        for c in found:
            draw.rectangle((c['left']-2,c['top']-2,c['left']+c['width']+2,c['top']+c['height']+2),outline='red',width=1)
        image.save(a.output/path.name)
    (a.output/'tracks.json').write_text(json.dumps(rows,indent=2),encoding='utf-8')
    with (a.output/'tracks.csv').open('w',newline='',encoding='utf-8') as out:
        writer=csv.writer(out); writer.writerow(['frame','state','x','y','width','height','area','contrast'])
        for r in rows:
            for c in r['candidates']: writer.writerow([r['frame'],r['state']]+[c[k] for k in ['x','y','width','height','area','contrast']])
    print(json.dumps({'frames':len(rows),'states':{s:sum(r['state']==s for r in rows) for s in ['single_candidate','ambiguous','no_candidate','scene_changed']}}))


if __name__=='__main__': main()
