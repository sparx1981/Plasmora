#version 300 es
precision highp float;
precision highp int;
in vec2 vUv;
out vec4 oCol;

uniform sampler2D uAudio;     // row 2 = spectrum
uniform sampler2D uHist;      // 256 x 40 spectrum history (row 0 = newest)
uniform float uAspect, uPix;  // uPix = size of one pixel in screen-height units
uniform int   uMode;          // 0 = scrolling mesh, 1 = synthwave valley, 2 = ripple plane
uniform int   uStyle;         // 0 = wire, 1 = dots
uniform float uTravel, uFrac, uAmp, uCells, uLineW, uCamH, uPitch, uRot;
uniform float uBeat, uBass;
uniform float uIntensity, uPhase, uSpread, uHueLevel;
uniform vec3 pa, pb, pc, pd;

const float TAU = 6.2831853;
const float ZSTEP = 0.22;     // world depth of one history row
const float XW = 3.4;         // half width of the mesh

vec3 pal(float x) { return clamp(pa + pb * cos(TAU * (pc * x + pd)), 0.0, 3.0); }

float spec(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x);
    int j = min(i + 1, 1023);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, x - float(i));
}
float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

float height(vec2 xz) {
    if (uMode == 0) {
        float v = (xz.y / ZSTEP - uFrac + 0.5) / 40.0;
        if (v < 0.0 || v > 1.0) return 0.0;
        float ax = abs(xz.x) / XW;
        float h = texture(uHist, vec2(pow(min(ax, 1.0), 1.4) * 0.9, v)).r;
        return h * uAmp * smoothstep(1.0, 0.65, ax) * smoothstep(1.0, 0.85, v);
    } else if (uMode == 1) {
        float ax = abs(xz.x);
        float w = smoothstep(0.55, 1.6, ax);
        vec2 q = vec2(xz.x * 0.85, xz.y * 0.85 + uTravel);
        float m = vnoise(q) * 0.65 + vnoise(q * 2.1) * 0.3;
        float s = spec(clamp((ax - 0.5) / 5.0, 0.0, 1.0) * 0.8 + 0.02);
        return w * (0.15 + 1.5 * m * m + s * uAmp * 1.6) * (1.0 + 0.25 * uBeat) * 0.55;
    } else {
        vec2 q = xz - vec2(0.0, 3.6);
        float r = length(q);
        float s = spec(clamp(r / 4.2, 0.0, 1.0) * 0.8 + 0.01);
        float env = smoothstep(4.4, 3.2, r);
        return env * (s * uAmp + 0.07 * uBass * sin(r * 5.0 - uTravel * 2.0) * exp(-r * 0.35));
    }
}

void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
    float fit = min(uAspect, 1.0);
    vec2 pp = p / fit;
    vec3 ro = vec3(0.0, uCamH, 0.0);
    vec3 rd = normalize(vec3(pp.x, pp.y, 1.15));
    float cs = cos(uPitch), sn = sin(uPitch);
    rd.yz = mat2(cs, sn, -sn, cs) * rd.yz;   // positive pitch looks down

    float tMax = 15.0;
    float t = 0.15, tPrev = 0.15;
    bool hit = false;
    for (int i = 0; i < 72; i++) {
        vec3 q = ro + rd * t;
        float d = q.y - height(q.xz);
        if (d < 0.0) { hit = true; break; }
        tPrev = t;
        t += max(0.03 + 0.012 * t, d * 0.5);
        if (t > tMax) break;
    }

    vec3 rgb = vec3(0.0);
    if (hit) {
        float a = tPrev, b = t;
        for (int k = 0; k < 5; k++) {
            float m = 0.5 * (a + b);
            vec3 q = ro + rd * m;
            if (q.y < height(q.xz)) b = m; else a = m;
        }
        t = 0.5 * (a + b);
        vec3 P = ro + rd * t;
        float h = height(P.xz);
        float hn = clamp(h / max(uAmp, 1e-3), 0.0, 1.5);

        vec2 q; vec2 cw;
        if (uMode == 0) { q = vec2(P.x * uCells, P.z / ZSTEP - uFrac); cw = vec2(1.0 / uCells, ZSTEP); }
        else if (uMode == 1) { q = vec2(P.x, P.z + uTravel) * uCells; cw = vec2(1.0 / uCells); }
        else {
            float c = cos(uRot), s = sin(uRot);
            vec2 g = mat2(c, -s, s, c) * (P.xz - vec2(0.0, 3.6));
            q = g * uCells; cw = vec2(1.0 / uCells);
        }
        vec2 f = abs(fract(q + 0.5) - 0.5) * cw;
        float pw = uPix * t / (fit * 1.15) * uLineW;
        float m;
        if (uStyle == 0) m = 1.0 - smoothstep(0.0, pw * 1.6, min(f.x, f.y));
        else {
            float dd = length(f);
            m = 1.0 - smoothstep(pw * 0.9, pw * 2.0, dd);
        }
        float fade = exp(-t * 0.13);
        vec3 col = pal(uPhase + hn * uHueLevel + P.z * 0.02 * uSpread + abs(P.x) * 0.06 * uSpread);
        float br = (0.30 + 1.25 * hn) * (1.0 + 0.5 * uBeat * hn);
        rgb = col * (m * br + 0.025) * fade;
    } else if (uMode == 1) {
        // synthwave sky and striped sun
        float sunY = 1.15 * tan(uPitch) + 0.2, r = 0.26;
        vec2 sp = pp - vec2(0.0, sunY);
        float sd = length(sp);
        float disc = smoothstep(r, r - 0.012, sd);
        float gap = clamp(-sp.y / r, 0.0, 1.0) * 0.55;
        float slit = smoothstep(gap - 0.03, gap + 0.03, fract(pp.y * 24.0));
        float vv = clamp(sp.y / r * 0.5 + 0.5, 0.0, 1.0);
        vec3 sun = mix(pal(uPhase + 0.35), pal(uPhase + 0.05) + vec3(0.3), vv);
        rgb = sun * disc * mix(1.0, slit, step(sp.y, 0.0)) * 0.8;
        rgb += pal(uPhase + 0.2) * exp(-sd * 3.0) * 0.14 * (1.0 + 0.5 * uBass);
        rgb += pal(uPhase + 0.6) * 0.10 * exp(-abs(rd.y) * 7.0);
    }
    oCol = vec4(rgb * uIntensity, 1.0);
}
