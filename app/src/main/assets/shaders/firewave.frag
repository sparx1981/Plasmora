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

// Symmetric neon waveform bundle. uAmp = height, uScale = width of the bundle, uP1 = strand count,
// uP2 = wave frequency, uP3 = speed, uWidth = strand thickness, uCore = hot core amount, uSpread = hue spread.
float env(float x) {
    float t = clamp(abs(x), 0.0, 1.0);
    float a = spec(t * 0.85) * 0.55 + spec(t * 0.85 + 0.03) * 0.25 + spec(max(t * 0.85 - 0.03, 0.0)) * 0.20;
    return pow(clamp(a, 0.0, 1.3), 1.15) * smoothstep(1.02, 0.55, t);
}
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    p = rot2(p, uRot);
    float xn = p.x / max(uScale * uAspect * 0.5, 0.05);
    float e = env(xn) * uAmp * (0.85 + 0.35 * uBeat);
    int n = int(clamp(uP1, 3.0, 40.0));
    float ph = uTime * uP3;
    vec3 col = vec3(0.0);
    float sig = max(uWidth, 0.0008);
    for (int k = 0; k < 40; k++) {
        if (k >= n) break;
        float u = float(k) / float(max(n - 1, 1));           // 0..1 across the bundle
        float m = mix(-1.0, 1.0, u);
        float fr = uP2 * (1.0 + 0.25 * m);
        float wv = cos(xn * fr * 3.0 + ph + m * 2.2) * (0.55 + 0.45 * sin(xn * 3.1 + ph * 0.7 + m * 4.0));
        float y = e * (0.12 + 0.88 * abs(m)) * wv * sign(m + 1e-3);
        float dy = p.y - y * 0.5;
        float g = exp(-dy * dy / (sig * sig)) + 0.18 * exp(-dy * dy / (sig * sig * 18.0));
        float heat = clamp(e * 0.9 + (1.0 - abs(m)) * 0.35, 0.0, 1.4);      // hot near the middle / loud parts
        vec3 c = pal(uPhase + u * uSpread + xn * 0.12);
        c = mix(c, vec3(1.0, 0.82, 0.45), clamp(heat * uCore * 0.6, 0.0, 0.9));
        col += c * g * (0.30 + 0.5 * (1.0 - abs(m)));
    }
    // soft haze along the axis
    col += pal(uPhase + 0.4) * exp(-p.y * p.y * 400.0) * 0.12 * (0.3 + e);
    // fade the far ends to a calm line
    col *= 0.25 + 0.75 * smoothstep(1.1, 0.2, abs(xn));
    finish(col);
}
