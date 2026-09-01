# GITOFY Native Git / libgit2 Integration

## What changed

GITOFY no longer packages or references Eclipse JGit. Repository creation's final Git operation now uses libgit2 through JNI:

`ZIP -> secure Android extraction -> native libgit2 index -> tree -> commit -> one smart-HTTP push`

The Android layer keeps ownership of SAF/ZIP validation, WorkManager, encrypted credentials and UI state. Native code owns Git object creation, hashing, pack generation and the GitHub push.

## Native dependencies

- libgit2 `v1.9.7` (CMake FetchContent, pinned tag)
- Mbed TLS `v3.6.3` for HTTPS/TLS
- Android NDK/CMake
- ABIs: `arm64-v8a`, `armeabi-v7a`

libgit2 1.9.6 is deliberately used instead of older 1.9.x releases because upstream shipped security fixes in 1.9.5 and a further Android-specific crash fix in 1.9.6.

## Authentication

GitHub HTTPS + PAT is implemented with libgit2's credential callback. The token is never embedded in the remote URL.

SSH is intentionally not enabled in this first native build because enabling it requires an additional libssh2 + TLS dependency chain. The architecture keeps authentication inside libgit2 callbacks so SSH can be added without changing the Kotlin upload pipeline.

## Progress

Native indexing reports exact source-file bytes/files while each file is added to the Git index. The network push reports libgit2 pack transfer progress. The UI never resets the monotonic source-byte counter to zero.

## Build

The Android Gradle Plugin invokes `app/src/main/cpp/CMakeLists.txt`. CMake fetches the pinned native sources during the build. GitHub Actions runners therefore need outbound access to GitHub during the first native build; subsequent builds are normally accelerated by Gradle/CI caches.
