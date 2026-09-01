# Contributing to GITOFY

## Branch Strategy

- `main` — production-ready, protected
- `develop` — integration branch
- `feature/*` — new features
- `fix/*` — bug fixes
- `hotfix/*` — critical production fixes
- `release/*` — release preparation

## Rules

- Direct pushes to `main` are disabled
- Pull requests are required
- CI must pass before merge
- At least one review is required
- Use Semantic Versioning for releases

## Commit Conventions

Use clear, descriptive commit messages:

```
feat: add repository search
fix: resolve ZIP extraction crash on large files
security: redact token in network logs
docs: update README with build instructions
```

## Code Style

- Kotlin idioms
- Coroutines with structured concurrency
- Immutable UI state
- No business logic in Composables
- No direct API/database calls from UI
- Small, focused classes

## Testing

- Unit tests for use cases, mappers, validators, error mapping
- Integration tests for API, Room, JGit, WorkManager
- UI tests for critical flows
- All tests must pass before merge

## Pull Request Process

1. Create a feature branch from `develop`
2. Implement changes with tests
3. Ensure CI passes
4. Request review
5. Address feedback
6. Squash and merge
