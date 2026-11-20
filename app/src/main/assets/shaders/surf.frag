#version 300 es
precision highp float;
precision highp int;
in vec2  vGrid;
in float vH;
in float vDepth;
in vec3  vW;
out vec4 oCol;

uniform int   uKind;
uniform float uTri, uIntensity, uPhase, uSpread, uHueLevel, uAlpha, uLineW, uBeat;
uniform vec3 pa, pb, pc, pd;

vec3 pal(float x) {
    return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0);
}

void main() {
    vec2 fw = max(fwidth(vGrid), vec2(1e-5));
    vec2 gr = abs(fract(vGrid - 0.5) - 0.5) / fw;       // pixel distance to nearest grid line
    float dpx = min(gr.x, gr.y);
    if (uTri > 0.5) {   // triangulated mesh: add the diagonal
        float sd = vGrid.x + vGrid.y;
        float fwd = max(fwidth(sd), 1e-5);
        dpx = min(dpx, abs(fract(sd - 0.5) - 0.5) / fwd);
    }
    float half_ = max(uLineW, 0.5) * 0.5;
    float line = 1.0 - smoothstep(half_, half_ + 1.0, dpx);
    float glow = exp(-dpx * 0.30);
    float dens = max(fw.x, fw.y);
    float keep = 1.0 - smoothstep(0.22, 0.55, dens);     // lines closer than ~2 px melt into the fill
    vec3 col = pal(uPhase + vH * uHueLevel + vDepth * uSpread);
    if (uKind == 0) {
        vec3 n = cross(dFdx(vW), dFdy(vW));
        float nl = length(n);
        n = nl > 1e-12 ? n / nl : vec3(0.0, 1.0, 0.0);
        float lam = clamp(dot(n, normalize(vec3(-0.4, 0.8, -0.45))) * sign(n.y + 1e-4), 0.0, 1.0);
        float fog = pow(max(1.0 - vDepth, 0.0), 0.85);
        vec3 fill = col * (0.03 + 0.10 * lam) * (0.4 + vH) * fog;
        vec3 ln = (col * 1.0 + vec3(0.30)) * line * keep * (0.35 + 0.95 * fog) * (0.45 + 0.9 * vH) * 1.25;
        vec3 glw = col * glow * 0.20 * keep * fog;
        float hl = smoothstep(0.55, 1.0, vH) * 0.6;
        oCol = vec4((fill + ln + glw + col * hl * line) * uIntensity * (1.0 + uBeat * 0.25), uAlpha);
    } else {
        float edge = 1.0 - smoothstep(0.80, 1.0, vDepth);
        vec3 fill = col * (0.010 + 0.10 * vH * vH) * edge;
        vec3 ln = (col + vec3(0.25) * vH) * line * keep * (0.35 + 0.8 * vH) * edge;
        vec3 glw = col * glow * 0.08 * keep * edge;
        oCol = vec4((fill + ln + glw) * uIntensity * (1.0 + uBeat * 0.3), 1.0);
    }
}
