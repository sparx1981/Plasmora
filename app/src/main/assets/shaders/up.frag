#version 300 es
precision highp float;
in vec2 vUv;
out vec4 oCol;
uniform sampler2D uSrc;
uniform vec2 uTexel;   // texel size of the (smaller) source
uniform float uWeight;

void main() {
    vec2 uv = vUv;
    vec2 d = uTexel;
    vec3 s = texture(uSrc, uv).rgb * 4.0;
    s += texture(uSrc, uv + vec2(-d.x, 0.0)).rgb * 2.0;
    s += texture(uSrc, uv + vec2( d.x, 0.0)).rgb * 2.0;
    s += texture(uSrc, uv + vec2(0.0, -d.y)).rgb * 2.0;
    s += texture(uSrc, uv + vec2(0.0,  d.y)).rgb * 2.0;
    s += texture(uSrc, uv + vec2(-d.x, -d.y)).rgb;
    s += texture(uSrc, uv + vec2( d.x, -d.y)).rgb;
    s += texture(uSrc, uv + vec2(-d.x,  d.y)).rgb;
    s += texture(uSrc, uv + vec2( d.x,  d.y)).rgb;
    oCol = vec4(s * (1.0 / 16.0) * uWeight, 1.0);
}
