#version 300 es
precision highp float;
precision highp int;

// Wireframe surfaces drawn as filled strips with an anti-aliased grid in the fragment shader.
// kind 0: scrolling mountain terrain (opaque, hidden-line, painter's order far -> near)
// kind 1: radial wireframe plane (additive), rings = time history, angle = frequency
uniform sampler2D uHist;      // 256 x 40, R16F
uniform int   uKind, uStyle, uRowsN, uColsN;
uniform float uFit, uAspect, uRot, uTilt, uDist, uCy, uFocal;
uniform float uAmp, uCells, uSpokes, uTravel, uFrac, uPow, uBass, uBeat, uSpanX, uFar, uInner, uOuter;

out vec2  vGrid;
out float vH;
out float vDepth;
out vec3  vW;

vec3 rx(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x, p.y * c + p.z * s, -p.y * s + p.z * c); }
vec3 ry(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float ridged(vec2 p) {
    float a = 0.5, s = 0.0;
    for (int i = 0; i < 4; i++) {
        float n = 1.0 - abs(2.0 * vnoise(p) - 1.0);
        s += a * n * n;
        p = p * 2.03 + vec2(17.1, 9.7);
        a *= 0.5;
    }
    return s;
}
float hist(float s, float rowF) {
    float x = clamp(s, 0.0, 1.0) * 255.0;
    int i = int(x);
    int j = min(i + 1, 255);
    float f = x - float(i);
    int r0 = clamp(int(rowF), 0, 39);
    int r1 = min(r0 + 1, 39);
    float fr = clamp(rowF - float(r0), 0.0, 1.0);
    float a = mix(texelFetch(uHist, ivec2(i, r0), 0).r, texelFetch(uHist, ivec2(j, r0), 0).r, f);
    float b = mix(texelFetch(uHist, ivec2(i, r1), 0).r, texelFetch(uHist, ivec2(j, r1), 0).r, f);
    return mix(a, b, fr);
}

void main() {
    int r = gl_InstanceID;
    int c = gl_VertexID >> 1;
    int side = gl_VertexID & 1;
    float cf = float(c) / float(uColsN);
    vec3 w;
    if (uKind == 0) {
        const float zNear = -2.6;
        float rr = float(r + side) / float(uRowsN);       // 0 = far edge, 1 = near edge
        float z = mix(uFar, zNear, rr);
        float x = (cf * 2.0 - 1.0) * uSpanX;
        float s = abs(cf * 2.0 - 1.0);
        float zt = z + uTravel;
        float rowF = (1.0 - rr) * 39.0;
        float hs = pow(clamp(hist(s, rowF), 0.0, 1.2), uPow);
        float mask = mix(0.30, 1.0, exp(-x * x * 0.5));
        float h;
        if (uStyle == 0) {
            float n = ridged(vec2(x * 0.85 + 3.0, zt * 0.85));
            h = uAmp * (mask * n * 1.5 * (0.75 + 0.5 * uBass) + 0.30 * hs * mask);
        } else {
            float sw = 0.5 * sin(x * 2.3 + zt * 1.1) + 0.3 * sin(x * 4.7 - zt * 1.7 + 1.3) + 0.2 * sin(x * 8.1 + zt * 2.9);
            h = uAmp * mask * (hs * 1.05 + 0.22 * (sw * 0.5 + 0.5) * (0.5 + uBass));
        }
        w = vec3(x, h, z);
        vGrid = vec2(x, zt) * uCells;
        vH = h / max(uAmp, 1e-4);
        vDepth = clamp(1.0 - rr, 0.0, 1.0);
    } else {
        float rn = (float(r + side) + uFrac) / float(uRowsN);
        float R = mix(uInner, uOuter, rn);
        float ang = cf * 6.2831853;
        float s = abs(cf * 2.0 - 1.0);
        float rowF = float(r + side) * 39.0 / float(uRowsN);
        float hs = pow(clamp(hist(s, rowF), 0.0, 1.2), uPow);
        float h = uAmp * hs * (1.0 - 0.45 * rn) * (1.0 + 0.3 * uBeat);
        float a2 = ang + uTravel;
        w = vec3(R * cos(a2), h, R * sin(a2));
        vGrid = vec2(ang * uSpokes / 6.2831853, R * uCells);
        vH = clamp(hs, 0.0, 1.4);
        vDepth = clamp(rn, 0.0, 1.0);
    }
    vW = w;
    vec3 q = rx(ry(w, uRot), uTilt) + vec3(0.0, 0.0, uDist);
    float fit = uFit;
    vec2 sc = q.xy / max(q.z, 0.05) * uFocal;
    gl_Position = vec4(sc.x * fit / uAspect, sc.y * fit + uCy, 0.0, 1.0);
}
