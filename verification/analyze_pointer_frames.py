"""Offline shape candidates only. A dark circle is not proof of cursor identity.
Usage: python analyze_pointer_frames.py <exported run directory> <output directory>
Requires Pillow and numpy. Never connects to a device or sends input.
"""
import argparse
import csv
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw


def candidates(image):
    gray = np.asarray(image.convert('L'))
    height, width = gray.shape
    found = []
    for threshold in (80, 120, 160):
        mask = gray < threshold
        seen = np.zeros_like(mask)
        for sy, sx in zip(*np.nonzero(mask)):
            if seen[sy, sx]:
                continue
            stack = [(int(sx), int(sy))]
            seen[sy, sx] = True
            points = []
            while stack:
                x, y = stack.pop()
                points.append((x, y))
                for nx, ny in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                    if 0 <= nx < width and 0 <= ny < height and mask[ny,nx] and not seen[ny,nx]:
                        seen[ny,nx] = True
                        stack.append((nx,ny))
            if not 12 <= len(points) <= 1600:
                continue
            xs, ys = zip(*points)
            left, right, top, bottom = min(xs), max(xs), min(ys), max(ys)
            w, h = right-left+1, bottom-top+1
            if not (4 <= w <= 44 and 4 <= h <= 44 and .75 <= w/h <= 1.33):
                continue
            if left < 2 or top < 2 or right >= width-2 or bottom >= height-2:
                continue
            shape = np.zeros((h,w), dtype=bool)
            shape[np.array(ys)-top, np.array(xs)-left] = True
            yy, xx = np.mgrid[:h,:w]
            disk = ((xx-(w-1)/2)/(w/2))**2 + ((yy-(h-1)/2)/(h/2))**2 <= 1
            iou = float(np.logical_and(shape,disk).sum()/np.logical_or(shape,disk).sum())
            if iou < .86:
                continue
            patch = gray[top-2:bottom+3,left-2:right+3]
            border = np.concatenate((patch[:2,:].ravel(),patch[-2:,:].ravel(),patch[2:-2,:2].ravel(),patch[2:-2,-2:].ravel()))
            contrast = float(np.median(border)-float(np.median(gray[np.array(ys),np.array(xs)])))
            if contrast < 12:
                continue
            cx, cy = (left+right)/2, (top+bottom)/2
            record = dict(x=cx,y=cy,u=cx/width,v=cy/height,width=w,height=h,
                          shape_score=round(iou,4),contrast=round(contrast,2),threshold=threshold,
                          identity='unverified')
            duplicate = next((i for i,c in enumerate(found) if abs(c['x']-cx)<3 and abs(c['y']-cy)<3),None)
            if duplicate is None:
                found.append(record)
            elif record['shape_score'] > found[duplicate]['shape_score']:
                found[duplicate] = record
    return sorted(found,key=lambda c:-c['shape_score'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source',type=Path)
    parser.add_argument('output',type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    report = []
    for path in sorted(args.source.glob('frame-*.png')):
        with Image.open(path) as source:
            frame = source.convert('RGB')
        detected = candidates(frame)
        report.append(dict(frame=path.name,candidates=detected,
                           state='ambiguous' if len(detected)>1 else 'unverified' if detected else 'no_candidate'))
        draw = ImageDraw.Draw(frame)
        for i,c in enumerate(detected):
            x,y,w,h = (c[k] for k in ('x','y','width','height'))
            draw.rectangle((x-w/2-2,y-h/2-2,x+w/2+2,y+h/2+2),outline='red',width=1)
            draw.text((x+w/2+3,y),str(i),fill='red')
        frame.save(args.output/path.name)
    (args.output/'candidates.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    with (args.output/'candidates.csv').open('w',newline='',encoding='utf-8') as handle:
        fields=['frame','x','y','u','v','width','height','shape_score','contrast','threshold','identity']
        writer=csv.DictWriter(handle,fields); writer.writeheader()
        for item in report:
            for c in item['candidates']:
                writer.writerow(dict(frame=item['frame'],**c))
    print(json.dumps({'frames':len(report),'candidates':sum(len(r['candidates']) for r in report),
                      'verified_cursor_positions':0,'note':'Shape score is not identity confidence. Manual validation required.'}))


if __name__ == '__main__':
    main()
