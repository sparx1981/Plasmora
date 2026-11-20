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


// Tunnel of LED dash rings with floating glass squares. uScale tunnel radius, uP1 dashes per ring, uP2 flight speed,
// uP3 ring density, uAmp brightness swing, uSpread hue spread, uRot spin, uCore square brightness, uCy offset.
float rbox(vec2 p, vec2 b, float rr) { vec2 q = abs(p) - b + rr; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rr; }
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    p = rot2(p, uRot);
    float r = max(length(p), 1e-4);
    float a = atan(p.y, p.x);
    float k = max(uScale, 0.05);
    float dens = clamp(uP3, 1.0, 14.0);
    float z = log(k / r) * dens + uTime * uP2 + 0.15 * uBass;
    float N = clamp(uP1, 24.0, 160.0);
    float zi = floor(z), zf = fract(z);
    float ua = a / TAU + 0.5;
    float ai = floor(ua * N), af = fract(ua * N);
    float mb = abs(2.0 * fract((ai + 0.5) / N + 0.25) - 1.0);
    float lvl = 0.35 + 0.25 * pow(clamp(spec(mb * 0.9 + 0.03 * hash11(zi)), 0.0, 1.4), 1.1);
    float depthFade = smoothstep(0.012, 0.09, r);
    float fz = fwidth(z) + 1e-4, fa = fwidth(af) + 1e-4;
    vec3 col = vec3(0.0);
    // bright white dashes (main rings)
    float dashA = 1.0 - smoothstep(0.62 - fa, 0.62 + fa, abs(af - 0.5) * 2.0 + 0.0);
    float band = 1.0 - smoothstep(0.32 - fz, 0.32 + fz, abs(zf - 0.5));
    float on = step(0.12 + 0.55 * (1.0 - lvl) * 0.0, 0.5 + lvl);
    vec3 hue = pal(uPhase + mb * uSpread * 0.4);
    vec3 dash = mix(hue, vec3(1.0), 0.4) * (0.32 + 0.8 * lvl * uAmp);
    col += dash * dashA * band * depthFade;
    // fine red dotted guide rings between
    float zf2 = fract(z + 0.5);
    float band2 = 1.0 - smoothstep(0.07 - fz, 0.07 + fz, abs(zf2 - 0.5) * 0.0 + abs(zf2 - 0.5) * 1.0 - 0.0) ;
    float b2 = 1.0 - smoothstep(0.045, 0.045 + fz * 1.5, abs(zf2 - 0.5));
    float dots = 1.0 - smoothstep(0.35 - fa * 2.0, 0.35 + fa * 2.0, abs(fract(ua * N * 2.0) - 0.5) * 2.0);
    col += pal(uPhase + 0.45) * b2 * dots * depthFade * (0.6 + 0.8 * lvl);
    // dark base between rings with a faint tint
    col += pal(uPhase + 0.3) * 0.05 * depthFade * (0.5 + 0.5 * cos(z * TAU));
    // floating glass squares drifting toward the camera
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float zr = mod(fi * 2.1 - uTime * uP2 * 0.0 + 0.0 - uTime * uP2, 14.0);
        float rz = k * exp(-zr / dens) * 1.0;
        float aa = hash11(fi * 3.7 + 1.0) * TAU;
        vec2 ctr = vec2(cos(aa), sin(aa)) * rz;
        float sz = rz * 0.26;
        vec2 dq = rot2(p - ctr, -aa + fi);
        float dsq = rbox(dq, vec2(sz), sz * 0.3);
        float fw = fwidth(dsq) * 1.2 + 1e-5;
        float edge = 1.0 - smoothstep(0.0, fw * 1.6, abs(dsq));
        float inside = 1.0 - smoothstep(-fw, fw, dsq);
        float vis = smoothstep(0.02, 0.1, rz);
        col *= 1.0 - 0.85 * inside * vis;
        col += mix(pal(uPhase + fi * 0.13), vec3(1.0), 0.55) * edge * (0.7 + 0.6 * lvl) * uCore * vis;
        col += pal(uPhase + fi * 0.13) * inside * 0.06 * vis;
    }
    col *= smoothstep(0.0, 0.02, r);
    finish(col);
}
