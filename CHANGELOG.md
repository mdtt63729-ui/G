# Changelog

## [4.0.0] — 2025-08-25 — Multi-Provider AI Gateway

### Added — AI Gateway Architecture (PRD v4.0 Sections 5-6)
- AIGateway with provider auth, model routing, rate limiting, context filtering, secret redaction, response normalization, usage accounting, fallback, retry classification, AI policy enforcement
- AIPolicyEnforcer for consent, feature flags, rate limits, usage limits
- UsageAccountant for AI request tracking (success, fallback, latency, tokens, cost)
- AIGatewayObservability for monitoring (request count, success rate, latency, provider errors, token usage)
- AIGatewaySecretManager for server-side credential management and API key rotation
- AIReliabilityTargets (gateway >99.5%, success >98%, fallback >90%, P95 <500ms)

### Added — AI Provider Abstraction (PRD Sections 7-14)
- AIProvider interface (generate, stream, analyze, summarize, explain, generatePatch, classify, healthCheck)
- ProviderRegistry for dynamic provider configuration
- GeminiProvider — configurable current models, multimodal, large-context
- NvidiaNimProvider — NIM-hosted models, high-performance coding, self-hosted
- OpenRouterProvider — multi-model routing, redundancy, cost optimization
- OpenCodeZenProvider — coding tasks, agentic tasks, streaming
- CustomProvider — OpenAI-compatible, self-hosted, enterprise gateways

### Added — AI Model Registry & Routing (PRD Sections 9, 15-20)
- ModelRegistry with capability-driven model records (modelId, contextWindow, visionSupport, toolCalling, structuredOutput, streaming, codingScore, reasoningScore, latencyClass, costClass, availability, fallbackPriority)
- ModelRouter with dynamic routing logic (task → capability → context → health → cost → preference → model)
- FreeFirstCostPolicy (free-first, not free-only; priority: free → low-cost → premium → emergency)
- FallbackSystem with capability-compatible fallback (429, 5xx, timeout, unavailable, context limit, capability mismatch)
- ProviderHealthSystem (AVAILABLE/DEGRADED/RATE_LIMITED/UNAVAILABLE/UNKNOWN with latency, error rate, success tracking)

### Added — AI Context Engine (PRD Sections 21-26, 63-64)
- AIContextEngineV4 with context budgeting (relevant resource detection, file/log ranking, context compression, secret redaction, token budget check)
- Large repository analysis (file index, symbol metadata, relevant-file retrieval, context ranking)
- AISecretRedactor detecting 15+ secret types (GitHub tokens, PATs, API keys, private keys, passwords, AWS, OAuth, .env, workflow secrets, authorization headers, Google API keys, Slack tokens, connection strings)
- AI Privacy Modes (Standard, Strict, Private)
- AIConsentManager for first-use consent dialog
- AIConversationMemory (minimized history, scoped conversations)
- AIContextCache (non-sensitive metadata cache, invalidated on repo change)
- AIProjectIndex (local file metadata, symbols, imports indexing)

### Added — AI Action System (PRD Sections 31-36, 85, 127-129)
- AIActionType enum (READ, ANALYZE, SUGGEST, GENERATE, MODIFY_LOCAL, COMMIT, PUSH, TRIGGER_WORKFLOW, CREATE_PR, MERGE_PR, CANCEL_WORKFLOW)
- AIPermissionPolicy (auto: READ/ANALYZE/SUGGEST/GENERATE; approval: MODIFY_LOCAL/COMMIT/PUSH/TRIGGER_WORKFLOW/MERGE)
- AIActionPlan (multi-step plans, approve individual actions)
- AIActionSandbox (AI → plan → policy → permission → approval → execute → verify)
- PatchSafety (base verification, concurrent change detection, dangerous pattern detection, diff preview)
- AIBranchStrategy (ai/fix-* branches, protected branch protection)
- AIApprovalManager (approval tied to exact plan hash, 30-min expiry)
- AISessionManager (1-hour session expiration, expired = no authorization)
- AIAgentScope (repository, branch, file, action, time, request limits)

