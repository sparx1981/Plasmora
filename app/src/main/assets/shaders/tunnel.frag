#version 300 es
precision highp float;
precision highp int;
in vec2 vUv;
out vec4 oCol;

uniform sampler2D uAudio;     // row 2 = spectrum
uniform float uAspect;
uniform float uSides;         // 0 = round tunnel, >=3 = polygon
uniform float uRibs;          // rings per unit of depth
uniform float uTravel;        // camera position along the tunnel
uniform float uTwist;         // spiral twist per unit depth
uniform float uBeat, uBass;
uniform float uIntensity, uPhase, uSpread;
uniform vec3 pa, pb, pc, pd;

const float TAU = 6.2831853;
const float PI = 3.1415927;

vec3 pal(float x) {
    return clamp(pa + pb * cos(TAU * (pc * x + pd)), 0.0, 3.0);
}

float spec(float t) {
    float x = clamp(t, 0.0, 1.0) * 1023.0;
    int i = int(x);
    int j = min(i + 1, 1023);
    return mix(texelFetch(uAudio, ivec2(i, 2), 0).r, texelFetch(uAudio, ivec2(j, 2), 0).r, x - float(i));
}

void main() {
    vec2 p = (vUv - 0.5) * vec2(uAspect, 1.0);
    float r = length(p);
    float a = atan(p.y, p.x);

    // distance to the wall: circular or polygonal
    float rr = r;
    float sides = uSides;
    if (sides > 2.5) {
        float seg = TAU / sides;
        float aa = mod(a + PI, seg) - seg * 0.5;
        rr = r * cos(aa) / cos(seg * 0.5);
    }
    float z = 0.32 / (rr + 0.015);         // depth of this pixel's wall point
    float zt = z + uTravel;
    float ang = a / TAU + uTwist * z * 0.1;

    // glowing rings across the tunnel
    float fr = fract(zt * uRibs);
    float dr = min(fr, 1.0 - fr);
    float rib = exp(-dr * dr * 160.0 / (0.6 + z * 0.12));

    // glowing lines along the tunnel
    float lines = max(sides, 12.0);
    float fl = fract(ang * lines);
    float dl = min(fl, 1.0 - fl);
    float lin = exp(-dl * dl * 380.0 / (0.6 + z * 0.1));

    // each angular slice listens to a different frequency
    float s = spec(abs(2.0 * fract(ang) - 1.0) * 0.8);
    float react = 0.25 + 1.6 * s;

    float depthFade = smoothstep(0.0, 0.28, rr);     // dark vanishing point
    float bright = (rib * 1.1 + lin * 0.45) * react * depthFade * (1.0 + 0.9 * uBeat);

    vec3 col = pal(uPhase + z * 0.07 * uSpread + abs(2.0 * fract(ang) - 1.0) * 0.25);
    vec3 rgb = col * bright;
    // a hot light at the end of the tunnel
    rgb += (col * 0.5 + vec3(0.5)) * exp(-rr * rr * 40.0) * (0.25 + 0.5 * uBass);
    oCol = vec4(rgb * uIntensity, 1.0);
}
