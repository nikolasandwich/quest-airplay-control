package com.localair.airplay;

import java.util.ArrayList;
import java.util.List;

/** Arrow-shape candidates for 720px video frames. Scores are not identity probabilities. */
public final class ArrowPointerDetector {
    public static final class Candidate {
        public final int x,y,width,height;
        public final double score;
        Candidate(int x,int y,int w,int h,double score){this.x=x;this.y=y;width=w;height=h;this.score=score;}
    }
    private static final String[][] SHAPES={
        {"##.....","###....","####...","#####..","######.","#######","#######","####...","###....","#......"},
        {".#.....","###....","####...","####...","#####..","#######","#######","#######","###....",".#....."},
        {".#.....","###....","####...","####...","####...","######.","######.","#######","###....","##....."},
        {"##.....","###....","####...","####...","#####..","######.","#######","######.","###....","##....."}
    };
    public List<Candidate> detect(int[] pixels,int width,int height) {
        if(width<20||height<20||width>720||height>720||pixels.length!=width*height)throw new IllegalArgumentException();
        int n=pixels.length,stride=width+1;
        int[] gray=new int[n],sum=new int[(width+1)*(height+1)];
        for(int y=0;y<height;y++) {
            int row=0;
            for(int x=0;x<width;x++) {
                int p=pixels[y*width+x];
                int value=(299*((p>>16)&255)+587*((p>>8)&255)+114*(p&255)+500)/1000;
                gray[y*width+x]=value;row+=value;sum[(y+1)*stride+x+1]=sum[y*stride+x+1]+row;
            }
        }
        byte[] polarity=new byte[n];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int l=Math.max(0,x-8),r=Math.min(width,x+9),t=Math.max(0,y-8),b=Math.min(height,y+9);
            int area=(r-l)*(b-t),local=sum[b*stride+r]-sum[t*stride+r]-sum[b*stride+l]+sum[t*stride+l];
            int contrast=gray[y*width+x]*area-local;
            polarity[y*width+x]=(byte)(contrast>18*area?1:contrast< -18*area?-1:0);
        }
        boolean[] seen=new boolean[n];int[] queue=new int[n],points=new int[120];
        List<Candidate> candidates=new ArrayList<>();
        for(int seed=0;seed<n;seed++) {
            if(seen[seed]||polarity[seed]==0)continue;
            int head=0,tail=1;queue[0]=seed;seen[seed]=true;
            int left=width,right=0,top=height,bottom=0;
            while(head<tail) {
                int at=queue[head++],x=at%width,y=at/width;
                if(head<=120)points[head-1]=at;
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
                for(int ny=Math.max(0,y-1);ny<Math.min(height,y+2);ny++)for(int nx=Math.max(0,x-1);nx<Math.min(width,x+2);nx++) {
                    int next=ny*width+nx;
                    if(!seen[next]&&polarity[next]==polarity[seed]){seen[next]=true;queue[tail++]=next;}
                }
            }
            int w=right-left+1,h=bottom-top+1;
            if(tail<20||tail>120||w<5||w>11||h<7||h>15||w/(double)h<.45||w/(double)h>.95||left<2||top<2||right>=width-2||bottom>=height-2)continue;
            boolean[] mask=new boolean[w*h];
            for(int i=0;i<tail;i++)mask[(points[i]/width-top)*w+points[i]%width-left]=true;
            double score=0;
            for(String[] shape:SHAPES) {
                int overlap=0,union=0;
                for(int y=0;y<10;y++)for(int x=0;x<7;x++) {
                    int sx=Math.min(w-1,(int)((x+.5)*w/7)),sy=Math.min(h-1,(int)((y+.5)*h/10));
                    boolean a=mask[sy*w+sx],b=shape[y].charAt(x)=='#';
                    if(a&&b)overlap++;if(a||b)union++;
                }
                score=Math.max(score,overlap/(double)union);
            }
            if(score>=.80)candidates.add(new Candidate(left,top,w,h,score));
        }
        candidates.sort((a,b)->Double.compare(b.score,a.score));
        return candidates;
    }
}
