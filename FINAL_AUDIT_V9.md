# GITOFY v9 Final Audit

## Fixed in v9
1. Create path no longer uses the obsolete GitHub REST blob/commit implementation; it uses libgit2.
2. Update path no longer uses GitDataCommitter/REST blob uploads; it uses native clone → replace tree → index → commit → push.
3. Native update supports cancellation checks during clone, indexing and push callbacks.
4. Native indexing uses git_index_add_all and explicit generated-directory removal.
5. Update refuses truncated GitHub tree snapshots during the comparison preflight.
6. Update/create verify the resulting branch commit SHA.
7. Large-file Git blob hashing is streaming instead of readBytes().
8. AI streaming uses cancellable OkHttp calls instead of non-cancellable blocking calls.
9. Gemini API keys are removed from request URLs.
10. Connection tests are dispatched to IO.
11. ETag interceptor no longer uses runBlocking.
12. Obsolete GitDataCommitter source was removed.

## Deliberate constraints
- SSH transport remains disabled; PAT/HTTPS is the production native transport.
- Room's destructive migration fallback is retained because the project does not currently ship complete schema migration history; removing it without migrations would create a worse runtime failure for existing installations.
- The settings-side provider discovery client still uses HttpURLConnection, but its callers already execute it on Dispatchers.IO; it is isolated from the production AI generation transport.

## Build limitation
The sandbox cannot download Gradle 8.11.1 from services.gradle.org, so a complete Android APK compile/link test cannot be truthfully claimed here.
