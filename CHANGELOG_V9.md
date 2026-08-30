# GITOFY v9 — Production Git/AI/Performance Pass

## Git / upload
- Create and update repository flows now use the native libgit2 engine for the actual Git commit/tree/push path.
- Update flow clones the current branch, replaces the working tree, rebuilds the index, creates one commit and performs one push.
- Native update detects a tree-identical result and avoids creating an empty commit.
- Native indexing uses `git_index_add_all` and removes generated directories from the index.
- Native push/update cancellation is propagated through the JNI callback into libgit2's push callback.
- Removed the obsolete REST blob/commit `GitDataCommitter` implementation.
- Git blob SHA comparison in the preflight diff now streams file contents instead of `readBytes()`.
- Remote tree truncation is now a hard safety failure rather than an incomplete comparison followed by a write.
- Final create/update verification checks the target branch commit SHA, not merely repository metadata.

## AI
- Provider streaming transport now uses cancellable OkHttp calls; coroutine cancellation calls `Call.cancel()`.
- Gemini API keys are sent through `x-goog-api-key` instead of query strings.
- Provider connection tests run on `Dispatchers.IO`.
- Existing ModelRouter health filtering remains authoritative for explicit unavailable/rate-limited providers.

## Performance / stability
- Removed the old bounce animation name and kept the lightweight slide/fade motion system.
- ETag database access no longer uses `runBlocking` inside the interceptor.
- Native upload/update temporary working directories are cleaned deterministically.
- Native generated-directory filtering is enforced at the Git index layer as well as file enumeration.

## Verification
- Source scans: no JGit implementation references, no obsolete GitDataCommitter references, no native `git_index_add_bypath`, no ETag `runBlocking`.
- Kotlin syntax pass on changed source files completed without parser/type-pattern errors.
- libgit2 API usage was cross-checked against the current public API documentation for clone options, index operations, references, commits and push callbacks.
- Full Android Gradle compilation remains environment-blocked because Gradle 8.11.1 distribution cannot be downloaded in this sandbox.
