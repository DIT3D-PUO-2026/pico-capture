[Back to README](../README.md)

# Pico Capture 0.1 Beta usage guide

Record your Pico Neo 3 or stream its view to a PC over Wi-Fi, with control over resolution, frame rate, bitrate and encoder settings.

Pico Capture 0.1 Beta is an experimental Android app for the Pico Neo 3. It runs in a dock panel and supports recording, streaming, or both at once.

> **Tested setup:** PICO Neo 3, rooted with **Magisk**, running the **Pico OS 5.9.9.0 ROM (Android 10)**. Other headset models and ROM versions have not been verified.

**1080p60 capture has been demonstrated on the tested headset.** Higher settings are selectable, but actual output depends on Pico's capture cadence, the running app and the encoder. Selecting 120 FPS does not guarantee 120 unique frames per second.

> **Mostly vibe coded:** Much of this app's original code was generated and refined using **OpenAI Codex / ChatGPT**. I directed the features and tested the app on my Pico Neo 3. Third-party components are credited below.

## Features

- Record locally, stream over Wi-Fi, or do both.
- Resolution presets from 720p to 4K, plus custom dimensions.
- Frame-rate presets from 30 to 120 FPS, plus custom values.
- Device-reported video encoders, adjustable bitrate and advanced encoder options.
- Browser playback, OBS/VLC inputs, RTSP over TCP and optional UDP output.
- Internal audio capture.
- GPU scaling and sharpening, including Qualcomm SGSR 1.
- Session status, measured output FPS and a session log.
- Temporary capture overrides with restoration on Stop.

## Requirements

- A rooted Pico Neo 3 with Magisk `su` and `resetprop` available.
- Pico OS **5.9.9.0**. The capture helper checks this firmware version; other firmware is unsupported.
- A matching native runtime library for the optional 90 Hz capture workaround.
- For Wi-Fi streaming, a PC on a network that can reach the headset.

The app uses Pico-specific capture APIs. Compatibility with other Pico models or Android devices has not been established.

## Getting started

1. Build and install the APK using the instructions below.
2. Wake the headset, put it on and open **Pico Capture**.
3. Select **Record + stream**, **Record only**, or **Stream only**.
4. Start with **1080p / 60 FPS / AVC / 12 Mbps / GPU enhancement Off**.
5. Enable **Internal audio** if needed.
6. Tap **Start capture** and allow the Magisk root request.
7. For streaming, use **Copy browser link** or the desktop feed address.
8. Tap **Stop & restore** when finished. Recordings are saved in `Movies/PicoCapture`.

Opening the app does not request root; starting capture does. Stop the current session before changing its configuration. Keep the headset awake: losing source frames or taking it off can end capture.

## Watch in a browser

Open the short address shown by the app:

```text
http://HEADSET_IP:8088/
```

Replace `HEADSET_IP` with your headset's address. The player includes sound, fullscreen, reconnect and buffer controls.

The player tries the selected codec's native stream. With **Browser compatibility encoder** enabled, unsupported non-AVC streams can fall back to a second hardware AVC/AAC feed. This requires extra encoding and GPU work. Browser codec support varies by browser and PC.

The player reports displayed FPS separately from the headset's encoder output. Its frame-age reading is an estimate, not a complete measurement of motion-to-screen latency.

## Use with OBS

For the initial setup, select **AVC** and a streaming mode in Pico Capture, then start capture.

1. In OBS, add a **Media Source**.
2. Uncheck **Local File**.
3. Set **Input** to `http://HEADSET_IP:8088/live`.
4. Set **Input Format** to `mpegts` if automatic detection fails.
5. Try a network buffer of **0–1 MB** for lower delay; increase it if your Wi-Fi needs more buffering.
6. Enable hardware decoding when supported by your PC and selected codec.

OBS can buffer more than the browser player. Increasing its buffer usually increases delay. The sender drops stale queued data when a receiver falls behind, but the receiver still controls its own buffering.

For RTSP, use `rtsp://HEADSET_IP:8554/live` with **TCP transport**. Available feeds depend on the selected codec; the browser player's desktop feed list shows the current options.

## Stream formats

The addresses below use the default ports. Streaming must be enabled in the app.

