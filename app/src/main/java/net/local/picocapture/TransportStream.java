package net.local.picocapture;
import java.io.*;import java.util.*;

/** Small MPEG-TS muxer for one Annex-B AVC/HEVC video stream and optional AAC. */
final class TransportStream {
    final boolean hevc,audio;final int videoStreamType;final int[] cc=new int[8192];
    TransportStream(String type,boolean a){hevc=type.equals("video/hevc");audio=a;videoStreamType=hevc?0x24:type.equals("video/mp4v-es")?0x10:0x1b;}
    synchronized byte[] tables(){ByteArrayOutputStream all=new ByteArrayOutputStream();
        byte[] pat=new byte[]{0x00,(byte)0xb0,0x0d,0,1,(byte)0xc1,0,0,0,1,(byte)0xe1,0};
        write(all,pack(0,true,crc(pat),-1));
        int length=13+(audio?10:5);ByteArrayOutputStream p=new ByteArrayOutputStream();write(p,new byte[]{2,(byte)0xb0,(byte)length,0,1,(byte)0xc1,0,0,(byte)0xe1,1,(byte)0xf0,0,(byte)videoStreamType,(byte)0xe1,1,(byte)0xf0,0});
        if(audio)write(p,new byte[]{0x0f,(byte)0xe1,2,(byte)0xf0,0});write(all,pack(0x100,true,crc(p.toByteArray()),-1));return all.toByteArray();}
    synchronized byte[] video(byte[] bytes,long us){return pes(0x101,0xe0,bytes,us,true);}
    synchronized byte[] audio(byte[] bytes,long us){return pes(0x102,0xc0,adts(bytes),us,false);}
    byte[] crc(byte[] section){int crc=0xffffffff;for(byte b:section){crc^=(b&255)<<24;for(int i=0;i<8;i++)crc=(crc<<1)^((crc<0)?0x04c11db7:0);}ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(0);write(b,section);write(b,new byte[]{(byte)(crc>>>24),(byte)(crc>>>16),(byte)(crc>>>8),(byte)crc});return b.toByteArray();}
    byte[] pes(int pid,int stream,byte[] data,long us,boolean video){long pts=Math.max(0,us)*90/1000;int n=video?0:data.length+8;ByteArrayOutputStream p=new ByteArrayOutputStream();write(p,new byte[]{0,0,1,(byte)stream,(byte)(n>>>8),(byte)n,(byte)0x80,(byte)0x80,5});write(p,new byte[]{(byte)(0x21|((pts>>>29)&0x0e)),(byte)(pts>>>22),(byte)(((pts>>>14)&0xfe)|1),(byte)(pts>>>7),(byte)(((pts<<1)&0xfe)|1)});write(p,data);return pack(pid,true,p.toByteArray(),video?pts:-1);}
    byte[] pack(int pid,boolean start,byte[] data,long pcr){ByteArrayOutputStream out=new ByteArrayOutputStream();int offset=0;boolean first=true;
        while(offset<data.length){byte[] packet=new byte[188];Arrays.fill(packet,(byte)0xff);boolean withPcr=first&&pcr>=0;int capacity=withPcr?176:184;int n=Math.min(capacity,data.length-offset);int padding=184-n;packet[0]=0x47;packet[1]=(byte)((pid>>>8)|((first&&start)?0x40:0));packet[2]=(byte)pid;packet[3]=(byte)((padding>0?0x30:0x10)|(cc[pid]++&15));int pos=4;
            if(padding>0){packet[pos++]=(byte)(padding-1);if(padding>1){packet[pos++]=(byte)(withPcr?0x10:0);if(withPcr){packet[pos++]=(byte)(pcr>>>25);packet[pos++]=(byte)(pcr>>>17);packet[pos++]=(byte)(pcr>>>9);packet[pos++]=(byte)(pcr>>>1);packet[pos++]=(byte)(((pcr&1)<<7)|0x7e);packet[pos++]=0;}pos=4+padding;}}
            System.arraycopy(data,offset,packet,pos,n);write(out,packet);offset+=n;first=false;
        }return out.toByteArray();}
    static byte[] discontinuity(){ByteArrayOutputStream b=new ByteArrayOutputStream();for(int pid:new int[]{0,0x100,0x101,0x102}){byte[] p=new byte[188];Arrays.fill(p,(byte)0xff);p[0]=0x47;p[1]=(byte)(pid>>>8);p[2]=(byte)pid;p[3]=0x20;p[4]=(byte)183;p[5]=(byte)0x80;write(b,p);}return b.toByteArray();}
    byte[] adts(byte[] data){int n=data.length+7;ByteArrayOutputStream b=new ByteArrayOutputStream();write(b,new byte[]{(byte)0xff,(byte)0xf1,0x4c,(byte)(0x80|(n>>>11)),(byte)(n>>>3),(byte)(((n&7)<<5)|0x1f),(byte)0xfc});write(b,data);return b.toByteArray();}
    static void write(ByteArrayOutputStream b,byte[] d){b.write(d,0,d.length);}
}
