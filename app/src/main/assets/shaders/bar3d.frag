#version 300 es
precision highp float;
precision highp int;

in vec3  vN;
in vec2  vUV;
in float vT;
in float vVal;
in float vDepth;
in float vFace;
in float vTop;
out vec4 oCol;

uniform int   uPass;
uniform float uIntensity, uPhase, uSpread, uHueLevel, uAlpha, uReflect, uFarFade, uBeat;
uniform vec3 pa, pb, pc, pd;

vec3 pal(float x) {
    return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0);
}

void main() {
    vec3 col = pal(uPhase + vT * uSpread + vVal * uHueLevel);

    // simple key light from above-left-front, so faces read as solid
    vec3 L = normalize(vec3(-0.35, 0.85, -0.45));
    float lam = max(dot(vN, L), 0.0);
    float shade = 0.22 + 0.95 * lam;
    if (uPass == 1) shade = 0.35 + 0.6 * lam;

    // glossy top: brighter towards the tip, thin bright rim on every face edge
    float e = min(min(vUV.x, 1.0 - vUV.x), min(vUV.y, 1.0 - vUV.y));
    float rim = 1.0 - smoothstep(0.0, 0.07, e);
    float tipGlow = (vTop > 0.5) ? 0.35 * smoothstep(0.0, 0.5, 1.0 - abs(vUV.y - 0.5) * 2.0) : 0.0;
    float grad = 0.55 + 0.75 * vUV.y * (vTop > 0.5 ? 0.0 : 1.0) + 0.3 * vVal;
    if (vTop > 0.5) grad = 0.9 + 0.5 * vVal;

    vec3 rgb = col * shade * grad;
    rgb += col * rim * 0.55 + vec3(rim * 0.12 + tipGlow * 0.25) * (0.4 + vVal);
    rgb *= 1.0 + uBeat * 0.35 * vVal;

    float fade = mix(1.0, 0.0, pow(clamp(vDepth, 0.0, 1.0), 1.6) * uFarFade);
    rgb *= fade;
    if (uPass == 1) {
        rgb *= uReflect * (0.35 + 0.65 * (1.0 - vVal));
    }
    oCol = vec4(rgb * uIntensity, uAlpha);
}
