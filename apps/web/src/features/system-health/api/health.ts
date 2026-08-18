import { getJson } from '../../../lib/api';

export interface BackendHealth {
  status: string;
  components?: Record<string, { status: string }>;
}

/**
 * Actuator answers 503 with a well-formed body when a component is DOWN,
 * so the body is used whether or not the status is 2xx.
 */
export async function fetchBackendHealth(): Promise<BackendHealth> {
  const response = await getJson<BackendHealth>('/actuator/health');
  return response.data;
}
