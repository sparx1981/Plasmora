PLASMORA - music visualizer for Android (inspired by Geiss)
===========================================================

WHAT IT DOES
- Real-time, audio-reactive plasma / waveform visuals rendered on the GPU
  (OpenGL ES 3, 16-bit HDR feedback buffers, glow, dithering).
- Listens to YouTube Music (or any app) using Android's audio capture,
  falls back to the microphone, plays local music files itself,
  and has a demo mode with built-in test music.
- 20 effects (including spectrum bars, mirror bars, valley bars, filled spectrum waves
  and a ridge-line "valley ridges" analyser), 12 palettes, auto-changing effects with smooth transitions.

HOW TO USE
1. Install the APK (allow "install unknown apps" for your file manager/browser).
2. Start music in YouTube Music.
3. Open Plasmora and tap "YouTube Music only" (never uses the mic) or "YouTube Music + mic"
   (falls back to the mic if capture is blocked), allow the microphone,
   then accept the screen-capture prompt (only AUDIO is used; nothing is recorded).
   If asked what to share, choose the entire screen.
4. Swipe left/right = change effect, double-tap = random effect,
   tap = show/hide controls, gear icon = settings, "PiP" = float over other apps.
5. For your own files choose "Local files" and pick songs.

If YouTube Music blocks capture for a track, Plasmora switches to the microphone.

SETTINGS
- Visuals: effect picker, auto-change timer, shuffle, transition length
- Colour: palettes, hue shift, drift speed, saturation, brightness, tint
- Quality: render scale (x0.5 .. x2 supersampled), frame rate (30..Max),
  16-bit HDR, glow quality/strength, colour fringing, grain, vignette
- Effects: cinematic post effects (temporal anti-aliasing, motion blur, beat camera shake,
  anamorphic streaks, lens flare, halation, god rays, depth of field, floor reflections,
  per-palette colour grading, sharpening). Off automatically in battery saver / wallpaper.
- Audio: sensitivity, bass/mid/treble, smoothing, beat detection,
  auto volume levelling, local-file sync offset
- Round 6: fluid simulation (Liquid Light, Ink Vortex), GPU particle systems (Swarm,
  Strange Attractor, Pulse Shell), raymarched 3D (Mandelbulb, Metaball Bloom, Crystal Cathedral),
  stem-style audio analysis (kick, snare, vocal, melody/pitch, tempo, structure), gyro parallax,
  beat-synced auto-director, edge glow over other apps, dim always-on mode, screensaver,
  Android TV support (remote keys) and mirroring to an external display (Settings > App).

BUILD FROM SOURCE
- Open this folder in Android Studio (Ladybug or newer) and press Run, or:
    ./gradlew assembleRelease
  (needs JDK 17+ and the Android SDK; the APK is signed with the debug key).
- Code layout:
    app/src/main/assets/shaders   GLSL shaders (warp, shapes, bloom, final)
    app/src/main/java/app/plasmora/gl     renderer, presets, palettes
    app/src/main/java/app/plasmora/audio  capture service, analyzer, local player
    app/src/main/java/app/plasmora/ui     Compose UI and settings

NOTES
- Android 10+ is needed to capture other apps' audio; older versions use the mic.
- Plasmora is an original app inspired by the Geiss music visualizer; it contains
  no Geiss code or assets.

Round 7 - new 3D visual styles
  3D Bar Grids        Bar Grid, Neon Bar Grid, Bar Towers (glBars-style cubes, columns = frequency, rows = time, floor reflection)
  Ribbons & Dots      Ribbon Flux, Ribbon Helix, Rainbow Ribbon (neon line-mesh ribbons); Dot Waves, Dot Ocean, Dot Aurora (dotted wave landscapes)
  Wire Mountains & Planes  Peak Wire, Synth Range, Audio Hills (hidden-line wireframe terrain); Radial Plane, Radial Bloom, Plane & Terrain
  Spectrum & Bars     LED Spectrum, LED Mirror, LED Neon (segmented LED bars with peak caps, green-yellow-red by height)
  New shaders: bar3d, wave3d, surf. New layer modes 21 (bars), 22 (ribbons/dots), 23 (terrain/radial plane).
  New presets are appended to the end of the list, so saved favourites keep their numbers.

Round 8
- Black-square guards in Mandelbulb / bloom / warp / TAA shaders.
- Player controls: progress bar styles (Off, Straight, Wavy, Dancer, Equaliser, Neon, Orb), album art size (Off/Small/Medium/Large), tap art to open the music app. Settings > Music.
- Lyrics (Settings > Effects, default Off): Key lines (chorus/hook only) or Full lyrics, synced, from lrclib.net. Adds the Internet permission; only title/artist/album/length are sent. Cached on the phone.

