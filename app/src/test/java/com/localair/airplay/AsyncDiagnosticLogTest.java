package com.localair.airplay;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
public class AsyncDiagnosticLogTest {
    @Test public void closesAfterFlushingAndRotatesOffProducerThread()throws Exception{
        Path dir=Files.createTempDirectory("hid-log-test"),file=dir.resolve("hid-diagnostic.log");
        Files.write(file,new byte[262145]);
        AsyncDiagnosticLog log=new AsyncDiagnosticLog(file.toFile(),message -> {throw new AssertionError(message);});
        log.append("confirmed event");log.close();assertTrue(log.awaitClosed(5000));
        assertTrue(new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8).contains("confirmed event"));assertTrue(Files.exists(dir.resolve("hid-diagnostic.previous.log")));
    }
    @Test public void producerQueueHasFixedCapacity()throws Exception{
        Path dir=Files.createTempDirectory("hid-log-burst");
        AsyncDiagnosticLog log=new AsyncDiagnosticLog(dir.resolve("hid-diagnostic.log").toFile(),message -> {});
        for(int i=0;i<10000;i++){log.append("event "+i);assertTrue(log.queued()<=512);}
        log.close();assertTrue(log.awaitClosed(5000));
    }
}
