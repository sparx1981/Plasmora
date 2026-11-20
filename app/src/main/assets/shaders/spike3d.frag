#version 300 es
precision highp float;
in vec3 vA;
in vec3 vS;
out vec4 oCol;
uniform float uIntensity, uPhase, uSpread, uHueLevel;
uniform vec3 pa, pb, pc, pd;
vec3 pal(float x) { return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0); }
void main() {
    float t = vA.y;
    float shade = 0.55 + 0.45 * clamp(vA.x * -0.8 + 0.2, -1.0, 1.0);        // one flank lit, one in shadow
    vec3 tint = pal(uPhase + vS.y * uHueLevel + vA.z * uSpread);
    vec3 dark = vec3(0.07, 0.085, 0.10);
    float glow = pow(t, 1.6) * (0.3 + 1.1 * clamp(vS.y, 0.0, 1.3));
    vec3 rgb = mix(dark, tint * (0.55 + 0.7 * shade), clamp(glow * 1.2, 0.0, 1.0));
    rgb += tint * pow(t, 5.0) * 0.5 * shade;
    rgb *= vS.x * uIntensity;
    if (!(rgb.x < 64.0 && rgb.y < 64.0 && rgb.z < 64.0)) rgb = vec3(0.0);
    oCol = vec4(max(rgb, 0.0), 1.0);
}
