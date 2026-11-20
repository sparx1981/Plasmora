#version 300 es
precision highp float;
precision highp int;

// Field of sharp spectrum spikes on a grid, far rows first (instanced triangles).
uniform sampler2D uAudio;
uniform sampler2D uHist;
uniform int   uNX, uNZ;
uniform float uFit, uAspect, uRot, uTilt, uDist, uCy, uFocal, uSpan, uFar, uAmp, uPow, uTravel, uFrac, uBeat, uBass, uSharp;

out vec3 vA;            // x = across (-1..1), y = along (0..1), z = hue
out vec3 vS;            // x = depth fade, y = height 0..1, z = row seed

vec3 rx(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x, p.y * c + p.z * s, -p.y * s + p.z * c); }
vec3 ry(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }
float hash21(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float hist(float s, float rowF) {
    float x = clamp(s, 0.0, 1.0) * 255.0;
    int i = int(x); int j = min(i + 1, 255); float f = x - float(i);
    int r0 = clamp(int(rowF), 0, 39); int r1 = min(r0 + 1, 39);
    float fr = clamp(rowF - float(r0), 0.0, 1.0);
    float a = mix(texelFetch(uHist, ivec2(i, r0), 0).r, texelFetch(uHist, ivec2(j, r0), 0).r, f);
    float b = mix(texelFetch(uHist, ivec2(i, r1), 0).r, texelFetch(uHist, ivec2(j, r1), 0).r, f);
    return mix(a, b, fr);
}
void main() {
    int inst = gl_InstanceID;
    int ix = inst % uNX;
    int iz = uNZ - 1 - inst / uNX;                  // far first
    int v = gl_VertexID;                            // 0 left base, 1 right base, 2 apex
    float fx = (float(ix) + 0.5) / float(uNX);
    float x = fx * 2.0 - 1.0;
    float zf = float(iz) + uFrac;
    float zn = zf / float(uNZ);
    float jx = hash21(vec2(float(ix), float(iz) + floor(uTravel))) - 0.5;
    float jz = hash21(vec2(float(iz) + floor(uTravel), float(ix) * 1.7)) - 0.5;
    float cell = 2.0 * uSpan / float(uNX);
    float wx = x * uSpan + jx * cell * 0.8;
    float wz = -1.2 + zn * uFar + jz * (uFar / float(uNZ)) * 0.8;
    float s = abs(x);
    float hs = pow(clamp(hist(s * 0.92, zf * 39.0 / float(uNZ)), 0.0, 1.3), uPow);
    float rnd = 0.35 + 0.9 * hash21(vec2(float(ix) * 3.1, float(iz) * 5.7 + floor(uTravel * 0.5)));
    float h = uAmp * (0.03 + hs * rnd * (0.8 + 0.4 * uBeat)) * (1.0 - 0.3 * zn);
    float base = 0.0;
    vec3 wv = ry(vec3(1.0, 0.0, 0.0), -uRot);
    float wd = cell * 0.5 * uSharp;
    vec3 w;
    float side = 0.0, t = 0.0;
    if (v == 0) { w = vec3(wx, base, wz) - wv * wd; side = -1.0; }
    else if (v == 1) { w = vec3(wx, base, wz) + wv * wd; side = 1.0; }
    else { w = vec3(wx, base + h, wz); t = 1.0; }
    vA = vec3(side, t, fx);
    vS = vec3(clamp(1.2 - zn * 0.9, 0.15, 1.2), clamp(hs * rnd, 0.0, 1.3), hash21(vec2(float(iz), 3.0)));
    vec3 q = rx(ry(w, uRot), uTilt) + vec3(0.0, 0.0, uDist);
    vec2 sc = q.xy / max(q.z, 0.05) * uFocal;
    gl_Position = vec4(sc.x * uFit / uAspect, sc.y * uFit + uCy, 0.0, 1.0);
}
