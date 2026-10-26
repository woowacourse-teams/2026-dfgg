import { useEffect, useState } from 'react';

const CDRAGON_DATA =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default';

export const championIcon = (id: number): string => `${CDRAGON_DATA}/v1/champion-icons/${id}.png`;

export const championSplash = (id: number): string =>
  `https://cdn.communitydragon.org/latest/champion/${id}/splash-art/centered`;

export const championTile = (id: number): string =>
  `https://cdn.communitydragon.org/latest/champion/${id}/tile`;

type Entry = { id: number; iconPath: string };

// '/lol-game-data/assets/v1/...' 는 CommunityDragon 에서 소문자 경로로 열린다.
const toUrl = (iconPath: string) =>
  `${CDRAGON_DATA}${iconPath.toLowerCase().replace('/lol-game-data/assets', '')}`;

/** CommunityDragon 목록 하나를 받아 id → 그림 주소로 찾는다. 백엔드가 imageUrl 을 주는 자리를 시안에서 대신한다. */
function useCdragonIcons(file: string, pick: (body: unknown) => Entry[]) {
  const [icons, setIcons] = useState<Map<number, string>>(new Map());

  useEffect(() => {
    fetch(`${CDRAGON_DATA}/v1/${file}`)
      .then((response) => response.json())
      .then((body) =>
        setIcons(new Map(pick(body).map(({ id, iconPath }) => [id, toUrl(iconPath)]))),
      )
      .catch(() => setIcons(new Map()));
    // pick 은 모듈 상수만 넘기므로 의존성에서 뺀다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [file]);

  // 받기 전에는 빈 주소 대신 undefined 를 돌려 이미지가 페이지를 다시 요청하지 않게 한다.
  return (id: number) => icons.get(id);
}

const asList = (body: unknown) => body as Entry[];
const asStyles = (body: unknown) => (body as { styles: Entry[] }).styles;

export type IconOf = (id: number) => string | undefined;

export type Icons = { item: IconOf; rune: IconOf; tree: IconOf; spell: IconOf };

export function useIcons(): Icons {
  return {
    item: useCdragonIcons('items.json', asList),
    rune: useCdragonIcons('perks.json', asList),
    tree: useCdragonIcons('perkstyles.json', asStyles),
    spell: useCdragonIcons('summoner-spells.json', asList),
  };
}
