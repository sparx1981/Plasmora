#version 300 es
precision highp float;
in vec2 vUv;
out vec4 oCol;
uniform sampler2D uSrc;
uniform vec2 uTexel;       // texel size of the SOURCE
uniform float uThreshold;  // < 0 => no prefilter
uniform float uKnee;

void main() {
    vec2 uv = vUv;
    float x = uTexel.x, y = uTexel.y;
    vec3 a = texture(uSrc, uv + vec2(-2.0 * x,  2.0 * y)).rgb;
    vec3 b = texture(uSrc, uv + vec2( 0.0,      2.0 * y)).rgb;
    vec3 c = texture(uSrc, uv + vec2( 2.0 * x,  2.0 * y)).rgb;
    vec3 d = texture(uSrc, uv + vec2(-2.0 * x,  0.0)).rgb;
    vec3 e = texture(uSrc, uv).rgb;
    vec3 f = texture(uSrc, uv + vec2( 2.0 * x,  0.0)).rgb;
    vec3 g = texture(uSrc, uv + vec2(-2.0 * x, -2.0 * y)).rgb;
    vec3 h = texture(uSrc, uv + vec2( 0.0,     -2.0 * y)).rgb;
    vec3 i = texture(uSrc, uv + vec2( 2.0 * x, -2.0 * y)).rgb;
    vec3 j = texture(uSrc, uv + vec2(-x,  y)).rgb;
    vec3 k = texture(uSrc, uv + vec2( x,  y)).rgb;
    vec3 l = texture(uSrc, uv + vec2(-x, -y)).rgb;
    vec3 m = texture(uSrc, uv + vec2( x, -y)).rgb;
    vec3 col = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
    if (uThreshold >= 0.0) {
        float br = max(col.r, max(col.g, col.b));
        float soft = clamp(br - uThreshold + uKnee, 0.0, 2.0 * uKnee);
        soft = soft * soft / (4.0 * uKnee + 1e-4);
        float contrib = max(soft, br - uThreshold) / max(br, 1e-4);
        col *= contrib;
    }
    if (!(col.x < 1.0e4 && col.y < 1.0e4 && col.z < 1.0e4)) col = vec3(0.0);
    oCol = vec4(max(col, 0.0), 1.0);
}
