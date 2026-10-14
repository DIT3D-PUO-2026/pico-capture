package net.local.picocapture;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import org.json.*;
import java.util.concurrent.atomic.AtomicLong;

/** Dependency-free device checks; never starts capture or modifies capture profiles. */
public final class FeatureTestRunner extends Instrumentation {
    int checks;
    void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        metrics();queuesAndConnections();profiles();activity();
        result.putString("stream","\nPASS: "+checks+" feature checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable failure){result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,result);}}
    void metrics()throws Exception {
        AtomicLong time=new AtomicLong(0);ConnectionMetrics metrics=new ConnectionMetrics(time::get);
        check(metrics.snapshot().videoMbps==0,"Empty window");
        metrics.encoded(125000);metrics.sent(250000);time.set(1000);
        check(metrics.snapshot().videoMbps==1,"Encoded bitrate independent from transport fanout");
        check(metrics.snapshot().sentMbps==2,"Aggregate payload bitrate");
        metrics.queueReset(32);metrics.queueReset(4);
        check(metrics.snapshot().resets==2&&metrics.snapshot().discarded==36,"Queue counters");
        time.set(2250);check(metrics.snapshot().videoMbps==0&&metrics.snapshot().sentMbps==0,"Idle rates expire");
        metrics.encoded(250000);time.set(2500);check(metrics.snapshot().videoMbps==1,"Window accepts new data after idle");
        check(metrics.snapshot().resets==2,"Totals survive rate-window expiration");
        AtomicLong fixed=new AtomicLong(10000);ConnectionMetrics concurrent=new ConnectionMetrics(fixed::get);
        Thread[] threads=new Thread[4];for(int i=0;i<threads.length;i++){threads[i]=new Thread(()->{for(int n=0;n<1000;n++)concurrent.sent(125);});threads[i].start();}
        for(Thread thread:threads)thread.join();fixed.set(11000);
        check(concurrent.snapshot().sentMbps==4,"Concurrent writers retain all bytes");
    }
    void queuesAndConnections()throws Exception {
        RootCapture root=new RootCapture();
        RootCapture.Client transport=new RootCapture.Client(new java.net.Socket(),root.metrics);
        byte[] bytes={1,2,3};
        transport.enqueue(bytes,true,new byte[0]);for(int i=1;i<32;i++)transport.enqueue(bytes,false,null);
        check(!transport.enqueue(bytes,false,null),"TS reports overflow");
        check(transport.queue.isEmpty(),"TS stale queue cleared");
        transport.enqueue(bytes,true,new byte[0]);check(transport.queue.size()==1,"TS resumes at keyframe");
        LiveChannel channel=new LiveChannel(root.metrics,new LiveMatroska("video/x-vnd.on2.vp8",1280,720,new byte[0],"audio/opus",null));
        LiveChannel.Client client=new LiveChannel.Client(new java.net.Socket());channel.clients.add(client);
        channel.broadcast(bytes,true);for(int i=1;i<32;i++)channel.broadcast(bytes,false);
        channel.broadcast(bytes,false);check(client.queue.isEmpty()&&!client.ready,"Browser stale queue cleared");
        channel.broadcast(bytes,true);check(client.queue.size()==1&&client.ready,"Browser resumes at keyframe");
        root.nativeMux=channel;root.browserMux=channel;root.clients.add(transport);
        root.desktop=new DesktopOutputs(root,0,"");
        root.desktop.sinks.add(new RootCapture.Client(new java.net.Socket(),root.metrics));
        root.desktop.sinks.add(new RootCapture.Client(null,root.metrics));
        JSONObject result=new JSONObject();root.appendDiagnostics(result);
        check(result.getInt("httpConnections")==2,"Shared browser/native mux counted once");
        check(result.getInt("rtspConnections")==1&&result.getBoolean("udpActive"),"UDP output distinct from RTSP connections");
        check(result.getLong("streamQueueResets")==2&&result.getLong("discardedStreamChunks")==64,"All transport queue drops included");
        root.lastFrameMs=System.currentTimeMillis()-2000;root.appendDiagnostics(result);
        check(result.getString("fps").equals("0.00"),"Stalled FPS not reported as live");
        root.desktop.close();channel.close();transport.close();
    }
    void profiles()throws Exception {
        SharedPreferences prefs=getTargetContext().getSharedPreferences("feature_test_profiles",Context.MODE_PRIVATE);
        prefs.edit().clear().commit();try{
            CaptureProfiles store=new CaptureProfiles(prefs);JSONObject c=CaptureProfiles.starter(0,"test.avc");
            check(c.getBoolean("stream")&&!c.getBoolean("record")&&c.getInt("fps")==60,"OBS starter mode");
            check(CaptureProfiles.starter(1,"test.avc").getInt("width")==1280,"Browser starter resolution");
            check(CaptureProfiles.starter(2,"test.avc").getBoolean("record")&&!CaptureProfiles.starter(2,"test.avc").getBoolean("stream"),"Recording starter mode");
            c.put("width",1600).put("height",900).put("fps",45).put("upscale","sgsr").put("sgsrThreshold",4)
                .put("sourceWidth",1200).put("sourceHeight",676).put("sourceScale",0.75).put("audio",true)
                .put("port",8090).put("rtspPort",8555).put("udpDestination","192.0.2.1:9000")
                .put("advanced",new JSONObject().put("bitrate-mode",2));
            c.put("token","must-not-save").put("rootState","must-not-save");
            store.save(" Custom ",c);JSONObject loaded=new CaptureProfiles(prefs).load("Custom");
            check(!loaded.has("token")&&!loaded.has("rootState"),"Secrets and runtime state excluded");
            JSONObject expected=CaptureProfiles.settingsOnly(c);
            for(java.util.Iterator<String> keys=expected.keys();keys.hasNext();){String key=keys.next();check(expected.get(key).toString().equals(loaded.get(key).toString()),"Round trip: "+key);}
            c.getJSONObject("advanced").put("bitrate-mode",1);
            check(loaded.getJSONObject("advanced").getInt("bitrate-mode")==2,"Profile stores an independent snapshot");
            loaded.put("fps",30);store.save("Custom",loaded);check(store.load("Custom").getInt("fps")==30,"Overwrite");
            boolean rejected=false;try{store.save("  ",c);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Reject blank names");
            rejected=false;try{store.save(new String(new char[49]).replace('\0','x'),c);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Reject overlong names");
            for(int i=1;i<20;i++)store.save("Profile "+i,c);
            rejected=false;try{store.save("Too many",c);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Profile count bounded");
            store.save("Custom",c);check(store.names().size()==20,"Overwrite still works at capacity");
            store.delete("Custom");check(store.names().size()==19,"Delete persisted profile");
        }finally{prefs.edit().clear().commit();}
    }
    void activity()throws Exception {
        Intent intent=new Intent(getTargetContext(),DockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)startActivitySync(intent);
        try{
            waitForIdleSync();final Throwable[] failure=new Throwable[1];
            runOnMainSync(()->{try{
                JSONObject original=activity.readChoices();
                JSONObject custom=CaptureProfiles.settingsOnly(original).put("width",1600).put("height",900).put("fps",45)
                    .put("upscale","sgsr").put("sgsrThreshold",4).put("sourceScale",0.75).put("sharpness",55)
                    .put("advanced",new JSONObject().put("bitrate-mode",2));
                activity.applyChoices(custom);
            }catch(Throwable e){failure[0]=e;}});
            waitForIdleSync();
            runOnMainSync(()->{try{
                if(failure[0]!=null)return;JSONObject actual=activity.readChoices();
                check(actual.getInt("width")==1600&&actual.getInt("height")==900&&actual.getInt("fps")==45,"Custom fields survive spinner callbacks");
                check(actual.getString("upscale").equals("sgsr")&&actual.getInt("sgsrThreshold")==4,"SGSR profile selector");
                check(actual.getInt("sourceWidth")==1200&&actual.getInt("sourceHeight")==676,"Capture size derived from selected output");
                check(actual.getJSONObject("advanced").getInt("bitrate-mode")==2,"Advanced settings in UI");
                check(activity.profileSelector.getAdapter().getCount()>=4,"Starter profile controls present");
                JSONObject invalid=CaptureProfiles.settingsOnly(actual).put("encoder","missing.encoder");
                boolean rejected=false;try{activity.applyChoices(invalid);}catch(Exception e){rejected=true;}
                check(rejected&&activity.readChoices().getInt("width")==1600,"Missing encoder rejected before changing controls");
            }catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new AssertionError("Activity settings checks",failure[0]);
        }finally{runOnMainSync(activity::finish);}
    }
}
