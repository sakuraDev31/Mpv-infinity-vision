//!HOOK MAIN
//!BIND HOOKED
//!DESC REX Vision - Scharr Multi-scale Edge-aware Sharpen
float yl(vec2 o){return dot(HOOKED_texOff(o).rgb,vec3(0.2126,0.7152,0.0722));}
vec4 hook(){vec4 c=HOOKED_tex(HOOKED_pos);float y=dot(c.rgb,vec3(0.2126,0.7152,0.0722));float nw=yl(vec2(-1,-1)),n=yl(vec2(0,-1)),ne=yl(vec2(1,-1)),w=yl(vec2(-1,0)),e=yl(vec2(1,0)),sw=yl(vec2(-1,1)),s=yl(vec2(0,1)),se=yl(vec2(1,1));float gx=(3.0*(ne+se-nw-sw)+10.0*(e-w))/32.0,gy=(3.0*(sw+se-nw-ne)+10.0*(s-n))/32.0;float edge=length(vec2(gx,gy)),fine=y-(4.0*y+n+s+e+w)/8.0,broad=y-(4.0*y+2.0*(n+s+e+w)+nw+ne+sw+se)/16.0,detail=0.68*fine+0.32*broad;float conf=smoothstep(0.004,0.045,edge)*smoothstep(0.0025,0.014,abs(detail));float delta=clamp(detail*0.42*conf,-0.018,0.018);float lo=min(y,min(min(nw,n),min(ne,min(w,min(e,min(sw,min(s,se))))))),hi=max(y,max(max(nw,n),max(ne,max(w,max(e,max(sw,max(s,se)))))));return vec4(c.rgb+vec3(clamp(y+delta,lo-0.006,hi+0.006)-y),c.a);}
