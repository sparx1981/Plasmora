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

// Racing neon frames over a synthwave grid floor. uScale = floor height, uP1 = frame count, uP2 = frame spacing,
// uP3 = travel (camera distance), uAmp = spectrum drive, uCy = horizon height, uCore = streak amount.
float lineGlow(float d, float w) { return exp(-d * d / (w * w)); }
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    p = rot2(p, uRot);
    float f = 0.9;                       // focal length
    float camH = max(uScale, 0.2);
    vec3 col = vec3(0.0);
    float travel = uP3;

    // --- floor grid (below the horizon)
    if (p.y < -0.0005) {
        vec3 rd = vec3(p.x, p.y, f);
        float t = camH / -rd.y;
        vec2 w = vec2(rd.x * t, rd.z * t + travel);
        float dist = t;
        float fade = 1.0 / (1.0 + dist * 0.20);
        float px = abs(fract(w.x * 0.5 + 0.5) - 0.5) * 2.0;     // 0 at line
        float pz = abs(fract(w.y * 0.5) - 0.5) * 2.0;
        float aaX = fwidth(w.x) * 0.5 + 1e-3, aaZ = fwidth(w.y) * 0.5 + 1e-3;
        float gx = smoothstep(0.045 + aaX, 0.0, px);
        float gz = smoothstep(0.045 + aaZ, 0.0, pz);
        float band = spec(clamp(abs(w.x) / 9.0, 0.0, 1.0));
        vec3 cx = pal(uPhase + 0.05 + band * 0.25);
        vec3 cz = pal(uPhase + uSpread);
        col += (cx * gx * (0.55 + 1.6 * band * uAmp) + cz * gz * 0.7) * fade * 1.6;
        col += pal(uPhase + 0.5) * 0.05 * fade * smoothstep(0.0, 0.4, -p.y) ;
    }
    // --- horizon haze
    float hz = exp(-abs(p.y) * 9.0);
    col += pal(uPhase + 0.2) * hz * (0.22 + 0.25 * uBass) * exp(-abs(p.x) * 0.7);

    // --- frames
    int n = int(clamp(uP1, 2.0, 16.0));
    float spacing = max(uP2, 0.2);
    float off = fract(travel / spacing);
    for (int k = 0; k < 16; k++) {
        if (k >= n) break;
        float z = (float(k) - off) * spacing + 0.35;
        if (z < 0.25) continue;
        float id = float(k) + floor(travel / spacing);
        float hsh = hash11(id * 1.37 + 3.1);
        float half_ = 0.55 + 0.6 * hsh;                      // frame half-size in world units
        vec2 q = p * z / (f * half_);
        // frames sit on the floor: centre them above it
        q.y -= 0.0;
        float dd = max(abs(q.x), abs(q.y));
        float sp = spec(clamp(float(k) / float(n), 0.0, 1.0) * 0.8 + 0.05);
        float wdt = (0.012 + 0.02 * sp * uAmp) * (1.0 + 0.5 * uBeat) * (1.0 + 1.2 / z);
        float g = lineGlow(dd - 1.0, wdt) + 0.25 * lineGlow(dd - 1.0, wdt * 4.0);
        // some frames are broken into dashes
        float ang = atan(q.y, q.x);
        float dash = step(0.30 * hsh, fract(ang / TAU * (4.0 + 6.0 * hsh) + id * 0.37));
        g *= mix(1.0, dash, step(0.55, hsh));
        float fadeZ = smoothstep(0.25, 0.9, z) * (1.0 / (1.0 + z * 0.16));
        vec3 fc = pal(uPhase + hsh * uSpread * 1.4 + sp * 0.2);
        col += fc * g * fadeZ * (0.9 + 1.4 * sp * uAmp);
    }

    // --- speed streaks rushing past
    if (uCore > 0.01) {
        const float NS = 90.0;
        float ang = atan(p.y, p.x);
        float r = length(p);
        float bi = floor((ang / TAU + 0.5) * NS);
        float ca = (bi + 0.5) / NS * TAU - PI;
        float hs = hash11(bi * 3.7 + 1.0);
        float sp = 0.5 + 1.5 * hash11(bi * 5.3 + 2.0);
        float pos = fract(hs + travel * 0.18 * sp);
        float rr0 = pos * pos * 1.6;
        float len = 0.04 + 0.22 * pos;
        float across = (ang - ca) * r;
        float aw = 0.0016 + 0.002 * r;
        float s = exp(-across * across / (aw * aw)) * smoothstep(rr0 - len, rr0, r) * smoothstep(rr0 + 0.01, rr0 - 0.01, r);
        s *= step(0.55, hs);
        col += pal(uPhase + hs * uSpread * 2.0) * s * uCore * 1.4;
    }
    finish(col);
}
