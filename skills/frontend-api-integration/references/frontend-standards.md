# Frontend integration standards

- Keep transport code separate from page presentation and local UI state.
- Reuse the repository's HTTP client before generating a raw `fetch` wrapper.
- Generate explicit TypeScript request and response types; avoid `any`.
- Represent optional contract fields as optional TypeScript fields.
- Handle loading, empty, error, retry, cancellation, and stale response behavior where relevant.
- Centralize query keys and invalidate only affected resources after mutations.
- Do not log credentials, tokens, request bodies containing secrets, or personal information.
- Place generated code in the repository's existing folder convention and formatter pipeline.

