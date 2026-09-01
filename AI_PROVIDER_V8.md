# GITOFY v8 — Production AI Provider Upgrade

- Replaced fake AI provider responses with real BYOK HTTPS transports.
- Gito AI now uses `AIGateway.processStream()` instead of direct provider HTTP calls.
- OpenAI-compatible adapters: OpenAI, NVIDIA NIM, OpenRouter, OpenCode Zen.
- Dedicated Gemini `generateContent` / `streamGenerateContent` adapter.
- Dedicated Sarvam v1/v2 adapter using `api-subscription-key` and SSE streaming.
- Custom provider uses the configured OpenAI-compatible endpoint and model.
- Dedicated AI OkHttp client has no GitHub authentication interceptors.
- Exact selected provider/model is respected by the router.
- Provider health and streaming usage are recorded by the gateway.
- Real provider errors are normalized instead of being rendered as successful responses.
- Request cancellation propagates through coroutine cancellation to OkHttp/native IO.
- API keys remain in Keystore-backed encrypted storage.
