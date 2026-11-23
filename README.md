# Backspace Android

Native Android client (**Kotlin + Jetpack Compose + LiveKit native WebRTC**) for self-hosted [Backspace](https://github.com/TheZwiss/backspace) instances, focused on low-latency, battery-efficient voice communication.

## Features (v0.1)
- **Multi-instance support:** connect to any self-hosted Backspace instance over HTTPS/WSS.
- **Native low-latency voice:**
  - `livekit-android` (native C++ WebRTC + Opus)
  - Persistent `ForegroundService` with quick notification controls (*Mute / Deafen / Leave*)
  - Native Android audio routing (Bluetooth SCO/LE, Speaker, Wired Headset, Earpiece)
  - Configurable Opus settings (Voice vs. Music mode, Stereo, Auto/Manual bitrate, DTX & RED)
  - Live connection telemetry and idle audio suspension for battery savings
- **Aether Drift design system** + **5 languages** (`en`, `pt-BR`, `de`, `ru`, `zh-CN`).
- **Automated APK builds** via GitHub Actions (`.github/workflows/build-apk.yml`).

## Documentation
- [docs/architecture.md](./docs/architecture.md) — Multi-module architecture and roadmap.
- [docs/api-contract.md](./docs/api-contract.md) — REST, WebSocket, and LiveKit protocol reference.
- [AGENTS.md](./AGENTS.md) — Architecture rules and development guidelines.

## Building the APK

### GitHub Actions
Push to `main` (or create a `v*` tag) to build the APK automatically via the **Build Android APK** workflow.

### Local Build
1. Install **JDK 21** and the **Android SDK** (API 35).
2. Configure `JAVA_HOME` and `ANDROID_HOME`.
3. Run:
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`.
