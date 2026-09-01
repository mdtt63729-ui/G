# GITOFY V10 Stability Fixes

- Fixed release-startup loop caused by stale resource SHA-256 pins in `ResourceIntegrityEngine`.
- Removed the splash bitmap from the runtime integrity pin set after replacing it with a vector splash asset.
- Reworked adaptive launcher icon foreground/background so Android can scale and mask the icon correctly.
- Removed `windowFullscreen` from the base theme to avoid conflicting edge-to-edge/inset behavior during minimize/restore.
- Hardened Build Jobs details UI by replacing the nested weighted LazyColumn inside AlertDialog with one bounded LazyColumn, preventing measurement/scroll crashes when opening a job.
- Preserved the existing stable system-bar configuration and navigation state architecture.
