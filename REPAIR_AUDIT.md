# GITOFY Universal Fix Pass — 2026-08-29

This archive contains the repair pass for the issues identified in the previous full UI/motion/stability audit.

## Fixed

- Compact bottom navigation now uses one spring-driven morphing capsule with animated position, width, icon scale, and label appearance.
- Navigation chrome remains structurally mounted so NavHost transitions are not reset when chrome visibility changes.
- Edge-to-edge system UI policy hardened; system bars use transient swipe behavior and transparent navigation-bar configuration.
- Home `showLoading` declaration ordering corrected.
- Home refresh rotation now runs only while a refresh is actually active.
- Shared GITOFY buttons now receive consistent press-scale feedback through a shared interaction source.
- Settings category cards now enter with staggered motion.
- Upload/update current-file text is readable and transitions between filenames instead of snapping.
- Upload success content has staged entrance motion; success-check spring was tuned to be restrained and premium.
- Notification channel registration/posting consolidated behind the canonical NotificationManager; NotificationHelper remains only as a compatibility facade.
- Notification posting now checks whether notifications are enabled and catches missing POST_NOTIFICATIONS permission safely.
- Notification IDs are derived from stable event keys rather than notification-type ordinals/fixed IDs.
- Workflow completion/failure notification detection is centralized at the repository transition point, preventing list/detail duplicate notifications.
- Workflow timeout/failure conclusions are handled correctly.
- Build & Run settings no longer contain dead click handlers; controls now have working switch interactions.
- About-page link rows no longer contain dead click handlers; they open readable in-app policy/license dialogs.
- Existing Room infrastructure was preserved because active runtime features depend on it; removing it would have broken current functionality rather than fixing a UI defect.

## Verification

- Archive extracted and re-packed successfully.
- `unzip -t` passed with no archive corruption.
- File/delimiter structural checks passed for all modified Kotlin files.
- A local Gradle build was attempted, but the sandbox did not have Gradle 8.11.1 cached and outbound access to services.gradle.org is unavailable. Therefore no false claim of a successful Android build is made here.
