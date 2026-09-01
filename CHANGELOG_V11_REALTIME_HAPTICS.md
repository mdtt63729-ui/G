# GITOFY V11 — Realtime Upload/Update + Haptics

## Fixed
- Repository update operations now run through WorkManager and persist state in Room.
- Update progress survives screen recreation, navigation, rotation, and process recreation.
- Active update operations are automatically re-attached when the Update Repository screen is reopened.
- ZIP selections for update now request persistable read permission when the provider supports it.
- Selected update ZIPs are validated immediately without blocking the UI thread.
- Upload/update state is mirrored from the real sync engine into persistent operation records.
- Duplicate terminal notifications are suppressed in the update screen.
- Added tactile system haptics to shared GITOFY buttons, cards, tonal buttons, FABs, and switches.
- Haptics use Android's system haptic channel and therefore respect the device's global haptic setting.

## Verification
- ZIP archive rebuilt and inspected after source changes.
- Gradle compilation was attempted, but the environment could not download Gradle 8.11.1 because external network/DNS access is unavailable (`UnknownHostException: services.gradle.org`).
