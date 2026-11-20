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


// Floor of hexagonal LED dots seen in perspective; rings of dots swell with the music and ripple outward.
// uScale dot spacing (world), uP1 camera pitch, uP2 ripple speed, uP3 ripple tightness, uAmp swell, uSpread hue spread,
// uCore sparkle, uCy vertical offset, uRot spin.
void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0) * uZoom;
    p.y -= uCy;
    float pitch = clamp(uP1, 0.3, 1.3);
    float cp = cos(pitch), sp_ = sin(pitch);
    vec3 d = normalize(vec3(p.x, p.y, 1.1));
    d = vec3(d.x, d.y * cp - d.z * sp_, d.y * sp_ + d.z * cp);      // pitch down
    if (d.y > -0.02) { finish(vec3(0.0)); return; }
    float s = -1.0 / d.y;                                           // camera height 1 above the floor
    vec2 w = (d.xz * s - vec2(0.0, cos(pitch) / sin(pitch))) * 4.4;   // ring centre sits mid-screen, camera raised
    w = rot2(w, uRot);
    float g = max(uScale, 0.05);
    vec2 gp = w / g;
    vec2 r = vec2(1.0, 1.7320508), h = r * 0.5;
    vec2 a = mod(gp, r) - h, b = mod(gp - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    vec2 cw = (gp - gv) * g;                                        // cell centre in world units
    float rc = length(cw);
    float Rmax = 11.0;
    float x = clamp(rc / Rmax, 0.0, 1.0);
    float t = uTime * uP2;
    float wave = 0.5 + 0.5 * sin(rc * uP3 - t * 3.0);
    float e = pow(clamp(spec(pow(x, 0.7) * 0.9), 0.0, 1.4), 1.0);
    float band = pow(wave, 3.0);
    float swell = clamp((0.10 + 1.3 * e * uAmp) * (0.2 + 0.9 * band) * (0.8 + 0.4 * uBeat * (1.0 - x)), 0.0, 1.25);
    float rad = 0.40 * clamp(0.14 + 0.78 * swell, 0.06, 1.0);
    vec2 ag = abs(gv);
    float dh = max(ag.x * 0.8660254 + ag.y * 0.5, ag.y);            // hexagon distance
    float dd = mix(length(gv), dh, smoothstep(0.18, 0.42, rad));
    float fw = max(length(fwidth(gp)), 1e-4) * 0.75;
    float cov = 1.0 - smoothstep(rad - fw, rad + fw, dd);
    cov *= clamp(rad / (fw * 1.2), 0.0, 1.0);
    float fog = exp(-max(s - 2.0, 0.0) * 0.05);
    float hs = hash21(floor((gp - gv) * 8.0));
    vec3 hue = pal(uPhase + x * uSpread * 0.8 + hs * 0.22);
    vec3 hot = mix(hue, vec3(1.0), clamp(swell * swell * 0.3, 0.0, 0.35));
    vec3 col = hot * cov * (0.55 + 0.9 * swell) * fog;
    col += hue * exp(-dd * 5.0) * 0.12 * swell * fog;
    col += pal(uPhase + hs) * step(0.985, hs) * cov * uCore * 1.2 * fog;
    col *= smoothstep(-0.02, -0.12, d.y);                            // melt into the horizon
    finish(col);
}
