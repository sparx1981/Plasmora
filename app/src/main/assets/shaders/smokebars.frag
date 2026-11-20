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


// Curved bank of LED columns with volumetric smoke rising from the lit cells (particle-smoke look).
// uP1 columns, uScale half width, uAmp smoke height, uCy base line, uP2 smoke speed, uP3 smoke density,
// uCore bar brightness, uSpread hue spread along the bank, uWidth cell gap.
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1, 0)), f.x), mix(hash21(i + vec2(0, 1)), hash21(i + vec2(1, 1)), f.x), f.y); }
float fbm(vec2 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 5; i++) { s += a * vn(p); p = p * 2.07 + 13.1; a *= 0.5; } return s; }
float ridge(vec2 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 4; i++) { s += a * (1.0 - abs(2.0 * vn(p) - 1.0)); p = p * 2.1 + 7.3; a *= 0.5; } return s; }
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    float W = min(max(uScale, 0.1), 0.46 * uAspect * uZoom);
    float N = clamp(uP1, 8.0, 64.0);
    float x01 = clamp(p.x / (2.0 * W) + 0.5, 0.0, 1.0);
    float curve = 0.10 * pow(abs(p.x / W), 2.0);                // the bank bends like a shallow bowl
    float by = p.y - uCy - curve * 0.6;                         // height above the bank base
    vec3 col = vec3(0.0);

    // smoke source strength follows the spectrum under each x
    float e = 0.0;
    for (int k = -2; k <= 2; k++) e += spec(clamp(x01 + float(k) * 0.02, 0.0, 1.0) * 0.9) * (1.0 - abs(float(k)) * 0.18);
    e = pow(clamp(e / 4.2, 0.0, 1.4), 1.1);
    float top = (0.06 + e * uAmp * 0.55) * (0.85 + 0.35 * uBeat);
    float sx = x01 * 6.0;
    float t = uTime * uP2;
    vec2 q = vec2(p.x * 2.3, by * 2.0);
    vec2 w1 = vec2(fbm(q * 1.2 + vec2(0.0, -t * 0.55)), fbm(q * 1.2 + vec2(7.7, -t * 0.45)));
    float dn = fbm(q * 1.5 + 2.6 * w1 + vec2(0.0, -t * 0.6));
    float fil = ridge(q * 2.2 + 3.0 * w1 + vec2(t * 0.1, -t * 0.8));
    float env = smoothstep(top * 1.25, top * 0.15, by) * smoothstep(-0.03, 0.02, by);
    float side = smoothstep(W * 1.25, W * 0.5, abs(p.x));
    float sm = (pow(dn, 2.2) * 0.9 + pow(fil, 3.0) * 0.9) * env * side * uP3;
    vec3 sc = pal(uPhase + x01 * uSpread);
    col += sc * sm * (0.5 + 1.2 * e) + vec3(0.12, 0.16, 0.22) * sm * 0.35;

    // LED columns
    float u = clamp(p.x / (2.0 * W) + 0.5, 0.0, 1.0);
    if (p.x > -W && p.x < W && by < 0.02 && by > -0.30) {
        float colW = 2.0 * W / N;
        float ci = floor((p.x + W) / colW);
        float cf = (p.x + W) / colW - ci;
        float uc = (ci + 0.5) / N;
        float lvl = pow(spec(uc * 0.9), 1.1) * (0.95 + 0.3 * uBeat);
        float cells = 9.0;
        float ch = 0.30 / cells;
        float yy = -by;                                           // downward from the bank top
        float cyi = floor(yy / ch);
        float cyf = fract(yy / ch);
        float lit = step((cyi + 1.0) / cells, 0.04 + lvl);         // levels light from the top row down
        float gap = clamp(uWidth, 0.05, 0.5);
        float aa = 0.06;
        float cell = smoothstep(gap * 0.5, gap * 0.5 + aa, cf) * smoothstep(1.0 - gap * 0.5, 1.0 - gap * 0.5 - aa, cf)
                   * smoothstep(gap * 0.35, gap * 0.35 + aa, cyf) * smoothstep(1.0 - gap * 0.35, 1.0 - gap * 0.35 - aa, cyf);
        float persp = 0.75 + 0.25 * (1.0 - abs(p.x / W));
        vec3 cc = pal(uPhase + uc * uSpread);
        float depth = 1.0 - cyi / cells;
        col += cc * cell * (lit * (0.9 + 1.4 * lvl * uCore) * (0.5 + 0.5 * depth) + 0.035) * persp;
    }
    finish(col);
}
