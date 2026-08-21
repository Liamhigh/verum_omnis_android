# Building Verum Omnis Locally (VS Code / any machine)

A from-scratch guide to a working build. Verified against the repo state of
2026-08-07 (Constitution v8.0, app v5.3.1c).

## 1. Prerequisites

| Tool | Version | Why |
|------|---------|-----|
| JDK | **17** (or 21) | AGP 8.5.2 requires 17+. Do NOT set `org.gradle.java.home` in the repo's `gradle.properties` — use `JAVA_HOME` or `~/.gradle/gradle.properties` |
| Android SDK | Platform **34** + latest build-tools | `compileSdk = 34` |
| Android NDK | **27.2.12479018** (exact) | Pinned via `ndkVersion` for the llama.cpp native build |
| CMake | **3.22.1** (via SDK manager) | Pinned in `externalNativeBuild` |
| Git | any recent | CMake fetches llama.cpp from GitHub at configure time |

Install the SDK pieces with Android Studio's SDK Manager, or headless:

```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0" \
           "ndk;27.2.12479018" "cmake;3.22.1"
```

## 2. Point the build at your SDK

Either export `ANDROID_HOME`, or create `local.properties` in the repo root
(gitignored — never commit it):

```properties
sdk.dir=/path/to/Android/Sdk
```

## 3. Build and test

```bash
./gradlew testDebugUnitTest   # unit + JVM UI (Roborazzi) tests — must be GREEN before merging
./gradlew assembleDebug       # debug APK -> app/build/outputs/apk/debug/
./gradlew lintDebug           # Android lint
```

On Windows use `gradlew.bat`. In VS Code, any terminal works; for Kotlin
language support install the "Kotlin" extension, though the build itself is
pure Gradle and needs no IDE.

**First build is slow and needs network**: Gradle downloads dependencies, and
CMake clones llama.cpp (pinned to release b10312 in
`app/src/main/cpp/CMakeLists.txt`). Subsequent builds are incremental.

Install on a connected device:

```bash
./gradlew installDebug
```

## 4. One-time follow-ups (state as of 2026-08-07)

- **Model pinning** — the next-gen on-device models (Gemma 4 E4B/E2B,
  Qwen3.5-4B) are staged but inactive until their SHA-256 hashes are pinned:
  run `bash tools/pin-models.sh`, paste its output into
  `core/Constitution.kt`, rebuild. Until then the app serves the previously
  verified models (Gemma 3 4B/1B, Phi-3, Gemma 4 12B) — nothing is broken.
- **On-device LLM smoke test** — with a model downloaded on the device:
  `./gradlew connectedDebugAndroidTest` (runs `LlamaModelSmokeTest` among
  others; needs a real arm64 device, models are arm64-v8a only).
- **CI** — `.github/workflows/ci.yml` is parked (manual trigger only) because
  GitHub Actions is blocked at the account level. Local green builds are the
  merge gate until that is resolved.
- **Release builds** — no signing config exists yet; `assembleRelease` will
  produce an unsigned APK. Release engineering (signing, R8, Play data
  safety) is tracked in BUILD_STATUS.md.

## 5. Known build-environment quirks

- The NewsAPI key for Deep Research resolves from `-PNEWS_API_KEY`,
  `local.properties`, then a baked-in public default (see
  `app/build.gradle.kts`). For real use, set your own and rotate/restrict the
  default at newsapi.org — this is a public repository.
- The native library builds `arm64-v8a` only, with 16 KB page alignment
  (Android 15+/Play requirement) — x86 emulators will run the app but the
  inference bridge will be absent; everything degrades deterministically.
- `assets/constitution.md` is generated at build time from the repo's
  `CONSTITUTION.md` (`syncConstitution` task) — edit the root file, never the
  asset.
