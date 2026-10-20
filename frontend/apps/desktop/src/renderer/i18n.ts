import { intlTag, pickLocale, strings } from '../shared/i18n';

// main 이 정해 preload 로 넘긴 언어를 쓴다. navigator.languages 는 --lang 을 무시하고 OS 언어를 줄 때가 있다.
// 브라우저에서 devMock 으로 열 때처럼 값이 없으면 브라우저 언어로 정한다.
export const LOCALE = pickLocale(window.appLocale ? [window.appLocale] : navigator.languages);
export const t = strings(LOCALE);
export const INTL = intlTag(LOCALE);

document.documentElement.lang = LOCALE;
