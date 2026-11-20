#version 300 es
precision highp float;
precision highp int;
in vec2  vLine;
in float vT;
in float vDepth;
out vec4 oCol;

uniform int   uPass;
uniform float uAlpha, uPhase, uSpread, uIntensity, uDepthHue;
uniform vec3 pa, pb, pc, pd;

vec3 pal(float x) {
    return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0);
}

void main() {
    if (uPass == 0) { oCol = vec4(0.0, 0.0, 0.0, uAlpha); return; }
    float d = abs(vLine.x);
    float glow = exp(-d * d * 3.0);
    float core = exp(-d * d * 14.0);
    vec3 col = pal(uPhase + vT * uSpread + vDepth * uDepthHue);
    float fade = pow(1.0 - vDepth, 0.8);
    vec3 rgb = (col * 0.85 + vec3(0.5) * core) * uIntensity;
    oCol = vec4(rgb, glow * fade * uAlpha);
}
