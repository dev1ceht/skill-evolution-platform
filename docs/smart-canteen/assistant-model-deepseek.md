# DeepSeek model adapter

The backend now includes a feature-flagged `AssistantModelResolver` adapter for DeepSeek's
OpenAI-compatible Chat Completions API. The default model name is `deepseek-v4-flash` and the
default API base URL is `https://api.deepseek.com`.

Configure the backend process with deployment secrets; do not commit the API key:

```text
ASSISTANT_MODEL_ENABLED=true
ASSISTANT_MODEL_NAME=deepseek-v4-flash
ASSISTANT_MODEL_BASE_URL=https://api.deepseek.com
ASSISTANT_MODEL_MAX_REQUEST_CHARS=2000
ASSISTANT_MODEL_TIMEOUT_MS=10000
ASSISTANT_MODEL_MAX_RESPONSE_BYTES=65536
ASSISTANT_MODEL_ALLOWED_HOSTS=api.deepseek.com
ASSISTANT_MODEL_API_KEY=<secret>
```

The adapter requires HTTPS, rejects URLs with embedded credentials, allows only hosts listed in
`ASSISTANT_MODEL_ALLOWED_HOSTS`, and applies bounded connect/read timeouts and
response buffering. If an internal HTTPS proxy is used, add its host explicitly to the allowlist.

The model is a fallback classifier only. Deterministic rules run first, and the router accepts
only traceability queries, menu queries, clarifications, or unsupported results from the model.
Write intents, menu publishing, confirmations, and cancellations remain outside the model path.
The assistant HTTP rollout and the separate `agent.write` rollout must still be enabled and
scope-allowlisted independently.
