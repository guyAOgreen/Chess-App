export interface ApiResponse<T> {
  ok: boolean;
  status: number;
  data: T;
}

/**
 * Reads the JSON body regardless of status, because some endpoints
 * (Actuator health, validation failures) carry meaning in the body of a
 * non-2xx response. Callers decide what a given status means.
 */
export async function getJson<T>(path: string): Promise<ApiResponse<T>> {
  const response = await fetch(path, { headers: { Accept: 'application/json' } });
  const data = (await response.json()) as T;
  return { ok: response.ok, status: response.status, data };
}
