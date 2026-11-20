#version 300 es
precision highp float;
precision highp int;

uniform sampler2D uAudio;   // row 2 = spectrum
uniform int   uMode;        // 0 = galaxy, 1 = starfield, 2 = fountain ring
uniform float uAspect;
uniform float uTravel;      // time-like value that drives motion
uniform float uSize;        // point size in pixels
uniform float uScale;       // overall radius
uniform float uArms;        // spiral arms (galaxy)
uniform float uSwirl;       // how much the arms wind up
uniform float uBass, uBeat;
uniform float uKick, uSnare, uVocal, uPitch, uBright, uRot, uTilt, uTime3;
uniform float uPhase, uSpread;
uniform vec3 pa, pb, pc, pd;

out vec3 vCol;
out float vA;

const float TAU = 6.2831853;

float hsh(float n) { return fract(sin(n * 12.9898 + 4.1414) * 43758.5453); }
vec3 pal(float x) { return clamp(pa + pb * cos(TAU * (pc * x + pd)), 0.0, 3.0); }
float spec(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x);
    int j = min(i + 1, 1023);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, x - float(i));
}

mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0, -s, 0, 1, 0, s, 0, c); }
mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1, 0, 0, 0, c, s, 0, -s, c); }

// divergence-free ABC flow: a swirling, never-clumping swarm field
vec3 abc(vec3 p, float t) {
    float A = 1.0, B = 0.7 + 0.25 * sin(t * 0.3), C = 0.8 + 0.25 * cos(t * 0.23);
    return vec3(A * sin(p.z * 1.7 + t * 0.4) + C * cos(p.y * 1.7),
                B * sin(p.x * 1.7) + A * cos(p.z * 1.7 + t * 0.3),
                C * sin(p.y * 1.7 + t * 0.35) + B * cos(p.x * 1.7));
}
// Aizawa attractor derivative
vec3 aiz(vec3 p, float a) {
    float b = 0.7, c = 0.6, d = 3.5, e = 0.25, f = 0.1;
    return vec3((p.z - b) * p.x - d * p.y,
                d * p.x + (p.z - b) * p.y,
                c + a * p.z - p.z * p.z * p.z / 3.0 - (p.x * p.x + p.y * p.y) * (1.0 + e * p.z) + f * p.z * p.x * p.x * p.x);
}

