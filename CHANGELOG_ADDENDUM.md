# Changelog — Additional Entry

## [4.0.1] — 2025-08-25 — BYOK & AI Provider Configuration (PRD 2)

### Added — BYOK Credential System (PRD 2 Sections 4, 12-16)
- AiCredentialStore interface with save/get/remove/hasCredential operations
- EncryptedCredentialRepository using Android Keystore-backed encryption
- ProviderCredential model with encrypted API key, key hint (last 4 chars), validation status
- ApiKeyValidator with format validation per provider (Gemini, OpenAI, NVIDIA NIM, OpenRouter, OpenCode Zen, Sarvam)
- MemorySecurity — clears plaintext keys after processing, sanitizes logs
- ApiKeyBackupPolicy — no cloud backup, no export, no sharing of API keys
- AiProvider enum with 7 providers (6 mandatory + 1 optional custom)

### Added — New Providers (PRD 2 Sections 33, 37)
- OpenAiProvider — coding, reasoning, code review, structured output, agentic tasks, vision
- SarvamProvider — Indian language assistance (Bengali, Hindi, Tamil, Telugu, Marathi, Gujarati, Punjabi, Kannada, Malayalam)
- Sarvam Language Routing — detects Indian language from Unicode ranges, routes to Sarvam preferred
- IndianLanguage enum with 9 languages and BCP-47 codes

### Added — AI Setup Wizard (PRD 2 Sections 5-11, 57-60)
- AISetupViewModel with 4-step setup (Introduction → Provider Config → Security Confirmation → Complete)
- AISetupWizardScreen with provider cards, API key input (masked, show/hide, paste), validation
- Setup Progress display (X/6 configured), Setup Recovery (resume missing only)
- Setup Completion Rules (Finish enabled only when all 6 mandatory providers configured)
- Security Confirmation dialog before completion

### Added — Provider Health & Status (PRD 2 Sections 8, 56, 71-72, 94)
- ProviderHealthManager with 6 health states (HEALTHY/DEGRADED/RATE_LIMITED/INVALID_CREDENTIAL/UNAVAILABLE/NOT_CONFIGURED)
- ProviderStatus states (Not Configured/Validating/Connected/Invalid/Network Error/Rate Limited/Provider Error)
- AI Degraded Mode — shows "X of 6 providers available, automatic fallback active"
- No Silent Provider Switching — fallback notifications
- FallbackNotifier with configurable visibility

### Added — AI Error Mapping & Retry (PRD 2 Sections 43-45, 65)
- AIErrorMapping with 10 normalized error types (AI_AUTH_ERROR, AI_RATE_LIMIT, AI_TIMEOUT, etc.)
- RetryPolicy with exponential backoff (only for transient failures)
- AIRequestDeduplicator with request IDs (prevents double-tap, screen recreation, retry race)
- No fallback for: invalid prompt, permanent auth failure, unsupported capability, safety refusal
- Fallback Chain (OpenAI → Gemini → NVIDIA NIM → OpenRouter → OpenCode Zen → Sarvam)
- AIModelFallbackMatrix — Task → Primary → Fallback mapping
- AIProviderPriorityControls — per-task-type provider priority

### Added — AI Settings & Privacy (PRD 2 Sections 17-20, 40, 52-53, 86-91)
- AIProviderSettingsScreen — Settings → AI Providers (connected status, change key, test connection, remove key)
- AIPrivacySettingsScreen — privacy toggles (allow source code, exclude secrets, confirm before upload)
- UserAIPreferences — default/coding/reasoning/vision/language/fast response providers, fallback strategy
- AIPrivacyControls — source exclusion defaults (local.properties, .env, *.pem, *.key, credentials.*, secrets.*), custom exclusions
- AIConversationStorage — encrypted history, delete, clear all, no API credentials, no GitHub sync
- ProviderInfo — provider terms/privacy links, API documentation links

### Added — Extended Model Registry (PRD 2 Sections 30-37)
- OpenAI models: GPT-4o, GPT-4o-mini, o3-mini
- Sarvam AI models: Sarvam-1, Sarvam Translate
- Extended task types: 27 types (added CODE_COMPLETION, CODE_REFACTORING, BUG_FIX, ERROR_ANALYSIS, REPOSITORY_ANALYSIS, IMAGE_ANALYSIS, DOCUMENT_ANALYSIS, YAML_GENERATION, ARCHITECTURE_DESIGN, TRANSLATION, INDIAN_LANGUAGE_ASSISTANCE, UI_ANALYSIS)
- Capability-based routing updated for 7 providers
- Sarvam language routing in model registry

### Added — AI Configuration Export (PRD 2 Section 84)
- ExportableConfig with provider preferences, routing mode, feature flags, privacy mode
- API keys explicitly excluded from export

## 4.1.1 — Universal UX/Runtime Fix Pass

- Added staggered bounce + fade entrance motion to the GitHub Personal Access Token screen.
- Replaced the custom compact bottom-navigation overlay with the native Material 3 NavigationBar to eliminate the translucent/ghost indicator artifact.
- Re-applied immersive system-bar hiding on focus/resume to prevent persistent status/navigation-bar overlays.
- Fixed Telegram first-run sheet sizing: 76dp rounded channel image beside channel name/description; marked the prompt as seen on either Continue or Not now so it returns only after reinstall.
- Added Android 13+ notification permission request after first-run UI and wired upload/update/workflow success/failure notifications to the existing notification system.
- Made workflow definition cards expandable and load their real GitHub Actions runs underneath.
- Added lightweight active-run polling while the workflow screen is alive and terminal-state notifications for status transitions.
- Made upload cancellation actually cancel the tagged WorkManager task and mark the operation cancelled.
- Improved upload current-file text contrast.
- Kept repository, workflow, log, inbox, authentication, and Material 3 architecture intact; no source feature was intentionally removed.
