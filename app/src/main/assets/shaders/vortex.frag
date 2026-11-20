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

// Spectrum ring with dashed halo, outward spikes, inner waveform and a glowing core.
// uScale = ring radius, uAmp = spike length, uP1 = spike count, uP2 = spin speed, uP3 = haze amount,
// uWidth = ring thickness, uCore = core glow, uSpread = rainbow turns.
float vh(vec2 p) { return hash21(p); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(vh(i), vh(i + vec2(1, 0)), f.x), mix(vh(i + vec2(0, 1)), vh(i + vec2(1, 1)), f.x), f.y);
}
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    float r = length(p);
    float a = atan(p.y, p.x);
    float R = max(uScale, 0.05);
    float u = a / TAU + 0.5;                              // 0..1 around
    float mu = abs(2.0 * fract(u + uTime * 0.02 * uP2) - 1.0);   // mirrored so bass sits at one point
    float sp = spec(mu * 0.9);
    vec3 col = vec3(0.0);
    vec3 rb = pal(uPhase + u * uSpread);
    float aa = 1.5 / 1000.0;

    // dashed main ring
    float tw = max(uWidth, 0.002);
    float dRing = abs(r - R * (1.0 + 0.02 * uBeat));
    float dashes = smoothstep(0.80, 0.72, abs(fract(u * 22.0 - uTime * 0.12 * uP2) - 0.5) * 2.0 + 0.0);
    dashes = smoothstep(0.42, 0.36, abs(fract(u * 22.0 - uTime * 0.12 * uP2) - 0.5));
    col += rb * smoothstep(tw + aa, tw - aa, dRing) * dashes * 1.7;
    col += rb * exp(-dRing * dRing / (tw * tw * 30.0)) * 0.35;

    // dotted inner ring
    float dIn = abs(r - R * 0.90);
    float dots = smoothstep(0.42, 0.30, abs(fract(u * 120.0 + uTime * 0.05) - 0.5));
    col += pal(uPhase + u * uSpread + 0.25) * smoothstep(tw * 0.5 + aa, 0.0, dIn) * dots * 1.1;

    // spectrum spikes pointing out
    float M = max(uP1, 16.0);
    float bi = floor(u * M);
    float bu = (bi + 0.5) / M;
    float bmu = abs(2.0 * fract(bu + uTime * 0.02 * uP2) - 1.0);
    float bsp = spec(bmu * 0.9);
    float len = (0.015 + bsp * uAmp * 0.35) ;
    float arc = (fract(u * M) - 0.5) * TAU * r / M;
    float aw = 0.0016 + 0.0012 * r;
    float inSpike = exp(-arc * arc / (aw * aw)) * smoothstep(R * 1.04 - 0.004, R * 1.04, r) * smoothstep(R * 1.04 + len + 0.01, R * 1.04 + len * 0.6, r);
    col += pal(uPhase + bu * uSpread) * inSpike * (0.8 + 1.2 * bsp);
    // fine radial ticks inside (short)
    float inTick = exp(-arc * arc / (aw * aw * 0.8)) * smoothstep(R * 0.93, R * 0.94, r) * smoothstep(R * 0.985, R * 0.95, r) * 0.6;
    col += rb * inTick;

    // inner waveform ring
    float wob = 0.035 * sp * sin(a * 6.0 + uTime * 1.4) + 0.02 * sin(a * 11.0 - uTime * 2.2) * sp + 0.012 * sin(a * 3.0 + uTime * 0.8);
    float dW = abs(r - (R * 0.74 + wob));
    col += pal(uPhase + u * uSpread + 0.5) * exp(-dW * dW / (0.0022 * 0.0022)) * 1.0;
    col += pal(uPhase + u * uSpread + 0.5) * exp(-dW * dW / (0.012 * 0.012)) * 0.22;

    // glowing core
    float core = exp(-r * r / (R * R * 0.07)) * uCore * (0.8 + 0.8 * uBass + 0.4 * uBeat);
    col += mix(pal(uPhase), vec3(1.0), exp(-r * r / (R * R * 0.01))) * core;
    col += pal(uPhase + 0.7) * exp(-r * r / (R * R * 0.5)) * 0.10 * uCore;

    // particle haze following the spectrum, drifting outward
    if (uP3 > 0.01) {
        vec2 q = vec2(a * 3.0 + uTime * 0.05, r * 9.0 - uTime * 0.35);
        float n = vnoise(q * 2.0) * 0.6 + vnoise(q * 4.7 + 3.1) * 0.4;
        float band = smoothstep(R * 0.95, R * 1.1, r) * exp(-(r - R) * 3.2) * (0.3 + 1.6 * sp);
        float specks = smoothstep(0.62, 0.9, vnoise(q * 14.0 + 7.0)) * band;
        col += pal(uPhase + u * uSpread + 0.1) * (pow(n, 2.4) * band * 0.9 + specks * 1.2) * uP3;
    }
    finish(col);
}
