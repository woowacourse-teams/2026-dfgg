import * as Sentry from '@sentry/electron/renderer';

// DSN·활성화 여부는 메인 프로세스(src/main/sentry.ts) 설정을 따른다.
Sentry.init();
