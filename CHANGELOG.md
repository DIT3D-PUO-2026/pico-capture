# Changelog

## [Unreleased]

## [0.1-beta] - 2026-10-14

### Added

- Local recording, Wi-Fi streaming and simultaneous record-and-stream modes.
- Browser playback and OBS/VLC feeds, including HTTP, RTSP/TCP and optional UDP.
- Resolution, FPS, bitrate, encoder and internal-audio controls.
- Smooth, Bicubic, Sharp and SGSR 1 GPU enhancement options.
- Dock interface, measured FPS, session logs and Stop & restore.
- Automatic APK builds and beta releases using these changelog entries.

### Fixed

- Android Studio project setup and generated version-class indexing.
- Android lint errors involving layout/display constants and root-only internal audio.

### Compatibility

- Capture tested on a Magisk-rooted Pico Neo 3 running Pico OS 5.9.9.0 / Android 10.
- 1080p60 and short HEVC 4K30 capture tests passed. True 90/120 FPS and 4K60/120 remain unverified.
