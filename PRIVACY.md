# Privacy Policy

## Data Collection

GITOFY follows strict data minimization principles.

### What we collect locally:
- GitHub username and avatar URL (for display)
- Cached repository metadata (for offline access)
- Cached workflow run data (for monitoring)
- Operation history (for reliability)

### What we NEVER collect:
- Personal Access Tokens (stored only in Android Keystore)
- Source code from your projects
- Commit contents
- Repository file contents
- Authorization headers
- GitHub Actions secrets
- Workflow secret values

## Network Data

All network traffic goes directly to GitHub's API servers (api.github.com) over HTTPS.

No third-party analytics, tracking, or advertising SDKs are included.

## Analytics

If analytics are enabled, only anonymous event counts are collected:
- app_opened, auth_success/failure, repo_creation_success/failure, workflow_triggered, artifact_download_success/failure

No repository names, owner names, file contents, or tokens are ever attached to analytics events.

## Data Storage

- Credentials: Android Keystore + EncryptedSharedPreferences
- Cache: Room database (app-private)
- Downloads: Android Downloads directory (user-accessible)

## User Control

Users can:
- Sign out (clears all credentials)
- Clear cache
- Delete downloaded artifacts
- Disable background sync
- Disable notifications

## Backup

Sensitive data is excluded from Android cloud backup.
