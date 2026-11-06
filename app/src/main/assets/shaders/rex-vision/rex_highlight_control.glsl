//!HOOK MAIN
//!BIND HOOKED
//!DESC REX Vision - Highlight Roll-off and Chromaticity Preservation
float yl(vec2 o){return dot(HOOKED_texOff(o).rgb,vec3(0.2126,0.7152,0.0722));}
vec4 hook(){vec4 c=HOOKED_tex(HOOKED_pos);float y=dot(c.rgb,vec3(0.2126,0.7152,0.0722));if(y<=0.0||y>1.05)return c;float m=(yl(vec2(-1,0))+yl(vec2(1,0))+yl(vec2(0,-1))+yl(vec2(0,1)))*0.25,hi=smoothstep(0.78,0.98,y),isolated=smoothstep(0.025,0.14,max(y-m,0.0));float shoulder=0.78+0.22*(1.0-exp(-max(y-0.78,0.0)/0.22)),roll=mix(y,shoulder,0.12*hi),outY=mix(roll,min(roll,y-min(max(y-m,0.0)*0.035,0.006)),0.20*isolated*hi);return vec4(c.rgb*(outY/max(y,0.0001)),c.a);}
