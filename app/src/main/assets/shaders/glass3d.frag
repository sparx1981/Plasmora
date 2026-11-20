#version 300 es
precision highp float;
precision highp int;
in vec4 vA;
in vec4 vK;
out vec4 oCol;
uniform float uIntensity, uPhase, uSpread, uHueLevel, uCore;
uniform vec3 pa, pb, pc, pd;
vec3 pal(float x) { return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0); }
void main() {
    vec3 rgb;
    if (vK.x > 0.5) {                                   // floor ring
        float v = abs(vA.x);
        float core = exp(-v * v * 5.0);
        rgb = pal(uPhase + 0.1) * (core + 0.35 * exp(-v * v * 1.2)) * 1.2 * uCore;
    } else {
        vec2 uv = vA.xy;
        vec2 fw = max(fwidth(uv), vec2(1e-4));
        float edgeD = (1.0 - max(abs(uv.x), abs(uv.y))) / max(fw.x, fw.y);   // ~pixels from the face edge
        float line = 1.0 - smoothstep(0.5, 1.5, edgeD);
        float glow = exp(-edgeD * 0.6) * 0.05;
        float hh = vA.z;
        vec3 hue = pal(uPhase + vA.w * uSpread * 0.3 + hh * uHueLevel);
        vec3 hue2 = pal(uPhase + 0.5 * uSpread + hh * uHueLevel * 0.5);
        vec3 edge = mix(hue2, hue, smoothstep(0.15, 0.7, hh));
        float top = step(0.5, 1.0 - abs(vK.z));          // face 0 is the top cap
        vec3 glass = mix(vec3(0.55, 0.62, 0.72), hue, 0.30) * (0.025 + 0.04 * (1.0 - hh) + 0.03 * vK.w);
        rgb = edge * (line * 1.2 + glow) * (0.5 + 0.55 * vK.w) + glass;
        if (vK.z < 0.5) rgb += mix(edge, vec3(1.0), 0.6) * 0.12 * (0.4 + vK.w);   // top cap
        if (vK.y > 0.5) rgb *= 0.28 * (1.0 - hh * 0.6);   // reflection
    }
    rgb *= uIntensity;
    if (!(rgb.x < 64.0 && rgb.y < 64.0 && rgb.z < 64.0)) rgb = vec3(0.0);
    oCol = vec4(max(rgb, 0.0), 1.0);
}
