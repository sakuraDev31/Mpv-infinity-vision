//!HOOK MAIN
//!BIND HOOKED
//!DESC REX Vision - Local SDR Highlight Shoulder
float yl(vec2 o){return dot(HOOKED_texOff(o).rgb,vec3(0.2126,0.7152,0.0722));}
vec4 hook(){vec4 c=HOOKED_tex(HOOKED_pos);float y=dot(c.rgb,vec3(0.2126,0.7152,0.0722));if(y<=0.0||y>1.05)return c;float m=(4.0*y+2.0*(yl(vec2(0,-1))+yl(vec2(0,1))+yl(vec2(-1,0))+yl(vec2(1,0)))+yl(vec2(-1,-1))+yl(vec2(1,-1))+yl(vec2(-1,1))+yl(vec2(1,1)))/16.0;float shoulder=0.72+0.28*(1.0-exp(-max(y-0.72,0.0)/0.28)),detail=clamp((y-m)*0.025,-0.008,0.008),hi=smoothstep(0.70,0.98,y);float outY=mix(y,shoulder+detail,0.18*hi);return vec4(c.rgb*(outY/max(y,0.0001)),c.a);}
