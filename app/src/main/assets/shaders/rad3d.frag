#version 300 es
precision highp float;
in vec3 vA;
in vec2 vK;
out vec4 oCol;
uniform float uIntensity, uPhase, uSpread, uHueLevel;
uniform vec3 pa, pb, pc, pd;
vec3 pal(float x) { return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0); }
void main() {
    float across = abs(vA.x);
    vec3 col = pal(uPhase + vA.z * uSpread + vK.y * uHueLevel * 0.3);
    vec3 rgb;
    if (vK.x < 0.5) {
        float e = smoothstep(1.0, 0.2, across);
        rgb = col * (0.10 + 0.55 * vA.y) * e;
    } else {
        float e = smoothstep(1.0, 0.35, across);
        float hot = pow(vA.y, 3.0);                       // brightens toward the tip
        rgb = (col * (0.55 + 0.9 * vA.y) + vec3(0.7) * hot * (0.3 + vK.y)) * e * (0.35 + 0.9 * vK.y);
    }
    rgb *= uIntensity;
    if (!(rgb.x < 64.0 && rgb.y < 64.0 && rgb.z < 64.0)) rgb = vec3(0.0);
    oCol = vec4(max(rgb, 0.0), 1.0);
}
