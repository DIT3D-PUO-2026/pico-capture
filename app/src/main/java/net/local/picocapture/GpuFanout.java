package net.local.picocapture;
import android.graphics.SurfaceTexture;import android.opengl.*;import android.os.*;import android.view.Surface;
import java.nio.*;import java.io.*;import java.util.concurrent.*;import org.json.JSONObject;

/** GPU filtering; SGSR uses an intermediate RGBA8 texture for texture-gather support. */
final class GpuFanout implements AutoCloseable {
 final HandlerThread thread=new HandlerThread("capture-gpu");
 Handler handler;Surface input;SurfaceTexture texture;EGLDisplay display;EGLContext context;EGLSurface[] targets;
 int program,copyProgram,width,height,sourceWidth,sourceHeight,externalId,rgbaId,fbo;
 final String filter;final float sharpness,threshold;boolean twoD;volatile boolean closed;
 volatile String error="";volatile long frames,submitTotalNs,submitMaxNs;
 volatile boolean timerSupported;volatile long querySamples,queryTotalNs,queryMaxNs,disjointEvents;
 final int[] queries=new int[16];final boolean[] pending=new boolean[16],warmQuery=new boolean[16];int queryCursor;
 final float[] matrix=new float[16],identity=new float[16];long previousTimestamp;
 final FloatBuffer quad=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
 GpuFanout(JSONObject cfg,int sw,int sh,int w,int h,String mode,float sharp,Surface... outputs)throws Exception{
  sourceWidth=sw;sourceHeight=sh;width=w;height=h;filter=mode;sharpness=sharp;
  threshold=(float)cfg.optDouble("sgsrThreshold",8)/255f;
  twoD=filter.equals("sgsr");android.opengl.Matrix.setIdentityM(identity,0);
  quad.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);thread.start();handler=new Handler(thread.getLooper());CountDownLatch ready=new CountDownLatch(1);
  handler.post(()->{try{prepare(outputs);}catch(Throwable e){error=e.toString();e.printStackTrace();}finally{ready.countDown();}});
  if(!ready.await(8,TimeUnit.SECONDS)||!error.isEmpty()){close();throw new Exception("GPU processing: "+error);}
 }
 int shader(int kind,String code){int s=GLES20.glCreateShader(kind);GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
 int link(String fragment){int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,shader(GLES20.GL_VERTEX_SHADER,"#version 310 es\nin vec2 p;out highp vec2 uv;void main(){gl_Position=vec4(p,0.0,1.0);uv=(p+1.0)*0.5;}"));GLES20.glAttachShader(p,shader(GLES20.GL_FRAGMENT_SHADER,fragment));GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));return p;}
 String baseShader(){String s=GpuFilters.fragment(filter);s=s.replace("#extension GL_OES_EGL_image_external : require\n",twoD?"":"#extension GL_OES_EGL_image_external_essl3 : require\n");s=s.replace("varying vec2 uv;","in highp vec2 uv;out vec4 filteredColor;").replace("texture2D(","texture(").replace("gl_FragColor","filteredColor");if(twoD)s=s.replace("samplerExternalOES","sampler2D");return "#version 310 es\n"+s;}
 void prepare(Surface[] outputs)throws Exception{
  display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] v=new int[2];if(!EGL14.eglInitialize(display,v,0,v,1))throw new IOException("EGL init");
  EGLConfig[] c=new EGLConfig[1];int[] count=new int[1];if(!EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,0x40,0x3142,1,EGL14.EGL_NONE},0,c,0,1,count,0)||count[0]==0)throw new IOException("No GLES3 recordable EGL config");
  context=EGL14.eglCreateContext(display,c[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);targets=new EGLSurface[outputs.length];for(int i=0;i<outputs.length;i++){targets[i]=EGL14.eglCreateWindowSurface(display,c[0],outputs[i],new int[]{EGL14.EGL_NONE},0);if(targets[i].equals(EGL14.EGL_NO_SURFACE))throw new IOException("EGL surface");}EGL14.eglMakeCurrent(display,targets[0],targets[0],context);
  program=link(filter.equals("sgsr")?SgsrShader.CODE:baseShader());
  int[] ids=new int[2];GLES20.glGenTextures(2,ids,0);externalId=ids[0];rgbaId=ids[1];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,externalId);parameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
  texture=new SurfaceTexture(externalId);texture.setDefaultBufferSize(sourceWidth,sourceHeight);input=new Surface(texture);
  if(twoD){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,rgbaId);parameters(GLES20.GL_TEXTURE_2D);
   {GLES30.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES30.GL_RGBA8,sourceWidth,sourceHeight,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);int[] a=new int[1];GLES20.glGenFramebuffers(1,a,0);fbo=a[0];GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,rgbaId,0);if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new IOException("RGBA framebuffer incomplete");GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
    copyProgram=link("#version 310 es\n#extension GL_OES_EGL_image_external_essl3 : require\nprecision highp float;uniform samplerExternalOES image;uniform mat4 transform;in highp vec2 uv;out vec4 result;void main(){result=texture(image,(transform*vec4(uv,0.0,1.0)).xy);}");}
  }
  timerSupported=GLES20.glGetString(GLES20.GL_EXTENSIONS).contains("GL_EXT_disjoint_timer_query");if(timerSupported)GLES30.glGenQueries(queries.length,queries,0);
  System.out.println("GPU_PROCESS renderer="+GLES20.glGetString(GLES20.GL_RENDERER)+" version="+GLES20.glGetString(GLES20.GL_VERSION)+" filter="+filter+" source="+sourceWidth+"x"+sourceHeight+" output="+width+"x"+height+" threshold="+threshold+" timer="+timerSupported+" encoderSurfaces="+outputs.length);
  texture.setOnFrameAvailableListener(t->{if(closed)return;try{draw();}catch(Throwable e){error=e.toString();e.printStackTrace();}},handler);
 }
 void parameters(int target){GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);}
 void collectQueries(){if(!timerSupported)return;int[] v=new int[1];GLES20.glGetIntegerv(0x8FBB,v,0);if(v[0]!=0){disjointEvents++;for(int i=0;i<pending.length;i++)warmQuery[i]=false;}
  for(int i=0;i<pending.length;i++)if(pending[i]){GLES30.glGetQueryObjectuiv(queries[i],GLES30.GL_QUERY_RESULT_AVAILABLE,v,0);if(v[0]!=0){GLES30.glGetQueryObjectuiv(queries[i],GLES30.GL_QUERY_RESULT,v,0);long ns=Integer.toUnsignedLong(v[0]);if(warmQuery[i]){queryTotalNs+=ns;queryMaxNs=Math.max(queryMaxNs,ns);querySamples++;}pending[i]=false;}}
 }
 void render(int p,int target,int textureId,float[] transform,int w,int h){GLES20.glViewport(0,0,w,h);GLES20.glUseProgram(p);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(target,textureId);int a=GLES20.glGetAttribLocation(p,"p");GLES20.glEnableVertexAttribArray(a);quad.position(0);GLES20.glVertexAttribPointer(a,2,GLES20.GL_FLOAT,false,0,quad);GLES20.glUniform1i(GLES20.glGetUniformLocation(p,filter.equals("sgsr")&&p==program?"ps0":"image"),0);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(p,"transform"),1,false,transform,0);GLES20.glUniform2f(GLES20.glGetUniformLocation(p,"sourceSize"),sourceWidth,sourceHeight);GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"strength"),sharpness);GLES20.glUniform1f(GLES20.glGetUniformLocation(p,"threshold"),threshold);GLES20.glUniform4f(GLES20.glGetUniformLocation(p,"ViewportInfo[0]"),1f/sourceWidth,1f/sourceHeight,sourceWidth,sourceHeight);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);}
 void draw()throws Exception{
  long before=System.nanoTime();EGL14.eglMakeCurrent(display,targets[0],targets[0],context);collectQueries();texture.updateTexImage();texture.getTransformMatrix(matrix);long timestamp=texture.getTimestamp();if(timestamp<=0)timestamp=System.nanoTime();if(timestamp==previousTimestamp)return;previousTimestamp=timestamp;
  int query=-1;if(timerSupported&&!pending[queryCursor]){query=queryCursor;GLES30.glBeginQuery(0x88BF,queries[query]);queryCursor=(queryCursor+1)%queries.length;}
  if(twoD){GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);render(copyProgram,GLES11Ext.GL_TEXTURE_EXTERNAL_OES,externalId,matrix,sourceWidth,sourceHeight);GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);}
  for(int i=0;i<targets.length;i++){if(!EGL14.eglMakeCurrent(display,targets[i],targets[i],context))throw new IOException("EGL makeCurrent");render(program,twoD?GLES20.GL_TEXTURE_2D:GLES11Ext.GL_TEXTURE_EXTERNAL_OES,twoD?rgbaId:externalId,twoD?identity:matrix,width,height);
   EGLExt.eglPresentationTimeANDROID(display,targets[i],timestamp);if(!EGL14.eglSwapBuffers(display,targets[i]))throw new IOException("EGL swap");}
  if(query>=0){GLES30.glEndQuery(0x88BF);pending[query]=true;warmQuery[query]=frames>=60;}
  int err=GLES20.glGetError();if(err!=0)throw new IOException("GL error "+err);
  long elapsed=System.nanoTime()-before;submitTotalNs+=elapsed;submitMaxNs=Math.max(submitMaxNs,elapsed);frames++;
 }
 public void close(){closed=true;if(handler==null)return;CountDownLatch done=new CountDownLatch(1);handler.post(()->{try{if(input!=null)input.release();if(texture!=null)texture.release();if(display!=null){if(timerSupported)GLES30.glDeleteQueries(queries.length,queries,0);EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(targets!=null)for(EGLSurface s:targets)if(s!=null)EGL14.eglDestroySurface(display,s);if(context!=null)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);}}finally{done.countDown();}});try{done.await(2,TimeUnit.SECONDS);}catch(Exception ignored){}thread.quitSafely();}
}
