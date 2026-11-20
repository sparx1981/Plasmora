import ctypes as C, sys, math, os, numpy as np
from PIL import Image

SH = "/home/claude/plasmora/app/src/main/assets/shaders/"
egl = C.CDLL("libEGL.so.1"); gl = C.CDLL("libGLESv2.so.2")
egl.eglGetProcAddress.restype = C.c_void_p
# --- EGL surfaceless / pbuffer ES3 context
egl.eglGetPlatformDisplay.restype = C.c_void_p
egl.eglGetPlatformDisplay.argtypes = [C.c_uint, C.c_void_p, C.c_void_p]
dpy = egl.eglGetPlatformDisplay(0x31DD, None, None)  # surfaceless mesa
if not dpy:
    egl.eglGetDisplay.restype = C.c_void_p; dpy = egl.eglGetDisplay(None)
maj, mnr = C.c_int(), C.c_int()
egl.eglInitialize.argtypes = [C.c_void_p, C.POINTER(C.c_int), C.POINTER(C.c_int)]
assert egl.eglInitialize(C.c_void_p(dpy), C.byref(maj), C.byref(mnr)), "eglInitialize"
egl.eglBindAPI(0x30A0)
attr = (C.c_int * 13)(0x3033, 0x1, 0x3040, 0x40, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3038, 0x3038, 0)
cfg = C.c_void_p(); n = C.c_int()
egl.eglChooseConfig.argtypes = [C.c_void_p, C.c_void_p, C.POINTER(C.c_void_p), C.c_int, C.POINTER(C.c_int)]
assert egl.eglChooseConfig(C.c_void_p(dpy), attr, C.byref(cfg), 1, C.byref(n)) and n.value > 0, "chooseConfig"
egl.eglCreateContext.restype = C.c_void_p
egl.eglCreateContext.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p, C.c_void_p]
ctx = egl.eglCreateContext(C.c_void_p(dpy), cfg, None, (C.c_int * 3)(0x3098, 3, 0x3038))
assert ctx, "ctx"
W, H = int(os.environ.get("W", 540)), int(os.environ.get("H", 960))
egl.eglCreatePbufferSurface.restype = C.c_void_p
egl.eglCreatePbufferSurface.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p]
surf = egl.eglCreatePbufferSurface(C.c_void_p(dpy), cfg, (C.c_int * 5)(0x3057, 64, 0x3056, 64, 0x3038))
egl.eglMakeCurrent.argtypes = [C.c_void_p] * 4
assert egl.eglMakeCurrent(C.c_void_p(dpy), C.c_void_p(surf), C.c_void_p(surf), C.c_void_p(ctx)), "makeCurrent"

gl.glGetString.restype = C.c_char_p
print("GL:", gl.glGetString(0x1F02), gl.glGetString(0x1F01), gl.glGetString(0x1F00))
exts = (gl.glGetString(0x1F03) or b"").decode()
print("float rt:", "GL_EXT_color_buffer_float" in exts)

GL_VERTEX_SHADER, GL_FRAGMENT_SHADER = 0x8B31, 0x8B30
def compile_prog(vs, fs):
    def sh(t, src, name):
        s = gl.glCreateShader(t)
        b = C.c_char_p(src.encode()); gl.glShaderSource(s, 1, C.byref(b), None); gl.glCompileShader(s)
        ok = C.c_int(); gl.glGetShaderiv(s, 0x8B81, C.byref(ok))
        if not ok.value:
            buf = C.create_string_buffer(4096); gl.glGetShaderInfoLog(s, 4096, None, buf)
            print("SHADER FAIL", name, buf.value.decode()); sys.exit(1)
        return s
    p = gl.glCreateProgram()
    gl.glAttachShader(p, sh(GL_VERTEX_SHADER, open(SH + vs).read(), vs))
    gl.glAttachShader(p, sh(GL_FRAGMENT_SHADER, open(SH + fs).read(), fs))
    gl.glLinkProgram(p)
    ok = C.c_int(); gl.glGetProgramiv(p, 0x8B82, C.byref(ok))
    if not ok.value:
        buf = C.create_string_buffer(4096); gl.glGetProgramInfoLog(p, 4096, None, buf); print("LINK FAIL", vs, fs, buf.value.decode()); sys.exit(1)
    return p
