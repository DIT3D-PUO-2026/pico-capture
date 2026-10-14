package net.local.picocapture;

import android.app.*;
import android.os.*;
import android.content.*;
import android.media.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText width,height,fps,bitrate,port,advanced,rtspPort,udpDestination; Spinner resolution,rate,encoder,mode; CheckBox audio,nativeCadence,browserCompatibility;Spinner upscaler,sourceScale;EditText sharpness;
    TextView status,info; final Handler handler=new Handler(); ArrayList<String> names=new ArrayList<>();
    final Runnable refresh=new Runnable(){public void run(){status.setText(CaptureService.status);handler.postDelayed(this,1000);}};
    public void onCreate(Bundle b){super.onCreate(b);
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,20,24,24);scroll.addView(root);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Pico Capture " + BuildConfig.VERSION_NAME);title.setTextSize(27);root.addView(title);
        info=new TextView(this);info.setText("Root capture · recording + Wi-Fi\nPresets and custom modes. Actual FPS depends on display/app cadence and encoder support. 1080p60 tested; other modes experimental.");root.addView(info);
        mode=spinner(root,"Output",new String[]{"Record + stream","Record only","Stream only"});
        resolution=spinner(root,"Resolution",new String[]{"720p — 1280×720","1080p — 1920×1080","1440p — 2560×1440","4K — 3840×2160","Custom"});resolution.setSelection(1);
        width=number(root,"Width",1920);height=number(root,"Height",1080);
        rate=spinner(root,"Frame rate",new String[]{"30","60","72","90","120","Custom"});rate.setSelection(1);fps=number(root,"Requested FPS",60);
        bitrate=number(root,"Bitrate (Mbps)",12);port=number(root,"Wi-Fi port",8088);
        for(MediaCodecInfo c:new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) if(c.isEncoder()) for(String type:c.getSupportedTypes()) if(type.startsWith("video/")) {
            try { boolean surface=false;for(int f:c.getCapabilitiesForType(type).colorFormats)if(f==MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)surface=true;if(surface)names.add(c.getName()+" | "+type); }catch(Exception ignored){}
        }
        encoder=spinner(root,"Encoder",names.toArray(new String[0]));
        for(int i=0;i<names.size();i++)if(names.get(i).startsWith("OMX.qcom.video.encoder.avc")){encoder.setSelection(i);break;}
        audio=new CheckBox(this);audio.setText("Internal audio (experimental; no microphone)");root.addView(audio);
        advanced=new EditText(this);advanced.setText("{}");advanced.setHint("Advanced MediaFormat JSON");advanced.setMinLines(2);label(root,"Advanced encoder keys (JSON; int, decimal or string)");root.addView(advanced);
        TextView example=new TextView(this);example.setText("Example: {\"bitrate-mode\":2,\"i-frame-interval\":1}\nEncoder-specific keys are passed through. Invalid keys/modes return the codec error; dimensions/FPS/bitrate use the controls above.");root.addView(example);
        Button caps=new Button(this);caps.setText("Inspect selected encoder");root.addView(caps);caps.setOnClickListener(v->showCapabilities());
        Button start=new Button(this);start.setText("Start capture — request root");root.addView(start);start.setOnClickListener(v->startCapture());
        Button stop=new Button(this);stop.setText("Stop & restore");root.addView(stop);stop.setOnClickListener(v->startService(new Intent(this,CaptureService.class).setAction("STOP")));
        status=new TextView(this);status.setTextIsSelectable(true);status.setPadding(0,18,0,8);root.addView(status);
        Button copy=new Button(this);copy.setText("Copy stream link");root.addView(copy);copy.setOnClickListener(v->{String url=CaptureService.streamUrl;if(url.isEmpty()){Toast.makeText(this,"Start a streaming session first",Toast.LENGTH_SHORT).show();return;}((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Pico stream",url));Toast.makeText(this,"Stream link copied",Toast.LENGTH_SHORT).show();});
        Button logs=new Button(this);logs.setText("Show session log");root.addView(logs);logs.setOnClickListener(v->{try{String text=new String(java.nio.file.Files.readAllBytes(new java.io.File(getFilesDir(),"root.log").toPath()));new AlertDialog.Builder(this).setTitle("Session log").setMessage(text.length()>14000?text.substring(text.length()-14000):text).setPositiveButton("Close",null).show();}catch(Exception e){error(e.toString());}});
        resolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){int[][] s={{1280,720},{1920,1080},{2560,1440},{3840,2160}};if(i<4){width.setText(""+s[i][0]);height.setText(""+s[i][1]);}}});
        rate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){if(i<5)fps.setText(new String[]{"30","60","72","90","120"}[i]);}});
    }
    void label(LinearLayout root,String text){TextView t=new TextView(this);t.setText(text);t.setPadding(0,16,0,0);root.addView(t);}
    EditText number(LinearLayout root,String text,int n){label(root,text);EditText e=new EditText(this);e.setInputType(2);e.setText(""+n);root.addView(e);return e;}
    Spinner spinner(LinearLayout root,String text,String[] values){label(root,text);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values));root.addView(s);return s;}
    void error(String text){new AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK",null).show();}
    void restoreChoices(){try{java.io.File file=new java.io.File(getFilesDir(),"session.json");if(!file.exists())return;JSONObject c=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),"UTF-8"));int w=c.getInt("width"),h=c.getInt("height"),f=c.getInt("fps");int[][] presets={{1280,720},{1920,1080},{2560,1440},{3840,2160}};int size=4;for(int i=0;i<4;i++)if(presets[i][0]==w&&presets[i][1]==h)size=i;resolution.setSelection(size);width.setText(""+w);height.setText(""+h);int[] rates={30,60,72,90,120};int index=5;for(int i=0;i<5;i++)if(rates[i]==f)index=i;rate.setSelection(index);fps.setText(""+f);bitrate.setText(""+(c.getInt("bitrate")/1000000));mode.setSelection(c.getBoolean("record")?(c.getBoolean("stream")?0:1):2);audio.setChecked(c.optBoolean("audio"));port.setText(""+c.getInt("port"));advanced.setText(c.getJSONObject("advanced").toString());for(int i=0;i<names.size();i++)if(names.get(i).startsWith(c.getString("encoder")+" | ")){encoder.setSelection(i);break;}if(upscaler!=null){String[] filters={"off","smooth","bicubic","sharp"};for(int k=0;k<filters.length;k++)if(filters[k].equals(c.optString("upscale","off")))upscaler.setSelection(k);if(c.optString("upscale","off").equals("sgsr"))upscaler.setSelection(c.optInt("sgsrThreshold",8)==4?5:4);double scale=c.optDouble("sourceScale",2.0/3.0);double[] scales={0.5,2.0/3.0,0.75,1.0};for(int k=0;k<scales.length;k++)if(Math.abs(scales[k]-scale)<0.01)sourceScale.setSelection(k);sharpness.setText(""+c.optInt("sharpness",35));}if(nativeCadence!=null)nativeCadence.setChecked(c.optBoolean("nativeCadence",true));if(browserCompatibility!=null)browserCompatibility.setChecked(c.optBoolean("browserCompatibility",true));if(rtspPort!=null)rtspPort.setText(""+c.optInt("rtspPort",8554));if(udpDestination!=null)udpDestination.setText(c.optString("udpDestination",""));android.util.Log.i("PicoCapture","Restored filter="+c.optString("upscale","off")+" selector="+(upscaler==null?-1:upscaler.getSelectedItemPosition()));}catch(Exception e){android.util.Log.w("PicoCapture","Saved choices unavailable",e);}}
    void showCapabilities(){try{String[] n=names.get(encoder.getSelectedItemPosition()).split(" \\| ");MediaCodecInfo match=null;for(MediaCodecInfo c:new MediaCodecList(1).getCodecInfos())if(c.getName().equals(n[0]))match=c;MediaCodecInfo.VideoCapabilities v=match.getCapabilitiesForType(n[1]).getVideoCapabilities();error(n[0]+"\n"+n[1]+"\nHardware reported by Android: "+match.isHardwareAccelerated()+" · software: "+match.isSoftwareOnly()+"\nWidth: "+v.getSupportedWidths()+"\nHeight: "+v.getSupportedHeights()+"\nFPS: "+v.getSupportedFrameRates()+"\nBitrate: "+v.getBitrateRange()+"\nAdvertised support for selected size/FPS: "+v.areSizeAndRateSupported(Integer.parseInt(width.getText().toString()),Integer.parseInt(height.getText().toString()),Double.parseDouble(fps.getText().toString()))+"\nThis is Android capability metadata, not a configure test. Browser uses supported native codecs or optional hardware AVC compatibility encoding. Actual capture FPS can be lower.");}catch(Exception e){error(e.toString());}}
    void startCapture(){try{JSONObject c=new JSONObject();int w=Integer.parseInt(width.getText().toString()),h=Integer.parseInt(height.getText().toString()),f=Integer.parseInt(fps.getText().toString()),p=Integer.parseInt(port.getText().toString());long br=Long.parseLong(bitrate.getText().toString())*1000000L;
        if(w<1||h<1||f<1||br<1||br>Integer.MAX_VALUE||p<1024||p>65535)throw new Exception("Enter positive sizes/FPS/bitrate and a port from 1024–65535.");
        String[] n=names.get(encoder.getSelectedItemPosition()).split(" \\| ");c.put("width",w).put("height",h).put("fps",f).put("bitrate",br).put("port",p).put("encoder",n[0]).put("mime",n[1]).put("record",mode.getSelectedItemPosition()!=2).put("stream",mode.getSelectedItemPosition()!=1).put("rtspPort",rtspPort==null?8554:Integer.parseInt(rtspPort.getText().toString())).put("udpDestination",udpDestination==null?"":udpDestination.getText().toString().trim()).put("browserCompatibility",browserCompatibility==null||browserCompatibility.isChecked()).put("audio",audio.isChecked()).put("nativeCadence",nativeCadence==null||nativeCadence.isChecked()).put("advanced",new JSONObject(advanced.getText().toString()));
        String filter=upscaler==null?"off":new String[]{"off","smooth","bicubic","sharp","sgsr","sgsr"}[upscaler.getSelectedItemPosition()];double scale=filter.equals("off")?1:new double[]{0.5,2.0/3.0,0.75,1.0}[sourceScale.getSelectedItemPosition()];int amount=sharpness==null?35:Integer.parseInt(sharpness.getText().toString());if(amount<0||amount>100)throw new Exception("Sharpness must be 0–100.");
        c.put("upscale",filter).put("sgsrThreshold",upscaler!=null&&upscaler.getSelectedItemPosition()==5?4:8).put("sourceScale",sourceScale==null?2.0/3.0:new double[]{0.5,2.0/3.0,0.75,1.0}[sourceScale.getSelectedItemPosition()]).put("sourceWidth",filter.equals("off")?w:Math.max(2,(int)Math.round(w*scale/2)*2)).put("sourceHeight",filter.equals("off")?h:Math.max(2,(int)Math.round(h*scale/2)*2)).put("sharpness",amount);
        startForegroundService(new Intent(this,CaptureService.class).setAction("START").putExtra("config",c.toString()));
    }catch(Exception e){error(e.getMessage());}}
    protected void onResume(){super.onResume();handler.post(refresh);}
    protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
