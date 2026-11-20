#version 300 es
precision highp float;
precision highp int;

// Neon ribbon waves (kind 0: line strips) and dotted wave landscape (kind 1: points).
uniform sampler2D uAudio;     // 1024 x 4 (row 2 = spectrum)
uniform sampler2D uHist;      // 256 x 40 history, row 0 = newest
uniform int   uKind;          // 0 ribbons, 1 dots, 2 glossy beads (painter's order)
uniform int   uLines;         // ribbon line count / dot columns
uniform int   uRowsN;         // dot rows
uniform float uFit, uAspect, uRot, uTilt, uDist, uCy, uFocal, uSpan, uAmp, uWidth, uTwist, uRibW;
uniform float uTime, uTravel, uFrac, uPow, uSize, uBass, uBeat, uKick, uFar;

out vec2  vLine;
out float vT;
out float vA;
out float vFade;
out float vH;

const int NPT = 480;

vec3 rx(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x, p.y * c + p.z * s, -p.y * s + p.z * c); }
vec3 ry(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }

float tap(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x);
    int j = min(i + 1, 1023);
    float f = x - float(i);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, f);
}
float spec(float s) {
    float a = 0.0, w = 0.0;
    for (int k = -4; k <= 4; k++) {
        float ww = exp(-float(k * k) / 10.0);
        a += tap(s * 0.5 + float(k) * 0.004) * ww;
        w += ww;
    }
    return a / w;
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

vec2 project(vec3 w, out float zq) {
    vec3 q = rx(ry(w, uRot), uTilt) + vec3(0.0, 0.0, uDist);
    zq = q.z;
    float fit = uFit;
    vec2 sc = q.xy / max(q.z, 0.05) * uFocal;
    return vec2(sc.x * fit, sc.y * fit + uCy);        // aspect-corrected units (x not yet / uAspect)
}

// ribbon centre-line + a point on the ribbon sheet
vec3 ribbon(float t, float a) {
    float x = (t * 2.0 - 1.0);
    float s = abs(x);
    float env = mix(0.40, 1.0, exp(-s * s * 2.2)) * smoothstep(1.02, 0.80, s);
    float sp = pow(clamp(spec(s), 0.0, 1.2), uPow);
    float shape = (0.30 + 0.95 * sp) * env;
    float ph = uTime * 0.55;
    float wv = 0.50 * sin(x * 6.3 + ph) + 0.32 * sin(x * 11.0 - ph * 1.3 + 1.3) + 0.18 * sin(x * 17.0 + ph * 1.9 + 0.4);
    float y0 = uAmp * shape * wv * (0.75 + 0.5 * uBass);
    float z0 = uAmp * 0.55 * shape * sin(x * 3.6 - ph * 0.8 + 2.0);
    float phi = uTwist * (x * 4.2 + sin(x * 2.3 + ph * 0.7) * 1.1) + ph * 0.6;
    float w = (0.10 + 0.9 * shape) * uRibW * (0.85 + 0.25 * uBeat);
    float fan = a * 0.07 * sin(x * 7.0 + a * 3.0 + ph);
    return vec3(x * uSpan, y0 + a * w * cos(phi) + fan, z0 + a * w * sin(phi));
}

void main() {
    if (uKind == 0) {
        int inst = gl_InstanceID;
        int idx = gl_VertexID >> 1;
        float side = float((gl_VertexID & 1) * 2 - 1);
        float d = 1.0 / float(NPT - 1);
        float t = float(idx) * d;
        float a = float(inst) / float(max(uLines - 1, 1)) * 2.0 - 1.0;
        float z0, z1, z2;
        vec2 p0 = project(ribbon(t, a), z0);
        vec2 pa_ = project(ribbon(max(t - d, 0.0), a), z1);
        vec2 pb_ = project(ribbon(min(t + d, 1.0), a), z2);
        vec2 tgn = pb_ - pa_;
        float l = length(tgn);
        tgn = l > 1e-6 ? tgn / l : vec2(1.0, 0.0);
        vec2 nrm = vec2(-tgn.y, tgn.x);
        float wpx = uWidth * (1.0 + 0.6 * (1.0 - abs(a)));
        p0 += nrm * side * wpx * 0.5;
        gl_Position = vec4(p0.x / uAspect, p0.y, 0.0, 1.0);
        vLine = vec2(side, 0.0);
        vT = t;
        vA = a;
        vFade = clamp(1.25 - 0.18 * z0 / uDist, 0.25, 1.4);
        vH = 0.0;
    } else {
        int id = gl_VertexID;
        int ix = id % uLines;
        int iz = id / uLines;
        if (uKind == 2) iz = uRowsN - 1 - iz;           // far rows first so near beads cover them
        float fx = float(ix) / float(uLines - 1);
        float x = (fx * 2.0 - 1.0);
        float s = abs(x);
        float zf = float(iz) + uFrac;
        float zn = zf / float(uRowsN);
        float z = -1.2 + zn * uFar;
        // swell: slow travelling sines modulated by the music, plus the real spectrum history
        float tr = uTravel;
        float swell = 0.50 * sin(x * 2.7 + z * 1.3 - tr * 0.9)
                    + 0.32 * sin(x * 5.1 - z * 1.9 + tr * 1.25 + 1.7)
                    + 0.18 * sin(x * 9.3 + z * 3.1 - tr * 1.9 + 0.6);
        float hs = pow(clamp(hist(s, zf * 39.0 / float(uRowsN)), 0.0, 1.2), uPow);
        float envx = mix(0.45, 1.0, exp(-s * s * 1.8));
        float h = uAmp * envx * (swell * (0.30 + 0.55 * uBass) + hs * 0.85) * (1.0 - 0.35 * zn);
        vec3 w = vec3(x * uSpan, h, z);
        float zq;
        vec2 p = project(w, zq);
        gl_Position = vec4(p.x / uAspect, p.y, 0.0, 1.0);
        gl_PointSize = clamp(uSize * uDist / max(zq, 0.3), 1.5, uKind == 2 ? 64.0 : 24.0);
        vLine = vec2(0.0);
        vT = fx;
        vA = zn;
        vFade = pow(max(1.0 - zn, 0.0), 0.9) * smoothstep(0.0, 0.06, zn + 0.02);
        vH = h / max(uAmp, 1e-4);
    }
}