class Prog:
    def __init__(s, vs, fs): s.id = compile_prog(vs, fs); s.l = {}
    def loc(s, n):
        if n not in s.l: s.l[n] = gl.glGetUniformLocation(s.id, n.encode())
        return s.l[n]
    def u1f(s, n, v): gl.glUniform1f(s.loc(n), C.c_float(v))
    def u2f(s, n, a, b): gl.glUniform2f(s.loc(n), C.c_float(a), C.c_float(b))
    def u3f(s, n, a, b, c): gl.glUniform3f(s.loc(n), C.c_float(a), C.c_float(b), C.c_float(c))
    def u1i(s, n, v): gl.glUniform1i(s.loc(n), v)

warpP = Prog("fullscreen.vert", "warp.frag"); shapeP = Prog("shape.vert", "shape.frag")
downP = Prog("fullscreen.vert", "down.frag"); upP = Prog("fullscreen.vert", "up.frag"); finalP = Prog("fullscreen.vert", "final.frag")
print("all shaders compiled OK")

vao = C.c_uint(); gl.glGenVertexArrays(1, C.byref(vao)); gl.glBindVertexArray(vao)
def make_target(w, h, wrap):
    t = C.c_uint(); gl.glGenTextures(1, C.byref(t)); gl.glBindTexture(0xDE1, t)
    gl.glTexImage2D(0xDE1, 0, 0x881A, w, h, 0, 0x1908, 0x140B, None)  # RGBA16F
    for p, v in ((0x2801, 0x2601), (0x2800, 0x2601), (0x2802, wrap), (0x2803, wrap)): gl.glTexParameteri(0xDE1, p, v)
    f = C.c_uint(); gl.glGenFramebuffers(1, C.byref(f)); gl.glBindFramebuffer(0x8D40, f)
    gl.glFramebufferTexture2D(0x8D40, 0x8CE0, 0xDE1, t, 0)
    st = gl.glCheckFramebufferStatus(0x8D40); assert st == 0x8CD5, hex(st)
    gl.glViewport(0, 0, w, h); gl.glClearColor(C.c_float(0), C.c_float(0), C.c_float(0), C.c_float(1)); gl.glClear(0x4000)
    return (t.value, f.value, w, h)
SCALE = float(os.environ.get("SCALE", 1.0))
sw, sh_ = int(W * SCALE), int(H * SCALE)
sim = [make_target(sw, sh_, 0x8370), make_target(sw, sh_, 0x8370)]
bloom = []; bw, bh = sw, sh_
for i in range(6):
    bw //= 2; bh //= 2
    if bw < 4 or bh < 4: break
    bloom.append(make_target(bw, bh, 0x812F))
audio = C.c_uint(); gl.glGenTextures(1, C.byref(audio)); gl.glActiveTexture(0x84C1); gl.glBindTexture(0xDE1, audio)
gl.glTexImage2D(0xDE1, 0, 0x822D, 1024, 4, 0, 0x1903, 0x1406, None)
for p, v in ((0x2801, 0x2600), (0x2800, 0x2600), (0x2802, 0x812F), (0x2803, 0x812F)): gl.glTexParameteri(0xDE1, p, v)
black = C.c_uint(); gl.glGenTextures(1, C.byref(black)); gl.glBindTexture(0xDE1, black)
gl.glTexImage2D(0xDE1, 0, 0x8058, 1, 1, 0, 0x1908, 0x1401, (C.c_ubyte * 4)(0, 0, 0, 0))

# ---- presets (subset ported from Presets.kt)
def pp(**k):
    d = dict(zoom=1, rot=0, swirl=0, swirlR=3, tx=0, ty=0, ripA=0, ripF=10, ripP=0, ripR=1, flowA=0, flowS=2, flowT=0.01,
             kal=0, twist=0, decay=0.96, tr=1, tg=1, tb=1, hue=0, soft=0)
    d.update(k); return d
