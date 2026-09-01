# GITOFY v4 Repair Audit

## Scope
Static/deep source audit of the supplied `GITOFY-AOS-Material3-Premium-Final-UNIVERSAL-FIXED-v3.zip`, followed by targeted source repairs.

## Findings and repairs

### 1. CRITICAL — ZIP container was being counted/uploaded as a project file
`RepositoryUploadCoordinator` stores the selected archive as `source.zip`. `GitPushWorker` previously treated the operation directory itself as the project root, so a multi-file project could appear as `1 file / 3.1 MB` and the archive could be pushed instead of the extracted source tree.

**Fixed:** the worker now validates/extracts `source.zip`, detects the real project root, injects workflows there, and uploads the extracted project files only.

### 2. CRITICAL — one GitHub Contents API mutation per file created many workflow runs
Repository updates used `PUT /contents/{path}` or `DELETE /contents/{path}` for every changed file. Each successful file mutation creates a commit. A repository with ~43 changed files can therefore generate ~43 push-triggered Actions runs.

**Fixed:** added Git Data API support for blobs → tree → commit → single branch-ref update. All changed files now land in **one commit**, producing **one push-triggered workflow run**.

The same one-commit strategy is used for initial repository creation.

### 3. CRITICAL — progress was not real during the network phase
The old create flow used one long JGit push, so the UI could remain at `0 B / total` for the whole network operation and then jump at completion.

**Fixed:** file blobs are uploaded individually with bounded concurrency (4), and the operation record is updated from actual completed file bytes. The percentage is derived from actual uploaded source bytes and never intentionally simulated.

### 4. CRITICAL — progress stage timer was resetting
`GitPushWorker.updateStageWithHistory()` recreated `stageStartedAt` on repeated updates when the caller did not explicitly provide a timestamp. This made upload speed/ETA calculations unstable.

**Fixed:** the existing stage start timestamp is preserved until the stage actually changes.

### 5. HIGH — update result data was discarded
`UpdateRepositoryViewModel` discarded `SyncResult.Updated` counts and set `commitSha` to an empty string.

**Fixed:** the manager returns the actual sync result and the UI now receives Added/Modified/Deleted/Unchanged counts and the real commit SHA.

### 6. HIGH — update cancellation control was disabled
The update screen rendered a disabled `Cancelling...` button while an operation was active.

**Fixed:** it is now a functional `Cancel update` action.

### 7. HIGH — update progress did not expose transferred bytes
The update screen showed percentage/file count but not byte progress.

**Fixed:** the update state now carries real `bytesUploaded` and `totalBytes`, and the screen renders them.

### 8. HIGH — step timers could remain stale while a step was running
A running step calculated elapsed time only when another recomposition happened.

**Fixed:** running step rows now refresh their timer once per second.

### 9. MEDIUM — redundant post-PAT connection screen
After successful PAT validation, navigation went through `ConnectingWithGitHubScreen`, which showed the app icon again and added an artificial delay.

**Fixed:** successful PAT validation now navigates directly to Home with the existing smooth navigation transition.

### 10. MEDIUM — staggered entrance motion was unnecessarily delayed
The shared stagger helper used a 55 ms interval and an older bounce-named transition.

**Fixed:** stagger interval reduced to 40 ms, capped at 280 ms, and the shared entrance now uses the restrained slide+fade transition. No bounce/elastic entrance is used by the PAT page.

### 11. MEDIUM — Jobs copy affordances
The Jobs UI already had repository/job-ID/error copy support in several places, but commit/error details in the dialog were inconsistent.

**Fixed:** commit SHA and job error now also have copy controls in the job details dialog.

## Workflow-run explanation
The `43` badge in the supplied notification screenshot is consistent with the previous per-file Contents API update architecture: every file mutation could create a separate commit and therefore a separate push-triggered workflow run. The repaired Git Data flow creates the tree and commit first and changes the branch reference only once.

## Verification limits
The source tree was re-scanned after modifications and the targeted old per-file Contents API calls were removed from the create/update sync paths. A Gradle compile was attempted, including offline mode, but this environment has no cached Gradle 8.11.1 distribution and cannot resolve `services.gradle.org`. Therefore no claim of APK/build success is made here.

## Known separate architecture items
The repository still contains broader AI infrastructure that is outside this upload/update repair pass, including provider adapter placeholders and the permission-preflight stub. Those should not be represented as fixed merely because the repository now has a repaired upload/update pipeline.
