#version 300 es
precision highp float;
in vec2 vUv;
out vec4 oCol;

// Cinematic composite: camera shake, motion blur, depth of field, god rays, floor reflection,
// anamorphic streaks, lens flare, halation, bloom and album art. Output stays in linear HDR.
uniform sampler2D uScene;
uniform sampler2D uBloom;       // full bloom chain (level 0)
uniform sampler2D uBloomMid;    // level 1 (quarter res): used for streaks, flare and rays
uniform sampler2D uBloomWide;   // level 2 (eighth res): used for halation
uniform sampler2D uArt;
uniform vec2  uSSOff;           // supersample tap offset in uv (0 = off)
uniform vec2  uJitter;          // TAA sub-pixel jitter in uv
uniform float uBloomAmt, uChroma, uAspect, uTime, uBeat, uPhase;
uniform vec4  uShake, uShakePrev;   // x, y offset (uv), roll (rad), zoom
uniform vec2  uWarpMV;              // preset warp motion per frame: zoom (scale-1), rotation (rad)
uniform float uMB;                  // motion blur amount
uniform float uDof, uDofCamH, uDofPitch, uDofFocus;
uniform vec2  uSun;
uniform float uGod;
uniform float uRefl, uReflY, uReflRip;
uniform float uStreak, uFlare, uHalo;
uniform vec3 pa, pb, pc, pd;
uniform float uArtAmt, uArtLod, uArtAspect;

vec3 cmp(vec3 s) { return s / (1.0 + dot(s, vec3(0.4))); }
vec3 pal(float x) { return clamp(pa + pb * cos(6.2831853 * (pc * x + pd)), 0.0, 3.0); }

vec2 camT(vec2 uv, vec4 sh) {
    vec2 p = uv - 0.5;
    p.x *= uAspect;
    float c = cos(sh.z), s = sin(sh.z);
    p = mat2(c, -s, s, c) * p;
    p /= (1.0 + sh.w);
    p.x /= uAspect;
    return p + 0.5 + sh.xy;
}

