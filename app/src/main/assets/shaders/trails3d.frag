#version 300 es
precision highp float;
precision highp int;
in vec4 vA;
out vec4 oCol;
uniform float uIntensity, uPhase, uSpread, uCore;
uniform int uKind;
uniform vec3 pa, pb, pc, pd;
vec3 pal(float x) { return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0); }
void main() {
    float v = vA.x;
    float core = exp(-v * v * 7.0);
    float glow = exp(-v * v * 1.6) * 0.35;
    vec3 c = pal(uPhase + vA.y * uSpread);
    vec3 rgb = c * (core * 1.0 + glow) + vec3(1.0) * core * core * uCore * 0.45;
    if (uKind == 1) rgb += vec3(0.08, 0.0, -0.04) * core;
    rgb *= vA.z * uIntensity * (0.7 + 0.5 * sin(vA.w * 6.2831853));
    if (!(rgb.x < 64.0 && rgb.y < 64.0 && rgb.z < 64.0)) rgb = vec3(0.0);
    oCol = vec4(max(rgb, 0.0), 1.0);
}
