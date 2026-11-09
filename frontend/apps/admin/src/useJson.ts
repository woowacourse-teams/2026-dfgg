import { useEffect, useState } from 'react';

/**
 * 로컬 관리자 API(GET)를 부른다. reloadKey 가 바뀌면 같은 url 도 다시 부른다.
 * 새 응답이 올 때까지 이전 데이터를 그대로 보여준다.
 */
export function useJson<T>(url: string, reloadKey: number) {
  const requestKey = `${url}#${reloadKey}`;
  const [data, setData] = useState<T | null>(null);
  const [result, setResult] = useState<{ key: string; error: string | null } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetch(url, { signal: controller.signal })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.error ?? `${url} ${response.status}`);
        setData(body as T);
        setResult({ key: requestKey, error: null });
      })
      .catch((e: Error) => {
        if (e.name !== 'AbortError') setResult({ key: requestKey, error: e.message });
      });
    return () => controller.abort();
  }, [url, requestKey]);

  return { data, loading: result?.key !== requestKey, error: result?.error ?? null };
}
