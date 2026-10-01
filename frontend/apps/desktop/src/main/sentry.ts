import { app } from 'electron';
import * as Sentry from '@sentry/electron/main';

const SENTRY_DSN =
  'https://1f8b68803ea7d169f619192a0a33f4fc@o4512044427968512.ingest.us.sentry.io/4512179706200064';

// 개발 중 에러는 보내지 않는다. 개발 환경에서 연동을 확인할 때만 SENTRY_DEBUG=1 로 켠다.
Sentry.init({
  dsn: SENTRY_DSN,
  enabled: app.isPackaged || process.env.SENTRY_DEBUG === '1',
});

// 롤이 꺼져 있거나 파일이 아직 없는 건 정상 흐름이라 보내지 않는다.
const EXPECTED_ERROR_CODES = new Set(['ENOENT', 'ECONNREFUSED']);
const reported = new Set<string>();

function errorCode(error: unknown) {
  if (!(error instanceof Error)) return undefined;
  const cause = error.cause instanceof Error ? error.cause : undefined;
  return (
    (error as NodeJS.ErrnoException).code ?? (cause as NodeJS.ErrnoException | undefined)?.code
  );
}

/**
 * catch 로 삼킨 에러를 Sentry 로 보낸다.
 * 폴링·재시도에서 같은 에러가 반복돼 할당량을 태우지 않도록, 실행당 같은 (context, message) 는 한 번만 보낸다.
 */
export function reportError(context: string, error: unknown) {
  const code = errorCode(error);
  if (code && EXPECTED_ERROR_CODES.has(code)) return;

  const key = `${context}:${error instanceof Error ? error.message : String(error)}`;
  if (reported.has(key)) return;
  reported.add(key);

  Sentry.captureException(error, { tags: { context } });
}
