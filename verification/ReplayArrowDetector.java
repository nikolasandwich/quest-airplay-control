import com.localair.airplay.ArrowPointerDetector;
import com.localair.airplay.PointerIdentityTracker;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Offline replay of the actual runtime detector. Does not access Android or HID. */
public class ReplayArrowDetector {
    public static void main(String[] args) throws Exception {
        ArrowPointerDetector detector=new ArrowPointerDetector();
        PointerIdentityTracker tracker=new PointerIdentityTracker();
        var frameTimes=Files.readAllLines(Path.of(args[0],"frames.csv"));
        var rayLines=Files.readAllLines(Path.of(args[0],"rays.csv"));
        int trusted=0;
        int single=0,empty=0,multiple=0,total=0;
        try(var files=Files.list(Path.of(args[0]))) {
            for(Path p:files.filter(f->f.getFileName().toString().matches("frame-.*\\.png")).sorted().toList()) {
                var image=ImageIO.read(p.toFile());int w=image.getWidth(),h=image.getHeight();
                var found=detector.detect(image.getRGB(0,0,w,h,null,0,w),w,h);total++;
                int index=Integer.parseInt(p.getFileName().toString().substring(6,8));
                long time=Long.parseLong(frameTimes.get(index+1).split(",")[1]);
                double rx=Double.NaN,ry=Double.NaN;
                for(String line:rayLines.subList(1,rayLines.size())) {
                    var v=line.split(",");if(Long.parseLong(v[0])>time)break;
                    double scale=Math.min(1,720/Math.max(Double.parseDouble(v[3]),Double.parseDouble(v[4])));
                    rx=Double.parseDouble(v[1])*scale;ry=Double.parseDouble(v[2])*scale;
                }
                boolean locked=tracker.update(found,rx,ry,time);if(locked)trusted++;
                if(found.size()==1)single++;else if(found.isEmpty())empty++;else multiple++;
                System.out.print(p.getFileName()+","+found.size()+",locked="+locked);
                for(var c:found)System.out.printf(Locale.ROOT,",%d:%d:%.4f",c.x,c.y,c.score);
                System.out.println();
            }
        }
        System.out.printf("total=%d single=%d empty=%d ambiguous=%d trusted=%d%n",total,single,empty,multiple,trusted);
    }
}
