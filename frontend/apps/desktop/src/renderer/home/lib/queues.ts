import type { MatchSummary } from '../../../shared/types';

const SOLO_QUEUE_ID = 420;
const FLEX_QUEUE_ID = 440;
const ARAM_QUEUE_ID = 450;

export type QueueFilter = 'all' | 'solo' | 'flex' | 'aram' | 'etc';

const QUEUE_BY_ID: Record<number, QueueFilter> = {
  [SOLO_QUEUE_ID]: 'solo',
  [FLEX_QUEUE_ID]: 'flex',
  [ARAM_QUEUE_ID]: 'aram',
};

/** 탭에 보이는 순서대로. */
export const QUEUE_FILTERS: { key: QueueFilter; label: string }[] = [
  { key: 'all', label: '전체' },
  { key: 'solo', label: '솔로랭크' },
  { key: 'flex', label: '자유랭크' },
  { key: 'aram', label: '칼바람' },
  { key: 'etc', label: '기타' },
];

export const queueOf = (match: MatchSummary): QueueFilter => QUEUE_BY_ID[match.queueId] ?? 'etc';

export const filterMatches = (matches: MatchSummary[], filter: QueueFilter): MatchSummary[] =>
  filter === 'all' ? matches : matches.filter((match) => queueOf(match) === filter);
