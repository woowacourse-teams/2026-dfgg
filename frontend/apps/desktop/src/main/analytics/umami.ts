import { app } from 'electron';
import { getIdentity } from './identity';
import { readStore, updateStore, type PendingEvent } from './store';
import { reportError } from '../sentry';

const UMAMI_ENDPOINT = 'https://cloud.umami.is/api/send';
// web과 같은 사이트를 쓰고, hostname으로 데스크탑 트래픽을 구분한다.
const UMAMI_WEBSITE_ID = '93de5e12-4372-4120-82c0-deab98b3f33a';
const UMAMI_HOSTNAME = 'desktop.dfgg.pro';
const UMAMI_TIMEOUT_MS = 5000;

export type EventData = Record<string, string | number | boolean | undefined>;

async function send({ name, data, id }: PendingEvent) {
  const response = await fetch(UMAMI_ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      // 우마미는 User-Agent가 없거나 봇처럼 보이면 요청을 버린다.
      'User-Agent': app.userAgentFallback,
    },
    body: JSON.stringify({
      type: 'event',
      payload: {
        website: UMAMI_WEBSITE_ID,
        hostname: UMAMI_HOSTNAME,
        url: '/desktop',
        language: app.isReady() ? app.getLocale() : undefined,
        // 소환사(puuid) 단위로 세션을 묶는다.
        id,
        name,
        data: { version: app.getVersion(), ...data },
      },
    }),
    signal: AbortSignal.timeout(UMAMI_TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`umami ${response.status}`);
}

// 보내지 못한 이벤트는 다음 실행 때 보낸다.
export function enqueue(event: PendingEvent) {
  updateStore({ outbox: [...readStore().outbox, event] });
}

// 메인 프로세스에서 우마미 이벤트 보내기. 실패해도 앱 동작에는 영향을 주지 않는다.
export function trackEvent(name: string, data?: EventData, id = getIdentity()?.puuid) {
  const event = { name, data, id };

  // 개발 중 트래픽은 집계하지 않는다.
  if (!app.isPackaged) {
    console.debug('umami 이벤트(개발 모드라 전송 안 함)', event);
    return;
  }

  send(event).catch((error) => {
    console.debug('umami 이벤트 전송 실패 — 다음 실행 때 재전송', name, error);
    reportError('umami-send', error);
    enqueue(event);
  });
}

// 앱 시작 시 쌓여 있던 이벤트 보내기
export async function flushOutbox() {
  if (!app.isPackaged) return;

  const { outbox } = readStore();
  if (outbox.length === 0) return;
  updateStore({ outbox: [] });

  for (const event of outbox) {
    try {
      await send(event);
    } catch (error) {
      reportError('umami-flush', error);
      enqueue(event);
    }
  }
}
