#version 300 es
precision highp float;
in vec2 vLine;
in float vT;
out vec4 oCol;

uniform float uIntensity;
uniform float uPhase;
uniform float uSpread;
uniform float uCore;
uniform vec3 pa, pb, pc, pd;

vec3 pal(float x) {
    return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0);
}

void main() {
    float d = abs(vLine.x);
    float glow = exp(-d * d * 3.2);
    float core = exp(-d * d * 18.0);
    vec3 col = pal(uPhase + vT * uSpread);
    vec3 rgb = col * glow * 0.65 + (col * 0.35 + vec3(0.65)) * core * uCore;
    oCol = vec4(rgb * uIntensity, 1.0);
}
