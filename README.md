# GITOFY — Production-Ready GitHub Repository, CI/CD & AI Developer Platform

A production-grade Android developer platform for managing GitHub repositories, Git operations, GitHub Actions CI/CD, build artifacts, and AI-assisted development workflows directly from a mobile device.

## Core Product Principles

1. GitHub remains the remote source of truth.
2. User approval is required for consequential AI actions.
3. Security takes priority over convenience.
4. AI providers must be replaceable.
5. Provider credentials must never be embedded in the Android APK.
6. The existing GITOFY codebase must be upgraded before considering unnecessary rewrites.
7. Long-running operations must survive process death.
8. Every failure must have an actionable recovery path.
9. No source project may be destructively modified without explicit user action.
10. AI must receive only the minimum context required for the requested task.
11. Secrets must be removed before AI context creation.
12. Free/low-cost AI models should be preferred when they satisfy the task.
13. GitHub Actions must remain the primary CI/CD platform.
14. Production releases must be validated by GitHub Actions.

## AI Architecture

The AI system is provider-agnostic. The Android application does NOT directly contain production AI provider secrets.

```
GITOFY Android → Authenticated AI Gateway → Provider Router → AI Provider
```

### AI Gateway
Responsible for: Provider authentication, Model routing, Rate limiting, Provider health, Context filtering, Secret redaction, Request/response normalization, Usage accounting, Fallback, Retry classification, AI policy enforcement.

### AI Providers
- **Gemini** — Advanced reasoning, large-context analysis, code generation, multimodal
- **NVIDIA NIM** — High-performance coding, reasoning, agentic workflows, self-hosted
- **OpenRouter** — Multi-model routing, provider redundancy, cost optimization, fallback
- **OpenCode Zen** — Coding tasks, agentic tasks, streaming
- **Custom Provider** — OpenAI-compatible endpoints, self-hosted, enterprise gateways

### AI Features
- Multi-provider routing with capability-based model selection
- Free-first cost policy (not free-only)
- Capability-compatible fallback system
- Provider health monitoring (AVAILABLE/DEGRADED/RATE_LIMITED/UNAVAILABLE)
- AI Context Engine with context budgeting, secret redaction (15+ secret types), privacy modes
- AI Chat (scoped: Repository/Workflow/Job/File/General)
- AI Build Failure Analysis (root cause, evidence, confidence, recommended fix)
- AI Code Review, AI Patch Generation, AI PR Generation, AI Commit Generation
- AI Workflow Analysis & Generation (YAML validation, security checks)
- AI Vision System (screenshot-to-Compose)
- AI + GitHub Actions Closed Loop (detect → analyze → fix → approve → push → verify)

### AI Security
- Prompt Injection Defense, AI Hallucination Protection
- AI Output Validation (schema/safety/permission/action)
- Tool Calling Security (allowed tools, forbidden operations)
- AI Audit Log, AI Action Sandbox, AI Permission Policy
- AI Branch Strategy (ai/fix-* branches, protected branch protection)
- AI Session & Approval Expiration
- Deterministic Validation Priority (compiler > AI, test result > AI confidence)
- AI Mock Architecture (all providers mockable)

## CI/CD

- `ci.yml` — PR pipeline (compile, test, lint, artifact)
- `security.yml` — Trivy + TruffleHog + dependency review
- `dependency-review.yml` — PR dependency review
- `release.yml` — Release pipeline (signed AAB, GitHub Release)
- `ai-gateway-ci.yml` — AI module specific CI (routing, fallback, security, action tests)

## License

MIT License — see [LICENSE](LICENSE)
