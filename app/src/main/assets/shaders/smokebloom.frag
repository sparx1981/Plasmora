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


// Radial smoke bloom: plumes of smoke burst from a dark core, one petal per band of the spectrum.
// uScale core radius, uP1 petals, uAmp plume length, uP2 flow speed, uP3 turbulence, uCore rim glow,
// uSpread hue turns around the ring, uRot spin.
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1, 0)), f.x), mix(hash21(i + vec2(0, 1)), hash21(i + vec2(1, 1)), f.x), f.y); }
float fbm(vec2 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 5; i++) { s += a * vn(p); p = p * 2.05 + 9.3; a *= 0.5; } return s; }
float ridge(vec2 p) { float a = 0.5, s = 0.0; for (int i = 0; i < 4; i++) { s += a * (1.0 - abs(2.0 * vn(p) - 1.0)); p = p * 2.1 + 3.7; a *= 0.5; } return s; }
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    p = rot2(p, uRot);
    float R0 = max(uScale, 0.03);
    float r = length(p);
    float a = atan(p.y, p.x);
    float u = a / TAU + 0.5;
    float petals = clamp(uP1, 3.0, 12.0);
    // spectrum around the ring: mirrored so the pattern stays symmetric, with petal modulation
    float mu = abs(2.0 * fract(u + 0.25) - 1.0);
    float e = pow(spec(mu * 0.9), 1.1);
    float pet = 0.55 + 0.45 * cos(a * petals);
    float len = R0 + (0.10 + e * uAmp * 0.55 * (0.5 + 0.8 * pet)) * (0.85 + 0.3 * uBeat);
    float t = uTime * uP2;
    // smoke streaming outwards in polar space
    vec2 q = vec2(r * 4.0 - t * 0.9, a * 1.6 * (1.0 + 0.0));
    vec2 w1 = vec2(fbm(q + vec2(0.0, t * 0.1)), fbm(q + vec2(5.3, -t * 0.12)));
    float tb = 1.2 + uP3;
    float dn = fbm(q * 1.3 + tb * 1.6 * w1);
    float fil = ridge(vec2(r * 6.0 - t * 1.2, a * 3.0) + tb * 1.3 * w1);
    float rad = (r - R0) / max(len - R0, 0.02);
    float env = smoothstep(1.15, 0.0, rad) * smoothstep(-0.02, 0.06, r - R0);
    float body = (pow(dn, 2.0) * 0.8 + pow(fil, 3.0) * 1.0) * env;
    vec3 hue = pal(uPhase + u * uSpread);
    vec3 col = hue * body * (0.7 + 1.5 * e);
    // hot rim at the core and soft glow
    float rim = exp(-pow((r - R0 * 1.04) / (0.02 + 0.02 * e), 2.0));
    col += mix(hue, vec3(1.0), 0.55) * rim * uCore * (0.4 + 0.9 * e + 0.4 * uBeat);
    col += hue * exp(-max(r - R0, 0.0) * 5.0) * 0.08 * (0.4 + e) * smoothstep(R0 * 0.9, R0, r);
    col *= smoothstep(R0 * 0.78, R0 * 0.98, r);                    // dark hole in the middle
    finish(col);
}
