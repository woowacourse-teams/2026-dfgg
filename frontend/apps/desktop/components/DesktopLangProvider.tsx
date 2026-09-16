import { type ReactNode, useEffect, useState } from 'react';

import { initialLang, type Lang, storeLang } from '../../../packages/i18n/lang';
import { LangContext } from '../../../packages/i18n/useLang';

/**
 * 데스크톱용 언어 공급자.
 *
 * 웹의 LangProvider 와 달리 값을 메인 프로세스에 맡긴다. 오버레이는 클릭이
 * 통과하는 창이라 자기 버튼이 없어서, 메인 창에서 바꾼 언어를 IPC 로 받아야
 * 같은 언어로 그려진다 — 추천 방식(useRecommendMode)과 같은 방식이다.
 *
 * localStorage 에도 남겨 앱을 다시 켰을 때 지난 선택을 복원한다.
 */
export default function DesktopLangProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang);
  const api = window.lcu?.lang;

  useEffect(() => {
    if (!api) return;

    void api.get().then((shared) => {
      // 아직 아무도 안 정했으면 이 창이 고른 값(저장된 선택 또는 OS 언어)을 기준으로 삼는다.
      if (shared) setLangState(shared);
      else void api.set(initialLang());
    });

    return api.onChange((next) => {
      setLangState(next);
      storeLang(next);
    });
  }, [api]);

  const setLang = (next: Lang) => {
    // 클릭한 창은 IPC 왕복을 기다리지 않고 바로 반영한다.
    setLangState(next);
    storeLang(next);
    void api?.set(next);
  };

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  return <LangContext.Provider value={{ lang, setLang }}>{children}</LangContext.Provider>;
}
