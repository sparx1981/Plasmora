#version 300 es
precision highp float;
precision highp int;
in vec2 vUv;
out vec4 oCol;

uniform sampler2D uAudio;
uniform float uCy;
uniform float uAspect, uPix, uScale, uAmp, uRot, uTilt, uTime, uBeat, uBass, uLon, uLineW;
uniform float uIntensity, uPhase, uSpread;
uniform vec3 pa, pb, pc, pd;

const float TAU = 6.2831853;
const float PI = 3.1415927;
vec3 pal(float x) { return clamp(pa + pb * cos(TAU * (pc * x + pd)), 0.0, 3.0); }
float spec(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x);
    int j = min(i + 1, 1023);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, x - float(i));
}

mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0, -s, 0, 1, 0, s, 0, c); }
mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1, 0, 0, 0, c, s, 0, -s, c); }

float disp(vec3 n) {
    float lon = atan(n.z, n.x);
    float lat = asin(clamp(n.y, -1.0, 1.0));
    float s = abs(2.0 * fract(lon / TAU + lat * 0.14 + uTime * 0.02) - 1.0);
    float f = spec(s * 0.85);
    return uAmp * (f * (0.65 + 0.35 * sin(lat * 5.0 + uTime * 1.3)) + 0.04 * uBass);
}
float map(vec3 p) {
    float r = length(p);
    return r - (1.0 + disp(p / r));
}

void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
    float fit = min(uAspect, 1.0);
    vec2 pp = p / fit;
    pp.y -= uCy;
    float f = uScale / 0.33;
    vec3 ro = vec3(0.0, 0.0, -3.2);
    vec3 rd = normalize(vec3(pp, f));
    mat3 R = rotX(uTilt) * rotY(uRot);      // object -> world; we sample in object space with R^T
    mat3 Ri = transpose(R);
    vec3 oro = Ri * ro, ord = Ri * rd;

    float t = 1.6;
    bool hit = false;
    for (int i = 0; i < 48; i++) {
        float d = map(oro + ord * t);
        if (d < 0.002) { hit = true; break; }
        t += d * 0.6;
        if (t > 5.5) break;
    }
    vec3 rgb = vec3(0.0);
    if (hit) {
        vec3 P = oro + ord * t;
        vec3 n = normalize(P);
        float lon = atan(n.z, n.x);
        float lat = asin(clamp(n.y, -1.0, 1.0));
        float N = floor(uLon + 0.5);
        float M = floor(N * 0.6 + 0.5);
        float dlon = abs(fract(lon / TAU * N + 0.5) - 0.5) * TAU / N * cos(lat);
        float dlat = abs(fract(lat / PI * M + 0.5) - 0.5) * PI / M;
        float pw = t * uPix / (fit * f) * uLineW;
        float m = 1.0 - smoothstep(0.0, pw * 1.7, min(dlon, dlat));
        float d = disp(n);
        float rim = pow(1.0 - clamp(dot(R * n, -rd), 0.0, 1.0), 3.0);
        vec3 col = pal(uPhase + abs(2.0 * fract(lon / TAU + uTime * 0.02) - 1.0) * uSpread + d * 1.2);
        rgb = col * (m * (0.9 + 1.6 * d / max(uAmp, 1e-3)) + 0.04 + rim * 0.45) * (1.0 + 0.4 * uBeat);
    }
    oCol = vec4(rgb * uIntensity, 1.0);
}
