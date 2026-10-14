package net.local.picocapture;
import java.io.*;import java.net.*;import java.util.concurrent.*;import android.media.*;import android.os.*;

final class LiveChannel implements AutoCloseable{
 final String mime,contentType;final FragmentedMp4 mp4;final LiveMatroska matroska;RootCapture.Sample waiting;
 final CopyOnWriteArrayList<Client> clients=new CopyOnWriteArrayList<>();long resyncs;
 LiveChannel(FragmentedMp4 m){mp4=m;matroska=null;mime=m.mime;contentType="video/mp4";}
 LiveChannel(LiveMatroska m){matroska=m;mp4=null;mime=m.mime;contentType=m.contentType;}
 byte[] initialization(){return mp4!=null?mp4.initialization():matroska.initialization();}
 byte[] fragment(boolean a,byte[] b,long pts,long duration,boolean key){return mp4!=null?mp4.fragment(a,b,pts,duration,key):matroska.fragment(a,b,pts,duration,key);}
 synchronized void emit(boolean video,byte[] bytes,long pts,int flags){if(video){RootCapture.Sample previous=waiting;waiting=new RootCapture.Sample(true,bytes,pts,flags);if(previous==null)return;boolean key=(previous.flags&MediaCodec.BUFFER_FLAG_KEY_FRAME)!=0;broadcast(fragment(false,previous.bytes,previous.us,Math.max(1,pts-previous.us),key),key);}else broadcast(fragment(true,bytes,pts,20000,true),false);}
 void broadcast(byte[] packet,boolean key){for(Client c:clients){synchronized(c){if(!c.ready&&!key)continue;if(c.queue.remainingCapacity()==0){c.queue.clear();c.ready=false;resyncs++;}if(key)c.ready=true;if(c.ready)c.queue.offer(packet);}}}
 void serve(RootCapture root,Socket socket,OutputStream out,MediaCodec encoder)throws Exception{out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+contentType+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));out.write(initialization());out.flush();Client c=new Client(socket);clients.add(c);try{Bundle b=new Bundle();b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME,0);encoder.setParameters(b);while(root.running&&!socket.isClosed()){byte[] packet=c.queue.poll(2,TimeUnit.SECONDS);if(packet!=null){out.write(packet);out.flush();}}}finally{clients.remove(c);}}
 public void close(){for(Client c:clients)try{c.socket.close();}catch(Exception ignored){}clients.clear();}
 static final class Client{final Socket socket;Client(Socket s){socket=s;}boolean ready;ArrayBlockingQueue<byte[]> queue=new ArrayBlockingQueue<>(32);}
}
