import { getApiBaseUrl } from './config';
import { mainLocale } from '../locale';
import { reportError } from '../sentry';

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
        // UI 언어와 같은 기준으로 보내 추천 내용과 화면 언어가 어긋나지 않게 한다.
        'Accept-Language': mainLocale(),
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
    reportError(`api-post ${path}`, error);
    throw error;
  }
}
