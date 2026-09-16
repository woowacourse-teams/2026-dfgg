import { LANGS } from './lang';
import { useLang } from './useLang';

const LABEL: Record<(typeof LANGS)[number], string> = {
  ko: '한국어',
  en: 'English',
};

interface LangToggleProps {
  /** 창이 좁은 데스크톱 앱과 웹의 여백이 달라 밖에서 준다. */
  className?: string;
  /** 스크린 리더가 읽을 그룹 이름. 화면 언어에 맞춰 넘긴다. */
  label: string;
  /**
   * 선택된 쪽 / 선택 안 된 쪽 스타일. 웹과 데스크톱이 서로 다른 색 토큰을 쓰고
   * 있어서(웹은 --color-accent 계열, 데스크톱은 neutral/emerald) 여기서 색을
   * 정하면 한쪽에서는 안 보이는 버튼이 된다.
   */
  activeClass: string;
  inactiveClass: string;
}

/**
 * 언어 전환 버튼.
 *
 * 각 선택지를 자기 언어로 적는다("한국어"를 "Korean"으로 쓰지 않는다) — 지금
 * 화면이 읽을 수 없는 언어여도 자기 언어는 알아볼 수 있어야 빠져나올 수 있다.
 */
export default function LangToggle({
  className = '',
  label,
  activeClass,
  inactiveClass,
}: LangToggleProps) {
  const { lang, setLang } = useLang();

  return (
    <div className={`flex items-center gap-1 ${className}`} role='group' aria-label={label}>
      {LANGS.map((value) => (
        <button
          key={value}
          type='button'
          lang={value}
          onClick={() => setLang(value)}
          aria-pressed={lang === value}
          className={`cursor-pointer rounded px-2 py-1 text-xs font-bold transition-colors ${
            lang === value ? activeClass : inactiveClass
          }`}
        >
          {LABEL[value]}
        </button>
      ))}
    </div>
  );
}
