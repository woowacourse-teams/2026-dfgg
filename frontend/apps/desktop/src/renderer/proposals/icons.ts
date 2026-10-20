import { useEffect, useState } from 'react';

const CDRAGON_DATA =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default';

export const championIcon = (id: number): string => `${CDRAGON_DATA}/v1/champion-icons/${id}.png`;

export const championTile = (id: number): string =>
  `https://cdn.communitydragon.org/latest/champion/${id}/tile`;

type CdragonItem = { id: number; iconPath: string };

/** 아이템 그림 주소. DDragon 이 불안정해서 CommunityDragon 의 아이템 목록에서 찾는다. */
export function useItemIcons(): (id: number) => string | undefined {
  const [icons, setIcons] = useState<Map<number, string>>(new Map());

  useEffect(() => {
    fetch(`${CDRAGON_DATA}/v1/items.json`)
      .then((response) => response.json())
      .then((items: CdragonItem[]) =>
        setIcons(
          new Map(
            items.map(({ id, iconPath }) => [
              id,
              `${CDRAGON_DATA}${iconPath.toLowerCase().replace('/lol-game-data/assets', '')}`,
            ]),
          ),
        ),
      )
      .catch(() => setIcons(new Map()));
  }, []);

  // 목록을 받기 전에는 빈 주소 대신 undefined 를 돌려 이미지가 페이지를 다시 요청하지 않게 한다.
  return (id) => icons.get(id);
}
