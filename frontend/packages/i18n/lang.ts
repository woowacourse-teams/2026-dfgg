/**
 * 웹·데스크톱이 같이 쓰는 언어 선택 로직.
 *
 * 라이브러리를 쓰지 않는다. 문자열이 200개 남짓이고 언어가 둘뿐이라
 * i18next를 얹으면 번들만 커지고 얻는 게 없다.
 */

export const LANGS = ['ko', 'en'] as const;

export type Lang = (typeof LANGS)[number];

/** 수동 선택을 기억하는 키. 이 값이 있으면 브라우저 언어보다 우선한다. */
const STORAGE_KEY = 'dfgg.lang';

export function isLang(value: unknown): value is Lang {
  return typeof value === 'string' && (LANGS as readonly string[]).indexOf(value) !== -1;
}

/**
 * 브라우저·OS가 알려주는 언어. navigator.language 는 웹과 Electron 렌더러에서
 * 모두 같은 값을 준다. 한국어 계열이면 한국어, 나머지는 전부 영어로 본다.
 *
 * GeoIP로 접속 국가를 보지 않는 건 의도적이다. OS 언어 설정이 "이 사람이 읽을
 * 수 있는 언어"에 더 가깝고(해외 거주 한국인, 국내 거주 외국인), 네트워크 호출도
 * 필요 없다.
 */
export function detectLang(): Lang {
  if (typeof navigator === 'undefined') return 'en';
  const candidates = navigator.languages?.length ? navigator.languages : [navigator.language];
  return candidates.some((tag) => tag?.toLowerCase().startsWith('ko')) ? 'ko' : 'en';
}

/** 저장된 수동 선택. 없으면 null. */
export function readStoredLang(): Lang | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return isLang(saved) ? saved : null;
  } catch {
    // 시크릿 모드 등에서 localStorage 접근 자체가 막힐 수 있다. 감지값으로 넘어간다.
    return null;
  }
}

export function storeLang(lang: Lang): void {
  try {
    localStorage.setItem(STORAGE_KEY, lang);
  } catch {
    // 저장에 실패해도 이번 세션 동안은 동작해야 하므로 삼킨다.
  }
}

/** 초기 언어. 수동 선택 > 브라우저 언어 순. */
export function initialLang(): Lang {
  return readStoredLang() ?? detectLang();
}

/** Data Dragon 이 쓰는 로케일 코드. */
export const DDRAGON_LOCALE: Record<Lang, string> = {
  ko: 'ko_KR',
  en: 'en_US',
};

/** Array.sort 에 넘길 로케일 코드. */
export const SORT_LOCALE: Record<Lang, string> = {
  ko: 'ko',
  en: 'en',
};
