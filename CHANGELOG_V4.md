# GITOFY v4

- Fixed ZIP extraction/project-root handling in repository creation.
- Replaced per-file GitHub Contents commits with Git Data API single-commit synchronization.
- Added bounded concurrent blob uploads (4 at a time) for faster transfers.
- Added real byte/file progress for create and update operations.
- Preserved upload stage start time for stable speed/ETA.
- Fixed update result counts and commit SHA propagation.
- Enabled the Update screen cancellation action.
- Fixed running-step timers to update every second.
- Removed the redundant post-PAT Connecting with GitHub screen; PAT success now opens Home directly.
- Reduced stagger delay and standardized entrance motion to non-bouncy slide+fade.
- Added copy actions for job commit SHA and error details.

## Native Git / libgit2

- Removed Eclipse JGit dependency from the Android app.
- Added JNI bridge backed by libgit2 v1.9.7.
- Added Mbed TLS HTTPS backend for native GitHub pushes.
- Repository creation now creates one native Git index/tree/commit and performs one smart-HTTP push, avoiding one GitHub Contents API mutation per file.
- Native progress reports source file/byte indexing and push stages.
- PAT remains outside remote URLs and is supplied through libgit2's credential callback.
- Added `arm64-v8a` and `armeabi-v7a` native builds.
