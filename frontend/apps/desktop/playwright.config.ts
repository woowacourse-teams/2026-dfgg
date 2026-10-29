import { defineConfig } from '@playwright/test';

export default defineConfig({
  // E2E 테스트는 여기에만 둔다. 하네스(fake-league 등)는 테스트가 아니라 불러 쓰는 도구다
  testDir: './test/e2e',
  testMatch: '**/*.spec.ts',

  // 앱은 한 번에 하나만 켜진다(단일 실행 잠금). 테스트를 동시에 돌리면 두 번째 앱이 바로 꺼진다
  workers: 1,
  fullyParallel: false,

  // 앱을 켜고 창이 뜨기까지 시간이 걸린다. 기본 30초보다 넉넉하게
  timeout: 60_000,

  // 실패한 테스트만 기록(스크린샷, 동작 순서)을 남긴다
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  reporter: 'list',
});
