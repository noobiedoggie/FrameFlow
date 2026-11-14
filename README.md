# FrameFlow

A minimalistic, phone-first AI-assisted 2D frame-by-frame animation studio for Android (`minSdk = 26`, `compileSdk = 37`), built with Jetpack Compose and Navigation 3.

## Features

- **Phone-Ergonomic Minimalistic Workspace**
  - Dark obsidian workspace (`ObsidianBg` / `StudioSurface` with `IndigoAccent` and `MintAiAccent`) and distraction-free **Focus Mode**.
  - Thumb-zone bottom deck housing primary tools, contextual brush/tool options, Undo/Redo, and a hold-adjustable filmstrip timeline.
- **Multi-Touch Canvas & Vector Drawing Engine**
  - Smooth pinch-to-zoom, two-finger rotate/pan around centroid, two-finger tap **Undo**, three-finger tap **Redo**, and single-tap playback pause.
  - Vector brushes (`Pen`, `Pencil`, `Marker`, `Highlighter`), `Split` / `Whole` eraser for both strokes and fill patches, cross-layer bucket **Flood Fill**, Lasso selection/transform/duplicate/delete, mirror symmetry, shapes, onion skinning, and multi-layer opacity/visibility controls.
- **AI Animation Suite**
  - **AI In-Betweening (`MotionTweenEngine`)**: Automatic stroke pairing with orientation reversal checks, fade in/out for unmatched strokes, and `EASE_IN_OUT`, `LINEAR`, `BOUNCE`, and `ANTICIPATE` easing curves.
  - **Sketch Polish & Ghost Trajectory (`MotionPredictor` & `StrokeGeometry`)**: RDP + Chaikin stroke smoothing, rough shape snapping, and 2-frame velocity ghost preview with one-tap frame stamping.
  - **Prompt-to-Animation & AI Coach (`GeminiClient` + `LocalAnimationSynthesizer`)**: Uses Google Gemini Interactions API (`v1beta/interactions`, `gemini-3.8-flash` default, plus `gemini-4-argon`, `gemini-3.6-flash`, and `gemini-3.5-flash-lite`) with optional current-frame visual context and structured JSON output, with automatic offline synthesis fallback when no API key is configured.
  - **Backup Privacy**: Excludes `frameflow_ai_prefs.xml` (storing any user-entered Gemini API key) from cloud backup and device transfer while keeping project data backed up.
- **Export Pipeline**
  - Looping adaptive-256-color **GIF89a**, hardware-encoded YUV420 **MP4** (`MediaCodec` H.264/AVC respecting FPS and per-frame hold counts), and **PNG** frame exports with `MediaStore` gallery save and `FileProvider` sharing.

## Build & Verification

Run unit/Robolectric tests, Android Lint, and build the debug APK:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK output path:

```text
app/build/outputs/apk/debug/app-debug.apk
```
