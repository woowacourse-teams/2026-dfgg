import './index.css';

import type { ErrorInfo } from 'react';
import { createRoot } from 'react-dom/client';

import { afterPageLoad, initAnalytics } from './analytics';
import App from './App';
import { LOCALE, localeFromPath, localePath, strings, stripLocale } from './i18n/i18n';

const SENTRY_DSN =
  'https://fec5aaea14d81822eef4a934ae94c521@o4512044427968512.ingest.us.sentry.io/4512179818921984';

const PROD_HOSTNAMES = ['dfgg.pro', 'www.dfgg.pro'];
const { hostname } = window.location;

/**
 * Sentry 는 첫 화면을 그리는 데 필요 없어서 로딩이 끝난 뒤 따로 받는다.
 * 그 사이 React 에서 난 에러는 Sentry 를 받은 뒤 보낸다. 받기 전 window 전역 에러는 놓친다.
 * 로컬 개발 서버에서 난 에러는 보내지 않는다. dev 인스턴스 에러는 environment 로 구분한다.
 */
const sentryReady =
  hostname === 'localhost'
    ? null
    : afterPageLoad()
        .then(() => import(/* webpackChunkName: "sentry" */ '@sentry/react'))
        .then((Sentry) => {
          Sentry.init({
            dsn: SENTRY_DSN,
            environment: PROD_HOSTNAMES.includes(hostname) ? 'production' : 'development',
          });
          return Sentry;
        });

const reportReactError = (error: unknown, errorInfo: ErrorInfo) => {
  sentryReady?.then((Sentry) => Sentry.reactErrorHandler()(error, errorInfo)).catch(() => {});
};

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
  onUncaughtError: reportReactError,
  onCaughtError: reportReactError,
  onRecoverableError: reportReactError,
}).render(<App />);
