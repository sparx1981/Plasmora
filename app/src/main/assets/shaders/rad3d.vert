#version 300 es
precision highp float;
precision highp int;

// 3D radial spectrum disc: floor spokes on a tilted ring and spectrum spikes rising from the rim.
uniform sampler2D uAudio;
uniform int   uN;                 // spokes
uniform float uFit, uAspect, uRot, uTilt, uDist, uCy, uFocal, uR, uAmp, uPow, uBeat, uBass, uSpin, uInner, uSpikeW, uFloorW;

out vec3 vA;                      // x = across (-1..1), y = along (0..1), z = hue 0..1
out vec2 vK;                      // x = kind (0 floor, 1 spike), y = spike height 0..1

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
    int v6 = gl_VertexID % 6;
    int quad = gl_VertexID / 6;                       // 0 floor spoke, 1 spike
    int cidx = v6 == 0 ? 0 : (v6 == 1 ? 1 : (v6 == 2 ? 2 : (v6 == 3 ? 2 : (v6 == 4 ? 1 : 3))));
    float side = float((cidx & 1) * 2 - 1);
    float t = float(cidx >> 1);
    float u = (float(inst) + 0.5) / float(uN);
    float ang = u * TAU + uSpin;
    vec3 dir = vec3(cos(ang), 0.0, sin(ang));
    vec3 tng = vec3(-dir.z, 0.0, dir.x);
    float mu = abs(2.0 * fract(u) - 1.0);             // mirrored: symmetric disc
    float sp = pow(clamp(spec(mu * 0.92), 0.0, 1.3), uPow);
    float h = uAmp * (0.04 + sp) * (0.9 + 0.25 * uBeat);
    vec3 w;
    float arc = TAU * uR / float(uN);
    if (quad == 0) {
        float r = mix(uInner * uR, uR, t);
        w = dir * r + tng * side * arc * uFloorW * 0.5;
        vK = vec2(0.0, 0.0);
    } else {
        vec3 wv = ry(vec3(1.0, 0.0, 0.0), -uRot);     // camera-facing width vector
        float wd = arc * uSpikeW * 0.5 * mix(1.0, 0.25, t);
        w = dir * uR * 1.02 + vec3(0.0, h * t, 0.0) + wv * side * wd;
        vK = vec2(1.0, clamp(sp, 0.0, 1.3));
    }
    vA = vec3(side, t, u);
    vec2 p = proj(w);
    gl_Position = vec4(p.x / uAspect, p.y, 0.0, 1.0);
}
