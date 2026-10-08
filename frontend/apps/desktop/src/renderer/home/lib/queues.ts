import type { MatchSummary } from '../../../shared/types';

const SOLO_QUEUE_ID = 420;
const FLEX_QUEUE_ID = 440;
// 일반: 교차 선택(400), 비공개 선택(430), 신속 대전(480), 빠른 대전(490)
const NORMAL_QUEUE_IDS = [400, 430, 480, 490];
// 칼바람: 무작위 총력전(450), 아수라장(2400)
const ARAM_QUEUE_IDS = [450, 2400];

export type QueueFilter = 'all' | 'solo' | 'flex' | 'normal' | 'aram' | 'etc';

const QUEUE_BY_ID: Record<number, QueueFilter> = {
  [SOLO_QUEUE_ID]: 'solo',
  [FLEX_QUEUE_ID]: 'flex',
  ...Object.fromEntries(NORMAL_QUEUE_IDS.map((id) => [id, 'normal'])),
  ...Object.fromEntries(ARAM_QUEUE_IDS.map((id) => [id, 'aram'])),
};

/** 탭에 보이는 순서대로. */
export const QUEUE_FILTERS: { key: QueueFilter; label: string }[] = [
  { key: 'all', label: '전체' },
  { key: 'solo', label: '솔로랭크' },
  { key: 'flex', label: '자유랭크' },
  { key: 'normal', label: '일반' },
  { key: 'aram', label: '칼바람' },
  { key: 'etc', label: '기타' },
];

export const queueOf = (match: MatchSummary): QueueFilter => QUEUE_BY_ID[match.queueId] ?? 'etc';

export const filterMatches = (matches: MatchSummary[], filter: QueueFilter): MatchSummary[] =>
  filter === 'all' ? matches : matches.filter((match) => queueOf(match) === filter);
