package com.localair.airplay;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LocalBoardHttpServerTest {
    private String get(LocalBoardHttpServer server,String request)throws Exception{
        try(Socket s=new Socket("127.0.0.1",server.port())){s.setSoTimeout(2000);s.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));return new String(s.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}
    }
    @Test public void onlyServesExplicitReadOnlyEndpoints()throws Exception{
        try(LocalBoardHttpServer server=new LocalBoardHttpServer(0,"page".getBytes(),()->"{\"status\":\"ready\"}".getBytes())){
            assertTrue(get(server,"GET /state HTTP/1.1\r\n\r\n").contains("\"ready\""));
            assertTrue(get(server,"POST /state HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 404"));
            assertTrue(get(server,"GET /../../secret HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 404"));
        }
    }
    @Test public void tricklingBytesCannotExtendTotalDeadline()throws Exception{
        try(LocalBoardHttpServer server=new LocalBoardHttpServer(0,new byte[0],()->new byte[0],200);Socket s=new Socket("127.0.0.1",server.port())){
            s.setSoTimeout(1000);long began=System.nanoTime();boolean closed=false;
            for(int i=0;i<10;i++)try{s.getOutputStream().write('G');Thread.sleep(60);}catch(IOException e){closed=true;break;}
            if(!closed)closed=s.getInputStream().read()==-1;
            assertTrue(closed);assertTrue((System.nanoTime()-began)/1_000_000<1000);
        }
    }
    @Test public void closeReleasesQueuedAsWellAsRunningConnections()throws Exception{
        LocalBoardHttpServer server=new LocalBoardHttpServer(0,new byte[0],()->new byte[0]);List<Socket> clients=new ArrayList<>();
        try{
            for(int i=0;i<8;i++)clients.add(new Socket("127.0.0.1",server.port()));
            long end=System.currentTimeMillis()+1000;while(server.openConnections()<8&&System.currentTimeMillis()<end)Thread.sleep(5);
            assertEquals(8,server.openConnections());server.close();
            for(Socket s:clients){s.setSoTimeout(1000);assertEquals(-1,s.getInputStream().read());}
            assertEquals(0,server.openConnections());
        }finally{server.close();for(Socket s:clients)s.close();}
    }
}
