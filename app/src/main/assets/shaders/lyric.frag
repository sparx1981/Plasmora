#version 300 es
precision highp float;
in vec2 vUv;
out vec4 oCol;
uniform sampler2D uTex;
uniform vec4 uRect;      // centre.xy, half size.xy in NDC
uniform float uAlpha;
uniform float uBeat;
uniform float uTime;
uniform int uPass;       // 0 = dark halo behind the text, 1 = text
uniform float uBack;
uniform vec3 pa; uniform vec3 pb; uniform vec3 pc; uniform vec3 pd;

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    vec2 q = (p - uRect.xy) / uRect.zw;
    if (abs(q.x) > 1.0 || abs(q.y) > 1.0) { oCol = vec4(0.0, 0.0, 0.0, 1.0); return; }
    vec2 tuv = vec2(q.x * 0.5 + 0.5, 0.5 - q.y * 0.5);
    if (uPass == 0) {
        float s = 0.0;
        for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++)
            s += texture(uTex, tuv + vec2(float(i) * 0.007, float(j) * 0.03)).a;
        float halo = smoothstep(0.0, 12.0, s) * uBack * 0.4 * uAlpha;
        oCol = vec4(0.0, 0.0, 0.0, clamp(halo, 0.0, 1.0));
        return;
    }
    float a = texture(uTex, tuv).a;
    vec3 c = pa + pb * cos(6.28318 * (pc * (uTime * 0.05 + q.x * 0.35) + pd));
    c = mix(c, vec3(1.0), 0.25);
    vec3 col = c * a * uAlpha * (1.05 + 0.12 * uBeat);
    if (!(col.x < 64.0 && col.y < 64.0 && col.z < 64.0)) col = vec3(0.0);
    oCol = vec4(max(col, 0.0), 1.0);
}
