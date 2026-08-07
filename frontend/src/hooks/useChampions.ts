import { useEffect, useState } from 'react';

const DDRAGON = 'https://ddragon.leagueoflegends.com';
const LOCALE = 'ko_KR';

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
  /** 백엔드로 보내는 표시 이름. (예: 손오공) */
  name: string;
  imageUrl: string;
  /** 한글명 + 영문 id를 소문자로 합친 검색 키 */
  searchKey: string;
  /** 한글명의 초성. "아리" → "ㅇㄹ" */
  chosung: string;
}

interface DDragonChampion {
  id: string;
  name: string;
}

/**
 * Data Dragon 챔피언 목록을 한 번만 받아온다.
 * 버전은 하드코딩하지 않고 versions.json의 최신값을 쓴다.
 */
export function useChampions() {
  const [champions, setChampions] = useState<ChampionInfo[]>([]);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    const load = async () => {
      try {
        const versionRes = await fetch(`${DDRAGON}/api/versions.json`, {
          signal: controller.signal,
        });
        if (!versionRes.ok) throw new Error(String(versionRes.status));
        const [version]: string[] = await versionRes.json();

        const listRes = await fetch(`${DDRAGON}/cdn/${version}/data/${LOCALE}/champion.json`, {
          signal: controller.signal,
        });
        if (!listRes.ok) throw new Error(String(listRes.status));
        const { data }: { data: Record<string, DDragonChampion> } = await listRes.json();

        setChampions(
          Object.keys(data)
            // Jade_* 는 별도 모드용 중복 항목이다. 최신 본편 챔피언만 남긴다.
            .filter((key) => key.indexOf('_') === -1)
            .map((key): ChampionInfo => {
              const { id, name } = data[key];
              return {
                id,
                name,
                imageUrl: `${DDRAGON}/cdn/${version}/img/champion/${id}.png`,
                // 공백을 지운 형태로도 찾을 수 있게 한다. ("리신" → "리 신")
                searchKey: `${name} ${name.replace(/\s/g, '')} ${id}`.toLowerCase(),
                chosung: toChosung(name.replace(/\s/g, '')),
              };
            })
            .sort((a, b) => a.name.localeCompare(b.name, 'ko')),
        );
      } catch (error) {
        if (controller.signal.aborted) return;
        console.error(error);
        setFailed(true);
      }
    };

    load();
    return () => controller.abort();
  }, []);

  return { champions, failed };
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