| Endpoint | Format | Video codecs |
| --- | --- | --- |
| `http://HEADSET_IP:8088/live` | MPEG-TS | AVC, HEVC, MPEG-4 |
| `http://HEADSET_IP:8088/live.mp4` | Fragmented MP4 | AVC, HEVC, MPEG-4, H.263 |
| `http://HEADSET_IP:8088/live.webm` | WebM | VP8, VP9 |
| `http://HEADSET_IP:8088/live.mkv` | Matroska | All selectable video families |
| `http://HEADSET_IP:8088/browser.mp4` | AVC/AAC fragmented MP4 | AVC or the compatibility feed |
| `rtsp://HEADSET_IP:8554/live` | TCP interleaved RTP/MP2T | AVC, HEVC, MPEG-4 |

Optional UDP output sends MPEG-TS to the **PC IP:port** entered in Advanced options. For example, send to port `9000` and receive with `udp://0.0.0.0:9000`. The receiver's firewall must allow that traffic. Set the RTSP port to `0` to disable RTSP; leave the UDP destination empty to disable UDP.

Internal audio uses AAC for MP4/TS video families and Opus for native VP8/VP9 WebM. The browser compatibility feed uses AAC. Microphone capture is not implemented.

The streaming endpoints are unencrypted and accessible to devices that can reach the headset. Use a trusted local network. RTMP, SRT, HLS, WebRTC and MJPEG are not implemented.

## GPU upscaling and sharpening

**Resolution** sets the encoded output size. **Capture size relative to output** sets the source size when enhancement is enabled. The selected filter applies to the recording and every stream output.

| Mode | What it does |
| --- | --- |
| Off | Original capture path |
| Smooth | Fast bilinear scaling |
| Bicubic | Catmull–Rom scaling |
| Sharp | Bilinear scaling with adjustable sharpening |
| SGSR 1 · balanced | Qualcomm spatial upscaling and adaptive sharpening |
| SGSR 1 · fine edges | SGSR with more aggressive processing of smaller edges |

For **720p → 1080p**, select **1080p output / 60 FPS / SGSR 1 balanced / capture size 67%**. For **1080p → 4K**, select **4K output / capture size 50%** and start at **30 FPS** with HEVC.

Capture size options are 50%, 67%, 75% and 100%. The separate **Sharpness** setting applies only to Sharp mode. To disable enhancement, select **Off**, then Stop and Start.

SGSR runs on the headset's GPU. It is a spatial upscaler, not neural AI, temporal reconstruction or frame generation. It does not increase the game's render resolution. In the identical-frame comparison, SGSR produced crisper edges while Bicubic scored closer to the native-size reference. Lowering capture resolution can reduce capture work, but filtering adds GPU work and does not guarantee better performance or lower latency.

## Tested behavior and limits

The following are short tests on the inspected Neo 3, not performance guarantees for every game.

| Configuration | Observed result |
| --- | --- |
| AVC 1080p60 | Recording and streaming at approximately 60 FPS |
| HEVC 1080p60 + AVC browser compatibility | Simultaneous encoding and playback passed |
| SGSR 720p → 1080p60 | Balanced and Fine edges passed; normal installed AVC recording averaged about 60.29 FPS |
| SGSR 1080p → 4K30, HEVC + compatibility | Recording and streaming passed |
| VP8 720p30 | Recording and browser playback passed |
| Software VP9 640×480, requested 30 FPS | Approximately 13–14 FPS |
| MPEG-4 640×480, requested 30 FPS | Approximately 26–28 FPS |
| H.263 352×288, requested 30 FPS | Capture and container checks passed |

AVC, HEVC and VP8 hardware encoders are available on the inspected device. Software encoders are also exposed. Android's reported capabilities and hardware flags do not establish that a configuration will run successfully or at the requested speed.

True 90/120 FPS, 4K60/120 and sustained performance under heavy game load remain unverified. An advertised HEVC Main10 configuration failed with Surface input; HDR/10-bit capture is not established. Capture covers the headset's viewport, not a 360° sphere.

Container and desktop transport checks used FFmpeg. Chromium playback and fallback were tested. Not every codec/container combination has been checked directly in OBS or every browser. These runtime results predate the Android Studio build conversion; newly rebuilt APKs still need an on-device check.

