#version 300 es
precision highp float;
precision highp int;
in vec2  vLocal;
in float vT;
in float vVal;
in float vH;
in float vHalf;
out vec4 oCol;

uniform int   uStyle;
uniform int   uCap;
uniform float uIntensity, uPhase, uSpread, uHueLevel;
uniform float uSeg, uSegGap, uReflect, uFill, uEdgeW, uYHue;
uniform vec3 pa, pb, pc, pd;

vec3 pal(float x) {
    return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0);
}

void main() {
    vec3 col = pal(uPhase + vT * uSpread + vVal * uHueLevel + (uCap == 1 ? vVal : vLocal.y * vVal) * uYHue);
    vec3 rgb;
    if (uStyle != 1) {
        float ex = smoothstep(1.0, 0.45, abs(vLocal.x));
        if (uCap == 1) rgb = mix(col, vec3(1.0), 0.55) * ex * 1.15;
        else           rgb = col * (0.28 + 0.95 * vLocal.y) * ex;
        if (uSeg > 0.0 && uCap == 0) {
            float sc = fract(vLocal.y * vH / uSeg);
            rgb *= smoothstep(uSegGap * 0.5, uSegGap * 0.5 + 0.08, sc) * (1.0 - smoothstep(1.0 - 0.08, 1.0, sc) * 0.0);
        }
        if (vHalf > 0.5) rgb *= uReflect;
    } else {
        float y = vLocal.y;
        float dist = (1.0 - y) * vH;
        float ew = uEdgeW > 0.0 ? uEdgeW : 0.0258;
        float edge = exp(-dist * dist / (ew * ew));
        rgb = col * (0.10 + 0.55 * y * y) * uFill + (col * 0.4 + vec3(0.6)) * edge;
        if (vHalf > 0.5) rgb *= uReflect;
    }
    oCol = vec4(rgb * uIntensity, 1.0);
}
