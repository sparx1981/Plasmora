#version 300 es
precision highp float;
precision highp int;
in vec2 vUv;
out vec4 oCol;

// Raymarched 3D scenes: 0 Mandelbulb, 1 Metaballs (glossy, reflective), 2 Crystal cathedral (KIFS)
uniform sampler2D uAudio;
uniform int   uScene;
uniform int   uSteps;
uniform float uAspect, uPix, uScale, uCy, uRot, uTilt, uTime;
uniform float uBass, uBeat, uKick, uSnare, uVocal, uMelody, uPitch, uBright, uAmp;
uniform float uIntensity, uPhase, uSpread;
uniform vec3 pa, pb, pc, pd;

const float TAU = 6.2831853;
vec3 pal(float x) { return clamp(pa + pb * cos(TAU * (pc * x + pd)), 0.0, 3.0); }
float spec(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x);
    int j = min(i + 1, 1023);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, x - float(i));
}
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0, -s, 0, 1, 0, s, 0, c); }
mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1, 0, 0, 0, c, s, 0, -s, c); }
mat2 rot2(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }

float gTrap;   // orbit trap for colouring

// ---------------------------------------------------------------- Mandelbulb
float deBulb(vec3 p) {
    float power = 6.0 + 2.0 * sin(uTime * 0.17) + 1.6 * uKick * uAmp;
    vec3 z = p;
    float dr = 1.0, r = 0.0;
    float trap = 1e9;
    for (int i = 0; i < 7; i++) {
        r = length(z);
        if (r > 2.0) break;
        float rs = max(r, 1e-5);
        float th = acos(clamp(z.z / rs, -1.0, 1.0));
        float ph = (abs(z.x) + abs(z.y) < 1e-9) ? 0.0 : atan(z.y, z.x);
        dr = pow(r, power - 1.0) * power * dr + 1.0;
        float zr = pow(r, power);
        th *= power; ph *= power;
        z = zr * vec3(sin(th) * cos(ph), sin(ph) * sin(th), cos(th)) + p;
        trap = min(trap, dot(z, z));
    }
    gTrap = trap;
    return 0.5 * log(max(r, 1e-4)) * r / dr;
}

// ---------------------------------------------------------------- Metaballs
float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}
vec3 blobC(int i, float s) {
    float fi = float(i);
    float a = uTime * (0.35 + 0.11 * fi) + fi * 1.9;
    float r = 0.85 + 0.2 * sin(uTime * 0.21 + fi) + 0.25 * s;
    return vec3(r * cos(a), 0.55 * sin(a * 1.3 + fi), r * sin(a) * 0.9);
}
float deBlobs(vec3 p) {
    float d = length(p) - (0.30 + 0.22 * uKick * uAmp);
    float acc = 1e9;
    float rs[6];
    rs[0] = 0.26 + 0.20 * uSnare * uAmp;
    rs[1] = 0.22 + 0.22 * uVocal * uAmp;
    rs[2] = 0.20 + 0.16 * uBass * uAmp;
    rs[3] = 0.18 + 0.20 * uBright * uAmp;
    rs[4] = 0.20 + 0.20 * uMelody * uAmp;
    rs[5] = 0.16 + 0.12 * uBeat * uAmp;
    for (int i = 0; i < 6; i++) {
        vec3 c = blobC(i, spec(0.08 + 0.14 * float(i)) * uAmp);
        acc = smin(acc, length(p - c) - rs[i], 0.28);
    }
    gTrap = clamp(0.5 + 0.5 * sin(p.y * 3.0 + p.x * 2.0), 0.0, 1.0);
    return smin(d, acc, 0.32);
}

// ---------------------------------------------------------------- Crystal cathedral (KIFS)
float deCrystal(vec3 p) {
    float fold = 0.55 + 0.25 * uKick * uAmp;
    float s = 1.0;
    float trap = 1e9;
    p.xz *= rot2(uTime * 0.07);
    for (int i = 0; i < 6; i++) {
        p = abs(p) - vec3(fold, 0.18 + 0.12 * uSnare * uAmp, fold * 0.8);
        if (p.x < p.y) p.xy = p.yx;
        if (p.x < p.z) p.xz = p.zx;
        if (p.y < p.z) p.yz = p.zy;
        p.xy *= rot2(0.35 + 0.25 * sin(uTime * 0.11) + 0.3 * uVocal * uAmp * 0.2);
        p = p * 1.65 - vec3(0.35, 0.18, 0.28);
        s *= 1.65;
        trap = min(trap, dot(p, p));
    }
    gTrap = trap;
    vec3 q = abs(p) - vec3(1.0, 1.0, 1.0);
    float box = (length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0)) / s;
    return box;
}

