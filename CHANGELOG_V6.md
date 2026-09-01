# v6 Final Performance & Stability Pass

- Fixed update-operation return-type mismatch.
- Removed artificial post-splash branding delay.
- Removed dead connecting screen.
- Removed fake AI streaming delay.
- Coalesced native progress events before Room persistence.
- Switched to libgit2 push-transfer progress.
- Added .gitignore-aware native indexing.
- Expanded upload exclusions.
- Bounded native packbuilder parallelism.
- Reduced update blob concurrency.
- Scoped upload notification IDs by operation.
- Smoothed progress bar without animating the numeric percentage.
- Hardened FetchContent mbedTLS/libgit2 CMake integration.
