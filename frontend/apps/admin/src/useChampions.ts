import { useEffect, useState } from 'react';

import { type ChampionInfo, loadDDragon } from '../../../packages/shared/ddragon';

/**
 * 이벤트에는 챔피언이 Riot 영문 키(예: MonkeyKing)로 들어 있다.
 * Data Dragon 으로 한글명과 아이콘을 붙인다. 못 불러오면 영문 키만 보여준다.
 */
export function useChampions() {
  const [byKey, setByKey] = useState<Map<string, ChampionInfo>>(new Map());

  useEffect(() => {
    const controller = new AbortController();
    loadDDragon(controller.signal)
      .then(({ byChampionId }) => {
        setByKey(new Map([...byChampionId.values()].map((info) => [info.riotKey, info])));
      })
      .catch(() => {});
    return () => controller.abort();
  }, []);

  return byKey;
}
