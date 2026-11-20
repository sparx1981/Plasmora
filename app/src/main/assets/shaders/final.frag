#version 300 es
precision highp float;
in vec2 vUv;
out vec4 oCol;

uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform vec2  uSSOff;     // supersample tap offset in uv (0 = off)
uniform float uBloomAmt;
uniform float uExposure, uSat, uGrad, uVig, uChroma, uGrain, uTime, uPhase;
uniform vec3 pa, pb, pc, pd;
uniform sampler2D uArt;
uniform float uArtAmt, uArtLod, uArtAspect, uAspect;

vec3 pal(float x) {
    return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0);
}

vec3 sceneAt(vec2 uv) {
    if (uSSOff.x > 0.0) {
        vec2 o = uSSOff;
        return 0.25 * (texture(uScene, uv + vec2( o.x,  o.y)).rgb
                     + texture(uScene, uv + vec2(-o.x,  o.y)).rgb
                     + texture(uScene, uv + vec2( o.x, -o.y)).rgb
                     + texture(uScene, uv + vec2(-o.x, -o.y)).rgb);
    }
    return texture(uScene, uv).rgb;
}

vec2 coverUv(vec2 uv) {
    vec2 p = uv - 0.5;
    if (uAspect > uArtAspect) p.y *= uArtAspect / uAspect;
    else p.x *= uAspect / uArtAspect;
    p += 0.5;
    p.y = 1.0 - p.y;
    return p;
}

float hash(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec2 uv = vUv;
    vec2 dd = uv - 0.5;
    vec2 off = dd * uChroma * 0.012;
    vec3 col;
    col.r = sceneAt(uv + off).r;
    col.g = sceneAt(uv).g;
    col.b = sceneAt(uv - off).b;
    col += texture(uBloom, uv).rgb * uBloomAmt;
    if (uArtAmt > 0.001) {
        vec3 art = textureLod(uArt, coverUv(uv), uArtLod).rgb;
        col += pow(art, vec3(2.2)) * uArtAmt * 0.8;
    }

    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    if (uGrad > 0.0) {
        vec3 g = pal(clamp(l, 0.0, 1.5) * 0.55 + uPhase) * (l * 1.5);
        col = mix(col, g + col * 0.25, uGrad);
    }

    col *= uExposure;
    col = aces(col);
    float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(lum), col, uSat);
    col *= 1.0 - uVig * dot(dd, dd) * 1.7;
    col = pow(max(col, 0.0), vec3(1.0 / 1.35));

    float n1 = hash(gl_FragCoord.xy + fract(uTime) * 91.7);
    float n2 = hash(gl_FragCoord.xy * 1.37 + fract(uTime * 1.3) * 53.1);
    float tri = n1 + n2 - 1.0;
    col += tri * (1.0 / 255.0);
    col += (n1 - 0.5) * uGrain * 0.05 * (0.4 + lum);
    oCol = vec4(clamp(col, 0.0, 1.0), 1.0);
}
