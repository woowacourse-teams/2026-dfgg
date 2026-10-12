import { randomUUID } from 'node:crypto';
import { app } from 'electron';
import { getIdentity } from './identity';
import { AnalyticsTarget, readStore, updateStore, type PendingEvent } from './store';
import { reportError } from '../sentry';

const TIMEOUT_MS = 5000;
const ALL_TARGETS: AnalyticsTarget[] = ['umami', 'posthog'];

const UMAMI_ENDPOINT = 'https://cloud.umami.is/api/send';
// web과 같은 사이트를 쓰고, hostname으로 데스크탑 트래픽을 구분한다.
const UMAMI_WEBSITE_ID = '93de5e12-4372-4120-82c0-deab98b3f33a';
const UMAMI_HOSTNAME = 'desktop.dfgg.pro';

const POSTHOG_ENDPOINT = 'https://us.i.posthog.com/i/v0/e/';
const POSTHOG_KEY = 'phc_xCWU3NRFsUo8ebg8T25PFRSuu75Q9HQzdcqDNXpxHUir';

export type EventData = Record<string, string | number | boolean | undefined>;

// 소환사를 알기 전(앱 실행 직후)에도 같은 기기로 묶이게 설치 단위 id 를 둔다.
function installId() {
  const saved = readStore().installId;
  if (saved) return saved;
  const id = randomUUID();
  updateStore({ installId: id });
  return id;
}

async function sendUmami({ name, data, id }: PendingEvent) {
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
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`umami ${response.status}`);
}

async function sendPosthog({ name, data, id, timestamp }: PendingEvent) {
  const response = await fetch(POSTHOG_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      api_key: POSTHOG_KEY,
      event: name,
      // 소환사를 알면 puuid, 모르면 설치 id
      distinct_id: id ?? installId(),
      timestamp,
      properties: {
        platform: 'desktop',
        version: app.getVersion(),
        install_id: installId(),
        $lib: 'dfgg-desktop',
        $os: 'Windows',
        language: app.isReady() ? app.getLocale() : undefined,
        ...data,
      },
    }),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!response.ok) throw new Error(`posthog ${response.status}`);
}

const SENDERS: Record<AnalyticsTarget, (event: PendingEvent) => Promise<void>> = {
  umami: sendUmami,
  posthog: sendPosthog,
};

// 남은 곳에 보내고, 실패한 곳만 돌려준다.
async function send(event: PendingEvent): Promise<AnalyticsTarget[]> {
  const targets = event.targets ?? ALL_TARGETS;
  const results = await Promise.allSettled(targets.map((target) => SENDERS[target](event)));
  return targets.filter((_, index) => results[index].status === 'rejected');
}

// 보내지 못한 이벤트는 다음 실행 때 보낸다.
export function enqueue(event: PendingEvent) {
  updateStore({ outbox: [...readStore().outbox, event] });
}

// 메인 프로세스에서 이벤트 보내기. 실패해도 앱 동작에는 영향을 주지 않는다.
export function trackEvent(name: string, data?: EventData, id = getIdentity()?.puuid) {
  const event: PendingEvent = { name, data, id, timestamp: new Date().toISOString() };

  // 개발 중 트래픽은 집계하지 않는다.
  if (!app.isPackaged) {
    console.debug('analytics 이벤트(개발 모드라 전송 안 함)', event);
    return;
  }

  send(event).then((failed) => {
    if (failed.length > 0) {
      console.debug('analytics 전송 실패 — 다음 실행 때 재전송', name, failed);
      reportError('analytics-send', failed);
      enqueue({ ...event, targets: failed });
    }
  });
}

// 앱 시작 시 쌓여 있던 이벤트 보내기
export async function flushOutbox() {
  if (!app.isPackaged) return;

  const { outbox } = readStore();
  if (outbox.length === 0) return;
  updateStore({ outbox: [] });

  for (const event of outbox) {
    const failed = await send(event);
    if (failed.length > 0) {
      reportError('analytics-flush', failed);
      enqueue({ ...event, targets: failed });
    }
  }
}
