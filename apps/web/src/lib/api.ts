export type QueryParams = Record<string, string | number | null | undefined>;

/**
 * Absent and blank mean the same thing to this API, so both are dropped rather
 * than sent as an empty parameter. Zero is a value — it is page one.
 */
export function queryString(params: QueryParams): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    search.set(key, String(value));
  }
  const query = search.toString();
  return query === '' ? '' : `?${query}`;
}

/**
 * Three outcomes, because they mean three different things and a caller should
 * have to say which it is handling.
 *
 * `body` is a response carrying JSON, whatever its status: Actuator health and
 * problem+json rejections both put the answer in the body of a non-2xx.
 * `invalid-body` is a response that arrived and was not JSON — an HTML error page
 * from a proxy, or an empty body. `unreachable` is a `fetch` that never produced a
 * response at all.
 */
export type JsonResponse<T> =
  | { kind: 'body'; ok: boolean; status: number; data: T }
  | { kind: 'invalid-body'; ok: boolean; status: number; message: string }
  | { kind: 'unreachable'; message: string };

/** Deliberately not `RequestInit`: `getJson` owns its `Accept` header, and a
 * caller supplying one would silently replace it. Cancellation is the only thing
 * a caller varies. A request with a method and a body is a different helper. */
export interface GetJsonOptions {
  signal?: AbortSignal;
}

export async function getJson<T>(
  path: string,
  options: GetJsonOptions = {},
): Promise<JsonResponse<T>> {
  let response: Response;
  try {
    response = await fetch(path, {
      headers: { Accept: 'application/json' },
      signal: options.signal,
    });
  } catch (error: unknown) {
    // An aborted request lands here too. `getJson` cannot know whether the abort
    // was deliberate, so it does not try to; the caller checks its own signal.
    return { kind: 'unreachable', message: messageOf(error) };
  }

  try {
    const data = (await response.json()) as T;
    return { kind: 'body', ok: response.ok, status: response.status, data };
  } catch (error: unknown) {
    return {
      kind: 'invalid-body',
      ok: response.ok,
      status: response.status,
      message: messageOf(error),
    };
  }
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
