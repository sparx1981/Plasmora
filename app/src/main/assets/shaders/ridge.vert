#version 300 es
precision highp float;
precision highp int;

uniform sampler2D uHist;     // 256 x ROWS, row 0 = newest
uniform int   uRow, uRows, uPass;   // pass 0 = hidden-line fill, 1 = line
uniform float uFrac, uAspect, uAmp, uWidth, uTop, uBot, uSpan, uFar, uDrop;

out vec2  vLine;
out float vT;
out float vDepth;

const int NPT = 512;

float H(float s) {
    float x = clamp(s, 0.0, 1.0) * 255.0;
    int i = int(x);
    int j = min(i + 1, 255);
    float f = x - float(i);
    return mix(texelFetch(uHist, ivec2(i, uRow), 0).r, texelFetch(uHist, ivec2(j, uRow), 0).r, f);
}

float depthOf() {
    return clamp((float(uRow) + uFrac) / float(uRows - 1), 0.0, 1.0);
}

vec2 P(float t) {
    float s = abs(t * 2.0 - 1.0);
    float p = depthOf();
    float xs = mix(1.0, uFar, p);
    float env = mix(0.30, 1.0, exp(-s * s * 5.0));
    float taper = smoothstep(1.0, 0.80, s);
    float h = H(s) * uAmp * env * taper * mix(1.0, 0.65, p);
    return vec2((t * 2.0 - 1.0) * uSpan * xs, mix(uBot, uTop, p) + h);
}

void main() {
    int idx = gl_VertexID >> 1;
    float side = float((gl_VertexID & 1) * 2 - 1);
    float d = 1.0 / float(NPT - 1);
    float t = float(idx) * d;
    vec2 p0 = P(t);
    float p = depthOf();
    if (uPass == 0) {
        float base = mix(uBot, uTop, p);
        if (side < 0.0) p0.y = base - uDrop;
        vLine = vec2(0.0);
    } else {
        vec2 a = P(t - d), b = P(t + d);
        vec2 tg = b - a;
        float l = length(tg);
        tg = l > 1e-6 ? tg / l : vec2(1.0, 0.0);
        p0 += vec2(-tg.y, tg.x) * side * uWidth * 0.5;
        vLine = vec2(side, 0.0);
    }
    vT = t;
    vDepth = p;
    gl_Position = vec4(p0.x / uAspect, p0.y, 0.0, 1.0);
}
