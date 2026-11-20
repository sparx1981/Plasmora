#version 300 es
precision highp float;
in vec2 vUv;
out vec4 oCol;

// Temporal anti-aliasing resolve with neighbourhood clamping and camera-shake reprojection.
uniform sampler2D uCur;
uniform sampler2D uHist;
uniform vec2  uTexel;
uniform vec4  uShake, uShakePrev;
uniform float uAspect, uAlpha;

vec2 camT(vec2 uv, vec4 sh) {
    vec2 p = uv - 0.5;
    p.x *= uAspect;
    float c = cos(sh.z), s = sin(sh.z);
    p = mat2(c, -s, s, c) * p;
    p /= (1.0 + sh.w);
    p.x /= uAspect;
    return p + 0.5 + sh.xy;
}

void main() {
    vec3 c = texture(uCur, vUv).rgb;
    if (!(c.x < 1.0e4 && c.y < 1.0e4 && c.z < 1.0e4)) c = vec3(0.0);
    vec3 mn = c, mx = c;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        if (x == 0 && y == 0) continue;
        vec3 s = texture(uCur, vUv + vec2(float(x), float(y)) * uTexel).rgb;
        mn = min(mn, s); mx = max(mx, s);
    }
    vec2 dT = camT(vUv, uShake) - camT(vUv, uShakePrev);
    vec2 hu = vUv + dT;
    vec3 h = texture(uHist, hu).rgb;
    if (any(isnan(h)) || any(isinf(h))) h = c;
    h = clamp(h, mn, mx);
    float px = length(dT / uTexel);
    float a = uAlpha * (1.0 - smoothstep(0.75, 4.0, px));
    if (hu.x < 0.0 || hu.x > 1.0 || hu.y < 0.0 || hu.y > 1.0) a = 0.0;
    oCol = vec4(mix(c, h, a), 1.0);
}
