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

// Kaleidoscopic neon labyrinth zooming towards you. uScale = cell size, uP1 = layers, uP2 = zoom speed,
// uP3 = line sharpness, uAmp = spectrum drive, uRot = slow spin.
float edgeH(vec2 a, vec2 b, float seed) {
    vec2 lo = min(a, b), hi = max(a, b);
    return hash21(lo * 7.13 + hi * 3.71 + seed);
}
float seg(vec2 p, vec2 a, vec2 b) {
    vec2 pa_ = p - a, ba = b - a;
    float h = clamp(dot(pa_, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa_ - ba * h);
}
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p = rot2(p, uRot);
    p = abs(p);                                   // mirror both axes: kaleidoscopic symmetry
    vec3 col = vec3(0.0);
    int nl = int(clamp(uP1, 1.0, 6.0));
    float zoomT = uTime * uP2;
    for (int l = 0; l < 6; l++) {
        if (l >= nl) break;
        float ph = fract(zoomT + float(l) / float(nl));
        float layerId = floor(zoomT + float(l) / float(nl));
        float s = exp2(ph * 2.0) * uScale;       // each layer doubles in size twice per cycle
        vec2 q = p * s + vec2(0.37, 0.21) * (layerId + float(l));
        vec2 c = floor(q);
        vec2 f = fract(q) - 0.5;
        float seed = layerId * 13.7 + float(l) * 5.1;
        float thr = 0.52;
        float eR = edgeH(c, c + vec2(1.0, 0.0), seed);
        float eL = edgeH(c, c - vec2(1.0, 0.0), seed);
        float eU = edgeH(c, c + vec2(0.0, 1.0), seed + 9.0);
        float eD = edgeH(c, c - vec2(0.0, 1.0), seed + 9.0);
        float d = 9.0;
        if (eR > thr) d = min(d, seg(f, vec2(0.0), vec2(0.5, 0.0)));
        if (eL > thr) d = min(d, seg(f, vec2(0.0), vec2(-0.5, 0.0)));
        if (eU > thr) d = min(d, seg(f, vec2(0.0), vec2(0.0, 0.5)));
        if (eD > thr) d = min(d, seg(f, vec2(0.0), vec2(0.0, -0.5)));
        float hs = hash21(c + seed);
        float band = spec(hs);
        float w = (0.006 + 0.010 * band * uAmp) / max(uP3, 0.3);
        float aa = fwidth(d) + 1e-4;
        float core = smoothstep(w + aa, w - aa * 0.5, d);
        float glow = exp(-d * d / (w * w * 5.0)) * 0.22;
        float vis = smoothstep(0.0, 0.18, ph) * smoothstep(1.0, 0.72, ph);     // fade in/out so layers never pop
        vec3 lc = pal(uPhase + hs * uSpread + float(l) * 0.17 + band * 0.15);
        col += (lc * core * 1.25 + lc * glow * 0.6 + vec3(core) * 0.3) * vis * (0.45 + 1.5 * band * uAmp + 0.5 * uBeat);
    }
    finish(col);
}
