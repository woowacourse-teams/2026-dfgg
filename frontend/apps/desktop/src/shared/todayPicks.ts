import type { TodayPick } from './types';

/**
 * 오늘의 픽 하나를 가리키는 값. 같은 선수가 다른 챔피언으로 두 번 오기도 해서 이름만으로는 겹친다.
 * 한 응답 안에서 챔피언은 겹치지 않으므로 선수 + 챔피언이면 하나로 정해진다.
 */
export const todayPickKey = (pick: TodayPick): string => `${pick.player.name}:${pick.champion.id}`;