float hash(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

vec3 fetchC(vec2 uv, vec2 off) {
    return vec3(texture(uScene, uv + off).r, texture(uScene, uv).g, texture(uScene, uv - off).b);
}

vec3 sceneAt(vec2 uv, vec2 off) {
    if (uSSOff.x > 0.0) {
        vec2 o = uSSOff;
        vec3 a = fetchC(uv + vec2( o.x,  o.y), off);
        vec3 b = fetchC(uv + vec2(-o.x,  o.y), off);
        vec3 c = fetchC(uv + vec2( o.x, -o.y), off);
        vec3 d = fetchC(uv + vec2(-o.x, -o.y), off);
        return 0.25 * (a + b + c + d);
    }
    return fetchC(uv, off);
}

vec2 coverUv(vec2 uv) {
    vec2 p = uv - 0.5;
    if (uAspect > uArtAspect) p.y *= uArtAspect / uAspect;
    else p.x *= uAspect / uArtAspect;
    p += 0.5;
    p.y = 1.0 - p.y;
    return p;
}

void main() {
    vec2 cam = camT(vUv, uShake);
    vec2 uv = cam + uJitter;
    vec2 dT = cam - camT(vUv, uShakePrev);
    vec2 dd = vUv - 0.5;
    vec2 dda = dd * vec2(uAspect, 1.0);
    vec2 perp = vec2(-dda.y, dda.x); perp.x /= uAspect;
    vec2 mv = (dT + dd * uWarpMV.x + perp * uWarpMV.y) * uMB;
    vec2 off = (uv - 0.5) * uChroma * 0.012;

    vec3 col;
    if (uMB > 0.0 && length(mv * vec2(uAspect, 1.0)) > 0.0006) {
        col = vec3(0.0);
        for (int i = 0; i < 8; i++) {
            float s = (float(i) + 0.5) / 8.0 - 0.5;
            col += fetchC(uv + mv * s, off);
        }
        col *= 0.125;
    } else {
        col = sceneAt(uv, off);
    }

    // depth of field: depth of the ground plane is known analytically from the terrain camera
    float cocOut = 0.0;
    if (uDof > 0.001) {
        vec2 pp = dda / min(uAspect, 1.0);
        vec3 rd = normalize(vec3(pp, 1.15));
        float cs = cos(uDofPitch), sn = sin(uDofPitch);
        rd.yz = mat2(cs, sn, -sn, cs) * rd.yz;
        float t = rd.y < -0.002 ? min(uDofCamH / -rd.y, 11.0) : 11.0;
        float ratio = abs(t - uDofFocus) / (t + 0.6);
        float coc = smoothstep(0.10, 0.80, ratio);
        cocOut = coc;
        float rad = coc * uDof * 0.011;
        if (rad > 0.0004) {
            vec3 acc = vec3(0.0); float ws = 0.0;
            float rot = hash(gl_FragCoord.xy) * 6.2831853;
            for (int k = 0; k < 18; k++) {
                float r = sqrt((float(k) + 0.5) / 18.0) * rad;
                float a = float(k) * 2.39996323 + rot;
                vec2 o = vec2(cos(a), sin(a)) * r;
                o.x /= uAspect;
                vec3 s = texture(uScene, uv + o).rgb;
                float w = 1.0 + dot(s, vec3(0.45));          // bright points make bokeh discs
                acc += s * w; ws += w;
            }
            col = mix(col, acc / ws, clamp(coc * 2.2, 0.0, 1.0));
        }
    }

    // floor reflection (mirror about a horizontal line in scene space)
    if (uRefl > 0.001) {
        float dy = uReflY - uv.y;
        if (dy > 0.0) {
            float fade = exp(-dy * 8.0) * smoothstep(0.0, 0.012, dy) * 0.6;
            float rip = sin(dy * 90.0 + uTime * 2.2) * uReflRip * (0.3 + dy * 4.0);
            vec3 r = texture(uScene, vec2(uv.x + rip, 2.0 * uReflY - uv.y)).rgb;
            col += r * fade * uRefl * vec3(0.85, 0.95, 1.0);
        }
        col += pal(uPhase + 0.3) * exp(-abs(uv.y - uReflY) * 300.0) * 0.18 * uRefl;
    }

    col += texture(uBloom, uv).rgb * uBloomAmt;

    // god rays: radial march towards the sun through the bright parts of the scene
    if (uGod > 0.001) {
        vec2 d = uSun - uv;
        const int N = 28;
        vec2 stp = d * (0.97 / float(N));
        vec2 u = uv + stp * hash(gl_FragCoord.xy + fract(uTime) * 17.0);
        vec3 g = vec3(0.0); float w = 1.0;
        for (int i = 0; i < N; i++) {
            vec3 s = min(texture(uBloomMid, u).rgb, vec3(1.4));
            float l = dot(s, vec3(0.333));
            g += s * smoothstep(0.45, 1.3, l) * w;
            w *= 0.962; u += stp;
        }
        float dist = length(d * vec2(uAspect, 1.0));
        col += g * (1.0 / float(N)) * uGod * (0.7 + 1.2 * uBeat) * smoothstep(1.4, 0.05, dist) * 0.2;
    }

    // halation: warm wide glow around the brightest areas
    if (uHalo > 0.001) {
        vec3 h = cmp(texture(uBloomWide, uv).rgb);
        col += h * vec3(1.0, 0.38, 0.12) * uHalo * 0.8;
    }

    // anamorphic streaks: long horizontal blue-tinted smear of bright highlights
    if (uStreak > 0.001) {
        vec3 st = vec3(0.0); float ws = 0.0;
        for (int i = -16; i <= 16; i++) {
            float x = float(i) / 16.0;
            float w = exp(-abs(x) * 2.6);
            vec3 s = max(cmp(texture(uBloomWide, vec2(uv.x + x * 0.6, uv.y)).rgb) - 0.12, 0.0);
            st += s * w; ws += w;
        }
        vec3 tint = mix(vec3(0.38, 0.62, 1.0), pal(uPhase + 0.1), 0.3);
        col += st / ws * tint * uStreak * (0.45 + 1.6 * uBeat) * 2.6;
    }

    // lens flare: ghosts and halo mirrored through the optical centre
    if (uFlare > 0.001) {
        vec2 c = vec2(0.5);
        vec2 fu = vec2(1.0) - uv;
        vec2 gv = (c - fu) * 0.36;
        vec3 f = vec3(0.0);
        for (int i = 1; i <= 3; i++) {
            vec2 s = clamp(fu + gv * float(i), 0.0, 1.0);
            float wgt = pow(clamp(1.0 - length(c - s) / 0.7071, 0.0, 1.0), 3.0);
            vec3 t;
            t.r = texture(uBloomWide, s + gv * 0.04).r;
            t.g = texture(uBloomWide, s).g;
            t.b = texture(uBloomWide, s - gv * 0.04).b;
            f += max(cmp(t) - 0.4, 0.0) * wgt;
        }
        vec2 hv = normalize(c - fu + vec2(1e-4)) * 0.42;
        vec2 hs = fu + hv;
        float hw = pow(clamp(1.0 - length(c - hs) / 0.7071, 0.0, 1.0), 3.0);
        f += max(cmp(texture(uBloomWide, clamp(hs, 0.0, 1.0)).rgb) - 0.4, 0.0) * hw * 1.0;
        col += f * uFlare * (0.05 + 0.7 * uBeat * uBeat) * vec3(0.85, 0.95, 1.0);
    }

    if (uArtAmt > 0.001) {
        vec3 art = textureLod(uArt, coverUv(vUv), uArtLod).rgb;
        col += pow(art, vec3(2.2)) * uArtAmt * 0.8;
    }
    col = max(col, vec3(0.0));
    if (any(isnan(col)) || any(isinf(col))) col = vec3(0.0);
    oCol = vec4(col, 1.0);
}
