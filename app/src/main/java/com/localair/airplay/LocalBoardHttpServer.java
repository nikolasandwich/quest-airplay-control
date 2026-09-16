package com.localair.airplay;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Read-only endpoints with a total request deadline and ownership of every socket. */
public final class LocalBoardHttpServer implements AutoCloseable {
    private final ServerSocket server;
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers;
    private final Thread acceptor;
    private volatile boolean closed;
    public LocalBoardHttpServer(int port,byte[] page,Supplier<byte[]> state)throws IOException{this(port,page,state,2000);}
    public LocalBoardHttpServer(int port,byte[] page,Supplier<byte[]> state,int deadlineMs)throws IOException{
        server=new ServerSocket(port);
        workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),task -> {Thread t=new Thread(task,"BoardClient");t.setDaemon(true);return t;});
        acceptor=new Thread(() -> {
            while(!closed){
                try{
                    Socket socket=server.accept();sockets.add(socket);
                    if(closed){drop(socket);continue;}
                    try{workers.execute(() -> serve(socket,page,state,deadlineMs));}catch(RejectedExecutionException e){drop(socket);}
                }catch(IOException e){if(!closed)close();}
            }
        },"CalibrationBoard");acceptor.setDaemon(true);acceptor.start();
    }
    public int port(){return server.getLocalPort();}
    public int openConnections(){return sockets.size();}
    private void drop(Socket socket){sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}
    private void serve(Socket socket,byte[] page,Supplier<byte[]> state,int deadlineMs){
        try{
            long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(deadlineMs);
            StringBuilder header=new StringBuilder();InputStream in=socket.getInputStream();
            while(!header.toString().endsWith("\r\n\r\n")){
                long left=end-System.nanoTime();if(left<=0)throw new SocketTimeoutException();
                socket.setSoTimeout((int)Math.max(1,TimeUnit.NANOSECONDS.toMillis(left)));
                if(header.length()>=8192){respond(socket,431,"text/plain",new byte[0]);return;}
                int b=in.read();if(b<0)return;header.append((char)b);
            }
            String[] request=header.toString().split("\r\n",2)[0].split(" ");
            String path=request.length>1?request[1].split("\\?",2)[0]:"";
            if(request.length!=3||!request[0].equals("GET")||!Set.of("/","/state","/favicon.ico").contains(path)){
                respond(socket,404,"text/plain","Not found".getBytes(StandardCharsets.UTF_8));return;
            }
            respond(socket,200,path.equals("/state")?"application/json":"text/html",path.equals("/state")?state.get():path.equals("/favicon.ico")?new byte[0]:page);
        }catch(IOException|RuntimeException ignored){}finally{drop(socket);}
    }
    private void respond(Socket socket,int code,String type,byte[] body)throws IOException{
        String headers="HTTP/1.1 "+code+(code==200?" OK":" Error")+"\r\nContent-Type: "+type+"; charset=utf-8\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'\r\n\r\n";
        OutputStream out=socket.getOutputStream();out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
    }
    @Override public void close(){closed=true;try{server.close();}catch(IOException ignored){}for(Socket s:sockets)drop(s);workers.shutdownNow();}
}
