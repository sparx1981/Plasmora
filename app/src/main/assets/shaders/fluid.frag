#version 300 es
precision highp float;
precision highp int;
in vec2 vUv;
out vec4 oCol;

// Stable-fluids simulation (Stam) with vorticity confinement. One shader, one pass per uPass.
uniform int uPass;            // 0 advect 1 divergence 2 pressure 3 gradient 4 curl 5 vorticity 6 splat 7 display
uniform sampler2D uSrc;       // field being updated / displayed
uniform sampler2D uVel;       // velocity field
uniform sampler2D uAux;       // divergence / curl / pressure helper
uniform vec2  uTexel;         // texel size of the target being written
uniform vec2  uVelTexel;      // texel size of the velocity grid
uniform float uDt, uDiss, uVort, uAspect, uIntensity, uShade;
uniform int   uCount, uSplatMode;
uniform vec4  uSplat[8];      // x y radius _
uniform vec4  uSplatVel[8];   // vx vy _ _
uniform vec4  uSplatCol[8];   // rgb amount
uniform vec3 pa, pb, pc, pd;
uniform float uPhase, uSpread;

const float TAU = 6.2831853;
vec3 pal(float x) { return clamp(pa + pb * cos(TAU * (pc * x + pd)), 0.0, 3.0); }

void main() {
    vec2 uv = vUv;
    vec2 t = uTexel;
    if (uPass == 0) {
        vec2 v = texture(uVel, uv).xy;
        vec2 coord = uv - uDt * v * uVelTexel;
        oCol = texture(uSrc, coord) * uDiss;
    } else if (uPass == 1) {
        float L = texture(uVel, uv - vec2(t.x, 0.0)).x;
        float R = texture(uVel, uv + vec2(t.x, 0.0)).x;
        float B = texture(uVel, uv - vec2(0.0, t.y)).y;
        float T = texture(uVel, uv + vec2(0.0, t.y)).y;
        vec2 C = texture(uVel, uv).xy;
        if (uv.x - t.x < 0.0) L = -C.x;
        if (uv.x + t.x > 1.0) R = -C.x;
        if (uv.y - t.y < 0.0) B = -C.y;
        if (uv.y + t.y > 1.0) T = -C.y;
        oCol = vec4(0.5 * (R - L + T - B), 0.0, 0.0, 1.0);
    } else if (uPass == 2) {
        float L = texture(uSrc, uv - vec2(t.x, 0.0)).x;
        float R = texture(uSrc, uv + vec2(t.x, 0.0)).x;
        float B = texture(uSrc, uv - vec2(0.0, t.y)).x;
        float T = texture(uSrc, uv + vec2(0.0, t.y)).x;
        float d = texture(uAux, uv).x;
        oCol = vec4((L + R + B + T - d) * 0.25, 0.0, 0.0, 1.0);
    } else if (uPass == 3) {
        float L = texture(uAux, uv - vec2(t.x, 0.0)).x;
        float R = texture(uAux, uv + vec2(t.x, 0.0)).x;
        float B = texture(uAux, uv - vec2(0.0, t.y)).x;
        float T = texture(uAux, uv + vec2(0.0, t.y)).x;
        vec2 v = texture(uVel, uv).xy - 0.5 * vec2(R - L, T - B);
        oCol = vec4(v, 0.0, 1.0);
    } else if (uPass == 4) {
        float L = texture(uVel, uv - vec2(t.x, 0.0)).y;
        float R = texture(uVel, uv + vec2(t.x, 0.0)).y;
        float B = texture(uVel, uv - vec2(0.0, t.y)).x;
        float T = texture(uVel, uv + vec2(0.0, t.y)).x;
        oCol = vec4(0.5 * (R - L - T + B), 0.0, 0.0, 1.0);
    } else if (uPass == 5) {
        float L = texture(uAux, uv - vec2(t.x, 0.0)).x;
        float R = texture(uAux, uv + vec2(t.x, 0.0)).x;
        float B = texture(uAux, uv - vec2(0.0, t.y)).x;
        float T = texture(uAux, uv + vec2(0.0, t.y)).x;
        float C = texture(uAux, uv).x;
        vec2 f = 0.5 * vec2(abs(T) - abs(B), abs(L) - abs(R));
        f /= length(f) + 1e-4;
        f *= uVort * C;
        vec2 v = texture(uSrc, uv).xy + f * uDt;
        oCol = vec4(clamp(v, -400.0, 400.0), 0.0, 1.0);
    } else if (uPass == 6) {
        vec4 base = texture(uSrc, uv);
        for (int i = 0; i < 8; i++) {
            if (i >= uCount) break;
            vec2 d = (uv - uSplat[i].xy) * vec2(uAspect, 1.0);
            float g = exp(-dot(d, d) / max(uSplat[i].z, 1e-5));
            if (uSplatMode == 0) base.xy += uSplatVel[i].xy * g;
            else base.rgb += uSplatCol[i].rgb * (uSplatCol[i].a * g);
        }
        oCol = vec4(base.rgb, 1.0);
        if (uSplatMode == 0) oCol.xy = clamp(base.xy, -500.0, 500.0);
    } else {
        // display: dye with a lit, glassy relief taken from its own gradient
        vec3 c = texture(uSrc, uv).rgb;
        float lc = dot(c, vec3(0.3, 0.5, 0.2));
        float lL = dot(texture(uSrc, uv - vec2(t.x, 0.0)).rgb, vec3(0.3, 0.5, 0.2));
        float lR = dot(texture(uSrc, uv + vec2(t.x, 0.0)).rgb, vec3(0.3, 0.5, 0.2));
        float lB = dot(texture(uSrc, uv - vec2(0.0, t.y)).rgb, vec3(0.3, 0.5, 0.2));
        float lT = dot(texture(uSrc, uv + vec2(0.0, t.y)).rgb, vec3(0.3, 0.5, 0.2));
        vec3 n = normalize(vec3((lL - lR) * uShade, (lB - lT) * uShade, 1.0));
        vec3 L = normalize(vec3(-0.45, 0.6, 0.65));
        float diff = max(dot(n, L), 0.0);
        float spec = pow(max(dot(reflect(-L, n), vec3(0.0, 0.0, 1.0)), 0.0), 36.0);
        float rim = pow(1.0 - n.z, 1.5);
        vec3 tint = pal(uPhase + lc * uSpread);
        vec3 col = c * (0.65 + 0.55 * diff) + tint * rim * 0.9 * min(lc * 3.0, 1.0) + vec3(1.0, 0.95, 0.9) * spec * min(lc * 4.0, 1.0);
        oCol = vec4(col * uIntensity, 1.0);
    }
}
