# Pico Capture 0.1 Beta

Record your Pico Neo 3 or stream its view to a PC over Wi-Fi. Runs in a dock panel with configurable resolution, FPS, bitrate and encoder settings.

## Compatibility

| Requirement | Tested setup |
| --- | --- |
| Headset | PICO Neo 3 |
| ROM | Pico OS 5.9.9.0, Android 10 |
| Root | Magisk with `su` and `resetprop` |
| Capture | 1080p60; HEVC 4K30 also passed short tests |

Other models and ROM versions are unverified. The capture helper checks for this firmware version. The optional 90 Hz workaround also requires a matching native library.

## Features

- Record locally, stream, or do both, with optional internal audio.
- 720p to 4K output presets, custom sizes and 30–120 FPS requests.
- Selectable device encoders, bitrate and advanced encoder options.
- Browser playback and OBS/VLC feeds over HTTP, RTSP/TCP or optional UDP.
- Smooth, Bicubic, Sharp and Qualcomm SGSR 1 GPU enhancement.
- Measured output FPS, session logs and **Stop & restore** for temporary capture overrides.

Requested FPS is not guaranteed output. True 90/120 FPS and 4K60/120 remain unverified. SGSR is spatial upscaling, not AI or frame generation.

## How to use

1. Install the APK and open **Pico Capture** with the headset awake and worn.
2. Choose **Record + stream**, **Record only**, or **Stream only**.
3. Start with **1080p / 60 FPS / AVC / 12 Mbps / enhancement Off**.
4. Tap **Start capture** and grant Magisk root access.
5. Use **Copy browser link** to watch on your PC. Tap **Stop & restore** when finished.

Recordings are saved in `Movies/PicoCapture`. Stop before changing settings or uninstalling.

## Browser and OBS

Connect the PC and headset to a trusted local network. Replace `HEADSET_IP` with the address shown by the app.

| Player | Address / setup |
| --- | --- |
| Browser | `http://HEADSET_IP:8088/` |
| OBS Media Source | Uncheck Local File; input `http://HEADSET_IP:8088/live`; format `mpegts` with AVC/HEVC |
| VLC / RTSP | `rtsp://HEADSET_IP:8554/live`, using TCP transport |

For lower OBS delay, try a 0–1 MB network buffer. For browser codec problems, use AVC or enable **Browser compatibility encoder**. Taking off the headset or losing source frames can stop capture.

More formats, enhancement settings and troubleshooting: [Usage guide](docs/USAGE.md).

## Build

Open the repository root containing `settings.gradle` in Android Studio and let Gradle sync. Build the **app** module, or run:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Keep signing keys private; updating an installed APK requires the same key.

Pushes to `main` and pull requests build APKs automatically. Download them from **Actions → Build APK → Artifacts**. [Automatic builds and beta releases](docs/AUTO_BUILD.md).

## Development

Mostly **vibe coded**: much of this app's original code was generated and refined using **OpenAI Codex / ChatGPT**. I directed the features and tested the app on my Pico Neo 3.

## License and credits

Pico Capture is licensed under [MIT](LICENSE).

- [Pico3dResolution](https://github.com/chaixshot/Pico3dResolution) by chaixshot: UI reference, with its [MIT notice](third_party/licenses/Pico3dResolution-MIT.txt) preserved separately.
- [Snapdragon Game Super Resolution](https://github.com/SnapdragonGameStudios/snapdragon-gsr) by Qualcomm: SGSR 1, under [BSD-3-Clause](app/src/main/assets/SGSR-LICENSE.txt).
