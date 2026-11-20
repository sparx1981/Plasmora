# Plasmora - handoff notes

Android music visualiser (Geiss-inspired). Kotlin + Jetpack Compose + OpenGL ES 3.0.
- Package / applicationId: `app.plasmora`. minSdk 29 (Android 10+), target/compile 36.
- Published on Google Play: closed testing (Alpha), version 1 (1.0). Next upload must bump `versionCode` to 2 in `app/build.gradle.kts`.
- Privacy policy: https://sites.google.com/view/plasmora-privacy-policy/home (linked in Settings > App > About).

## Build
`./gradlew --offline -q assembleRelease bundleRelease`
Release signing reads `keystore.properties` at the project root (NOT in git):
```
storeFile=/path/to/plasmora-release.jks
storePassword=...
keyAlias=plasmora
keyPassword=...
```
The upload keystore is kept outside this repo. Never commit it.
The keystore (`plasmora-release.jks`), its details (`keystore-details.txt`) and file backups are in the owner's Google Drive folder "My Android App Creations".

## Layout
- `app/src/main/java/app/plasmora/` - Kotlin: `gl/` renderer + presets, `ui/` Compose screens, `audio/` capture service, `media/` now-playing, lyrics (LRCLIB), overlays.
- `app/src/main/assets/shaders/` - GLSL. Layer modes dispatched in `VisualizerRenderer.drawLayer`; next free mode = 45.
- `store/` - Play Store icon, feature graphic, phone screenshots, listing text, release notes, privacy policy.
- `tools/` - `icon.py`, `store.py` (store graphics); `tools/render-harness/` - software-GL shader renderer used for previews and screenshots (`review.py`, `t8.py`, set W/H env vars).
- `README.txt` - change log by round.

## Conventions the owner prefers
- Code/scripts delivered as .txt when sent in chat.
- Clarifying questions as multiple choice.
- Always state that builds are untested on a real device unless they were.
