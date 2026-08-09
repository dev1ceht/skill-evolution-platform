# API IR

Normalize every operation to these fields:

```json
{
  "operationId": "listUsers",
  "method": "GET",
  "path": "/api/users",
  "tags": ["users"],
  "parameters": [],
  "requestBody": null,
  "responses": {},
  "provenance": {
    "documentHash": "sha256 hex",
    "pointer": "/paths/~1api~1users/get"
  }
}
```

Preserve JSON Pointer escaping: replace `~` with `~0` and `/` with `~1`. Sort paths and use a stable HTTP method order so re-running normalization creates comparable output. Retain unknown schemas instead of guessing their frontend representation.

