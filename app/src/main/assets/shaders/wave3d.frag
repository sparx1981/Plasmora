#version 300 es
precision highp float;
precision highp int;
in vec2  vLine;
in float vT;
in float vA;
in float vFade;
in float vH;
out vec4 oCol;

uniform int   uKind;
uniform float uIntensity, uPhase, uSpread, uHueLevel, uAlpha;
uniform vec3 pa, pb, pc, pd;

vec3 pal(float x) {
    return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0);
}

void main() {
    if (uKind == 0) {
        float d = abs(vLine.x);
        float glow = exp(-d * d * 2.6);
        float core = exp(-d * d * 13.0);
        vec3 col = pal(uPhase + vT * uSpread + vA * uHueLevel);
        vec3 rgb = (col * 0.9 + vec3(0.45) * core) * uIntensity * vFade;
        oCol = vec4(rgb * glow, 1.0);
    } else if (uKind == 2) {
        vec2 q = gl_PointCoord * 2.0 - 1.0;
        q.y = -q.y;
        float r2 = dot(q, q);
        if (r2 > 1.0) discard;
        vec3 n = vec3(q, sqrt(1.0 - r2));
        vec3 L = normalize(vec3(-0.45, 0.65, 0.62));
        float dif = clamp(dot(n, L), 0.0, 1.0);
        vec3 Hh = normalize(L + vec3(0.0, 0.0, 1.0));
        float spc = pow(clamp(dot(n, Hh), 0.0, 1.0), 90.0);
        float rim = pow(1.0 - n.z, 2.0);
        vec3 base = pal(uPhase + vT * uSpread * 0.3 + clamp(vH, 0.0, 1.2) * uHueLevel);
        float shade = 0.06 + 1.15 * dif * dif;
        vec3 rgb = base * shade + vec3(1.0) * spc * 1.1 + base * rim * 0.5;
        float rr = sqrt(r2);
        float aa = fwidth(rr) * 1.1 + 0.02;
        float edge = 1.0 - smoothstep(1.0 - aa, 1.0, rr);
        rgb *= mix(0.55, 1.0, vFade) * smoothstep(1.0, 0.82, rr) + 0.25 * (1.0 - smoothstep(1.0, 0.82, rr));
        rgb = clamp(rgb * uIntensity, 0.0, 8.0);
        oCol = vec4(rgb, edge * uAlpha * clamp(vFade * 2.2, 0.0, 1.0));
    } else {
        vec2 q = gl_PointCoord * 2.0 - 1.0;
        float r2 = dot(q, q);
        if (r2 > 1.0) discard;
        float a = exp(-r2 * 3.2);
        vec3 col = pal(uPhase + vT * uSpread + vH * uHueLevel + vA * 0.2);
        vec3 rgb = (col + vec3(0.35) * exp(-r2 * 9.0)) * a * uIntensity * vFade;
        oCol = vec4(rgb, 1.0);
    }
}
