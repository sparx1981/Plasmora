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


// LED dot-matrix equaliser on a glossy grid floor with a mirrored reflection and drifting bokeh.
// uScale half width, uP1 columns, uAmp height, uP2 dot rows per unit height, uP3 bokeh amount, uCore sparkle,
// uSpread hue spread across the bars, uCy horizon height.
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    float hz = uCy;
    float W = max(uScale, 0.2) * uAspect * uZoom * 0.5;
    float N = clamp(uP1, 8.0, 80.0);
    float cw = 2.0 * W / N;
    vec3 col = vec3(0.0);
    float y = p.y - hz;
    bool refl = y < 0.0;
    float yy = refl ? -y * 1.0 : y;               // mirrored coordinate
    float x = p.x;
    float ci = floor((x + W) / cw);
    float cf = (x + W) / cw - ci;
    if (ci >= 0.0 && ci < N) {
        float uc = (ci + 0.5) / N;
        float lvl = pow(clamp(spec(uc * 0.9), 0.0, 1.4), 1.05) * (0.95 + 0.3 * uBeat);
        float H = 0.03 + lvl * uAmp * 0.5;
        float rowH = cw;                                     // square dot pitch
        float ri = floor(yy / rowH);
        float rf = fract(yy / rowH);
        vec2 dp = vec2(cf - 0.5, rf - 0.5);
        float rad = 0.40;
        float dd = length(dp);
        float fw = fwidth(dd) * 1.2 + 1e-4;
        float dot_ = 1.0 - smoothstep(rad - fw, rad + fw, dd);
        float lit = step((ri + 0.5) * rowH, H);
        float topRow = lit * (1.0 - step((ri + 1.5) * rowH, H));
        vec3 hue = pal(uPhase + uc * uSpread);
        vec3 c = hue * (0.18 + 0.95 * lit) * (0.55 + 0.45 * (1.0 - ri * rowH / max(H, 0.05)));
        c += mix(hue, vec3(1.0), 0.7) * topRow * uCore * 0.9;
        float fade = refl ? 0.32 * exp(-yy * 5.5) : 1.0;
        col += c * dot_ * fade;
        col += hue * exp(-dd * 4.0) * 0.07 * lit * fade;
    }
    // glossy perspective floor grid below the horizon
    if (refl) {
        float depth = -y;
        float zf = 0.05 / (depth + 0.01);
        float gx = p.x * zf * 1.6;
        float fx = fwidth(gx) + 1e-4, fz = fwidth(zf * 3.0) + 1e-4;
        float vl = 1.0 - smoothstep(0.0, fx * 1.3, 0.5 - abs(fract(gx) - 0.5));
        float hl = 1.0 - smoothstep(0.0, fz * 1.3, 0.5 - abs(fract(zf * 3.0 - uTime * 0.2) - 0.5));
        float ux = clamp((p.x + W) / (2.0 * W), 0.0, 1.0);
        col += pal(uPhase + ux * uSpread) * max(vl, hl) * 0.28 * smoothstep(0.0, 0.25, depth) * exp(-depth * 1.2);
    }
    // bokeh drifting down
    float bokeh = uP3;
    if (bokeh > 0.01) {
        for (int L = 0; L < 2; L++) {
            float sc = 7.0 + 5.0 * float(L);
            vec2 bp = vec2(p.x * sc, p.y * sc + uTime * (0.25 + 0.2 * float(L)));
            vec2 bi = floor(bp), bf = fract(bp) - 0.5;
            float hs = hash21(bi + float(L) * 31.0);
            vec2 off = (vec2(hash21(bi + 3.1), hash21(bi + 7.7)) - 0.5) * 0.5;
            float bd = length(bf - off);
            float rad = 0.06 + 0.10 * hs;
            float disc = 1.0 - smoothstep(rad, rad + 0.04, bd);
            float live = step(0.72, hs) * clamp(spec(hash21(bi) * 0.8), 0.0, 1.0);
            col += pal(uPhase + hs * uSpread) * disc * live * 0.25 * bokeh;
        }
    }
    finish(col);
}
