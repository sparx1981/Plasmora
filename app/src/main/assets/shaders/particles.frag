#version 300 es
precision highp float;
in vec3 vCol;
in float vA;
out vec4 oCol;
uniform float uIntensity;
void main() {
    vec2 d = gl_PointCoord - 0.5;
    float r2 = dot(d, d) * 4.0;
    if (r2 > 1.0) discard;
    float soft = exp(-r2 * 3.2);
    oCol = vec4((vCol * soft + vec3(0.5) * soft * soft) * vA * uIntensity, 1.0);
}
