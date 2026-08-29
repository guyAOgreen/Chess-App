import { getJson } from '../../../lib/api';

export interface BackendHealth {
  status: string;
  components?: Record<string, { status: string }>;
}

/**
 * Actuator answers 503 with a well-formed body when a component is DOWN, so the
 * body is used whether or not the status is 2xx. Anything that is not a body at
 * all is thrown, and `useBackendHealth`'s catch turns it into `unreachable`.
 */
export async function fetchBackendHealth(): Promise<BackendHealth> {
  const response = await getJson<BackendHealth>('/actuator/health');
  if (response.kind !== 'body') {
    throw new Error(response.message);
  }
  return response.data;
}
