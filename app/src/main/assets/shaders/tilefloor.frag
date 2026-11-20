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

// Glossy perspective tiles with glowing edges, vertical light rain, and a mirrored reflection.
// uScale = camera height, uP1 = tile size, uP2 = rain density (columns), uP3 = travel, uAmp = spectrum drive,
// uCy = horizon, uWidth = edge thickness, uCore = rain strength.
vec3 rain(vec2 q, float dens, float time, float mirror) {
    // q: x across, y above horizon (>0)
    float cols = max(dens, 8.0);
    float ci = floor(q.x * cols);
    float hs = hash11(ci * 1.7 + 0.3);
    float cx = (ci + 0.5) / cols;
    float across = (q.x * cols - ci - 0.5) / cols;
    float sp = spec(clamp(abs(cx) * 0.9 + 0.02, 0.0, 1.0));
    float speed = 0.35 + 0.9 * hash11(ci * 4.1);
    float ph = fract(hs * 7.0 - time * speed * 0.35);
    float trail = exp(-ph * (5.0 - 2.5 * sp)) * step(ph, 0.9);
    float h = q.y;
    float top = (0.12 + 0.55 * sp * uAmp + 0.12 * hs);
    float yy = h / top;
    float body = smoothstep(1.05, 0.7, yy) * smoothstep(-0.02, 0.05, h);
    float pos = fract(hs * 3.0 + time * speed * 0.5);
    float head = exp(-pow((h - pos * top * 1.2) / 0.035, 2.0));
    float wdt = 0.0006 + 0.0012 * (1.0 - h);
    float line = exp(-across * across / (wdt * wdt));
    float m = line * (body * 0.35 + head * 1.3) * (0.6 + 0.6 * sp);
    vec3 c = pal(uPhase + hs * uSpread + sp * 0.2);
    return c * m * mirror;
}
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    p = rot2(p, uRot * 0.0);
    float f = 0.9;
    float camH = max(uScale, 0.2);
    float tile = max(uP1, 0.3);
    vec3 col = vec3(0.0);
    float horizon = 0.0;
    if (p.y < -0.0005) {
        vec3 rd = vec3(p.x, p.y, f);
        float t = camH / -rd.y;
        vec2 w = vec2(rd.x * t, rd.z * t + uP3);
        vec2 g = w / tile;
        vec2 id = floor(g);
        vec2 fl = fract(g) - 0.5;
        float fade = 1.0 / (1.0 + t * 0.14);
        float hs = hash21(id);
        float band = spec(clamp(abs(id.x * tile) / 10.0, 0.0, 1.0));
        float pulse = band * uAmp * (0.6 + 0.8 * hash21(id + 5.0));
        // glossy dark tile with subtle gradient
        float e = max(abs(fl.x), abs(fl.y));                   // 0 centre .. 0.5 edge
        float tw = max(uWidth, 0.004);
        float aaE = fwidth(e) + 1e-4;
        float edge = smoothstep(0.5 - tw - aaE, 0.5 - tw, e) * smoothstep(0.5 + aaE, 0.5 - aaE * 0.5, e) ;
        float edgeSoft = exp(-pow((e - 0.5 + tw * 0.4) / (tw * 3.0), 2.0)) * 0.5;
        vec3 ec = pal(uPhase + hs * uSpread + pulse * 0.3);
        col += ec * (edge * 1.5 + edgeSoft) * fade * (0.45 + 1.4 * pulse + 0.4 * uBeat);
        // tile face glow when its band is loud
        col += pal(uPhase + hs * uSpread + 0.4) * smoothstep(0.5, 0.0, e) * pulse * 0.28 * fade;
        // sheen: soft light streak across the floor
        col += pal(uPhase + 0.3) * 0.05 * fade * smoothstep(0.35, 0.0, abs(fl.x + fl.y * 0.6));
        // reflection of the light rain (mirror about the horizon, dimmed)
        vec2 q = vec2(p.x / max(-p.y * 1.0 + 0.25, 0.25), -p.y);
        col += rain(vec2(p.x * 0.9 / (0.55 + 0.9 * (-p.y)), -p.y * 0.7), uP2, uTime, 0.28) * fade * 1.0;
    } else {
        // sky: light rain falling behind the horizon
        vec2 q = vec2(p.x * 0.9, p.y);
        col += rain(q, uP2, uTime, 1.0) * uCore;
    }
    float hz = exp(-abs(p.y) * 14.0);
    col += pal(uPhase + 0.15) * hz * (0.25 + 0.3 * uBass) * exp(-abs(p.x) * 0.5);
    finish(col);
}