PRESETS = {
 "nebula": (lambda a, mt: pp(zoom=1.004 + 0.004 * a, rot=0.0015 * math.sin(mt * .3) + .001, swirl=.012, swirlR=2.5, flowA=.0025, flowS=2.2, flowT=.01, decay=.972, hue=.006, soft=.25),
   [dict(mode=0, amp=.34, scale=.92, width=.008, inten=.11, phase=0.05, spread=.5), dict(mode=1, amp=.22, scale=.26, width=.006, inten=.13, phase=.5, spread=.6)]),
 "vortex": (lambda a, mt: pp(zoom=1.012 + .006 * a, rot=.01, swirl=.05 + .03 * a, swirlR=2, decay=.955, hue=.01, soft=.15),
   [dict(mode=2, p1=3.5, scale=.55, amp=.25, width=.01, inten=.13, phase=.08, spread=.7)]),
 "kaleido": (lambda a, mt: pp(kal=6, zoom=1.008, rot=.003, ripA=.002, ripF=14, ripP=.04, decay=.965, hue=.012, soft=.2),
   [dict(mode=8, p1=5, p3='mt*.3', scale=.5, amp=.15, width=.01, inten=.12, phase=.05, spread=.8), dict(mode=4, scale=.2, amp=.4, width=.007, inten=.12, spread=1.0, phase=.3)]),
 "lissa": (lambda a, mt: pp(zoom=1.003, rot=.004, flowA=.004, flowS=3, flowT=.02, decay=.935, hue=.02, soft=.1),
   [dict(mode=3, p1=.035, scale=1, amp=1.8, width=.006, inten=.11, phase=.06, spread=.9)]),
 "tunnel": (lambda a, mt: pp(zoom=1.035 + .02 * a, twist=.012, rot=.004, decay=.935, hue=.015, soft=.1),
   [dict(mode=4, scale=.12, amp=.5, width=.009, inten=.2, phase=.04, spread=2.0)]),
 "silk": (lambda a, mt: pp(zoom=1.0015, flowA=.008, flowS=1.8, flowT=.02, decay=.988, soft=-.1, hue=.004),
   [dict(mode=0, amp=.3, scale=.95, cy=.28, width=.007, inten=.1, spread=.6), dict(mode=0, amp=-.3, scale=.95, cy=-.28, width=.007, inten=.1, phase=.4, spread=.6)]),
 "harmono": (lambda a, mt: pp(zoom=1.002, rot=.002, flowA=.0015, flowS=2.5, flowT=.008, decay=.975, hue=.006, soft=.3),
   [dict(mode=7, p1=3, p2=2, p3='mt*.3', scale=.65, amp=.4, width=.007, inten=.13, phase=.05, spread=.8), dict(mode=7, p1=5, p2=4, p3='-mt*.4', scale=.4, amp=.3, width=.006, inten=.1, phase=.5, spread=1.0)]),
}
PAL = [0.5,0.5,0.5, 0.5,0.5,0.5, 1,1,1, 0.0,0.33,0.67]
if os.environ.get("PAL") == "inferno": PAL = [0.5,0.35,0.3, 0.5,0.4,0.3, 1,0.7,0.4, 0,0.15,0.2]

def audio_frame(t, beat):
    x = np.arange(1024) / 1024.0
    f0 = 3.0
    L = 0.35 * np.sin(2 * np.pi * (x * 3 + t * 2)) + 0.2 * np.sin(2 * np.pi * (x * 7 + t * 5)) * (0.6 + 0.4 * beat) + 0.12 * np.sin(2 * np.pi * (x * 19 - t * 3))
    R = 0.35 * np.sin(2 * np.pi * (x * 3 + t * 2 + 0.1)) + 0.2 * np.sin(2 * np.pi * (x * 5 + t * 3)) + 0.1 * np.sin(2 * np.pi * (x * 23 + t))
    sp = np.clip(0.75 - 0.55 * x + 0.12 * np.sin(2 * np.pi * (x * 14 + t)) + 0.25 * beat * np.exp(-x * 6), 0, 1.2)
    return np.stack([L, R, sp, sp]).astype(np.float32)

