import * as Sentry from '@sentry/electron/renderer';

// DSN·활성화 여부는 메인 프로세스(src/main/sentry.ts) 설정을 따른다.
// 개발 화면을 브라우저로 직접 열면 메인 프로세스가 없어 연결 오류만 나므로 건너뛴다.
if (navigator.userAgent.includes('Electron')) Sentry.init();
