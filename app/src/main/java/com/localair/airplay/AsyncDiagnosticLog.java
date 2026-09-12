package com.localair.airplay;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Bounded, non-blocking producer. Only its worker opens or rotates files. */
public final class AsyncDiagnosticLog implements AutoCloseable {
    private final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(512);
    private final AtomicLong dropped=new AtomicLong();
    private final Thread worker;
    private volatile boolean accepting=true;
    public AsyncDiagnosticLog(File file,Consumer<String> failure){
        worker=new Thread(() -> {
            ArrayList<String> batch=new ArrayList<>(64);
            while(accepting||!queue.isEmpty()){
                try {
                    String first=queue.poll(100,TimeUnit.MILLISECONDS);if(first==null)continue;
                    batch.clear();batch.add(first);queue.drainTo(batch,63);
                    long lost=dropped.getAndSet(0);if(lost>0)batch.add("diagnostic queue dropped="+lost);
                    if(file.length()>262144)Files.move(file.toPath(),new File(file.getParentFile(),"hid-diagnostic.previous.log").toPath(),StandardCopyOption.REPLACE_EXISTING);
                    try(FileWriter writer=new FileWriter(file,true)){for(String line:batch){writer.write(line);writer.write('\n');}}
                }catch(InterruptedException ignored){}catch(IOException e){failure.accept("Diagnostic file unavailable: "+e.getClass().getSimpleName());}
            }
        },"HidDiagnosticWriter");worker.setDaemon(true);worker.start();
    }
    public synchronized void append(String text){if(accepting&&!queue.offer(text))dropped.incrementAndGet();}
    public int queued(){return queue.size();}
    public boolean awaitClosed(long millis)throws InterruptedException{worker.join(millis);return !worker.isAlive();}
    @Override public synchronized void close(){accepting=false;}
}