## Capture workaround and restoration

On the inspected firmware, the 90 Hz native capture path forced a divisor that limited capture to roughly 30 FPS. The optional **90 Hz capture workaround** changes one instruction in the runtime process's memory to honor the configured divisor. This enabled approximately 60 FPS in the tested scene.

The patch checks the native library hash and expected instruction bytes. It changes process memory and temporary capture properties; it does not replace vendor APKs or library files, change panel refresh rate, or alter thermal policy.

**Stop & restore** finalizes recording and restores the instruction and previous property values. A separate guard handles helper termination. Capture also stops after 15 seconds without a controller heartbeat or five seconds without source frames. Normal Stop, controller-loss cleanup and instruction restoration were tested.

Stop capture before uninstalling. Reboot clears the temporary process-memory and property overrides if normal cleanup is unavailable. Avoid running Pico's stock recorder or casting session at the same time.

## Build from source

### Android Studio

1. Open the repository root containing `settings.gradle`, not only the `app` folder.
2. Allow Gradle sync and install any missing SDK components.
3. Select the **app** run configuration and your headset.
4. Build or Run.

Current build configuration:

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 9.3.3 |
| Gradle wrapper | 9.5.0 |
| Compile SDK / build-tools | 36 / 36.0.0 |
| Minimum / target SDK | 29 / 29 |
| Java source compatibility | 8 |

Use a compatible Android Studio installation and its bundled Gradle JDK. Configure the Android SDK through Studio or `ANDROID_HOME`. The first build needs network access for build dependencies.

### Command line

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux:

```sh
chmod +x gradlew
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

With ADB connected, install it using:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`Build.ps1` is an optional Windows wrapper around the same Gradle build. `:app:assembleRelease` produces a signed release APK when the local project key is present, or an unsigned APK otherwise. See [Automatic builds](AUTO_BUILD.md) for CI signing and tagged beta releases. Code shrinking is disabled because the root helper is launched by class name. The Play Store target-SDK lint check is excluded for this Android 10 sideloaded utility.

### Signing and Git

If `signing/debug.keystore` exists locally, the debug build uses it. Otherwise, Android uses its normal local debug key. Updating an installed APK requires the same signing key that signed it.

Keep signing keys private and backed up. The included `.gitignore` excludes `signing/`, keystores, `local.properties`, IDE metadata, Gradle caches and generated build output. Commit the Gradle wrapper scripts and `gradle/wrapper` files.

## Troubleshooting

| Problem | Check |
| --- | --- |
| Root request fails | Check Pico Capture's Magisk grant, especially after reinstalling |
| Browser or OBS cannot connect | Select a streaming mode; check headset IP, ports and network isolation |
| Browser rejects a codec | Try AVC or enable Browser compatibility encoder |
| Output stays below requested FPS | Check measured output, source cadence, game load and selected encoder |
| Capture stops after removing the headset | Keep the headset awake and worn while capturing |
| Encoder configuration fails | Inspect its advertised capabilities, then reduce size/FPS or remove advanced options |
| Studio treats the app as plain Java | Reopen the repository root containing `settings.gradle` and sync Gradle |
| APK update reports a signature mismatch | Build with the original private signing key |

When reporting a problem, include firmware version, encoder, resolution, requested and measured FPS, filter settings and the relevant **Session log** excerpt. Remove private addresses and tokens before posting logs.

## Credits and licenses

- **SGSR 1:** Qualcomm's [Snapdragon Game Super Resolution](https://github.com/SnapdragonGameStudios/snapdragon-gsr), adapted from revision `d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e`. The [BSD-3-Clause notice](../app/src/main/assets/SGSR-LICENSE.txt) and [source provenance](../app/src/main/assets/SGSR-provenance.json) are included. The notice is also available in the app's Advanced options.
- **UI reference:** [Pico3dResolution](https://github.com/chaixshot/Pico3dResolution) by chaixshot. Its [MIT notice](../third_party/licenses/Pico3dResolution-MIT.txt) is included.

Pico Capture is licensed under the [MIT License](../LICENSE). Third-party components retain their respective licenses and notices listed above.