def run(name, frames, out):
    global mt
    pf, layers = PRESETS[name]
    cur = 0; mt = 0.0; flow = 0.0; rip = 0.0
    dt = 1 / 60.0
    for f in range(frames):
        t = f * dt; mt += dt * (0.55 + 0.45 * 1.0)
        beat = max(0.0, 1.0 - ((t % 0.5) * 4.2))  # 120bpm pulse
        a = 1.0 + 0.8 * beat
        au = audio_frame(t, beat)
        gl.glActiveTexture(0x84C1); gl.glBindTexture(0xDE1, audio)
        gl.glTexSubImage2D(0xDE1, 0, 0, 0, 1024, 4, 0x1903, 0x1406, au.ctypes.data_as(C.c_void_p))
        P = pf(a, mt)
        src, dst = sim[cur], sim[1 - cur]
        gl.glBindFramebuffer(0x8D40, dst[1]); gl.glViewport(0, 0, dst[2], dst[3]); gl.glDisable(0xBE2)
        gl.glUseProgram(warpP.id); gl.glActiveTexture(0x84C0); gl.glBindTexture(0xDE1, src[0])
        asp = W / H; dt60 = 1.0
        flow += P['flowT']; rip += P['ripP']
        w = warpP; w.u1i("uPrev", 0); w.u2f("uTexel", 1 / dst[2], 1 / dst[3]); w.u1f("uAspect", asp)
        w.u1f("uZoom", P['zoom']); w.u1f("uRot", P['rot']); w.u1f("uSwirl", P['swirl']); w.u1f("uSwirlR", P['swirlR'])
        w.u2f("uTrans", P['tx'], P['ty']); w.u1f("uRipA", P['ripA']); w.u1f("uRipF", P['ripF']); w.u1f("uRipP", rip); w.u1f("uRipR", P['ripR'])
        w.u1f("uFlowA", P['flowA']); w.u1f("uFlowS", P['flowS']); w.u1f("uFlowT", flow); w.u1f("uKal", P['kal']); w.u1f("uTwist", P['twist'])
        w.u1f("uDecay", P['decay']); w.u3f("uTint", P['tr'], P['tg'], P['tb']); w.u1f("uHue", P['hue']); w.u1f("uSoft", P['soft'])
        gl.glDrawArrays(4, 0, 3)
        gl.glEnable(0xBE2); gl.glBlendFunc(1, 1)
        s = shapeP; gl.glUseProgram(s.id); s.u1i("uAudio", 1); s.u1f("uAspect", asp); s.u1f("uTime", t); s.u1i("uN", 1024)
        for nm, v in (("pa", PAL[0:3]), ("pb", PAL[3:6]), ("pc", PAL[6:9])): s.u3f(nm, *v)
        shift = t * 0.4 * 0.02
        s.u3f("pd", PAL[9] + shift, PAL[10] + shift, PAL[11] + shift)
        for L in layers:
            d = dict(mode=0, amp=.3, scale=.8, width=.01, inten=.1, rot=0, cx=0, cy=0, phase=0, spread=.3, core=1, p1=0, p2=0, p3=0); d.update(L); d = {k: (eval(v, {'mt': mt}) if isinstance(v, str) else v) for k, v in d.items()}
            s.u1i("uMode", d['mode']); s.u1f("uAmp", d['amp'] * (1 + 0.3 * beat)); s.u1f("uScale", d['scale']); s.u1f("uWidth", d['width'] + 0.006 * beat)
            s.u1f("uRot", d['rot'] + mt * 0.2); s.u1f("uP1", d['p1']); s.u1f("uP2", d['p2']); s.u1f("uP3", d['p3']); s.u2f("uCenter", d['cx'], d['cy'])
            s.u1f("uIntensity", d['inten']); s.u1f("uPhase", d['phase'] + mt * 0.05); s.u1f("uSpread", d['spread']); s.u1f("uCore", d['core'])
            gl.glDrawArrays(5, 0, 2 * 1024)
        gl.glDisable(0xBE2)
        cur = 1 - cur
    res = sim[cur]
    # bloom
    gl.glActiveTexture(0x84C0); gl.glUseProgram(downP.id); downP.u1i("uSrc", 0)
    st, sw_, sh2 = res[0], res[2], res[3]
    for i, b in enumerate(bloom):
        gl.glBindFramebuffer(0x8D40, b[1]); gl.glViewport(0, 0, b[2], b[3]); gl.glBindTexture(0xDE1, st)
        downP.u2f("uTexel", 1 / sw_, 1 / sh2); downP.u1f("uThreshold", 0.45 if i == 0 else -1.0); downP.u1f("uKnee", 0.35)
        gl.glDrawArrays(4, 0, 3); st, sw_, sh2 = b[0], b[2], b[3]
    gl.glEnable(0xBE2); gl.glBlendFunc(1, 1); gl.glUseProgram(upP.id); upP.u1i("uSrc", 0)
    for i in range(len(bloom) - 2, -1, -1):
        sm, bg = bloom[i + 1], bloom[i]
        gl.glBindFramebuffer(0x8D40, bg[1]); gl.glViewport(0, 0, bg[2], bg[3]); gl.glBindTexture(0xDE1, sm[0])
        upP.u2f("uTexel", 1 / sm[2], 1 / sm[3]); upP.u1f("uWeight", 0.85); gl.glDrawArrays(4, 0, 3)
    gl.glDisable(0xBE2)
    # final into an RGBA8 FBO sized W,H
    outT = C.c_uint(); gl.glGenTextures(1, C.byref(outT)); gl.glBindTexture(0xDE1, outT)
    gl.glTexImage2D(0xDE1, 0, 0x8058, W, H, 0, 0x1908, 0x1401, None)
    gl.glTexParameteri(0xDE1, 0x2801, 0x2601); gl.glTexParameteri(0xDE1, 0x2800, 0x2601)
    outF = C.c_uint(); gl.glGenFramebuffers(1, C.byref(outF)); gl.glBindFramebuffer(0x8D40, outF)
    gl.glFramebufferTexture2D(0x8D40, 0x8CE0, 0xDE1, outT, 0)
    gl.glViewport(0, 0, W, H); gl.glUseProgram(finalP.id)
    gl.glActiveTexture(0x84C0); gl.glBindTexture(0xDE1, res[0]); gl.glActiveTexture(0x84C2); gl.glBindTexture(0xDE1, bloom[0][0])
    fp = finalP; fp.u1i("uScene", 0); fp.u1i("uBloom", 2); fp.u2f("uSSOff", 0, 0)
    fp.u1f("uBloomAmt", float(os.environ.get("BLOOM", 0.72))); fp.u1f("uExposure", float(os.environ.get("EXPO", 1.0))); fp.u1f("uSat", 1.1); fp.u1f("uGrad", 0.2)
    fp.u1f("uVig", 0.35); fp.u1f("uChroma", 0.35); fp.u1f("uGrain", 0.12); fp.u1f("uTime", frames / 60.0); fp.u1f("uPhase", 0)
    for nm, v in (("pa", PAL[0:3]), ("pb", PAL[3:6]), ("pc", PAL[6:9]), ("pd", PAL[9:12])): fp.u3f(nm, *v)
    gl.glDrawArrays(4, 0, 3)
    buf = (C.c_ubyte * (W * H * 4))(); gl.glReadPixels(0, 0, W, H, 0x1908, 0x1401, buf)
    img = np.frombuffer(buf, dtype=np.uint8).reshape(H, W, 4)[::-1, :, :3]
    Image.fromarray(img).save(out)
    print(name, "mean", img.mean().round(1), "max", img.max(), "->", out)

for nm in sys.argv[1:]:
    run(nm, int(os.environ.get("FRAMES", 240)), f"/home/claude/harness/{nm}.png")
