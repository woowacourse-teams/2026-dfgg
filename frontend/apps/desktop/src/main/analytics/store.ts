import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import type { EventData } from './umami';
import { reportError } from '../sentry';

export type PendingEvent = { name: string; data?: EventData; id?: string };

type AnalyticsStore = {
  installed?: boolean;
  // 보내지 못한 이벤트. 다음 실행 때 다시 보낸다.
  outbox: PendingEvent[];
  // 진행 중인 세션/게임 스냅샷. 비정상 종료되면 다음 실행 때 outbox로 옮긴다.
  openSession?: PendingEvent;
  openGame?: PendingEvent;
};

const MAX_OUTBOX = 50;

let cache: AnalyticsStore | null = null;

const storePath = () => path.join(app.getPath('userData'), 'analytics.json');

export function readStore(): AnalyticsStore {
  if (cache) return cache;

  try {
    cache = { outbox: [], ...JSON.parse(fs.readFileSync(storePath(), 'utf8')) };
  } catch (error) {
    reportError('analytics-store-read', error);
    cache = { outbox: [] };
  }
  return cache as AnalyticsStore;
}

export function updateStore(patch: Partial<AnalyticsStore>) {
  const next = { ...readStore(), ...patch };
  next.outbox = next.outbox.slice(-MAX_OUTBOX);
  cache = next;

  try {
    fs.writeFileSync(storePath(), JSON.stringify(next));
  } catch (error) {
    console.debug('analytics 저장 실패', error);
    reportError('analytics-store-write', error);
  }
}
