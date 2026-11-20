#version 300 es
precision highp float;
precision highp int;

uniform sampler2D uAudio;   // 1024 x 4  (L wave, R wave, spectrum, peak spectrum)
uniform int   uMode;
uniform int   uN;
uniform float uAspect;
uniform float uAmp, uScale, uWidth, uRot, uTime;
uniform float uP1, uP2, uP3;
uniform vec2  uCenter;

out vec2 vLine;
out float vT;

const float TAU = 6.2831853;

float tap(float t, int row) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x);
    int j = min(i + 1, 1023);
    float f = x - float(i);
    return mix(texelFetch(uAudio, ivec2(i, row), 0).r, texelFetch(uAudio, ivec2(j, row), 0).r, f);
}
// waveform that closes on itself (ends pinned to zero)
float loopWave(float t, int row) {
    return tap(t, row) - mix(tap(0.0, row), tap(1.0, row), t);
}
float spec(float t) { return tap(t, 2); }

vec2 shape(float t) {
    float fit = min(uAspect, 1.0);
    vec2 p;
    if (uMode == 0) {
        p = vec2((t * 2.0 - 1.0) * uAspect * uScale, tap(t, 0) * uAmp);
    } else if (uMode == 1) {
        float a = t * TAU;
        float r = uScale + loopWave(t, 0) * uAmp;
        p = r * vec2(cos(a), sin(a)) * fit;
    } else if (uMode == 2) {
        float turns = max(uP1, 0.5);
        float a = t * TAU * turns;
        float r = uScale * t + tap(t, 0) * uAmp * (0.25 + t);
        p = r * vec2(cos(a), sin(a)) * fit;
    } else if (uMode == 3) {
        float x = tap(t, 0);
        float y = tap(t + uP1, 1);
        p = vec2(x - y, x + y) * 0.7071 * uScale * uAmp * fit;
    } else if (uMode == 4) {
        float s = abs(t * 2.0 - 1.0);
        float a = t * TAU;
        float r = uScale + spec(s) * uAmp;
        p = r * vec2(cos(a), sin(a)) * fit;
    } else if (uMode == 5) {
        p = vec2((t * 2.0 - 1.0) * uAspect * uScale, spec(t) * uAmp);
    } else if (uMode == 6) {
        float s = abs(t * 2.0 - 1.0);
        p = vec2((t * 2.0 - 1.0) * uAspect * uScale, spec(s) * uAmp);
    } else if (uMode == 7) {
        float a = t * TAU;
        float wv = loopWave(t, 0) * uAmp;
        p = uScale * vec2(sin(uP1 * a + uP3), sin(uP2 * a)) * (1.0 + wv) * fit;
    } else if (uMode == 8) {
        float a = t * TAU;
        float r = uScale * (0.55 + 0.45 * cos(uP1 * a + uP3)) + loopWave(t, 0) * uAmp;
        p = r * vec2(cos(a), sin(a)) * fit;
    } else {
        float a = t * TAU;
        float r = uScale + 0.015 * sin(a * uP1 + uTime * 3.0);
        p = r * vec2(cos(a), sin(a)) * fit;
    }
    float c = cos(uRot), s = sin(uRot);
    p = mat2(c, -s, s, c) * p + uCenter;
    return p;
}

void main() {
    int idx = gl_VertexID >> 1;
    float side = float((gl_VertexID & 1) * 2 - 1);
    float d = 1.0 / float(uN - 1);
    float t = float(idx) * d;
    vec2 p0 = shape(t);
    vec2 pa = shape(t - d);
    vec2 pb = shape(t + d);
    vec2 tang = pb - pa;
    float tl = length(tang);
    tang = tl > 1e-6 ? tang / tl : vec2(1.0, 0.0);
    vec2 n = vec2(-tang.y, tang.x);
    // taper the ends of open lines so they fade instead of ending abruptly
    float w = uWidth * 0.5;
    p0 += n * side * w;
    vLine = vec2(side, 0.0);
    vT = t;
    gl_Position = vec4(p0.x / uAspect, p0.y, 0.0, 1.0);
}
