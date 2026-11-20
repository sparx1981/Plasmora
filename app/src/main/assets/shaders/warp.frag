#version 300 es
precision highp float;
in vec2 vUv;
out vec4 oCol;

uniform sampler2D uPrev;
uniform vec2 uTexel;
uniform float uAspect;

uniform float uZoom;     // >1 = content flows outward
uniform float uRot;      // radians this frame
uniform float uSwirl;    // extra rotation near the centre
uniform float uSwirlR;   // swirl falloff
uniform vec2  uTrans;
uniform float uRipA, uRipF, uRipP, uRipR;
uniform float uFlowA, uFlowS, uFlowT;
uniform float uKal;
uniform float uTwist;
uniform float uDecay;
uniform vec3  uTint;
uniform float uHue;
uniform float uSoft;     // >0 blur, <0 sharpen

vec3 hueRot(vec3 c, float h) {
    const vec3 k = vec3(0.57735026);
    float ca = cos(h), sa = sin(h);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);

    if (uKal > 0.5) {
        float seg = 6.2831853 / floor(uKal + 0.5);
        float a = atan(p.y, p.x);
        float r0 = length(p);
        a = mod(a, seg);
        a = abs(a - seg * 0.5);
        p = r0 * vec2(cos(a), sin(a));
    }

    float r = length(p);
    float ang = uRot + uSwirl * exp(-r * uSwirlR) + uTwist * log(r + 0.03);
    float c = cos(ang), s = sin(ang);
    p = mat2(c, -s, s, c) * p;
    p /= uZoom;
    p += uTrans;

    if (uRipA != 0.0) {
        float rr = length(p);
        vec2 dir = p / (rr + 1e-4);
        p += dir * (uRipA * uRipR) * sin(rr * uRipF - uRipP);
        float lin = 1.0 - uRipR;
        p.x += uRipA * lin * sin(p.y * uRipF + uRipP);
        p.y += uRipA * lin * cos(p.x * uRipF * 0.8 - uRipP * 1.3);
    }

    if (uFlowA != 0.0) {
        vec2 q = p * uFlowS;
        vec2 f = vec2(
            sin(q.y * 1.3 + uFlowT) + 0.5 * sin(q.y * 2.7 - uFlowT * 1.3 + q.x),
            cos(q.x * 1.1 - uFlowT * 0.9) + 0.5 * cos(q.x * 2.3 + uFlowT * 1.7 + q.y));
        p += f * uFlowA;
    }

    vec2 uv = p / vec2(uAspect, 1.0) + 0.5;
    vec3 c0 = textureLod(uPrev, uv, 0.0).rgb;
    if (uSoft != 0.0) {
        vec2 o = uTexel;
        vec3 b = 0.25 * (textureLod(uPrev, uv + vec2(o.x, o.y), 0.0).rgb
                       + textureLod(uPrev, uv + vec2(-o.x, o.y), 0.0).rgb
                       + textureLod(uPrev, uv + vec2(o.x, -o.y), 0.0).rgb
                       + textureLod(uPrev, uv + vec2(-o.x, -o.y), 0.0).rgb);
        c0 += (b - c0) * uSoft;
    }
    c0 = hueRot(c0, uHue) * uTint * uDecay;
    c0 = max(c0 - 0.0003, 0.0);
    // soft energy limiter: keeps hot spots from washing out to flat white
    float m = max(c0.r, max(c0.g, c0.b));
    c0 /= 1.0 + max(m - 1.2, 0.0) * 0.30;
    c0 = min(c0, vec3(6.0));
    if (!(c0.x < 1.0e3 && c0.y < 1.0e3 && c0.z < 1.0e3)) c0 = vec3(0.0);
    oCol = vec4(max(c0, 0.0), 1.0);
}
