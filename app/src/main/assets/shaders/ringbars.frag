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


// Concentric rings of razor-sharp radial bars, dashed outer rings, glass columns and a neon grid floor.
// uScale outer ring radius, uP1 ring count, uAmp bar length, uWidth bar fill, uP2 floor/column strength (0 = off),
// uP3 ring spin speed, uCore brightness, uSpread hue alternation, uCy centre height.
float box(vec2 p, vec2 b) { vec2 q = abs(p) - b; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0); }
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    vec2 pc0 = p;
    p.y -= uCy;
    float R = max(uScale, 0.1);
    float K = clamp(uP1, 2.0, 7.0);
    float r = length(p);
    float ang = atan(p.y, p.x);
    vec3 col = vec3(0.0);
    float fr = fwidth(r) * 1.1;
    for (int k = 0; k < 7; k++) {
        if (float(k) >= K) break;
        float fk = float(k) / max(K - 1.0, 1.0);
        float Rk = R * (0.16 + 0.84 * pow(fk, 1.25) * 0.78 + 0.0) * (0.55 + 0.45 * fk) / (0.55 + 0.45 * 1.0) + 0.04;
        float N = floor(mix(40.0, 120.0, fk));
        float spin = uTime * uP3 * (mod(float(k), 2.0) < 0.5 ? 1.0 : -1.0) * 0.12 / (1.0 + fk * 2.0);
        float u = (ang + spin) / TAU + 0.5;
        float ai = floor(u * N), af = fract(u * N);
        float mb = abs(2.0 * fract((ai + 0.5) / N + 0.25) - 1.0);
        float lvl = pow(clamp(spec(mb * 0.85 + 0.12 * fk), 0.0, 1.4), 1.1) * (0.95 + 0.3 * uBeat);
        float L = 0.008 + lvl * uAmp * (0.05 + 0.11 * (0.4 + fk));
        float rr = r - Rk;
        float da = abs(af - 0.5) * 2.0;
        float fa = max(fwidth(da), 1e-4);
        float fillw = clamp(uWidth, 0.2, 0.95);
        float mAng = 1.0 - smoothstep(fillw - fa, fillw + fa, da);
        float mRad = smoothstep(-fr, fr, rr) * (1.0 - smoothstep(L - fr, L + fr, rr));
        vec3 c = mod(float(k), 2.0) < 0.5 ? pal(uPhase) : pal(uPhase + 0.5 * uSpread);
        col += c * mAng * mRad * (0.8 + 0.5 * lvl) * uCore;
        // thin ring at the base of the bars
        col += c * 0.35 * exp(-pow(rr / (fr * 1.1), 2.0));
    }
    // dashed outer rings
    for (int j = 0; j < 3; j++) {
        float fj = float(j);
        float Rj = R * (1.05 + 0.34 * fj);
        float N = 26.0 + 8.0 * fj;
        float u = (ang + uTime * uP3 * 0.03 * (1.0 + fj) * (mod(fj, 2.0) < 0.5 ? -1.0 : 1.0)) / TAU + 0.5;
        float ai = floor(u * N), af = fract(u * N);
        float hsh = hash11(ai * 1.7 + fj * 13.1);
        float len = mix(0.25, 0.9, hsh);
        float lvl = clamp(spec(hsh * 0.5 + 0.1 * fj), 0.0, 1.2);
        float dashM = 1.0 - smoothstep(len - fwidth(af) * 1.5, len + fwidth(af) * 1.5, af);
        float th = 0.006 + 0.012 * lvl;
        float ring = 1.0 - smoothstep(th, th + fr * 1.2, abs(r - Rj));
        vec3 c = mod(fj, 2.0) < 0.5 ? pal(uPhase + 0.5 * uSpread) : pal(uPhase);
        col += c * ring * dashM * (0.5 + 0.8 * lvl) * step(0.3, hash11(ai + fj * 7.7) + lvl * 0.5);
    }
    // neon grid floor and glass columns (uP2 > 0)
    if (uP2 > 0.01) {
        float hz = -0.36;
        float y = pc0.y - hz;
        if (y < 0.0) {
            float depth = -y;
            float zf = 0.12 / depth;
            float gx = pc0.x * zf * 3.0;
            float gl1 = abs(fract(gx) - 0.5), gl2 = abs(fract(zf * 6.0 + uTime * 0.5) - 0.5);
            float fx = fwidth(gx) + 1e-4, fz2 = fwidth(zf * 6.0) + 1e-4;
            float line = 1.0 - smoothstep(0.0, fx * 1.2, 0.5 - gl1 * 1.0 + 0.0 - 0.5 + gl1 * 0.0 + gl1 - 0.0);
            float lx = 1.0 - smoothstep(0.0, fx * 1.3, abs(fract(gx) - 0.5) * -1.0 + 0.5 - 0.0 + 0.0);
            float vlines = 1.0 - smoothstep(0.0, fx * 1.3, 0.5 - abs(fract(gx) - 0.5));
            float hlines = 1.0 - smoothstep(0.0, fz2 * 1.3, 0.5 - abs(fract(zf * 6.0 + uTime * 0.5) - 0.5));
            float fade = smoothstep(0.0, 0.2, depth) * exp(-zf * 0.0);
            col += mix(pal(uPhase), pal(uPhase + 0.5 * uSpread), step(0.0, pc0.x)) * max(vlines, hlines) * 0.45 * fade * uP2;
        }
        // columns
        for (int i = 0; i < 8; i++) {
            float fi = float(i);
            float side = (i < 4) ? -1.0 : 1.0;
            float idx = mod(fi, 4.0);
            float bx = side * (0.22 + 0.19 * idx) * uAspect * uZoom * 0.55;
            float lvl = clamp(spec(0.06 + 0.2 * idx + (side > 0.0 ? 0.04 : 0.0)), 0.0, 1.2);
            float hgt = (0.06 + lvl * 0.38) * (1.0 - 0.18 * idx);
            vec2 q = vec2(pc0.x - bx, pc0.y - (hz + hgt * 0.5));
            float wcol = (0.028 + 0.004 * (3.0 - idx)) * min(uAspect * uZoom, 1.6) * 0.75;
            float d = box(q, vec2(wcol, hgt * 0.5));
            float fw = fwidth(d) * 1.2 + 1e-5;
            float body = 1.0 - smoothstep(-fw, fw, d);
            float edge = 1.0 - smoothstep(0.0, fw * 1.6, abs(d));
            vec3 c = mod(fi, 2.0) < 0.5 ? pal(uPhase) : pal(uPhase + 0.5 * uSpread);
            float facet = q.x < 0.0 ? 1.0 : 0.55;
            col += (c * body * facet * (0.10 + 0.35 * clamp((pc0.y - hz) / max(hgt, 0.05), 0.0, 1.0))) * uP2;
            col += mix(c, vec3(1.0), 0.6) * edge * 0.9 * uP2;
            // reflection
            vec2 qr = vec2(pc0.x - bx, -(pc0.y - hz) - hgt * 0.5 - 0.0);
            qr.y = pc0.y - (hz - hgt * 0.5);
            float dr = box(qr, vec2(wcol, hgt * 0.5));
            col += c * (1.0 - smoothstep(-fw, fw, dr)) * 0.10 * uP2 * smoothstep(hz - hgt, hz, pc0.y);
        }
    }
    finish(col);
}
