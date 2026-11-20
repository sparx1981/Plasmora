#version 300 es
precision highp float;
precision highp int;
in vec2 vUv;
out vec4 oCol;
uniform sampler2D uAudio;     // row 2 = spectrum
uniform float uZoom, uAspect, uTime, uBeat, uBass, uKick;
uniform float uIntensity, uPhase, uSpread, uAmp, uScale, uWidth, uRot, uP1, uP2, uP3, uCore, uCy;
uniform vec3 pa, pb, pc, pd;
const float TAU = 6.2831853;
const float PI = 3.1415927;
vec3 pal(float x) { return clamp(pa + pb * cos(TAU * (pc * x + pd)), 0.0, 3.0); }
float tap(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x); int j = min(i + 1, 1023);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, x - float(i));
}
// spectrum 0..1 (log-ish mapping so bass gets room), lightly smoothed
float spec(float s) {
    s = clamp(s, 0.0, 1.0);
    float u = pow(s, 1.6) * 0.5;
    float a = 0.0, w = 0.0;
    for (int k = -3; k <= 3; k++) { float ww = exp(-float(k * k) / 6.0); a += tap(u + float(k) * 0.003) * ww; w += ww; }
    return a / w;
}
float hash11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float hash21(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
vec2 rot2(vec2 p, float a) { float c = cos(a), s = sin(a); return vec2(c * p.x - s * p.y, s * p.x + c * p.y); }
void finish(vec3 col) {
    col *= uIntensity;
    if (!(col.x < 64.0 && col.y < 64.0 && col.z < 64.0)) col = vec3(0.0);
    oCol = vec4(max(col, 0.0), 1.0);
}


// Endless neon polygon tunnel. uP1 sides (3..8), uP2 frame count, uP3 travel, uScale frame size,
// uRot twist, uAmp spectrum drive, uCore shards, uCy floor level (<-1 = none), uSpread hue spread.
float polyD(vec2 q, float n) {
    float sec = TAU / n;
    float a = atan(q.y, q.x);
    float a2 = mod(a + sec * 0.5, sec) - sec * 0.5;
    return length(q) * cos(a2);
}
vec3 tunnel(vec2 p, float att) {
    vec3 col = vec3(0.0);
    float n = floor(clamp(uP1, 3.0, 8.0) + 0.5);
    int K = int(clamp(uP2, 4.0, 16.0));
    float travel = uP3;
    float off = fract(travel);
    for (int k = 0; k < 16; k++) {
        if (k >= K) break;
        float depth = (float(k) + 1.0 - off);                 // 0.. K
        float z = depth * 0.42;
        float id = float(k) + floor(travel);
        float hsh = hash11(id * 2.3 + 1.7);
        float twist = uRot * depth + 0.35 * sin(id * 0.7);
        vec2 q = rot2(p, twist) * z / max(uScale, 0.05);
        float d = polyD(q, n);
        float sp = spec(fract(id * 0.137 + 0.05) * 0.85 + 0.03);
        float wdt = (0.005 + 0.010 * sp * uAmp) * (1.0 + 0.4 * uBeat) * (1.0 + 0.5 / z);
        float g = exp(-(d - 1.0) * (d - 1.0) / (wdt * wdt)) + 0.14 * exp(-(d - 1.0) * (d - 1.0) / (wdt * wdt * 16.0));
        float fadeZ = smoothstep(0.1, 0.6, z) * exp(-z * 0.17);
        vec3 fc = pal(uPhase + hsh * uSpread + sp * 0.2);
        col += fc * g * fadeZ * (0.8 + 1.5 * sp * uAmp);
        // chunky shards flanking the frame
        if (uCore > 0.01) {
            float a = atan(q.y, q.x);
            float bins = n * 4.0;
            float bi = floor((a / TAU + 0.5) * bins + id);
            float hs = hash21(vec2(bi, id));
            float inShell = smoothstep(1.02, 1.08, d) * smoothstep(1.55, 1.2, d);
            float cell = step(0.62, hs) * inShell * (0.35 + 0.65 * fract(hs * 7.0 + uBeat));
            col += fc * cell * fadeZ * uCore * (0.10 + 0.45 * sp);
        }
    }
    return col * att;
}
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= 0.0;
    vec3 col = tunnel(p, 1.0);
    if (uCy > -0.9) {
        // glossy floor: mirror the tunnel below the floor line and tint it
        float fl = uCy;
        if (p.y < fl) {
            vec2 m = vec2(p.x, 2.0 * fl - p.y);
            float dist = fl - p.y;
            col = tunnel(m, 0.75 * exp(-dist * 2.5));
            col += pal(uPhase + 0.05) * 0.35 * exp(-dist * 3.0) * (0.5 + 0.5 * uBass);
            col += pal(uPhase + 0.1) * exp(-dist * dist * 400.0) * 0.5;
        }
    }
    // central depth glow
    col += pal(uPhase + 0.3) * exp(-dot(p, p) * 40.0) * 0.08 * (0.5 + uBass);
    finish(col);
}
