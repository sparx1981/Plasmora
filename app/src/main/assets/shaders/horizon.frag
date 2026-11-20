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


// A glowing globe horizon: fine spectrum bars along the arc with light rays streaming up from loud bars.
// uScale globe radius, uP1 bar count, uAmp bar height, uP2 ray length, uP3 ray strength, uCore tip glow,
// uSpread hue spread, uCy arc centre height (negative = arc near the bottom), uRot sway.
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    float R = max(uScale, 0.3);
    vec2 c0 = vec2(0.0, uCy - R + 0.0);
    vec2 q = rot2(p - c0, uRot * 0.0);
    float r = length(q);
    float a = atan(q.x, q.y);                      // 0 straight up, +right
    float maxA = 0.5 * uAspect * uZoom / R * 1.25 + 0.2;
    float m = clamp(abs(a) / maxA, 0.0, 1.0);
    float N = max(floor(uP1 * clamp(uAspect / 1.6, 0.22, 1.0)), 36.0);   // keep bars the same pixel width in portrait
    float ai = floor((a / maxA * 0.5 + 0.5) * N), af = fract((a / maxA * 0.5 + 0.5) * N);
    float mb = clamp(abs((ai + 0.5) / N * 2.0 - 1.0), 0.0, 1.0);
    float lvl = pow(clamp(spec((1.0 - mb) * 0.9), 0.0, 1.4), 1.15) * (0.95 + 0.3 * uBeat);
    float rr = r - R;
    float fr = fwidth(r) * 1.1;
    float L = 0.004 + lvl * uAmp * 0.30;
    float da = abs(af - 0.5) * 2.0;
    float fa = max(fwidth(da), 1e-4);
    float mAng = 1.0 - smoothstep(0.62 - fa, 0.62 + fa, da);
    float mRad = smoothstep(-fr, fr, rr) * (1.0 - smoothstep(L - fr, L + fr, rr));
    vec3 hue = pal(uPhase + (1.0 - mb) * uSpread * 0.5);
    vec3 col = vec3(0.0);
    float cs = 0.011;
    float cf = fract(rr / cs);
    float cell = smoothstep(0.0, 0.12, cf) * (1.0 - smoothstep(0.78, 0.9, cf));
    col += mix(hue, vec3(1.0), 0.45) * mAng * mRad * mix(1.0, cell, 0.6) * (0.7 + 0.7 * lvl);
    col += vec3(1.0) * mAng * smoothstep(L - 0.01, L, rr) * mRad * lvl * uCore;
    // rays above the arc: narrow streaks that fade with height
    float rays = 0.0;
    for (int i = 0; i < 2; i++) {
        float f = float(i);
        float N2 = N * (0.5 - 0.25 * f);
        float u2 = (a / maxA * 0.5 + 0.5) * N2;
        float id = floor(u2);
        float x = abs(fract(u2) - 0.5) * 2.0;
        float mb2 = clamp(abs((id + 0.5) / N2 * 2.0 - 1.0), 0.0, 1.0);
        float lv2 = pow(clamp(spec((1.0 - mb2) * 0.9), 0.0, 1.4), 1.3);
        float fa2 = max(fwidth(x), 1e-4);
        float streak = (1.0 - smoothstep(0.12 - fa2, 0.12 + fa2 * 4.0, x));
        float reach = (0.08 + lv2 * uP2) * (1.0 + 0.5 * hash11(id));
        rays += streak * lv2 * exp(-max(rr, 0.0) / reach) * step(0.0, rr);
    }
    col += pal(uPhase + 0.2) * rays * uP3 * 0.7;
    // globe: deep glow inside, bright rim just under the bars
    float inside = smoothstep(0.0, -0.06, rr);
    col += pal(uPhase + 0.1) * inside * (0.05 + 0.10 * exp(rr * 3.0)) * (0.6 + 0.4 * lvl);
    col += pal(uPhase + 0.15) * exp(-pow((rr + 0.01) / 0.03, 2.0)) * 0.55 * (0.5 + 0.8 * lvl);
    col += pal(uPhase + 0.25) * exp(rr * 2.2) * 0.06 * step(rr, 0.0);
    finish(col);
}
