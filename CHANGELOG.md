# Changelog

## [Unreleased]

## [0.2-beta] - 2026-10-14

### Added

- Named capture profiles saved locally, including encoder, audio, enhancement and advanced settings, with three starting profiles for OBS, browser streaming and recording.
- Live encoded/sent bitrate, HTTP and RTSP connection counts, UDP output status, stream queue resets and discarded-chunk counters in the dock and browser.
- On-device checks for profile persistence, custom settings restoration and stream diagnostics.

### Changed

- Reduced public release downloads to the install APK, checksums and license notices. Debug APKs and build metadata remain in Actions artifacts.

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
