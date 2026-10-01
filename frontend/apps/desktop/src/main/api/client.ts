import { app } from 'electron';
import { getApiBaseUrl } from './config';

const API_TIMEOUT_MS = 5000;

export async function apiPost<T>(
  path: string,
  body: unknown,
  timeoutMs = API_TIMEOUT_MS,
): Promise<T> {
  const baseUrl = getApiBaseUrl();

  try {
    const response = await fetch(`${baseUrl}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept-Language': app.getLocale(),
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`... ${response.status}: ${text.slice(0, 200)}`);
    }
    const result = (await response.json()) as T;
    return result;
  } catch (error) {
    console.error(`POST ${path} 실패`, error);
    throw error;
  }
}
