import './index.css';

import { createRoot } from 'react-dom/client';

import App from './App';
import { LOCALE, localeFromPath, localePath, strings,stripLocale } from './i18n/i18n';

const { pathname, search, hash } = window.location;
if (!localeFromPath(pathname) && LOCALE === 'ko') {
  window.history.replaceState(null, '', localePath('ko', stripLocale(pathname)) + search + hash);
}

const { meta } = strings();
document.documentElement.lang = LOCALE;
document.title = meta.title;
document.querySelector('meta[name="description"]')?.setAttribute('content', meta.description);

createRoot(document.getElementById('root')!).render(<App />);
