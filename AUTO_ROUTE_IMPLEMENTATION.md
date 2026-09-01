# Auto Route Mode

- Adds a persistent `AUTO_ROUTE` model selection.
- Ranks configured free models dynamically across all discovered providers.
- Task-aware scoring for coding, reasoning/debugging, vision/image, files and video.
- Prefers highest-quality coding models for coding tasks and vision-capable models when an image is attached.
- On retryable provider/model failures (429/5xx/timeouts/network/unavailable/model-not-found), automatically advances through the ranked candidates.
- Candidate switching can cross providers and can switch models within the same provider.
- Keeps the UI selector labelled `Auto Route`; the currently active candidate is stored in `autoRouteModel` for status/debug UI.
- Manual model selection remains unchanged and disables Auto Route.
- Auto Route is free-first by design; it never silently chooses a paid model.
