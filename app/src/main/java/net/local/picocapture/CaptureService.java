package net.local.picocapture;



import android.app.*;import android.content.*;import android.os.*;

import org.json.*;import java.io.*;import java.net.*;import java.security.*;import java.util.*;



public class CaptureService extends Service {

    public static volatile String status="Ready. Start requests root once; opening the app does not request root.";

    public static volatile String streamUrl="";
    public static volatile String diagnostics="Connection diagnostics appear during capture.";

    volatile boolean running=false; java.lang.Process worker;String token;int port;PowerManager.WakeLock lock;

    public IBinder onBind(Intent i){return null;}

    public int onStartCommand(Intent i,int flags,int id){if(i==null){stopSelf();return START_NOT_STICKY;}if("STOP".equals(i.getAction())){new Thread(()->{stopCapture();stopSelf();}).start();return START_NOT_STICKY;}if(running)return START_NOT_STICKY;

        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("capture","Capture status",NotificationManager.IMPORTANCE_LOW));

        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,DockActivity.class),PendingIntent.FLAG_IMMUTABLE);

        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);

        startForeground(42,new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("Pico Capture running").setContentText("Tap Stop to finalize recordings and restore capture properties.").setContentIntent(open).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build());

        diagnostics="Waiting for live capture measurements…";running=true;new Thread(()->runCapture(i.getStringExtra("config")),"capture-controller").start();return START_NOT_STICKY;

    }

    void runCapture(String config){try{

        JSONObject c=new JSONObject(config);byte[] r=new byte[16];new SecureRandom().nextBytes(r);StringBuilder s=new StringBuilder();for(byte b:r)s.append(String.format("%02x",b&255));token=s.toString();port=c.getInt("port");c.put("token",token);

        File file=new File(getFilesDir(),"session.json");try(FileOutputStream f=new FileOutputStream(file)){f.write(c.toString().getBytes("UTF-8"));}

        File log=new File(getFilesDir(),"root.log");try(FileOutputStream f=new FileOutputStream(log)){f.write(new byte[0]);}

        lock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PicoCapture:session");lock.acquire();

        String command="CLASSPATH="+quote(getApplicationInfo().sourceDir)+" app_process /system/bin net.local.picocapture.RootCapture "+quote(file.getAbsolutePath())+" >> "+quote(log.getAbsolutePath())+" 2>&1";

        status="Requesting root / starting capture…";worker=new ProcessBuilder("su","-c",command).start();

        java.lang.Process active=worker;long start=System.currentTimeMillis();while(running){if(!active.isAlive())throw new IOException("Capture process exited ("+active.exitValue()+"). See session log.");try{String state=request("status");JSONObject j=new JSONObject(state);diagnostics=formatDiagnostics(j,c.getBoolean("stream"));streamUrl=c.getBoolean("stream")?"http://"+address()+":"+port+"/":"";status="Requested: "+c.getInt("width")+"×"+c.getInt("height")+" @ "+c.getInt("fps")+" FPS\n"+j.optString("state")+"\nGPU: "+j.optString("upscale","off")+" · capture "+j.optInt("sourceWidth",c.getInt("width"))+"×"+j.optInt("sourceHeight",c.getInt("height"))+"\nActual output: "+j.optString("fps")+" FPS · frames "+j.optLong("frames")+"\nRecording: "+j.optString("file")+"\n"+(c.getBoolean("stream")?"VLC / OBS: http://"+address()+":"+port+(c.getString("mime").contains("vp")?"/live.webm":c.getString("mime").equals("video/3gpp")?"/live.mp4":"/live")+"\nBrowser: http://"+address()+":"+port+"/":"");}catch(Exception e){status="Capture status unavailable; retrying…";diagnostics=status;if(System.currentTimeMillis()-start>45000)throw e;}Thread.sleep(2000);}

        status="Stopped. Session is finalizing; recordings are in Movies/PicoCapture. Capture properties restore when the helper exits.";

    }catch(Exception e){status="Stopped: "+e.getMessage();}finally{stopCapture();stopSelf();}}

    static String formatDiagnostics(JSONObject j,boolean streaming){
        String rates=String.format(java.util.Locale.US,"Encoded video: %.1f Mbps",j.optDouble("videoBitrateMbps"));
        if(!streaming)return rates+"\nStreaming disabled";
        return rates+String.format(java.util.Locale.US," · sent: %.1f Mbps",j.optDouble("sentMbps"))
            +"\nConnections: "+j.optInt("httpConnections")+" HTTP · "+j.optInt("rtspConnections")+" RTSP"
            +(j.optBoolean("udpActive")?" · UDP sending":"")
            +"\nStream queue resets: "+j.optLong("streamQueueResets")+" · discarded chunks: "+j.optLong("discardedStreamChunks");
    }
    String quote(String s){return "'"+s.replace("'","'\\''")+"'";}

    String request(String path)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+"/"+token+"/"+path).openConnection();c.setConnectTimeout(1000);c.setReadTimeout(2000);try(InputStream in=c.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return b.toString("UTF-8");}finally{c.disconnect();}}

    synchronized void stopCapture(){running=false;streamUrl="";diagnostics="No active capture session.";if(worker!=null){try{request("stop");}catch(Exception ignored){}// The root helper has a heartbeat watchdog; avoid killing it before its finally block.

        worker=null;}if(lock!=null&&lock.isHeld())lock.release();lock=null;}

    String address(){try{Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();while(e.hasMoreElements()){NetworkInterface n=e.nextElement();for(Enumeration<InetAddress> a=n.getInetAddresses();a.hasMoreElements();){InetAddress ip=a.nextElement();if(ip instanceof Inet4Address&&!ip.isLoopbackAddress()&&!ip.isLinkLocalAddress())return ip.getHostAddress();}}}catch(Exception ignored){}return "HEADSET_IP";}

    public void onDestroy(){stopCapture();super.onDestroy();}

}
