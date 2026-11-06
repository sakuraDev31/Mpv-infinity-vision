//!HOOK MAIN
//!BIND HOOKED
//!DESC REX Vision - Texture-aware Spatial Denoising
float yl(vec3 c){return dot(c,vec3(0.2126,0.7152,0.0722));}
vec4 hook(){vec4 c=HOOKED_tex(HOOKED_pos);float y=yl(c.rgb);vec3 sum=c.rgb*4.0;float ws=4.0,lo=y,hi=y;for(int j=-1;j<=1;++j)for(int i=-1;i<=1;++i){if(i==0&&j==0)continue;vec3 q=HOOKED_texOff(vec2(float(i),float(j))).rgb;float z=yl(q);lo=min(lo,z);hi=max(hi,z);float spatial=(i==0||j==0)?1.0:0.72;float wt=spatial*exp(-abs(z-y)*28.0);sum+=q*wt;ws+=wt;}float flat=1.0-smoothstep(0.018,0.075,hi-lo);return vec4(mix(c.rgb,sum/max(ws,0.0001),0.16*flat),c.a);}
