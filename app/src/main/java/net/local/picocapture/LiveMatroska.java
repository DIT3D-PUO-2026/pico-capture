package net.local.picocapture;
import java.io.*;import java.nio.*;import java.util.*;

/** Bounded live Matroska/WebM muxing. Each sample has its own timestamped cluster. */
final class LiveMatroska{
 final String type,audioType,mime,contentType;final int width,height;final byte[] config,audioConfig;final boolean audio,webm;
 LiveMatroska(String t,int w,int h,byte[] c,String at,byte[] ac){type=t;width=w;height=h;config=c;audioType=at;audioConfig=ac;audio=ac!=null;webm=t.contains("vp8")||t.contains("vp9");contentType=webm?"video/webm":"video/x-matroska";mime=webm?"video/webm; codecs=\""+(t.contains("vp8")?"vp8":"vp9")+(audio?",opus":"")+"\"":contentType;}
 static byte[] join(byte[]... a){return FragmentedMp4.join(a);}
 static byte[] number(long x,int n){byte[] b=new byte[n];for(int i=n-1;i>=0;i--){b[i]=(byte)x;x>>>=8;}return b;}
 static byte[] integer(long x){int n=1;while(n<8&&(x>>>(n*8))!=0)n++;return number(x,n);}
 static byte[] id(long x){return integer(x);}
 static byte[] size(int length){int n=1;while(n<8&&length>=(1L<<(7*n))-1)n++;return number((1L<<(7*n))|length,n);}
 static byte[] e(long id,byte[] data){return join(id(id),size(data.length),data);}
 static byte[] u(long id,long value){return e(id,integer(value));}
 static byte[] str(long id,String text){try{return e(id,text.getBytes("UTF-8"));}catch(Exception x){throw new IllegalStateException(x);}}
 static byte[] dbl(long id,double x){return e(id,ByteBuffer.allocate(8).putDouble(x).array());}
 byte[] initialization(){byte[] header=e(0x1a45dfa3,join(u(0x4286,1),u(0x42f7,1),u(0x42f2,4),u(0x42f3,8),str(0x4282,webm?"webm":"matroska"),u(0x4287,4),u(0x4285,2)));
  byte[] info=e(0x1549a966,join(u(0x2ad7b1,1000000),str(0x4d80,"Pico Capture"),str(0x5741,"Pico Capture")));
  String codec=type.contains("vp8")?"V_VP8":type.contains("vp9")?"V_VP9":type.equals("video/avc")?"V_MPEG4/ISO/AVC":type.equals("video/hevc")?"V_MPEGH/ISO/HEVC":type.equals("video/mp4v-es")?"V_MPEG4/ISO/ASP":"V_MS/VFW/FOURCC";
  byte[] privateData=new byte[0];if(type.equals("video/avc")||type.equals("video/hevc")){FragmentedMp4 m=new FragmentedMp4(width,height,type,config,audioType,null);byte[] entry=m.videoEntry();privateData=Arrays.copyOfRange(entry,94,entry.length);}else if(type.equals("video/mp4v-es"))privateData=config;else if(type.equals("video/3gpp")){privateData=ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN).putInt(40).putInt(width).putInt(height).putShort((short)1).putShort((short)24).put(new byte[]{'H','2','6','3'}).putInt(width*height*3).putInt(0).putInt(0).putInt(0).putInt(0).array();}
  byte[] v=e(0xae,join(u(0xd7,1),u(0x73c5,1),u(0x83,1),u(0x9c,0),str(0x86,codec),privateData.length>0?e(0x63a2,privateData):new byte[0],e(0xe0,join(u(0xb0,width),u(0xba,height)))));
  byte[] a=new byte[0];if(audio){boolean opus=audioType.equals("audio/opus");a=e(0xae,join(u(0xd7,2),u(0x73c5,2),u(0x83,2),u(0x9c,0),str(0x86,opus?"A_OPUS":"A_AAC"),e(0x63a2,audioConfig),opus?join(u(0x56aa,6500000),u(0x56bb,80000000)):new byte[0],e(0xe1,join(dbl(0xb5,48000),u(0x9f,2)))));}
  return join(header,id(0x18538067),new byte[]{1,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255},info,e(0x1654ae6b,join(v,a)));}
 byte[] fragment(boolean isAudio,byte[] bytes,long pts,long duration,boolean key){byte[] payload=!isAudio&&(type.equals("video/avc")||type.equals("video/hevc"))?FragmentedMp4.avcc(bytes,type.equals("video/hevc")):bytes;return e(0x1f43b675,join(u(0xe7,Math.max(0,pts/1000)),e(0xa3,join(new byte[]{(byte)(isAudio?0x82:0x81),0,0,(byte)(isAudio||key?0x80:0)},payload))));}
}