void main() {
    float id = float(gl_VertexID);
    if (uMode >= 3) {
        float g1 = hsh(id), g2 = hsh(id + 17.3), g3 = hsh(id + 91.7), g4 = hsh(id + 233.1);
        float life = fract(g1 + uTravel * (0.02 + 0.04 * g2));
        vec3 q; float sp = spec(mix(0.02, 0.85, g3 * 0.8 + life * 0.2));
        float size = 1.0;
        if (uMode == 3) {
            // swarm: integrate the flow from a seeded start; kick pulses open the swarm
            vec3 p = (vec3(g1, g2, g3) - 0.5) * 3.2;
            float t = uTravel;
            float n = 6.0 + floor(life * 10.0);
            for (int i = 0; i < 16; i++) {
                if (float(i) >= n) break;
                p += abc(p, t) * 0.035;
            }
            q = p * 0.42 * (1.0 + 0.35 * uKick + 0.12 * uBeat);
            size = 0.7 + 1.2 * g4;
        } else if (uMode == 4) {
            // Aizawa strange attractor, trail length grows along the particle's life
            float sr = sqrt(g1) * 1.1, sa = g2 * TAU;
            vec3 p = vec3(sr * cos(sa), sr * sin(sa), -0.4 + 1.6 * g3);
            float a = 0.95 + 0.18 * uKick + 0.06 * sin(uTravel * 0.2);
            float steps = 14.0 + floor(fract(life + uTravel * 0.05) * 34.0);
            for (int i = 0; i < 48; i++) {
                if (float(i) >= steps) break;
                p += aiz(p, a) * 0.02;
            }
            q = vec3(p.x, p.z - 0.2, p.y) * 0.85;
            size = 0.6 + 1.0 * g4;
        } else {
            // kick-burst shell: a sphere of particles that blasts outward on kicks and snares
            float lon = g1 * TAU, lat = acos(2.0 * g2 - 1.0);
            vec3 d = vec3(sin(lat) * cos(lon), cos(lat), sin(lat) * sin(lon));
            float sd = spec(0.04 + 0.8 * abs(fract(lon / TAU * 3.0 + uTravel * 0.02) * 2.0 - 1.0));
            sp = sd;
            float shell = 0.5 + 0.34 * sd * (0.6 + 0.4 * abs(d.y)) + 0.5 * uKick * (0.4 + g3) + 0.35 * uSnare * g4;
            q = d * shell * (1.0 + 0.4 * uBeat);
            q += vec3(sin(uTravel * 0.7 + g3 * 6.0), cos(uTravel * 0.6 + g4 * 6.0), 0.0) * 0.05;
            size = 0.7 + 1.0 * g3 + 1.2 * uSnare * g4;
        }
        q = rotX(uTilt) * rotY(uRot) * q;
        float s = 1.7 / (2.5 + q.z);
        vec2 pr = q.xy * s * uScale;
        gl_Position = vec4(pr.x * min(uAspect, 1.0) / uAspect, pr.y * min(uAspect, 1.0), 0.0, 1.0);
        gl_PointSize = max(1.2, uSize * size * s * (0.6 + 1.2 * sp));
        vCol = pal(uPhase + g2 * uSpread * 0.5 + length(q) * 0.25 + uPitch * 0.25);
        float env = smoothstep(0.0, 0.08, life) * smoothstep(1.0, 0.8, life);
        vA = (0.35 + 1.1 * sp + 0.5 * uVocal) * (0.5 + 0.5 * s) * (uMode == 4 ? 0.8 : 1.0) * (uMode == 5 ? 1.0 : env + 0.35);
        return;
    }
    float h1 = hsh(id), h2 = hsh(id + 17.3), h3 = hsh(id + 91.7), h4 = hsh(id + 233.1);
    float life = fract(h1 + uTravel * (0.035 + 0.05 * h2));
    float fit = min(uAspect, 1.0);
    float r, a;
    if (uMode == 0) {
        float arm = floor(h2 * uArms) / uArms * TAU;
        r = (0.04 + 0.96 * pow(life, 0.75)) * uScale;
        a = arm + r * uSwirl + (h3 - 0.5) * 0.7 * (0.3 + life) + uTravel * 0.08;
    } else if (uMode == 1) {
        r = pow(life, 2.2) * uScale * 1.5;
        a = h2 * TAU + r * uSwirl;
    } else {
        float k = life;
        r = (0.25 + 0.55 * h3) * uScale * (0.8 + 0.5 * k) ;
        a = h2 * TAU + uTravel * 0.2 * (h4 - 0.5);
        r += 0.18 * uScale * sin(k * 6.2831853 + id);
    }
    float sp = spec(mix(0.02, 0.9, life * 0.6 + h3 * 0.4));
    r *= 1.0 + 0.16 * uBass * (1.0 - life) + 0.10 * uBeat;
    r += sp * 0.12 * uScale * (h4 - 0.3);
    vec2 p = r * vec2(cos(a), sin(a)) * fit;
    gl_Position = vec4(p.x / uAspect, p.y, 0.0, 1.0);
    gl_PointSize = max(1.5, uSize * (0.5 + 0.9 * h3 + 1.4 * sp) * (uMode == 1 ? (0.3 + life * 1.6) : 1.0));
    vCol = pal(uPhase + life * uSpread + h2 * 0.15);
    float env = smoothstep(0.0, 0.1, life) * smoothstep(1.0, 0.75, life);
    vA = env * (0.35 + 1.3 * sp);
}
