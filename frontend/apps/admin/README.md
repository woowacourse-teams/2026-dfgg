# DFGG Admin

PostHog(이전 Umami)와 Microsoft Store 분석 데이터를 한 화면에 모은 **로컬 전용** 관리자 페이지.

```bash
cp apps/admin/.env.example apps/admin/.env   # 값 채우기
pnpm install
pnpm admin                                    # http://127.0.0.1:3100
```

키 없이 화면만 보려면 `.env`에 `ADMIN_MOCK=1`.

| 페이지                  | 내용                                                                           |
| ----------------------- | ------------------------------------------------------------------------------ |
| 대시보드 (`#/`)         | 웹·데스크탑·스토어 지표, 일별 추이, 전환 흐름, 추천 성공률, 이벤트             |
| 앱 사용자 (`#/players`) | Riot ID별 사용자 목록과 각자 플레이한 게임 (챔피언·포지션·큐·승패·오버레이 등) |

## 구조

```
브라우저(React) ──/admin-api/*──▶ webpack dev server 미들웨어(Node, server/)
                                   ├─ PostHog HogQL  (POSTHOG_PERSONAL_API_KEY)  ← 기본
                                   ├─ Umami API      (ANALYTICS_SOURCE=umami 일 때)
                                   └─ Microsoft Store: data/*.csv (Entra 키가 있으면 API)
```

- 키는 `server/`가 `.env`에서만 읽는다. 브라우저에는 집계된 숫자만 내려간다.
- dev server는 `127.0.0.1`에만 열린다. 배포 워크플로는 이 앱을 빌드하지 않는다.
- 웹에 올리고 싶어지면 `server/`를 백엔드 API로 옮기고 인증을 붙여야 한다.

## 키 발급

**PostHog** — 설정 → Personal API keys → 새 키(권한 `query:read`, 프로젝트 dfgg) → `POSTHOG_PERSONAL_API_KEY`.

**Umami** (예전 데이터를 볼 때만) — cloud.umami.is → Settings → API keys → `UMAMI_API_KEY`, `.env`에 `ANALYTICS_SOURCE=umami`.

**Microsoft Store** — 기본은 CSV.

1. Partner Center → **Insights → 다운로드 허브 → 새 보고서 만들기** → 앱 및 게임의 **취득**(또는 설치) 보고서, 집계 **일별**, 형식 **CSV**.
2. 받은 파일을 `apps/admin/data/` 에 넣는다. 여러 개 넣어도 되고, 똑같은 행은 한 번만 센다. 이 폴더는 커밋되지 않는다.
3. 날짜·지표(취득/설치/페이지 조회수)·시장 열은 머리글 이름으로 찾는다(영어·한국어). 못 찾으면 화면에 그 파일의 머리글이 표시된다.

회사(Entra ID) 계정이 있으면 `MS_TENANT_ID`·`MS_CLIENT_ID`·`MS_CLIENT_SECRET` 을 채워 분석 API 로 바로 가져올 수도 있다. 개인 Microsoft 계정으로 등록한 개발자 계정은 Entra 앱을 만들 수 없다.

스토어 데이터는 보통 1~3일 늦게 들어온다. 차트에서 아직 안 들어온 날은 빈칸으로 그린다.

## 지표 정의

| 지표                 | 출처                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------ |
| 웹 방문자·페이지뷰   | 데스크탑이 아닌 `$pageview` (방문 = `$session_id`, 옮겨온 데이터는 `umami_visit_id`) |
| 데스크탑 활성 사용자 | `desktop-`으로 시작하는 이벤트를 보낸 순 방문자(소환사 단위)                         |
| 스토어 획득·설치     | Partner Center `appacquisitions`, `installs`                                         |
| 전환 흐름            | 단계별 이벤트를 보낸 순 방문자 (`server/config.js`)                                  |
| 추천 성공률          | `*recommend(-v3)-success / -fail / -error` 이벤트 횟수                               |

앱 사용자 페이지는 `desktop-connect`(레벨·버전)와 `desktop-game-end`(게임 한 판) 이벤트를
Riot ID로 묶는다. PostHog 는 쿼리 한 번으로, Umami 는 이벤트마다 데이터를 따로 받는다(메모리 캐시).
데스크탑 이벤트는 `desktop-` 이름이거나 `platform=desktop` 속성이 있는 이벤트다. Riot ID가 없는 이벤트는 같은 사용자 id(PUUID 기반)에서
Riot ID가 보이면 그 사용자로, 아니면 "Riot ID 없음"으로 따로 보여준다.

이벤트 이름을 바꾸거나 추가하면 `server/config.js`의 `FUNNEL`도 같이 고친다.
