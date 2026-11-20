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


// Sharp radial LED bars around a ring with fine volumetric smoke streaming outward from the tips.
// uScale ring radius, uP1 bar count, uAmp bar length, uWidth bar fill (0..1), uP2 smoke speed, uP3 smoke density,
// uCore tip brightness, uSpread hue spread, uRot spin, uCy vertical offset.
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1, 0)), f.x), mix(hash21(i + vec2(0, 1)), hash21(i + vec2(1, 1)), f.x), f.y); }
float fbm(vec2 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 6; i++) { s += a * vn(p); p = p * 2.03 + 11.7; a *= 0.5; } return s; }
float ridge(vec2 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 5; i++) { s += a * (1.0 - abs(2.0 * vn(p) - 1.0)); p = p * 2.11 + 5.3; a *= 0.5; } return s; }
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    vec2 q0 = p;
    p = rot2(p, uRot);
    float R0 = max(uScale, 0.05);
    float N = clamp(uP1, 24.0, 200.0);
    float r = length(p);
    float u = atan(p.y, p.x) / TAU + 0.5;
    float ai = floor(u * N), af = fract(u * N);
    float mb = abs(2.0 * fract((ai + 0.5) / N + 0.25) - 1.0);
    float mc = abs(2.0 * fract(u + 0.25) - 1.0);
    float lvl = pow(clamp(spec(mb * 0.92), 0.0, 1.4), 1.1) * (0.95 + 0.3 * uBeat);
    float lc = pow(clamp(spec(mc * 0.92), 0.0, 1.4), 1.1);
    float L = 0.012 + lvl * uAmp * 0.34;
    float rr = r - R0;
    float fr = fwidth(r) * 1.1;
    vec3 col = vec3(0.0);
    vec3 hue = pal(uPhase + mb * uSpread);

    // bars: crisp cells
    float da = abs(af - 0.5) * 2.0;
    float fa = max(fwidth(da), 1e-4);
    float fillw = clamp(uWidth, 0.2, 0.95);
    float mAng = 1.0 - smoothstep(fillw - fa, fillw + fa, da);
    float mRad = smoothstep(-fr, fr, rr) * (1.0 - smoothstep(L - fr, L + fr, rr));
    float cs = 0.014;
    float cf = fract(rr / cs);
    float cg = smoothstep(0.0, fwidth(rr / cs) * 1.2 + 0.001, cf) * (1.0 - smoothstep(0.82 - fwidth(rr / cs), 0.82 + fwidth(rr / cs), cf));
    float bar = mAng * mRad;
    float tipT = clamp(rr / max(L, 1e-3), 0.0, 1.0);
    vec3 bc = hue * (0.55 + 0.9 * tipT) * mix(1.0, cg, 0.85);
    float cap = bar * smoothstep(L - 0.016, L - 0.004, rr);
    col += bc * bar;
    col += mix(hue, vec3(1.0), 0.65) * cap * uCore * (0.6 + 0.8 * lvl);
    // glow hugging each bar
    col += hue * mAng * 0.18 * exp(-max(rr - L, 0.0) * 55.0) * smoothstep(-0.01, 0.0, rr) * (0.3 + lvl);

    // fine base ring + faint outer guide ring
    float rl = exp(-pow(rr / (fr * 1.2), 2.0));
    col += hue * rl * 0.9 + pal(uPhase + 0.5) * exp(-pow((r - R0 * 1.0 - 0.34) / (fr * 1.2), 2.0)) * 0.10;

    // smoke streaming outward, brightest above loud bars
    float t = uTime * uP2;
    vec2 dir = normalize(q0 + 1e-5);
    vec2 q = q0 * 2.4 - dir * t * 0.9;
    vec2 w1 = vec2(fbm(q * 1.1 + vec2(0.0, t * 0.1)), fbm(q * 1.1 + vec2(6.1, -t * 0.12)));
    float dn = fbm(q * 1.6 + 2.5 * w1);
    float fil = ridge(q * 2.6 + 3.0 * w1 + dir * t * 0.3);
    float H = L + 0.05 + lc * uAmp * 0.30;
    float env = smoothstep(H, L * 0.4, rr) * smoothstep(-0.01, 0.03, rr);
    float sm = (pow(dn, 2.2) * 0.9 + pow(fil, 3.2) * 1.0) * env * uP3 * (0.25 + 1.2 * lc);
    sm *= 1.0 - 0.8 * bar;
    col += pal(uPhase + mc * uSpread + 0.12) * sm * 2.2;
    finish(col);
}
