# external reference

The contract contains two unresolved external references under `GET /api/v1/resources/{resourceId}`:

- `/paths/~1api~1v1~1resources~1{resourceId}/get/parameters/0/$ref` points to `./common.yaml#/components/parameters/ResourceId` (expected relative file: `contracts/common.yaml`).
- `/paths/~1api~1v1~1resources~1{resourceId}/get/responses/200/content/application~1json/schema/$ref` points to `./schemas.yaml#/components/schemas/ResourceResponse` (expected relative file: `contracts/schemas.yaml`).

Both target files are missing, so neither reference can be resolved from this contract. After the files are supplied, resolve both exact references and confirm that the referenced component paths exist before continuing.

# impact

The missing `ResourceId` parameter definition means the client cannot safely generate or validate the request parameter: the path text alone does not establish the component's authoritative parameter name, schema type, required flag, or serialization behavior. The missing `ResourceResponse` definition means the `200` `application/json` success schema and its typed response cannot safely be generated; its properties, requiredness, nullability, nested schemas, and additional-property behavior are unknown. After the files are supplied, rerun reference resolution and confirm the parameter and success schema normalize without unresolved references.

# required confirmation

Please provide `contracts/common.yaml` with `#/components/parameters/ResourceId` and `contracts/schemas.yaml` with `#/components/schemas/ResourceResponse`, preserving the exact component names referenced above. Do not infer either definition from `/api/v1/resources/{resourceId}` or from the response description. Once provided, verify that `ResourceId` is a valid path parameter for `resourceId` and that `ResourceResponse` is the intended `200` success schema before generating any typed parameter or success-response model.

# next step

Stop processing this contract until both external files are available. The next verification must resolve the two `$ref` values, validate the `GET /api/v1/resources/{resourceId}` parameter and `200` success schema, and only then continue with any normalization or integration artifacts. No safe success schema or parameter can be produced from the current contract alone.
