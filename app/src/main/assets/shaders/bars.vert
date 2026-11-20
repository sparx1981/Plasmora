#version 300 es
precision highp float;
precision highp int;

uniform sampler2D uAudio;   // 1024 x 4 (row 2 = spectrum, row 3 = peak hold)
uniform int   uStyle;       // 0 = bars, 1 = filled curve
uniform int   uBars;        // bar count (style 0) or curve points (style 1)
uniform int   uMirror;      // 0 = up from baseline, 1 = centre mirror, 2 = valley (from top and bottom)
uniform int   uCap;         // 1 = draw peak caps (bars only)
uniform float uRot, uTilt, uCy;
uniform int uSym;
uniform float uAspect, uBase, uHeight, uHalfW, uGap, uPow, uCapH;

out vec2  vLocal;   // x: -1..1 across a bar,  y: 0 at origin .. 1 at tip
out float vT;
out float vVal;
out float vH;
out float vHalf;

float tap(float t, int row) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x);
    int j = min(i + 1, 1023);
    float f = x - float(i);
    return mix(texelFetch(uAudio, ivec2(i, row), 0).r, texelFetch(uAudio, ivec2(j, row), 0).r, f);
}
float band(float t0, float t1, int row) {
    float m = 0.0;
    for (int k = 0; k < 8; k++) m = max(m, tap(mix(t0, t1, (float(k) + 0.5) / 8.0), row));
    return m;
}
float smoothSpec(float t, int row) {
    float s = 0.0, w = 0.0;
    for (int k = -6; k <= 6; k++) {
        float ww = exp(-float(k * k) / 20.0);
        s += tap(t + float(k) / 1023.0, row) * ww;
        w += ww;
    }
    return s / w;
}

void main() {
    int halves = (uMirror == 0) ? 1 : 2;
    int inst = gl_InstanceID;
    int i = (uStyle == 0) ? inst / halves : 0;
    int half_ = inst - (inst / halves) * halves;
    float y0, dir;
    if (uMirror == 0)      { y0 = uBase; dir = 1.0; }
    else if (uMirror == 1) { y0 = uBase; dir = (half_ == 0) ? 1.0 : -1.0; }
    else                   { y0 = (half_ == 0) ? -1.0 : 1.0; dir = (half_ == 0) ? 1.0 : -1.0; }
    int row = (uCap == 1) ? 3 : 2;

    vec2 p; float v; float t;
    if (uStyle == 2) {
        // radial bars: a ring of spectrum bars around the centre
        float n = float(uBars);
        float u0 = float(inst) / n;
        float u1 = float(inst + 1) / n;
        t = 0.5 * (u0 + u1);
        float s0 = abs(2.0 * u0 - 1.0), s1 = abs(2.0 * u1 - 1.0);
        v = pow(clamp(band(min(s0, s1), max(s0, s1) + 0.0005, row), 0.0, 1.2), uPow) * uHeight;
        float ang = t * 6.2831853 + uRot;
        vec2 rad = vec2(cos(ang), sin(ang));
        vec2 tan_ = vec2(-rad.y, rad.x);
        float hw = 3.14159265 * uBase / n * (1.0 - uGap);
        float sx = float(gl_VertexID & 1) * 2.0 - 1.0;
        float sy = float(gl_VertexID >> 1);
        vLocal = vec2(sx, sy);
        float rA, rB;
        if (uCap == 1) { rB = uBase + v; rA = rB - uCapH; } else { rA = uBase; rB = uBase + v; }
        float fit = min(uAspect, 1.0);
        p = (rad * mix(rA, rB, sy) + tan_ * sx * hw) * fit;
    } else if (uStyle == 3) {
        // 3D ring of spikes standing on a tilted ellipse
        float n = float(uBars);
        float u0 = float(inst) / n;
        float u1 = float(inst + 1) / n;
        t = 0.5 * (u0 + u1);
        float s0 = abs(2.0 * u0 - 1.0), s1 = abs(2.0 * u1 - 1.0);
        v = pow(clamp(band(min(s0, s1), max(s0, s1) + 0.0006, row), 0.0, 1.2), uPow) * uHeight;
        float ang = t * 6.2831853 + uRot;
        vec2 rad = vec2(cos(ang), sin(ang));
        float depth = 0.85 + 0.15 * rad.y;               // far side slightly smaller
        float fit = min(uAspect, 1.0);
        vec2 p0 = vec2(rad.x * uBase, rad.y * uBase * uTilt) * fit + vec2(0.0, uCy);
        float hw = 3.14159265 * uBase / n * (1.0 - uGap) * fit * depth;
        float sx = float(gl_VertexID & 1) * 2.0 - 1.0;
        float sy = float(gl_VertexID >> 1);
        vLocal = vec2(sx, sy);
        float w = hw * (1.0 - 0.85 * sy);
        p = p0 + vec2(sx * w, sy * v * depth * (uCap == 1 ? 0.0 : 1.0) + (uCap == 1 ? v * depth : 0.0));
    } else if (uStyle == 0) {
        float n = float(uBars);
        float t0 = float(i) / n, t1 = float(i + 1) / n;
        t = 0.5 * (t0 + t1);
        float b0 = t0, b1 = t1;
        if (uSym == 1) {
            float s0 = abs(2.0 * t0 - 1.0), s1 = abs(2.0 * t1 - 1.0);
            b0 = min(s0, s1); b1 = max(s0, s1) + 0.0006;
        }
        v = pow(clamp(band(b0, b1, row), 0.0, 1.2), uPow) * uHeight;
        float slot = 2.0 * uHalfW / n;
        float cx = -uHalfW + (float(i) + 0.5) * slot;
        float hw = slot * 0.5 * (1.0 - uGap);
        float sx = float(gl_VertexID & 1) * 2.0 - 1.0;
        float sy = float(gl_VertexID >> 1);
        vLocal = vec2(sx, sy);
        float yA, yB;
        if (uCap == 1) { yB = y0 + dir * v; yA = yB - dir * uCapH; }
        else           { yA = y0;           yB = y0 + dir * v; }
        p = vec2(cx + sx * hw, mix(yA, yB, sy));
    } else {
        int idx = gl_VertexID >> 1;
        float top = float(gl_VertexID & 1);
        t = float(idx) / float(uBars - 1);
        v = pow(clamp(smoothSpec(t, 2), 0.0, 1.2), uPow) * uHeight;
        p = vec2((t * 2.0 - 1.0) * uHalfW, y0 + dir * v * top);
        vLocal = vec2(0.0, top);
    }
    vT = t;
    vHalf = float(half_);
    vVal = v / max(uHeight, 1e-4);
    vH = v;
    gl_Position = vec4(p.x / uAspect, p.y, 0.0, 1.0);
}
