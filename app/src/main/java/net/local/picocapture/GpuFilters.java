package net.local.picocapture;

/** Spatial filters for the external compositor texture. No CPU pixel readback. */
final class GpuFilters {
 static String fragment(String filter){
  String head="#extension GL_OES_EGL_image_external : require\nprecision highp float;uniform samplerExternalOES image;uniform mat4 transform;uniform vec2 sourceSize;uniform float strength;varying vec2 uv;\n"
   // Pixel offsets precede SurfaceTexture's crop/flip transform.
   +"vec3 sampleAt(vec2 q){vec2 halfPixel=0.5/sourceSize;q=clamp(q,halfPixel,vec2(1.0)-halfPixel);return texture2D(image,(transform*vec4(q,0.0,1.0)).xy).rgb;}\n";
  if(filter.equals("bicubic"))return head
   +"vec4 weights(float t){float t2=t*t,t3=t2*t;return vec4(-0.5*t+t2-0.5*t3,1.0-2.5*t2+1.5*t3,0.5*t+2.0*t2-1.5*t3,-0.5*t2+0.5*t3);}\n"
   +"void main(){vec2 p=uv*sourceSize-0.5;vec2 base=floor(p);vec2 f=fract(p);vec4 wx=weights(f.x),wy=weights(f.y);vec3 result=vec3(0.0);"
   +"for(int y=0;y<4;y++){for(int x=0;x<4;x++){vec2 q=(base+vec2(float(x-1),float(y-1))+0.5)/sourceSize;result+=sampleAt(q)*wx[x]*wy[y];}}gl_FragColor=vec4(clamp(result,0.0,1.0),1.0);}";
  if(filter.equals("sharp"))return head
   +"void main(){vec2 step=1.0/sourceSize;vec3 c=sampleAt(uv),n=sampleAt(uv+vec2(0.0,step.y)),s=sampleAt(uv-vec2(0.0,step.y)),e=sampleAt(uv+vec2(step.x,0.0)),w=sampleAt(uv-vec2(step.x,0.0));"
   +"vec3 lo=min(c,min(min(n,s),min(e,w))),hi=max(c,max(max(n,s),max(e,w)));vec3 enhanced=c+strength*(c-(n+s+e+w)*0.25);gl_FragColor=vec4(clamp(enhanced,lo,hi),1.0);}";
  return head+"void main(){gl_FragColor=vec4(sampleAt(uv),1.0);}";
 }
}
