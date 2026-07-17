# NeverSoft Media Editor

A fast, dead-simple photo & video editor for Android. Built to make the common
edits — cut, caption, filter, score, export — obvious on first open, with no
sign-in, no watermark, and no upload wait.

## Why it's fast to *import*

There is no "upload". When you pick media, Android's Photo Picker hands the app
a scoped content URI and the editor feeds that URI **straight into the Media3
engine** — the source file is never copied or re-encoded on the way in. So a
4-minute 4K clip drops onto the timeline as instantly as a photo, regardless of
size, and nothing ever leaves the device. Re-encoding happens exactly once, at
export, and only for what you actually keep.

## What it does

- **One-tap import** of any number of photos and videos (Photo Picker — no
  storage permission, no dialogs).
- **Timeline** with real thumbnails (decoded video frames), sized by duration.
- **Trim** video with a range slider; set **photo duration** with a slider.
- **Split** the selected clip at the playhead.
- **Auto-Enhance** — one-tap punch-up (brightness + contrast + saturation); "Enhance all" too.
- **Auto-Clip** — trim a long video down to a centred 10/15/30/60-second window.
- **Rotate** 90° and set the **aspect ratio** (9:16 / 1:1 / 16:9), clips letterboxed to fit.
- **Speed** from 0.25× (slow-mo) to 4× (fast-forward), audio kept in sync.
- **Filters** — Vivid, Warm, Cool, Mono, Fade — pure GL colour math, no LUT files.
- **Captions** anchored top / centre / bottom, burned per clip.
- **Background music** with a volume slider.
- **Mute**, **duplicate**, **reorder**, **delete** clips.
- **Undo** every edit.

### Music Studio

A small dedicated audio editor (from the home screen):

- **Crop** a song's length with a range slider, with live preview.
- **Volume** (0–200%) and **speed** (0.5×–2×).
- **Re-tag** — save a new **title / artist / album**.
- Saves a fresh `.m4a` into **Music › NeverSoft**; the new tags register in the
  device music library (`MediaStore.Audio`).

### Stem separation — on-device, experimental

Split a song into **Vocals + Instrumental**, entirely on the phone:

- Powered by **Open-Unmix (UMX-L)** exported to ONNX, run via **ONNX Runtime**.
  The vocals model (~108 MB, MIT) is downloaded once by the app's model manager;
  after that, **all processing is local** — nothing is uploaded.
- Pipeline (`dsp/` + `engine/StemSeparator.kt`): decode → STFT (n_fft 4096,
  hop 1024) → run the model on the magnitude spectrogram → soft-mask → the
  complement is the instrumental → iSTFT. Processed in ~20 s chunks to bound
  memory. Outputs two WAVs into Music › NeverSoft.
- **Experimental:** the STFT/iSTFT is matched to `torch.stft` but not yet
  device-verified, and separation is a single-model 2-stem approximation, so
  quality varies by track. 4-stem (drums/bass/other) is a natural next step.

Model: [`chinedudave06/demucs-onnx`](https://hf.co/chinedudave06/demucs-onnx)
(Open-Unmix UMX-L, MIT).
- **Export** to MP4 and save straight to the gallery (Movies › NeverSoft), with
  a live progress readout.

Preview plays the raw clips (with trim and photo duration applied) on a
standard, battle-tested `ExoPlayer` — so photos and videos of any size/length
play reliably. The polished looks — filters, captions and speed — are rendered
into the file by `Transformer` on export.

## How it's built

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose (Material 3), single dark theme |
| Preview | `androidx.media3.exoplayer.ExoPlayer` (images + video, any size) |
| Render | `androidx.media3.transformer.Transformer` |
| Effects | `androidx.media3.effect` (filters, speed, text/overlay, presentation) |
| Thumbnails | Coil + `VideoFrameDecoder` (no file copy) |

Metadata is probed off the main thread, so importing a long/large video never
blocks the UI.

Key files:

- `model/Project.kt` — immutable edit state (clips, trims, filters, captions, music).
- `engine/CompositionFactory.kt` — turns a `Project` into a Media3 `Composition`
  (shared by preview and export).
- `engine/EditorExporter.kt` — runs `Transformer`, publishes to the gallery.
- `ui/EditorViewModel.kt` — all edit operations + undo + split-at-playhead.
- `ui/editor/*` — preview, timeline, and the labelled tool bar / panels.

## Build

```
gradle :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
Toolchain: JDK 17, AGP 8.6.1, Gradle 8.7, compileSdk 35, **minSdk 26**,
Media3 1.6.1.

CI (`.github/workflows/build.yml`) builds the APK on every push and re-publishes
it to a rolling `latest` release, so it shows up automatically in the Neversoft
App Center.

## Roadmap

Transitions, keyframed text animation, stickers/emoji overlays, audio
waveforms and beat-snapping, per-clip volume, aspect-ratio presets
(9:16 / 1:1 / 16:9), and saved projects.
