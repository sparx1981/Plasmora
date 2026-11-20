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


// Wisps of coloured smoke with neon circuit traces. uScale zoom, uP1 trace density, uP2 flow speed,
// uP3 trace brightness, uAmp spectrum drive, uSpread hue spread, uCore smoke strength.
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1, 0)), f.x), mix(hash21(i + vec2(0, 1)), hash21(i + vec2(1, 1)), f.x), f.y); }
float fbm(vec2 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 5; i++) { s += a * vn(p); p = p * 2.03 + 11.7; a *= 0.5; } return s; }
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom * max(uScale, 0.3);
    p = rot2(p, uRot);
    float t = uTime * uP2;
    // domain-warped flow
    vec2 q = vec2(fbm(p * 1.4 + vec2(0.0, t * 0.4)), fbm(p * 1.4 + vec2(5.2, -t * 0.35)));
    vec2 r = vec2(fbm(p * 1.7 + 3.0 * q + vec2(1.7, 9.2) + t * 0.25), fbm(p * 1.7 + 3.0 * q + vec2(8.3, 2.8) - t * 0.2));
    float f = fbm(p * 1.3 + 3.2 * r);
    float body = smoothstep(0.50, 1.00, f);
    float bandX = spec(clamp(vUv.x, 0.0, 1.0) * 0.9);
    float energy = 0.35 + 1.4 * bandX * uAmp + 0.5 * uBeat;
    float hue = uPhase + r.x * uSpread + p.x * 0.08;
    vec3 col = pal(hue) * body * body * energy * 0.7 * uCore;
    col += pal(hue + 0.33) * pow(f, 5.0) * 0.4 * uCore;
    // fine bright filaments give the smoke crisp edges
    float fv = fbm(p * 3.4 + 3.6 * r + vec2(0.0, t * 0.15));
    float fil = pow(1.0 - abs(2.0 * fv - 1.0), 9.0);
    col += pal(hue + 0.12) * fil * smoothstep(0.38, 0.75, f) * energy * 0.85 * uCore;

    // neon circuit traces riding the flow
    vec2 g = (p + r * 0.35) * (2.2 + uP1 * 0.35) + vec2(t * 0.3, 0.0);
    vec2 c = floor(g); vec2 fr = fract(g) - 0.5;
    float hs = hash21(c);
    float horiz = step(0.5, hash21(c + 17.0));
    float d = horiz > 0.5 ? abs(fr.y) : abs(fr.x);
    float along = horiz > 0.5 ? fr.x : fr.y;
    float seg = step(0.45, hs) * smoothstep(0.5, 0.42, abs(along));
    float w = 0.007 + 0.010 * spec(hs) * uAmp;
    float line = exp(-d * d / (w * w)) * seg;
    float knot = exp(-dot(fr, fr) / 0.002) * step(0.8, hs);
    vec3 tc = pal(hue + 0.5 + hs * 0.4);
    float actv = smoothstep(0.55, 0.9, f + 0.4 * bandX);
    col += tc * (line * 1.4 + knot * 1.8) * uP3 * (0.4 + 1.2 * actv) * (0.5 + uBeat);
    col += tc * exp(-d * d / (w * w * 16.0)) * seg * 0.14 * uP3;
    finish(col);
}
