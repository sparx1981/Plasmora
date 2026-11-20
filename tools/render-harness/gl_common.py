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

