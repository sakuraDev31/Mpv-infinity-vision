//!HOOK MAIN
//!BIND HOOKED
//!DESC REX Vision - Conservative Edge Overshoot Cleanup
float yl(vec2 o){return dot(HOOKED_texOff(o).rgb,vec3(0.2126,0.7152,0.0722));}
vec4 hook(){vec4 c=HOOKED_tex(HOOKED_pos);float y=dot(c.rgb,vec3(0.2126,0.7152,0.0722));float n=yl(vec2(0,-1)),s=yl(vec2(0,1)),w=yl(vec2(-1,0)),e=yl(vec2(1,0)),nw=yl(vec2(-1,-1)),ne=yl(vec2(1,-1)),sw=yl(vec2(-1,1)),se=yl(vec2(1,1));float lo=min(min(min(n,s),min(w,e)),min(min(nw,ne),min(sw,se))),hi=max(max(max(n,s),max(w,e)),max(max(nw,ne),max(sw,se)));float edge=smoothstep(0.012,0.075,hi-lo),outlier=smoothstep(0.0015,0.012,max(y-hi,lo-y));float clean=mix(y,clamp(y,lo,hi),0.24*edge*outlier);return vec4(c.rgb+vec3(clean-y),c.a);}