Round 8b
- Tap album art now launches the playing app directly (falls back to its player screen).
- Dancer progress bar: seven fluid moves (groove, disco point, run, jumping jacks, hands-up wave, spin, sway) that blend into each other.
- Lyrics styles: Plain, Glow, Neon, Pulse, Palette, In the visual (drawn in the GL scene); position Top/Centre/Bottom.
- Player background opacity slider (Settings > Music).
- Lyrics readability shade slider (dark halo behind text, works in all styles incl. In the visual).
- Round 8c: title bar lowered clear of the camera cut-out; progress bar defaults to logo purple (album-art colour when "Colours from album art" is on, also tints play/pause); settings sheet no longer closes on swipe (use Done); lyrics settings moved to Music tab; Reset moved to App tab.
- Round 8d: five dancers (change per track), logo-gradient play button, cut-out-aware title bar, scroll-proof swipe-to-close settings sheet (+ Close button).
- Round 8e: calmer 3D bar grids (wider time smoothing, lower gain, softer beat pop).
- Round 8f: settings UI themed with the logo colours (violet primary, cyan secondary, pink accent, indigo surfaces).
- Round 8g: favourites use logo pink instead of gold.
- Round 8h: settings panel restyled (Aurora glass, logo gradient) and explanatory text pruned.
- Round 8i: favourites blue, slim slider thumbs, flat toggles, headings pruned, Motion speed/intensity moved to Audio.
- Round 8j: dancer tempo follows detected BPM; lighter in-visual lyric shade; thinner gradient slider tracks.
- Round 8k: source picker modal on launch when no audio is linked.
Round 9: 8 new looks, 24 new presets (appended at the end so favourites keep their places). New categories: Hex & Tiles, Neon Tunnels & Mazes, Fire & Halos, Radial & Beads. Modes 24-30: hex tiles, neon frame tunnel + grid floor, neon maze, fire waveform, vortex halo, neon tile floor + light rain, 3D radial spectrum disc. Bead Terrain = glossy sphere version of the dot landscape. Generate previews in Settings > Visuals.
Round 9b: removed presets Fire Wave, Plasma Strands, Ember Thread, Bead Pearl, Frame Dash.
Round 9c: settings sheet closes only by pulling the handle (taller handle strip; content swallows all drags).
Round 9d: removed Solar Ring, Tile Pulse, Aurora Curtains, Aurora Fountain, Dot Waves, Dot Aurora (favourites remapped once); Halo Pulse smaller.
Round 9e: restored normal scrolling; sheet hide blocked for all gestures/scrim, only a pull on the handle (or Back) closes it.
Round 10: targetSdk/compileSdk 36; release signing via keystore.properties (not in source zip); bundleRelease produces Plasmora.aab for Play. Keep the keystore backed up.
Round 11: removed Strange Attractor (favourites remapped once).
Round 12: 12 new presets from the sample videos (modes 31-34): Pulse & Tunnels (Cherenkov Pulse, Pulse Ripples, Amber Pulse, Neon Corridor, Triangle Gate, Hex Portal), Smoke & Spikes (Neon Smoke, Circuit Flow, Ink Aurora, Crystal Spikes, Ember Spikes, Ice Spires).
Round 13: lyrics drawn straight to screen after all post (no spin/echo), softer beat pulse.
Round 14: new visuals from video (Smoke Bars/Pillars/Bloom/Nova, Circuit Trails, Wire Bundle, Neon Ribbons); removed Liquid Silk + colour-only duplicates; new defaults; palette ghosted with album-art colours; previews auto-build on launch.
Round 15: thumbs up/down + don't play disliked; audio reconnect check; previews prompt; Smoke Crown; Ocean Waves mesh; Glass Ring, Dot Ripples, LED Tunnel, Ring Bars, Dot Equaliser, Horizon Spectrum; float-over-apps off by default.
Round 16: portrait framing pass (zoom-out for all full-screen looks, smaller 3D rings/trails), Zoom slider, sharper smoke/maze/glass lines.
Round 17: LED Tunnel calmer, Dot Ripples reframed, sharper beads, source label removed, flat thumb icons, like/dislike on previews.
Round 17b: removed 'Listening to...' status notes.
Round 18: Horizon Spectrum portrait bar definition, slower beads/Solar Flare, less glass glow.
Round 19: removed TV & casting (external display + Cast screen); added Privacy policy link in Settings > App > About; Play Store icon 512px.
Round 20: notification-access prompt also shown on Source tab under Always show player controls.
Round 21: minSdk 29 (Android 10+); 3-step first-launch onboarding (replay from Settings > App > About).
Round 22: Always show player controls ghosted until notification access granted.
Round 23: notification access prompt moved above Always show player controls.
Round 24: Visuals tab: Changing effects then Framing moved to top.
Round 25: optional voice assistant mic button (Settings > Player controls, on by default); top-right under the cog, always visible; launches the device assistant, falls back to Android's voice assistant settings.
