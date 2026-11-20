#version 300 es
precision highp float;
precision highp int;

// Ring of translucent glass bars with neon edges, mirrored below the floor, plus a glowing floor ellipse.
uniform sampler2D uAudio;
uniform int   uN, uRings;
uniform float uFit, uAspect, uRot, uTilt, uDist, uCy, uFocal, uR, uAmp, uPow, uBeat, uBW, uSpin, uHueLevel, uSpread, uPhase;

out vec4 vA;     // xy = face uv (-1..1), z = height fraction, w = hue
out vec4 vK;     // x = kind (0 body, 1 floor ring), y = reflection flag, z = face id, w = loudness

const float TAU = 6.2831853;
vec3 rx(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x, p.y * c + p.z * s, -p.y * s + p.z * c); }
vec3 ry(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }
float tap(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x); int j = min(i + 1, 1023);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, x - float(i));
}
float spec(float s) {
    float a = 0.0, w = 0.0;
    for (int k = -3; k <= 3; k++) { float ww = exp(-float(k * k) / 6.0); a += tap(pow(clamp(s, 0.0, 1.0), 1.5) * 0.5 + float(k) * 0.003) * ww; w += ww; }
    return a / w;
}
vec2 proj(vec3 w) {
    vec3 q = rx(ry(w, uRot), uTilt) + vec3(0.0, 0.0, uDist);
    vec2 sc = q.xy / max(q.z, 0.05) * uFocal;
    return vec2(sc.x * uFit, sc.y * uFit + uCy);
}

void main() {
    int inst = gl_InstanceID;
    int bars = uN * uRings;
    int v = gl_VertexID;
    int v6 = v % 6;
    int cidx = v6 == 0 ? 0 : (v6 == 1 ? 1 : (v6 == 2 ? 2 : (v6 == 3 ? 2 : (v6 == 4 ? 1 : 3))));
    float cx = float((cidx & 1) * 2 - 1);
    float cy = float(cidx >> 1) * 2.0 - 1.0;

    if (inst >= bars * 2) {                           // floor ring segment
        int k = inst - bars * 2;
        int segs = 120;
        float a0 = float(k) / float(segs) * TAU + uSpin, a1 = float(k + 1) / float(segs) * TAU + uSpin;
        float Rr = uR * 1.32;
        vec2 pa_ = proj(vec3(cos(a0) * Rr, 0.0, sin(a0) * Rr));
        vec2 pb_ = proj(vec3(cos(a1) * Rr, 0.0, sin(a1) * Rr));
        vec2 ab = pb_ - pa_;
        vec2 d = ab / max(length(ab), 1e-5);
        vec2 n = vec2(-d.y, d.x);
        float wdt = 0.006;
        vec2 base = mix(pa_, pb_, float(cidx >> 1));
        vec2 pxy = base + n * wdt * cx + d * wdt * (float(cidx >> 1) * 2.0 - 1.0) * 0.5;
        gl_Position = vec4(pxy.x / uAspect, pxy.y, 0.0, 1.0);
        vA = vec4(cx, 0.0, 0.0, float(k) / float(segs));
        vK = vec4(1.0, 0.0, 0.0, 0.0);
        return;
    }
    int refl = inst >= bars ? 1 : 0;
    int bi = inst - refl * bars;
    int ring = bi / uN;
    int i = bi - ring * uN;
    float rr = uR * (1.0 - 0.34 * float(ring));
    float u = (float(i) + 0.5 + 0.5 * float(ring)) / float(uN);
    float ang = u * TAU + uSpin * (ring == 0 ? 1.0 : -1.3);
    float mu = abs(2.0 * fract(u) - 1.0);
    float sp = pow(clamp(spec(mu * 0.92 + 0.04 * float(ring)), 0.0, 1.3), uPow);
    float h = uAmp * (0.05 + sp) * (0.9 + 0.25 * uBeat);
    float arc = TAU * rr / float(uN);
    float hw = arc * uBW * 0.5;
    vec3 dir = vec3(cos(ang), 0.0, sin(ang));
    vec3 tng = vec3(-dir.z, 0.0, dir.x);
    // box local coords: x radial, y up, z tangent
    int face = v / 6;
    vec3 n3 = face == 0 ? vec3(0, 1, 0) : (face == 1 ? vec3(0, -1, 0) : (face == 2 ? vec3(1, 0, 0) : (face == 3 ? vec3(-1, 0, 0) : (face == 4 ? vec3(0, 0, 1) : vec3(0, 0, -1)))));
    vec3 u3 = abs(n3.y) > 0.5 ? vec3(1, 0, 0) : (abs(n3.x) > 0.5 ? vec3(0, 0, 1) : vec3(1, 0, 0));
    vec3 v3 = abs(n3.y) > 0.5 ? vec3(0, 0, 1) : vec3(0, 1, 0);
    vec3 lp = n3 + u3 * cx + v3 * cy;                  // in [-1,1]^3
    vec3 ext = vec3(hw, h * 0.5, hw);
    vec3 loc = lp * ext;
    float yy = loc.y + h * 0.5;                        // 0..h
    vec3 w = dir * rr + dir * loc.x + tng * loc.z;
    w.y = refl == 1 ? -yy : yy;
    vA = vec4(cx, cy, clamp(yy / max(h, 1e-4), 0.0, 1.0), u);
    vK = vec4(0.0, float(refl), float(face), clamp(sp, 0.0, 1.3));
    vec2 p = proj(w);
    gl_Position = vec4(p.x / uAspect, p.y, 0.0, 1.0);
}