float DE(vec3 p) {
    if (uScene == 0) return deBulb(p);
    if (uScene == 1) return deBlobs(p);
    return deCrystal(p);
}
vec3 normalAt(vec3 p, float e) {
    vec2 k = vec2(1.0, -1.0);
    vec3 n = k.xyy * DE(p + k.xyy * e) + k.yyx * DE(p + k.yyx * e) + k.yxy * DE(p + k.yxy * e) + k.xxx * DE(p + k.xxx * e);
    float l = dot(n, n);
    return (l > 1e-20) ? n * inversesqrt(l) : vec3(0.0, 1.0, 0.0);   // never normalise a zero vector (NaN -> black squares)
}

vec3 env(vec3 d) {
    // soft studio environment tinted by the palette
    float up = d.y * 0.5 + 0.5;
    vec3 sky = mix(pal(uPhase + 0.55) * 0.10, pal(uPhase + 0.15) * 0.55, pow(up, 1.6));
    float key = pow(max(dot(d, normalize(vec3(-0.5, 0.7, -0.5))), 0.0), 28.0);
    float fill = pow(max(dot(d, normalize(vec3(0.7, 0.2, -0.4))), 0.0), 18.0);
    return sky + vec3(1.0, 0.95, 0.9) * key * 1.6 + pal(uPhase + 0.35) * fill * 0.8;
}

void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
    float fit = min(uAspect, 1.0);
    vec2 pp = p / fit;
    pp.y -= uCy;
    float f = uScale / 0.33;
    float camD = (uScene == 0) ? 2.9 : (uScene == 1 ? 3.2 : 5.6);
    vec3 ro = vec3(0.0, 0.0, -camD);
    vec3 rd = normalize(vec3(pp, f));
    mat3 R = rotX(uTilt) * rotY(uRot);
    mat3 Ri = transpose(R);
    vec3 oro = Ri * ro, ord = Ri * rd;

    // bounding sphere skip
    float bb = (uScene == 2) ? 3.4 : (uScene == 1 ? 1.9 : 1.7);
    float b = dot(oro, ord);
    float c = dot(oro, oro) - bb * bb;
    float h = b * b - c;
    vec3 col = vec3(0.0);
    if (h > 0.0) {
        float t = max(-b - sqrt(h), 0.0);
        float tmax = -b + sqrt(h);
        float glow = 0.0;
        bool hit = false;
        int steps = 0;
        float md = 1e9;
        for (int i = 0; i < 160; i++) {
            if (i >= uSteps) break;
            float d = DE(oro + ord * t);
            md = min(md, d);
            glow += exp(-max(d, 0.0) * 14.0) * 0.012;
            if (d < 0.0012 * max(t, 1.0)) { hit = true; steps = i; break; }
            t += d * (uScene == 0 ? 0.9 : 0.8);
            steps = i;
            if (t > tmax + 0.3) break;
        }
        vec3 haze = pal(uPhase + 0.4) * glow;
        if (hit) {
            vec3 P = oro + ord * t;
            float tr = gTrap;
            vec3 N = normalAt(P, 0.0015 * max(t, 1.0));
            vec3 V = -ord;
            vec3 L1 = normalize(vec3(-0.5, 0.7, -0.5));
            float diff = max(dot(N, L1), 0.0);
            float ao = clamp(1.0 - float(steps) / float(max(uSteps, 1)) * 1.4, 0.0, 1.0);
            float fres = pow(1.0 - max(dot(N, V), 0.0), 3.0);
            vec3 base;
            if (uScene == 0) base = pal(uPhase + sqrt(tr) * uSpread * 0.9 + 0.1 * uPitch);
            else if (uScene == 1) base = pal(uPhase + tr * uSpread + uPitch * 0.3);
            else base = pal(uPhase + log(1.0 + tr) * uSpread * 0.35 + 0.08 * uPitch);
            vec3 refl = env(reflect(ord, N));
            float gloss = (uScene == 1) ? 0.85 : 0.35;
            col = base * (0.10 + 0.95 * diff) * (0.35 + 0.65 * ao)
                + refl * (0.12 + gloss * fres) * (0.4 + 0.6 * ao)
                + pal(uPhase + 0.7) * fres * 0.45;
            if (uScene == 1) {
                // one extra bounce for glossy blob-on-blob reflections
                vec3 rro = P + N * 0.02, rrd = reflect(ord, N);
                float tt = 0.0; bool h2 = false;
                for (int j = 0; j < 28; j++) {
                    float d = DE(rro + rrd * tt);
                    if (d < 0.003) { h2 = true; break; }
                    tt += d; if (tt > 3.0) break;
                }
                if (h2) col = mix(col, base * 0.8 + env(rrd) * 0.2, 0.22 * fres + 0.12);
            }
            col *= 1.0 + 0.25 * uBeat;
        }
        col += haze * (0.5 + 0.5 * uBass);
    }
    col *= uIntensity;
    // NaN / Inf would be smeared by the bloom and feedback passes into black squares, so never let one out
    if (!(col.x < 64.0 && col.y < 64.0 && col.z < 64.0)) col = vec3(0.0);
    oCol = vec4(max(col, 0.0), 1.0);
}
