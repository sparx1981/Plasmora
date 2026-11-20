#version 300 es
precision highp float;
precision highp int;

// Neon light-trail lines in 3D. kind 0 = right-angled circuit paths, 1 = parallel strand bundles, 2 = smooth ribbons.
uniform sampler2D uAudio;
uniform int   uN, uSeg, uStr, uKind;
uniform float uFit, uAspect, uRot, uTilt, uDist, uCy, uFocal, uTime, uSpeed, uSize, uWidth, uAmp, uBeat, uBass, uGap, uSpread, uPhase, uTumble;

out vec4 vA;          // x = across (-1..1), y = hue, z = brightness, w = along 0..1

const float TAU = 6.2831853;
vec3 rx(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x, p.y * c + p.z * s, -p.y * s + p.z * c); }
vec3 ry(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }
float h11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float tap(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x); int j = min(i + 1, 1023);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, x - float(i));
}
float spec(float s) {
    float a = 0.0, w = 0.0;
    for (int k = -2; k <= 2; k++) { float ww = exp(-float(k * k) / 4.0); a += tap(pow(clamp(s, 0.0, 1.0), 1.5) * 0.5 + float(k) * 0.004) * ww; w += ww; }
    return a / w;
}
// Right-angle walk, sometimes cutting corners at 45 degrees
vec3 stepDir(float seed, int i) {
    float h0 = h11(seed + float(i) * 3.17), h1 = h11(seed + float(i) * 5.71 + 1.3), h2 = h11(seed + float(i) * 7.13 + 2.9);
    int ax = int(h0 * 3.0);
    float sg = h1 > 0.5 ? 1.0 : -1.0;
    float L = 0.18 + 0.55 * h2;
    vec3 d = vec3(0.0);
    if (ax == 0) d.x = sg * L; else if (ax == 1) d.y = sg * L; else d.z = sg * L;
    if (h2 > 0.72) { int ax2 = (ax + 1) % 3; if (ax2 == 0) d.x += sg * L * 0.7; else if (ax2 == 1) d.y += sg * L * 0.7; else d.z += sg * L * 0.7; }
    return d;
}
vec3 circuitNode(float seed, int k) {
    vec3 p = vec3(h11(seed + 1.1), h11(seed + 2.2), h11(seed + 3.3)) * 2.0 - 1.0;
    p *= 0.7;
    for (int i = 0; i < 9; i++) { if (i >= k) break; p += stepDir(seed, i); }
    return p;
}
vec3 ribbonPt(float seed, float t) {
    float a1 = 1.0 + floor(h11(seed + 4.0) * 3.0), a2 = 1.0 + floor(h11(seed + 5.0) * 3.0), a3 = 1.0 + floor(h11(seed + 6.0) * 2.0);
    float p1 = h11(seed + 7.0) * TAU, p2 = h11(seed + 8.0) * TAU, p3 = h11(seed + 9.0) * TAU;
    return vec3(sin(a1 * t + p1) * 0.9 + 0.35 * sin(2.3 * t + p3), sin(a2 * t + p2) * 0.55, sin(a3 * t + p3) * 0.7 + 0.3 * sin(3.1 * t + p1));
}

vec2 proj(vec3 w, out float zq) {
    vec3 q = rx(ry(w, uRot + uTumble), uTilt) + vec3(0.0, 0.0, uDist);
    zq = q.z;
    vec2 sc = q.xy / max(q.z, 0.2) * uFocal;
    return vec2(sc.x * uFit, sc.y * uFit + uCy);
}

void main() {
    int inst = gl_InstanceID;
    int perPath = uSeg * uStr;
    int path = inst / perPath;
    int rem = inst - path * perPath;
    int strand = rem / uSeg;
    int k = rem - strand * uSeg;
    int v = gl_VertexID;
    int cidx = v == 0 ? 0 : (v == 1 ? 1 : (v == 2 ? 2 : (v == 3 ? 2 : (v == 4 ? 1 : 3))));
    float side = float((cidx & 1) * 2 - 1);
    float endT = float(cidx >> 1);

    float pf = float(path);
    float lifeRate = uSpeed;
    float ph = h11(pf * 1.93 + 0.7);
    float lt = uTime * lifeRate + ph * 7.0;
    float cyc = floor(lt);
    float life = fract(lt);
    float seed = pf * 12.7 + cyc * 31.3 + 5.0;
    float env = pow(sin(3.14159 * life), 1.3);
    float band = h11(seed * 0.37 + 11.0);
    float lvl = pow(clamp(spec(band * 0.9), 0.0, 1.3), 1.1);

    vec3 A, B;
    if (uKind == 0 || uKind == 1) {
        A = circuitNode(seed, k); B = circuitNode(seed, k + 1);
    } else {
        float t0 = float(k) / float(uSeg) * 5.0 + lt * 0.5, t1 = float(k + 1) / float(uSeg) * 5.0 + lt * 0.5;
        A = ribbonPt(seed, t0); B = ribbonPt(seed, t1);
    }
    vec3 dir = normalize(B - A + vec3(1e-5));
    if (uKind == 1) {                                       // parallel strands offset sideways
        vec3 pr = normalize(cross(dir, vec3(0.31, 0.83, 0.47)));
        float so = (float(strand) - 0.5 * float(uStr - 1)) * uGap;
        A += pr * so; B += pr * so;
    }
    A *= uSize; B *= uSize;
    float zA, zB;
    vec2 a = proj(A, zA), b = proj(B, zB);
    vec2 ab = vec2((b.x - a.x) * uAspect, b.y - a.y);
    float len = max(length(ab), 1e-5);
    vec2 d2 = ab / len;
    vec2 n2 = vec2(-d2.y, d2.x);
    float zz = mix(zA, zB, endT);
    float wd = uWidth * (0.5 + 0.9 * lvl * uAmp + 0.35 * uBeat) * uDist / max(zz, 0.4);
    if (uKind == 1) wd *= 0.55;
    vec2 pos = mix(a, b, endT);
    vec2 pxy = vec2(pos.x * uAspect, pos.y) + d2 * wd * (endT * 2.0 - 1.0) + n2 * wd * side * 1.0;
    gl_Position = vec4(pxy.x / uAspect, pxy.y, 0.0, 1.0);
    float hue = band + float(strand) / float(max(uStr, 1)) * 0.25;
    vA = vec4(side, hue, env * (0.25 + 1.3 * lvl * uAmp) * (0.85 + 0.3 * uBeat), (float(k) + endT) / float(uSeg));
}
