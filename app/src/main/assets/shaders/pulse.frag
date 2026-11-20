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


// Cherenkov-style pulse: a pyramid of glowing bars standing in still water, mirrored below, with ripple rings.
// uP1 columns, uScale half-width (screen height units), uAmp bar height, uCy water line, uWidth bar fill (0..1),
// uP2 ripple density, uP3 ripple travel, uCore ripple strength, uSpread hue spread.
float barsAt(vec2 p, float W, float N, out float cap, out float hh) {
    float u = p.x / (2.0 * W) + 0.5;
    cap = 0.0; hh = 0.0;
    if (u < 0.0 || u > 1.0) return 0.0;
    float fi = u * N;
    float i = floor(fi);
    float f = fract(fi);
    float uc = (i + 0.5) / N;
    float s = abs(2.0 * uc - 1.0);
    float h = uAmp * (0.03 + pow(spec(s * 0.92), 1.15) * (1.0 - 0.55 * s * s)) * (0.92 + 0.3 * uBeat);
    h *= 0.8 + 0.4 * hash11(i * 1.9);
    float fill = clamp(uWidth, 0.2, 0.95);
    float aa = max(fwidth(fi), 1e-3);
    float inX = smoothstep(0.5 - fill * 0.5 - aa, 0.5 - fill * 0.5 + aa, f) * smoothstep(0.5 + fill * 0.5 + aa, 0.5 + fill * 0.5 - aa, f);
    float y = p.y;
    float inY = smoothstep(h + 0.004, h - 0.004, y) * step(0.0, y);
    cap = smoothstep(h - 0.03, h, y) * inY;
    hh = h;
    return inX * inY;
}
vec3 barColor(float b, float cap, float h, float y, float u) {
    vec3 c = pal(uPhase + h * uSpread + abs(u - 0.5) * 0.3);
    float g = 0.25 + 0.75 * clamp(y / max(h, 0.02), 0.0, 1.0);
    return (c * g * 1.25 + mix(c, vec3(1.0), 0.5) * cap * 0.9) * b;
}
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    float W = max(uScale, 0.1);
    float N = clamp(uP1, 9.0, 121.0);
    vec3 col = vec3(0.0);
    float cap, h;
    if (p.y >= 0.0) {
        float b = barsAt(p, W, N, cap, h);
        col += barColor(b, cap, h, p.y, p.x / (2.0 * W) + 0.5);
        // soft glow above the bars
        float glowY = exp(-max(p.y - h, 0.0) * 14.0) * 0.0;
        col += pal(uPhase) * 0.04 * exp(-p.y * 3.0) * smoothstep(W * 1.4, 0.0, abs(p.x));
    } else {
        // reflection in rippled water
        float q = -p.y;
        float wob = sin(q * 38.0 - uTime * 1.6) * 0.0015 * (1.0 + uBeat) + sin(q * 15.0 + uTime) * 0.002;
        vec2 rp = vec2(p.x + wob, q);
        float b = barsAt(rp, W, N, cap, h);
        float fade = exp(-q * 4.2) * 0.62;
        col += barColor(b, cap * 0.4, h, q, p.x / (2.0 * W) + 0.5) * fade;
        // bright waterline glow
        col += pal(uPhase + 0.1) * exp(-q * q * 600.0) * 0.5 * (0.5 + uBass);
    }
    // perspective ripple rings on the water, launched from the centre
    float py = -p.y;
    if (py > -0.02) {
        vec2 e = vec2(p.x, max(py, 0.0) * 3.2 + 0.002);
        float r = length(e);
        float rings = 0.0;
        for (int k = 0; k < 4; k++) {
            float ph = fract(uTime * 0.12 * (0.6 + uP3 * 0.0) + float(k) * 0.25 + uP3 * 0.0);
            float rr = ph * uP2 * 0.30;
            float wdt = 0.012 + ph * 0.05;
            float d = r - rr;
            rings += exp(-d * d / (wdt * wdt)) * (1.0 - ph) * (1.0 - ph);
        }
        float wmask = smoothstep(-0.02, 0.05, py);
        col += pal(uPhase + 0.12) * rings * uCore * wmask * (0.5 + 0.9 * uBass + 0.5 * uBeat) * (0.7 + 0.5 * exp(-r * 2.0));
    }
    // floating specks
    vec2 g = vUv * vec2(uAspect, 1.0) * 28.0 + vec2(0.0, -uTime * 0.2);
    vec2 gi = floor(g);
    float hs = hash21(gi);
    vec2 gf = fract(g) - 0.5 - (vec2(hash21(gi + 3.0), hash21(gi + 7.0)) - 0.5) * 0.6;
    col += mix(pal(uPhase + hs), vec3(1.0), 0.4) * step(0.965, hs) * smoothstep(0.08, 0.0, length(gf)) * (0.4 + 0.6 * uBeat);
    finish(col);
}
