package net.local.picocapture;

import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;

/** Session-only workaround for the inspected runtime's forced 90/3 capture divisor. */
final class NativeCadence implements AutoCloseable {
    static final String HASH="ee49aa7026d4ee5b18cbddc06920386c6baf9ca872884de49dc4ced211c5ee4c";
    static final byte[] ORIGINAL={(byte)0x77,1,(byte)0x8a,0x1a},PATCH={(byte)0xf7,3,0x0a,0x2a};
    int pid;long address;String identity;File dir,lease;boolean applied;
    static String read(File f)throws IOException{return new String(Files.readAllBytes(f.toPath()),"UTF-8");}
    static String startTime(int p)throws IOException{String s=read(new File("/proc/"+p+"/stat"));return s.substring(s.lastIndexOf(')')+2).split(" ")[19];}
    static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
    byte[] memory()throws IOException{byte[] b=new byte[4];try(RandomAccessFile f=new RandomAccessFile("/proc/"+pid+"/mem","r")){f.seek(address);f.readFully(b);}return b;}
    void write(byte[] b)throws IOException{try(RandomAccessFile f=new RandomAccessFile("/proc/"+pid+"/mem","rw")){f.seek(address);f.write(b);}if(!Arrays.equals(memory(),b))throw new IOException("Native instruction verification failed");}
    static NativeCadence enable()throws Exception{
        NativeCadence n=new NativeCadence();
        for(File proc:new File("/proc").listFiles()){
            if(!proc.getName().matches("[0-9]+"))continue;
            try{String cmd=read(new File(proc,"cmdline")).split("\u0000",2)[0];if(!cmd.equals("com.pico.xr.openxr_runtime"))continue;
                for(String line:read(new File(proc,"maps")).split("\n")){
                    String[] fields=line.trim().split("\\s+");if(fields.length<6||!fields[1].equals("r-xp")||!fields[2].equals("00000000")||!fields[5].endsWith("/libpxrcompositor.so"))continue;
                    if(!hash(new File(fields[5])).equals(HASH))throw new IOException("Runtime library fingerprint differs; workaround skipped");
                    long base=Long.parseLong(fields[0].split("-")[0],16);n.pid=Integer.parseInt(proc.getName());n.address=base+0x63348;n.identity=startTime(n.pid);
                    if(!Arrays.equals(n.memory(),ORIGINAL))throw new IOException("Native instruction differs; workaround skipped");
                    n.installGuard();
                    // The guard is ready before the first write, including an interrupted write.
                    n.applied=true;try{n.write(PATCH);}catch(Exception e){n.close();throw e;}
                    System.out.println("NATIVE_CADENCE enabled: bypass forced 90Hz / 3 rule, pid="+n.pid);return n;
                }
            }catch(FileNotFoundException ignored){}catch(java.nio.file.NoSuchFileException ignored){}
        }
        throw new IOException("Supported runtime process not found; workaround skipped");
    }
    void installGuard()throws Exception{
        int owner=android.os.Process.myPid();String ownerStart=startTime(owner);
        dir=new File("/data/local/tmp/pico-cadence-"+owner+"-"+ownerStart);if(!dir.mkdirs())throw new IOException("Cannot create cadence rollback directory");
        Files.write(new File(dir,"original.bin").toPath(),ORIGINAL);lease=new File(dir,"lease");Files.write(lease.toPath(),new byte[]{1});
        String prefix=dir.getAbsolutePath();String script="#!/system/bin/sh\n"+
          "echo ready > '"+prefix+"/ready'\n"+
          "while [ -f '"+prefix+"/lease' ] && [ \"$(awk '{print $22}' /proc/"+owner+"/stat 2>/dev/null)\" = '"+ownerStart+"' ]; do sleep 1; done\n"+
          "[ -f '"+prefix+"/lease' ] || exit 0\n"+
          "if [ \"$(awk '{print $22}' /proc/"+pid+"/stat 2>/dev/null)\" = '"+identity+"' ]; then\n"+
          "  current=$(dd if=/proc/"+pid+"/mem bs=4 skip="+(address/4)+" count=1 2>/dev/null | od -An -tx1 | tr -d ' \\n')\n"+
          "  if [ \"$current\" = f7030a2a ]; then dd if='"+prefix+"/original.bin' of=/proc/"+pid+"/mem bs=4 seek="+(address/4)+" count=1 conv=notrunc || exit 1; fi\n"+
          "fi\n"+
          "rm -f '"+prefix+"/lease'\n";
        File file=new File(dir,"guard.sh");Files.write(file.toPath(),script.getBytes("UTF-8"));
        new ProcessBuilder("/system/bin/sh",file.getAbsolutePath()).redirectOutput(new File(dir,"guard.log")).redirectErrorStream(true).start();
        File ready=new File(dir,"ready");for(int i=0;i<40&&!ready.exists();i++)Thread.sleep(50);if(!ready.exists())throw new IOException("Native rollback guard did not start");
    }
    public synchronized void close(){if(!applied)return;try{
        if(startTime(pid).equals(identity)){byte[] current=memory();if(Arrays.equals(current,PATCH))write(ORIGINAL);else if(!Arrays.equals(current,ORIGINAL))throw new IOException("Unexpected instruction; refusing to overwrite");}
        applied=false;lease.delete();System.out.println("NATIVE_CADENCE original instruction restored");
    }catch(java.nio.file.NoSuchFileException e){applied=false;if(lease!=null)lease.delete();}catch(Exception e){System.out.println("NATIVE_CADENCE restore deferred to guard: "+e);}}
}
