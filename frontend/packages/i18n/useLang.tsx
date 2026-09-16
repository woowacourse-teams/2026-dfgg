import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

import { initialLang, type Lang, storeLang } from './lang';

interface LangContextValue {
  lang: Lang;
  setLang: (lang: Lang) => void;
}

/**
 * 기본값을 두면 Provider를 깜빡해도 조용히 한국어로 동작해 버린다.
 * null로 두고 훅에서 던져야 빠뜨린 걸 바로 안다.
 */
export const LangContext = createContext<LangContextValue | null>(null);

export function LangProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang);

  const setLang = useCallback((next: Lang) => {
    setLangState(next);
    storeLang(next);
  }, []);

  // 스크린 리더가 어느 언어로 읽을지 정하는 값이라 화면 언어와 맞춰야 한다.
  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const value = useMemo(() => ({ lang, setLang }), [lang, setLang]);

  return <LangContext.Provider value={value}>{children}</LangContext.Provider>;
}

export function useLang(): LangContextValue {
  const context = useContext(LangContext);
  if (!context) throw new Error('useLang은 LangProvider 안에서만 쓸 수 있다');
  return context;
}

/**
 * 사전에서 현재 언어의 문자열 묶음을 꺼낸다.
 *
 *   const t = useDict(HOME_TEXT);
 *   <h1>{t.title}</h1>
 *
 * 키 자리에 문자열을 넘기는 t('home.title') 방식이 아니라 묶음을 통째로 꺼내는
 * 이유는, 이렇게 해야 오타난 키를 타입스크립트가 잡아주기 때문이다.
 */
export function useDict<T>(dict: Record<Lang, T>): T {
  const { lang } = useLang();
  return dict[lang];
}
