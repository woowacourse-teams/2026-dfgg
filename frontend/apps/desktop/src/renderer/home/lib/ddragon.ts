import * as Sentry from '@sentry/electron/renderer';
import { useEffect, useState } from 'react';

// 아이템은 CommunityDragon 에 숫자 id 경로가 없어 DDragon 을 쓴다. 그래서 패치 버전이 필요하다.
export const itemIconUrl = (version: string, itemId: number): string =>
  `https://ddragon.leagueoflegends.com/cdn/${version}/img/item/${itemId}.png`;

// 챔피언은 숫자 id 로 바로 받을 수 있어 key → id 매핑표가 필요 없다.
export const championIconUrl = (championId: number): string =>
  `https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/${championId}.png`;

/** 얼굴 중심으로 잘라 둔 정사각 일러스트. 전적 카드처럼 작은 칸에 쓴다. */
export const championTileUrl = (championId: number): string =>
  `https://cdn.communitydragon.org/latest/champion/${championId}/tile`;

/** 얼굴이 가운데 오는 가로 일러스트. 프로필 배너에 쓴다. */
export const championSplashUrl = (championId: number): string =>
  `https://cdn.communitydragon.org/latest/champion/${championId}/splash-art/centered`;

const SKIN_ID_BASE = 1000;

/** 스킨 id(챔피언 id * 1000 + 스킨 번호)로 그 스킨의 가로 일러스트를 받는다. */
export const skinSplashUrl = (skinId: number): string =>
  `https://cdn.communitydragon.org/latest/champion/${Math.floor(skinId / SKIN_ID_BASE)}/splash-art/centered/skin/${skinId % SKIN_ID_BASE}`;

/** DDragon 은 경로에 패치 버전이 들어간다. 목록의 첫 항목이 최신이다. */
export function useDdragonVersion(): string | null {
  const [version, setVersion] = useState<string | null>(null);

  useEffect(() => {
    fetch('https://ddragon.leagueoflegends.com/api/versions.json')
      .then((response) => response.json())
      .then((versions: string[]) => setVersion(versions[0]))
      .catch((error) => {
        console.error('DDragon 버전을 불러오지 못했습니다.');
        Sentry.captureException(error);
      });
  }, []);

  return version;
}
