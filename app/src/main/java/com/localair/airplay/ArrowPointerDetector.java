package com.localair.airplay;

import java.util.ArrayList;
import java.util.List;

/** Arrow-shape candidates for 720px video frames. Scores are not identity probabilities. */
public final class ArrowPointerDetector {
    // Owned by the single sampling worker. Reuse full-frame scratch buffers to
    // avoid several megabytes of temporary allocations on every observation.
    private int[] gray=new int[0],sum=new int[0],queue=new int[0];
    private byte[] polarity=new byte[0];
    private boolean[] seen=new boolean[0];
    private final int[] points=new int[1024];
    public static final class Candidate {
        public final int x,y,width,height;
        public final double score;
        public final boolean circular;
        Candidate(int x,int y,int w,int h,double score){this(x,y,w,h,score,false);}
        Candidate(int x,int y,int w,int h,double score,boolean circular){this.x=x;this.y=y;width=w;height=h;this.score=score;this.circular=circular;}
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
        if(gray.length!=n){gray=new int[n];queue=new int[n];polarity=new byte[n];seen=new boolean[n];}
        if(sum.length!=(width+1)*(height+1))sum=new int[(width+1)*(height+1)];
        else java.util.Arrays.fill(sum,0);
        java.util.Arrays.fill(seen,false);
        for(int y=0;y<height;y++) {
            int row=0;
            for(int x=0;x<width;x++) {
                int p=pixels[y*width+x];
                int value=(299*((p>>16)&255)+587*((p>>8)&255)+114*(p&255)+500)/1000;
                gray[y*width+x]=value;row+=value;sum[(y+1)*stride+x+1]=sum[y*stride+x+1]+row;
            }
        }
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int l=Math.max(0,x-8),r=Math.min(width,x+9),t=Math.max(0,y-8),b=Math.min(height,y+9);
            int area=(r-l)*(b-t),local=sum[b*stride+r]-sum[t*stride+r]-sum[b*stride+l]+sum[t*stride+l];
            int contrast=gray[y*width+x]*area-local;
            polarity[y*width+x]=(byte)(contrast>18*area?1:contrast< -18*area?-1:0);
        }
        List<Candidate> candidates=new ArrayList<>();
        for(int seed=0;seed<n;seed++) {
            if(seen[seed]||polarity[seed]==0)continue;
            int head=0,tail=1;queue[0]=seed;seen[seed]=true;
            int left=width,right=0,top=height,bottom=0;
            while(head<tail) {
                int at=queue[head++],x=at%width,y=at/width;
                if(head<=points.length)points[head-1]=at;
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
                for(int ny=Math.max(0,y-1);ny<Math.min(height,y+2);ny++)for(int nx=Math.max(0,x-1);nx<Math.min(width,x+2);nx++) {
                    int next=ny*width+nx;
                    if(!seen[next]&&polarity[next]==polarity[seed]){seen[next]=true;queue[tail++]=next;}
                }
            }
            int w=right-left+1,h=bottom-top+1;
            if(tail<20||tail>points.length||left<2||top<2||right>=width-2||bottom>=height-2)continue;
            boolean circleSize=w>=6&&h>=6&&w<=28&&h<=28&&Math.abs(w-h)<=2;
            boolean arrowSize=tail<=120&&w>=5&&w<=11&&h>=7&&h<=15&&w/(double)h>=.45&&w/(double)h<=.95;
            if(!circleSize&&!arrowSize)continue;
            boolean[] mask=new boolean[w*h];
            for(int i=0;i<tail;i++)mask[(points[i]/width-top)*w+points[i]%width-left]=true;
            if(circleSize){
                double circleScore=0;
                for(double inner:new double[]{0,.35,.5,.65,.8}){
                    int overlap=0,union=0;
                    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                        double nx=(x+.5-w/2.0)/(w/2.0),ny=(y+.5-h/2.0)/(h/2.0);
                        double radius=nx*nx+ny*ny;
                        boolean expected=radius<=1&&radius>=inner*inner,actual=mask[y*w+x];
                        if(expected&&actual)overlap++;if(expected||actual)union++;
                    }
                    circleScore=Math.max(circleScore,overlap/(double)Math.max(1,union));
                }
                if(circleScore>=.90){
                    candidates.add(new Candidate(left+w/2,top+h/2,w,h,circleScore,true));
                    continue;
                }
            }
            if(!arrowSize)continue;
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
        // Light and dark outlines of one ring may form separate components.
        List<Candidate> unique=new ArrayList<>();
        for(Candidate c:candidates){
            boolean duplicate=false;
            for(Candidate other:unique)if(c.circular&&other.circular&&Math.hypot(c.x-other.x,c.y-other.y)<=2&&Math.abs(c.width-other.width)<=6){duplicate=true;break;}
            if(!duplicate)unique.add(c);
        }
        return unique;
    }
}
