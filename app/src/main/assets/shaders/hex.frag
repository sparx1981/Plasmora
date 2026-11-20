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

// Honeycomb of glossy cells. uP1: 0 = frequency by distance from the centre, 1 = random, 2 = left to right.
// uScale: cells across the height. uP2: drift speed. uP3: bevel strength.
float hexD(vec2 p) { p = abs(p); return max(dot(p, normalize(vec2(1.0, 1.7320508))), p.x); }
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p = rot2(p, uRot);
    float sc = max(uScale, 2.0);
    p = p * sc + vec2(uTime * uP2, uTime * uP2 * 0.55);
    vec2 r = vec2(1.0, 1.7320508), h = r * 0.5;
    vec2 a = mod(p, r) - h, b = mod(p - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    vec2 id = floor((p - gv) * 16.0 + 0.5) / 16.0;
    float d = hexD(gv);
    float hs = hash21(id);
    float t;
    vec2 c0 = (vec2(0.0) * sc);
    if (uP1 < 0.5) t = clamp(length((id - vec2(uTime * uP2, uTime * uP2 * 0.55)) / sc * vec2(1.0, 1.0)) * 0.9, 0.0, 1.0);
    else if (uP1 < 1.5) t = hs;
    else t = clamp(abs((id.x - uTime * uP2) / sc) / (uAspect * 0.6), 0.0, 1.0);
    float v = spec(t);
    v = clamp(pow(v * 1.25, 1.15) + uBeat * 0.18 * hs, 0.0, 1.3);
    float rad = 0.47 * (0.30 + 0.70 * clamp(v, 0.0, 1.0));
    float aa = max(fwidth(d), 1e-4) * 1.2;
    float body = smoothstep(rad + aa, rad - aa, d);
    float inner = smoothstep(rad * 0.62, 0.0, d);                      // soft dome highlight
    float rim = smoothstep(rad - 0.10, rad, d) * body;                 // darker rim = bevel
    vec3 base = pal(uPhase + hs * uSpread + v * 0.35);
    base = mix(base, vec3(1.0), 0.28);
    vec3 col = base * (0.35 + 0.85 * clamp(v, 0.0, 1.2));
    col += vec3(1.0) * inner * uP3 * 0.35 * v;
    col *= 1.0 - rim * 0.55 * uP3;
    col *= body;
    // faint idle cells so the lattice always reads
    float idle = smoothstep(0.30 + aa, 0.30 - aa, d) * 0.035;
    col += pal(uPhase + 0.6) * idle * (1.0 - body);
    finish(col);
}
