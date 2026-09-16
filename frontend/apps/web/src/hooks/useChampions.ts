import { useEffect, useState } from 'react';

import { DDRAGON_LOCALE, SORT_LOCALE } from '../../../../packages/i18n/lang';
import { useLang } from '../../../../packages/i18n/useLang';

const DDRAGON = 'https://ddragon.leagueoflegends.com';

const HANGUL_BASE = 0xac00;
const HANGUL_LAST = 0xd7a3;
const JUNG_JONG_COUNT = 21 * 28;
const CHOSUNG = [
  'ㄱ',
  'ㄲ',
  'ㄴ',
  'ㄷ',
  'ㄸ',
  'ㄹ',
  'ㅁ',
  'ㅂ',
  'ㅃ',
  'ㅅ',
  'ㅆ',
  'ㅇ',
  'ㅈ',
  'ㅉ',
  'ㅊ',
  'ㅋ',
  'ㅌ',
  'ㅍ',
  'ㅎ',
];

/** "아리" → "ㅇㄹ". 한글이 아닌 글자는 그대로 둔다. */
export function toChosung(text: string): string {
  let result = '';
  for (const char of text) {
    const code = char.charCodeAt(0);
    if (code >= HANGUL_BASE && code <= HANGUL_LAST) {
      result += CHOSUNG[Math.floor((code - HANGUL_BASE) / JUNG_JONG_COUNT)];
    } else {
      result += char;
    }
  }
  return result;
}

/** 입력이 초성으로만 이뤄졌는지. "ㅇㄹ"은 true, "아리"는 false. */
export function isChosungOnly(text: string): boolean {
  return text.length > 0 && [...text].every((char) => CHOSUNG.indexOf(char) !== -1);
}

export interface ChampionInfo {
  /** Data Dragon 고유 키. 이미지 경로에 쓰인다. (예: MonkeyKing) */
  id: string;
  /** 화면에 보여줄 이름. 선택한 언어를 따른다. (예: 손오공 / Wukong) */
  name: string;
  imageUrl: string;
  /** 표시명 + 영문 id를 소문자로 합친 검색 키 */
  searchKey: string;
  /** 표시명의 초성. "아리" → "ㅇㄹ". 영어 이름이면 그냥 그 이름이 된다. */
  chosung: string;
}

interface DDragonChampion {
  id: string;
  name: string;
}

/**
 * 아이템 아이콘 URL. 버전은 useChampions가 받아온 값을 그대로 넘긴다.
 * 아이템은 id만 알면 URL이 조립되므로 760KB짜리 item.json은 받지 않는다.
 */
export function itemImageUrl(version: string, id: number): string {
  return `${DDRAGON}/cdn/${version}/img/item/${id}.png`;
}

/**
 * Data Dragon 챔피언 목록을 받아온다. 언어를 바꾸면 그 언어로 다시 받는다.
 * 버전은 하드코딩하지 않고 versions.json의 최신값을 쓴다.
 */
export function useChampions() {
  const { lang } = useLang();
  const [champions, setChampions] = useState<ChampionInfo[]>([]);
  // 아이템 아이콘 URL을 만들 때도 같은 버전을 써야 해서 밖으로 내보낸다.
  const [version, setVersion] = useState('');
  const [failed, setFailed] = useState(false);
  // 라인별 후보 목록(Champion.ts)이 한글명으로 적혀 있어서, 영어 화면에서도
  // 그 목록을 쓰려면 한글명 → riotKey 다리가 필요하다.
  const [koreanNameToId, setKoreanNameToId] = useState<Record<string, string>>({});

  useEffect(() => {
    const controller = new AbortController();

    const load = async () => {
      try {
        const versionRes = await fetch(`${DDRAGON}/api/versions.json`, {
          signal: controller.signal,
        });
        if (!versionRes.ok) throw new Error(String(versionRes.status));
        const [latestVersion]: string[] = await versionRes.json();

        const [listRes, koreanRes] = await Promise.all([
          fetch(`${DDRAGON}/cdn/${latestVersion}/data/${DDRAGON_LOCALE[lang]}/champion.json`, {
            signal: controller.signal,
          }),
          // 화면이 한국어면 같은 응답을 두 번 쓰지 않도록 재요청하지 않는다.
          lang === 'ko'
            ? null
            : fetch(`${DDRAGON}/cdn/${latestVersion}/data/${DDRAGON_LOCALE.ko}/champion.json`, {
                signal: controller.signal,
              }),
        ]);
        if (!listRes.ok) throw new Error(String(listRes.status));
        const { data }: { data: Record<string, DDragonChampion> } = await listRes.json();

        const koreanData: Record<string, DDragonChampion> = koreanRes
          ? (await koreanRes.json()).data
          : data;
        const koreanMap: Record<string, string> = {};
        for (const key of Object.keys(koreanData)) {
          if (key.indexOf('_') !== -1) continue;
          koreanMap[koreanData[key].name] = koreanData[key].id;
        }
        setKoreanNameToId(koreanMap);

        setChampions(
          Object.keys(data)
            // Jade_* 는 별도 모드용 중복 항목이다. 최신 본편 챔피언만 남긴다.
            .filter((key) => key.indexOf('_') === -1)
            .map((key): ChampionInfo => {
              const { id, name } = data[key];
              return {
                id,
                name,
                imageUrl: `${DDRAGON}/cdn/${latestVersion}/img/champion/${id}.png`,
                // 공백을 지운 형태로도 찾을 수 있게 한다. ("리신" → "리 신")
                searchKey: `${name} ${name.replace(/\s/g, '')} ${id}`.toLowerCase(),
                chosung: toChosung(name.replace(/\s/g, '')),
              };
            })
            .sort((a, b) => a.name.localeCompare(b.name, SORT_LOCALE[lang])),
        );
        setVersion(latestVersion);
        // 지난 언어에서 실패했더라도 이번에 성공했으면 경고를 내린다.
        setFailed(false);
      } catch (error) {
        if (controller.signal.aborted) return;
        console.error(error);
        setFailed(true);
      }
    };

    load();
    return () => controller.abort();
  }, [lang]);

  return { champions, version, failed, koreanNameToId };
}

/** 값이 delay 동안 안정될 때까지 갱신을 미룬다. */
export function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}