### Added — AI Features (PRD Sections 27-43, 59-62, 131-132)
- AIChatUseCase (scoped conversations: Repository/Workflow/Job/File/General)
- AIBuildFailureAnalysisUseCase (root cause, evidence, confidence, affected files, recommended fix, side effects)
- AICodeReviewUseCase (critical issues, warnings, security, performance, maintainability, testing, suggestions — advisory)
- AIPatchGenerationUseCase (problem → context → AI patch → validation → diff → approval → apply → test → commit)
- AIPRGenerationUseCase (title, summary, changes, testing, risks — editable)
- AICommitGenerationUseCase (staged changes → commit message → user approval)
- AIWorkflowUseCase (analysis: slow steps, duplicates, missing cache, permissions, security; generation: YAML → validation → diff → approval)
- AIVisionUseCase (screenshot analysis, screenshot-to-Compose, UI structure)
- AIDocumentationUseCase (README, release notes, commit messages, PR descriptions, changelog)
- AITestGenerationUseCase (Kotlin unit tests, Compose UI tests, regression tests — proposed changes)
- AIClosedLoopUseCase (detect failure → AI analyze → fix → approve → patch → commit → push → trigger → verify)
- AI Self-Verification ("Patch generated, verification pending" — never claim "Fixed successfully" before verification)
- AI Regression Detection (identify if AI change caused new failure)

### Added — AI Security & Safety (PRD Sections 50, 57-58, 107, 118, 121-125)
- PromptInjectionDefense (SYSTEM/USER/REPO/LOG separation, injection pattern detection)
- AIHallucinationProtection (evidence requirements, file verification, fact vs inference distinction)
- AIOutputValidator (schema → safety → permission → action validation)
- ToolCallingSecurity (16 allowed tools with permission requirements, 9 forbidden operations)
- AIAuditLog (timestamp, task, provider, model, action, approval, execution — no secrets)
- AISecurityBoundaries (8 forbidden automatic actions)
- AIFailureTransparency ("Unable to determine" instead of fabricating)
- MockAIProvider for testing (no real paid API required)

### Added — AI UI (PRD Sections 79-84, 119-120)
- AI Assistant screen (conversation, context selector, suggested prompts, input, attachment, model/provider indicator)
- AI conversation UX (markdown, code blocks, file references, diff previews, action cards, streaming, retry, copy, regenerate)
- AI diff viewer (Apply/Reject/Regenerate)
- AI action confirmation UX (approve individual actions)
- Model/provider transparency indicator
- Fallback UX notification

### Added — AI Infrastructure (PRD Sections 46, 52-53, 65, 111-115)
- AIFeatureFlags (8 flags: ai_assistant, ai_code_review, ai_build_analysis, ai_patch_generation, ai_pr_generation, ai_workflow_analysis, ai_vision, ai_agent_actions)
- AIUsageLimits (per-user, token, daily/monthly budgets, timeout, max concurrent)
- AICostControls (daily/monthly budget, max context/output, provider priority, fallback priority)
- AIEmergencyControls (disable provider/model/actions/patch/external context without APK update)
- AIQualityMetrics (success rate, fallback rate, latency, patch acceptance, verification, provider failures — anonymized)
- AI streaming support
- AI structured output (structured JSON for actions — free-form text never directly executes)

### Added — CI
- ai-gateway-ci.yml — AI module specific CI pipeline (routing, fallback, context, secret redaction, action safety, security tests)

## [3.0.0] — 2025-08-25

### Added
- v4.0-v7.0 features: PR/Issue/Branch/Code management, CI Intelligence, AI Layer, Workspace, Releases, Team & Org, Developer Intelligence
- Apollo GraphQL, ETag interceptor, offline-first sync
- Workflow Visualizer, Terminal Log Inspector, YAML Workflow Editor
- GitHub repo files (SECURITY.md, CONTRIBUTING.md, CODE_OF_CONDUCT.md, PRIVACY.md)
- Secret Detection, Gitignore Intelligence, Project Preflight Validation
- Operation Center, Global Search, Dynamic Workflow Inputs

## [2.0.0] — 2025-08-25

### Added
- Complete GITOFY app — Material 3 design system, navigation, network layer
- Room database (10 entities, 10 DAOs), Secure ZIP extraction
- Feature screens, WorkManager workers, GitHub Actions CI/CD workflow
