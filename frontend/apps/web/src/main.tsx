import './index.css';

import * as Sentry from '@sentry/react';
import { createRoot } from 'react-dom/client';

import { initAnalytics } from './analytics';
import App from './App';
import { LOCALE, localeFromPath, localePath, strings, stripLocale } from './i18n/i18n';

const SENTRY_DSN =
  'https://fec5aaea14d81822eef4a934ae94c521@o4512044427968512.ingest.us.sentry.io/4512179818921984';

const PROD_HOSTNAMES = ['dfgg.pro', 'www.dfgg.pro'];
const { hostname } = window.location;

// 로컬 개발 서버에서 난 에러는 보내지 않는다. dev 인스턴스 에러는 environment 로 구분한다.
Sentry.init({
  dsn: SENTRY_DSN,
  enabled: hostname !== 'localhost',
  environment: PROD_HOSTNAMES.includes(hostname) ? 'production' : 'development',
});

const { pathname, search, hash } = window.location;
if (!localeFromPath(pathname) && LOCALE === 'ko') {
  window.history.replaceState(null, '', localePath('ko', stripLocale(pathname)) + search + hash);
}

const { meta } = strings();
document.documentElement.lang = LOCALE;
document.title = meta.title;
document.querySelector('meta[name="description"]')?.setAttribute('content', meta.description);

initAnalytics();

// React 19 는 렌더링 중 에러를 window.onerror 로 올리지 않아서 직접 연결해야 Sentry 에 잡힌다.
createRoot(document.getElementById('root')!, {
  onUncaughtError: Sentry.reactErrorHandler(),
  onCaughtError: Sentry.reactErrorHandler(),
  onRecoverableError: Sentry.reactErrorHandler(),
}).render(<App />);
