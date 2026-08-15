// Generated from API IR. Review before production use.

export type ResourcePatch = {
  name: string;
};

export type Resource = {
  id: string;
  name: string;
};

export type ResourceResponse = {
  code: number;
  message: string;
  data: Resource;
};

export async function updateResource(resourceId: string, idempotencyKey: string, body: ResourcePatch): Promise<ResourceResponse> {
  const encodedResourceId = encodeURIComponent(String(resourceId));
  let path = "/api/v1/resources/{resource-id}";
  path = path.replace("{resource-id}", encodedResourceId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Idempotency-Key"] = String(idempotencyKey);
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ResourceResponse>;
}
