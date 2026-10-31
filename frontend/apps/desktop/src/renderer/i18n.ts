import { intlTag, Locale, pickLocale, strings } from '../shared/i18n';

export const LOCALE = pickLocale(window.appLocale ? [window.appLocale] : navigator.languages);
export const t = strings(LOCALE);
export const INTL = intlTag(LOCALE);

document.documentElement.lang = LOCALE;

export const setLocale = (locale: Locale) => window.settings.setLocale(locale);
