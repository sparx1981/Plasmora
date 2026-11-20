#version 300 es
precision highp float;
precision highp sampler3D;
in vec2 vUv;
out vec4 oCol;

// Display pass: exposure, gradient map, tone map, saturation, vignette, per-palette LUT grade,
// contrast-adaptive sharpening, dither and grain.
uniform sampler2D uTex;
uniform sampler3D uLut;
uniform vec2  uTexel;
uniform float uLutAmt, uLutN, uSharp;
uniform float uExposure, uSat, uGrad, uVig, uGrain, uTime, uPhase, uAspect;
uniform vec3 pa, pb, pc, pd;

vec3 pal(float x) { return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0); }

float hash(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}
vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

vec3 display(vec2 uv) {
    vec3 col = texture(uTex, uv).rgb;
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    if (uGrad > 0.0) {
        vec3 g = pal(clamp(l, 0.0, 1.5) * 0.55 + uPhase) * (l * 1.5);
        col = mix(col, g + col * 0.25, uGrad);
    }
    col *= uExposure;
    col = aces(col);
    float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(lum), col, uSat);
    vec2 dd = uv - 0.5;
    col *= 1.0 - uVig * dot(dd, dd) * 1.7;
    return pow(max(col, 0.0), vec3(1.0 / 1.35));
}

void main() {
    vec2 uv = vUv;
    vec3 e = display(uv);
    if (uSharp > 0.001) {
        vec3 b = display(uv + vec2(0.0,  uTexel.y));
        vec3 h = display(uv - vec2(0.0,  uTexel.y));
        vec3 d = display(uv - vec2(uTexel.x, 0.0));
        vec3 f = display(uv + vec2(uTexel.x, 0.0));
        vec3 mn = min(min(e, min(b, d)), min(f, h));
        vec3 mx = max(max(e, max(b, d)), max(f, h));
        vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, 1e-4), 0.0, 1.0));
        vec3 w = amp * (-1.0 / mix(8.0, 4.5, clamp(uSharp, 0.0, 1.0)));
        vec3 sh = (b * w + d * w + f * w + h * w + e) / (1.0 + 4.0 * w);
        e = clamp(sh, 0.0, 1.0);
    }
    vec3 col = e;
    if (uLutAmt > 0.001) {
        vec3 q = clamp(col, 0.0, 1.0) * ((uLutN - 1.0) / uLutN) + 0.5 / uLutN;
        col = mix(col, texture(uLut, q).rgb, uLutAmt);
    }
    float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float n1 = hash(gl_FragCoord.xy + fract(uTime) * 91.7);
    float n2 = hash(gl_FragCoord.xy * 1.37 + fract(uTime * 1.3) * 53.1);
    float tri = n1 + n2 - 1.0;
    col += tri * (1.0 / 255.0);
    col += (n1 - 0.5) * uGrain * 0.05 * (0.4 + lum);
    oCol = vec4(clamp(col, 0.0, 1.0), 1.0);
}
