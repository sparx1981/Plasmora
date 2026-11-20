#version 300 es
precision highp float;
precision highp int;

// 3D bar grid (glBars style). One instance per bar, 36 vertices per cube.
// Columns = frequency, rows = time history (row 0 = newest, nearest the viewer).
uniform sampler2D uHist;      // 256 x 40, R16F
uniform float uFit, uAspect, uFrac, uRot, uTilt, uDist, uCy, uFocal;
uniform float uSpanX, uSpanZ, uHeight, uGap, uPow, uFarFade;
uniform float uBass, uBeat, uTime;
uniform int   uNX, uNZ, uRows;
uniform int   uPass;          // 0 = bars, 1 = floor reflection

out vec3  vN;
out vec2  vUV;
out float vT;
out float vVal;
out float vDepth;
out float vFace;
out float vTop;

vec3 rx(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x, p.y * c + p.z * s, -p.y * s + p.z * c); }
vec3 ry(vec3 p, float a) { float c = cos(a), s = sin(a); return vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c); }

float histRow(float x, int row) {
    int i = int(x);
    int j = min(i + 1, 255);
    float f = x - float(i);
    return mix(texelFetch(uHist, ivec2(i, row), 0).r, texelFetch(uHist, ivec2(j, row), 0).r, f);
}
// band value at a fractional history row (interpolated between rows so heights flow smoothly in time)
float bandAtRow(float t0, float t1, float rowF) {
    rowF = clamp(rowF, 0.0, 39.0);
    int r0 = int(rowF);
    int r1 = min(r0 + 1, 39);
    float fr = rowF - float(r0);
    float m = 0.0;
    for (int k = 0; k < 4; k++) {
        float x = clamp(mix(t0, t1, (float(k) + 0.5) / 4.0), 0.0, 1.0) * 255.0;
        m = max(m, mix(histRow(x, r0), histRow(x, r1), fr));
    }
    return m;
}
float bandSmooth(float t0, float t1, float rowF) {
    // wide, gentle smoothing in time: bars glide instead of snapping with every transient
    return 0.12 * bandAtRow(t0, t1, rowF - 3.2) + 0.2 * bandAtRow(t0, t1, rowF - 1.6) + 0.36 * bandAtRow(t0, t1, rowF)
         + 0.2 * bandAtRow(t0, t1, rowF + 1.6) + 0.12 * bandAtRow(t0, t1, rowF + 3.2);
}

void main() {
    int id = gl_InstanceID;
    int ox = id % uNX;
    int oz = id / uNX;

    // painter's order: farthest cell first so nearer bars overwrite it
    vec3 cam = ry(rx(vec3(0.0, 0.0, -uDist), -uTilt), -uRot);
    int ix = cam.x > 0.0 ? ox : (uNX - 1 - ox);
    int iz = cam.z > 0.0 ? oz : (uNZ - 1 - oz);

    float fx = (float(ix) + 0.5) / float(uNX);
    // bars stay in place; the music history flows backwards through them
    float stride = float(uRows - 1) / float(max(uNZ - 1, 1));
    float rowF = float(iz) * stride + 1.0 - uFrac;   // one row (50 ms) of delay keeps the front row continuous too

    // colour/height sample: symmetric about the centre so bass sits in the middle
    float s0 = float(ix) / float(uNX), s1 = float(ix + 1) / float(uNX);
    float h = bandSmooth(s0, s1, rowF);
    h = pow(clamp(h, 0.0, 1.2), uPow);
    float nearBoost = 1.0 + uBeat * 0.10 * (1.0 - float(iz) / float(uNZ));
    float idle = 0.035 + 0.03 * sin(uTime * 1.7 + float(ix) * 0.55 + float(iz) * 0.35);
    float height = 0.03 + (h * 0.8 * nearBoost + idle) * uHeight;

    float dx = 2.0 * uSpanX / float(uNX);
    float dz = 2.0 * uSpanZ / float(uNZ);
    float cx = -uSpanX + (float(ix) + 0.5) * dx;
    float cz = -uSpanZ + (float(iz) + 0.5) * dz;
    float bw = dx * 0.5 * (1.0 - uGap);
    float bd = dz * 0.5 * (1.0 - uGap);

    // cube corner from vertex id
    int v = gl_VertexID;
    int face = v / 6;
    int k = v - face * 6;
    int corner = (k == 0) ? 0 : (k == 1) ? 1 : (k == 2) ? 2 : (k == 3) ? 2 : (k == 4) ? 1 : 3;
    float cu = float(corner & 1) - 0.5;
    float cv = float(corner >> 1) - 0.5;
    int axis = face / 2;
    float sgn = float((face & 1) * 2 - 1);
    vec3 loc;  // in [-0.5, 0.5]
    vec3 nrm = vec3(0.0);
    if (axis == 0)      { loc = vec3(sgn * 0.5, cu, cv); nrm.x = sgn; }
    else if (axis == 1) { loc = vec3(cu, sgn * 0.5, cv); nrm.y = sgn; }
    else                { loc = vec3(cu, cv, sgn * 0.5); nrm.z = sgn; }
    // axis 0: u->y, v->z ; axis 1: u->x, v->z ; axis 2: u->x, v->y
    vec3 wp = vec3(cx + loc.x * 2.0 * bw, (loc.y + 0.5) * height, cz + loc.z * 2.0 * bd);
    float uvy = (axis == 0) ? (cu + 0.5) : (axis == 2) ? (cv + 0.5) : 0.5;
    vec2 uv = (axis == 0) ? vec2(cv + 0.5, cu + 0.5) : (axis == 1) ? vec2(cu + 0.5, cv + 0.5) : vec2(cu + 0.5, cv + 0.5);

    if (uPass == 1) { wp.y = -wp.y; nrm.y = -nrm.y; }

    // cull faces that point away from the camera (degenerate triangle)
    vec3 fc = vec3(cx, (uPass == 1 ? -0.5 : 0.5) * height, cz) + nrm * vec3(bw, 0.5 * height, bd);
    if (dot(nrm, cam - fc) <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        vN = nrm; vUV = uv; vT = 0.0; vVal = 0.0; vDepth = 1.0; vFace = 0.0; vTop = 0.0;
        return;
    }

    vec3 q = rx(ry(wp, uRot), uTilt) + vec3(0.0, 0.0, uDist);
    float fit = uFit;
    vec2 sc = q.xy / max(q.z, 0.05) * uFocal;
    gl_Position = vec4(sc.x * fit / uAspect, sc.y * fit + uCy, 0.0, 1.0);

    vN = nrm;
    vUV = uv;
    vT = fx;
    vVal = (height - 0.03) / max(uHeight, 1e-4);
    vDepth = clamp(float(iz) / float(uNZ), 0.0, 1.0);
    vFace = float(axis);
    vTop = (axis == 1) ? 1.0 : 0.0;
    if (uPass == 1) vVal = vVal * 0.6;
    vDepth = clamp(vDepth + 0.0, 0.0, 1.0);
}
