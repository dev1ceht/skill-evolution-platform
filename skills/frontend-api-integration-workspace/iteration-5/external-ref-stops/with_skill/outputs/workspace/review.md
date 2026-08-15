## external reference

`getResource` (`GET /api/v1/resources/{resourceId}`) has two unresolved external references: `./common.yaml#/components/parameters/ResourceId` and `./schemas.yaml#/components/schemas/ResourceResponse`. The first is the definition of the path parameter; the second is the success schema for the `200` `application/json` response. Their contents cannot be inferred safely from the reference names, so neither the parameter nor the success schema may be generated. After both files are supplied, resolve these exact JSON Pointers and verify that both targets exist.

## impact

Without `./common.yaml#/components/parameters/ResourceId`, the parameter wire name, location, required status, schema, and serialization details are unknown; guessing them could produce an incorrect request. Without `./schemas.yaml#/components/schemas/ResourceResponse`, the response fields, types, required/nullability rules, and any envelope shape are unknown; guessing them could produce an incorrect success type. Once the files are present, validate reference resolution and normalize the operation only after the parameter and `200` success schema are both concrete.

## required confirmation

Please provide `./common.yaml` with `components.parameters.ResourceId` and confirm that it is the path parameter required by `{resourceId}`. Please also provide `./schemas.yaml` with `components.schemas.ResourceResponse` and confirm the intended `200` response shape. These confirmations are required because the contract does not safely establish the parameter metadata or success payload beyond those external paths. Afterward, verify path-parameter consistency, the `application/json` response schema, and that no unresolved external `$ref` remains.

## next step

Add the two missing files without changing the referenced paths. Then resolve `./common.yaml#/components/parameters/ResourceId` and `./schemas.yaml#/components/schemas/ResourceResponse`, run the frontend-api-integration normalization checks, and review the resulting parameter and success-schema provenance. Only after those validations pass may `api-ir.json`, `client.ts`, or `integration-plan.json` be generated; no such artifacts are generated for this review.
