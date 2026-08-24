# 데이터 수집 및 통계 생성 파이프라인

## 전체 흐름

```text
챔피언·아이템 기준 데이터 수집
        ↓
플레이어 PUUID·티어 수집
        ↓
Match ID 조회
        ↓
Match-v5 상세 원본 + Timeline 원본 저장
        ↓
정규화 데이터 생성·저장
        ↓
생성한 정규화 객체로 ChampionBuildStats 즉시 집계
```

## 자동 파이프라인 빠른 실행

### 1. 사전 준비

- PostgreSQL의 `dfgg` 데이터베이스를 실행합니다.
- Riot Developer Portal에서 발급받은 API Key를 준비합니다.
- 프로젝트 루트에 `.env`가 없다면 예제 파일을 복사합니다.

```bash
cp .env.example .env
```

기존 `.env`가 있다면 덮어쓰지 말고 필요한 항목만 추가합니다.

### 2. 환경변수 설정

`.env`에 데이터베이스 접속 정보, Riot API Key, 스케줄러 설정을 입력합니다.

```properties
DB_USERNAME=postgres
DB_PASSWORD=postgres
RIOT_API_KEY=발급받은_Riot_API_Key

COLLECTION_SCHEDULER_ENABLED=true
COLLECTION_SCHEDULER_CRON=0 */5 * * * *
COLLECTION_SCHEDULER_ZONE=Asia/Seoul
COLLECTION_SCHEDULER_TIERS=PLATINUM
COLLECTION_SCHEDULER_DIVISIONS=IV
COLLECTION_SCHEDULER_LEAGUE_PAGE_COUNT=1
COLLECTION_SCHEDULER_PLAYER_PAGE_SIZE=100
COLLECTION_SCHEDULER_MATCH_START=0
COLLECTION_SCHEDULER_MATCH_COUNT=20
```

각 환경변수는 한 번만 선언해야 합니다. 같은 키가 여러 번 선언되면 뒤에 있는 값이 적용되어
예상과 다른 주기로 실행될 수 있습니다.

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

`COLLECTION_SCHEDULER_ENABLED=true`이면 애플리케이션 시작 후 다음 cron 시각에 자동 파이프라인이
실행됩니다. `0 */5 * * * *`는 매시 `0, 5, 10, 15 ...`분의 0초에 실행한다는 뜻입니다.

자동 실행 순서:

1. 챔피언 메타데이터 수집
2. 코어 아이템 메타데이터 수집
3. 플레이어 PUUID와 티어·디비전 cohort 수집
4. 플레이어별 Match ID 조회
5. Match 상세 원본과 Timeline 원본 저장
6. 누락 Timeline 보완
7. 신규 매치 정규화 및 저장
8. 방금 생성한 정규화 객체를 바로 전달해 통계 집계

일반 스케줄은 8단계에서 `normalized_match_participants`를 다시 조회하지 않습니다.
정규화 결과 객체를 즉시 통계 서비스에 넘겨 저장과 집계 사이의 중복 조회를 줄입니다.

### 4. 실행 확인

실행 결과는 아래 관리자 API와 저장 테이블을 조회해 확인합니다.
일부 항목이 실패해도 처리할 수 있는 나머지 단계는 계속 실행합니다.

### 5. 자동 실행 중지

`.env`에서 스케줄러를 비활성화한 뒤 애플리케이션을 재시작합니다.

```properties
COLLECTION_SCHEDULER_ENABLED=false
```

### 실행 시 주의사항

- 기본 권장 주기는 5분입니다. 수집 작업보다 짧은 주기를 사용하지 않습니다.
- 현재 스케줄러는 단일 애플리케이션 인스턴스 실행을 전제로 합니다.
- 여러 서버 인스턴스를 동시에 실행하면 각 인스턴스에서 같은 스케줄이 실행됩니다.
- 진행 위치는 메모리에 있으므로 애플리케이션을 재시작하면 설정된 시작 범위부터 다시 시작합니다.
- 이미 저장된 Match와 Timeline, 이미 완료된 통계는 중복 저장·집계되지 않습니다.
- 전체 파이프라인을 기다리지 않고 단계별로 확인하려면 아래의 관리자 API 실행 방법을 사용합니다.

## 사전 조건

애플리케이션을 먼저 실행합니다.

```bash
./gradlew bootRun
```

기본 서버 주소는 `http://localhost:8080`입니다.

## 1. 챔피언·아이템 기준 데이터 수집

```bash
curl -X POST "http://localhost:8080/admin/champions"
curl -X POST "http://localhost:8080/admin/items"
```

저장되는 테이블:

- `champions`
- `items`

통계 집계 시 챔피언과 아이템이 필요하므로 통계 생성 전에 실행해야 합니다.

## 2. 플레이어 PUUID와 티어 수집

```bash
curl -X POST \
  "http://localhost:8080/admin/riot/players?queue=RANKED_SOLO_5x5&tier=PLATINUM&division=I&page=1"
```

저장되는 테이블:

- `players`: 플레이어 PUUID와 플랫폼
- `player_cohorts`: 매치 수집에 사용할 PUUID·큐·티어·디비전

더 많은 플레이어를 수집하려면 티어·디비전·페이지를 바꿔 반복 호출합니다.

## 3. Match-v5 상세와 Timeline 수집

```bash
curl -X POST \
  "http://localhost:8080/admin/riot/matches?playerPage=0&playerCount=1&start=0&count=1"
```

파라미터:

| 파라미터 | 의미 |
| --- | --- |
| `playerPage` | 저장된 플레이어 페이지 번호 |
| `playerCount` | 이번 작업에서 사용할 플레이어 수 |
| `start` | 각 플레이어의 매치 조회 시작 위치 |
| `count` | 각 플레이어에게서 조회할 매치 수 |

처음 확인할 때는 `playerCount=1`, `count=1`로 한 명의 매치 한 개만 수집합니다.

저장되는 테이블:

- `raw_matches`: Match-v5 상세 원본 JSON
- `raw_match_timelines`: Match Timeline 원본 JSON
- `match_participant_cohorts`: 매치 수집 당시 PUUID와 티어의 연결 정보

이미 저장된 Match 상세는 다시 저장하지 않습니다. 상세 원본만 있고 Timeline이 없으면 Timeline만 보완합니다.

## 4. 기존 Match의 Timeline 보완

Timeline 수집 기능을 추가하기 전에 저장된 Match가 있다면 다음 API를 호출합니다.

```bash
curl -X POST \
  "http://localhost:8080/admin/riot/matches/timelines"
```

이 API는 `raw_matches` 중 `raw_match_timelines`가 없는 매치만 Riot API에서 조회합니다.

## 5. 정규화 및 ChampionBuildStats 반영

```bash
curl -X POST \
  "http://localhost:8080/admin/riot/matches/stats?tier=PLATINUM"
```

이 API는 외부 API에서 새 데이터를 수집하지 않습니다. 먼저 저장된 원본 데이터를
정규화하고, 생성한 정규화 객체를 바로 통계 서비스에 전달해 신규 sample을 반영합니다.
그런 다음 DB에 저장되어 있지만 아직 집계되지 않은 정규화 행을 조회해 복구·백필합니다.

- `normalized_match_participants`
- `composition_stats`
- `composition_stats_items`
- `composition_stats_samples`

`composition_stats`에는 패치, 큐, 티어, 챔피언, 포지션, 조합 조건, 코어 아이템 구매 순서가 저장됩니다.

2026 역할 퀘스트로 인해 Match의 최종 신발과 Timeline의 구매 이벤트가 다르게 표현되는 경우에는
[역할 퀘스트 신발 정규화 결정 기록](./role-quest-boots-normalization.md)의 규칙을 적용합니다.

관리자 API의 두 통계 경로는 역할이 다릅니다.

- 신규 정규화 경로: 이번 요청에서 생성한 정규화 객체를 즉시 집계합니다.
- 복구·백필 경로: 과거 실패나 배포 이전 데이터 때문에 DB에 남은 미집계 대상만 재조회합니다.

일반 스케줄은 신규 정규화 경로만 사용하며, DB pending 재조회는 관리자의
복구·백필 요청에서만 실행합니다. 모든 처리가 성공하면 응답 본문 없이
`204 No Content`를 반환합니다.

정규화 데이터 교체와 통계 집계의 매치 단위 트랜잭션 경계는 추후 고도화 대상으로 남겨둡니다.
현재도 한 매치의 통계 집계가 실패하면 나머지 매치 처리는 계속 시도하며, 완료되지 않은 매치는
다음 실행에서 관리자 복구·백필 API로 다시 시도할 수 있습니다.

기존 `normalized_match_participants` 행에 티어가 비어 있으면 미완료 데이터로 판단하여 다음 실행에서 다시 정규화합니다.

같은 데이터를 다시 집계해도 완료 기록을 확인하므로 중복 sample은 추가되지 않습니다.

### 단일 매치 재집계 시 주의

`POST /admin/riot/matches/{matchId}/stats/replay?tier=...`는 운영자가 동일 매치의 일반
스케줄 집계가 끝난 뒤 수동으로 호출하는 것을 전제로 하며, 동시 실행을 지원하지 않습니다.
재집계를 자동화하거나 동시 호출을 허용할 때는 `matchId` 기준으로 일반 집계와 재집계를
직렬화해야 합니다.

## 처음부터 최소 단위로 테스트하기

```bash
curl -X POST "http://localhost:8080/admin/champions"
curl -X POST "http://localhost:8080/admin/items"

curl -X POST \
  "http://localhost:8080/admin/riot/players?queue=RANKED_SOLO_5x5&tier=PLATINUM&division=I&page=1"

curl -X POST \
  "http://localhost:8080/admin/riot/matches?playerPage=0&playerCount=1&start=0&count=1"

curl -X POST \
  "http://localhost:8080/admin/riot/matches/stats?tier=PLATINUM"
```

## 데이터 저장 확인

```bash
psql -h 127.0.0.1 -p 5432 -U tuise -d dfgg
```

```sql
SELECT 'players' AS table_name, COUNT(*) FROM players
UNION ALL
SELECT 'player_cohorts', COUNT(*) FROM player_cohorts
UNION ALL
SELECT 'raw_matches', COUNT(*) FROM raw_matches
UNION ALL
SELECT 'raw_match_timelines', COUNT(*) FROM raw_match_timelines
UNION ALL
SELECT 'match_participant_cohorts', COUNT(*) FROM match_participant_cohorts
UNION ALL
SELECT 'normalized_match_participants', COUNT(*) FROM normalized_match_participants
UNION ALL
SELECT 'composition_stats', COUNT(*) FROM composition_stats
UNION ALL
SELECT 'composition_stats_items', COUNT(*) FROM composition_stats_items
UNION ALL
SELECT 'composition_stats_samples', COUNT(*) FROM composition_stats_samples;
```

## 운영 시 반복 수집 순서

새로운 플레이어·티어 데이터를 추가할 때는 다음 순서를 반복합니다.

1. `/admin/riot/players`로 PUUID와 수집 대상 cohort를 추가합니다.
2. `/admin/riot/matches`를 플레이어 페이지와 매치 범위별로 호출합니다.
3. 필요하면 `/admin/riot/matches/timelines`로 누락 Timeline을 보완합니다.
4. 모든 매치 수집이 끝난 후 `/admin/riot/matches/stats?tier=...`로 신규 정규화 결과를
   즉시 집계하고, DB에 남은 미집계 데이터도 백필합니다.

## 스케줄러 자동 수집

기본값은 비활성화이며, 활성화하면 5분마다(Asia/Seoul) 실행되도록 설정되어 있습니다.

```yaml
collection:
  scheduler:
    enabled: false
    cron: "0 */5 * * * *"
    zone: "Asia/Seoul"
```

운영 환경에서는 다음 환경변수로 활성화합니다.

```properties
COLLECTION_SCHEDULER_ENABLED=true
COLLECTION_SCHEDULER_CRON=0 */5 * * * *
COLLECTION_SCHEDULER_ZONE=Asia/Seoul
COLLECTION_SCHEDULER_TIERS=PLATINUM,EMERALD
COLLECTION_SCHEDULER_DIVISIONS=I,II,III,IV
```

추가 수집 범위 설정:

| 환경변수 | 기본값 | 의미 |
| --- | --- | --- |
| `COLLECTION_SCHEDULER_TIERS` | `PLATINUM` | 쉼표로 구분한 대상 티어 |
| `COLLECTION_SCHEDULER_DIVISIONS` | `I` | 수집을 시작할 디비전. 한 개를 설정하면 해당 디비전부터 I까지 진행 |
| `COLLECTION_SCHEDULER_LEAGUE_PAGE_COUNT` | `1` | 한 스케줄에서 진행할 League API 페이지 수 |
| `COLLECTION_SCHEDULER_PLAYER_PAGE_SIZE` | `100` | 한 번에 조회할 저장 플레이어 수 |
| `COLLECTION_SCHEDULER_MATCH_START` | `0` | 서버 시작 후 첫 플레이어별 매치 조회 위치 |
| `COLLECTION_SCHEDULER_MATCH_COUNT` | `20` | 한 스케줄에서 플레이어별로 조회할 매치 수 |

서버는 위 설정을 초기값과 처리 크기로 사용합니다. 예를 들어 디비전을 `IV`로 설정하면 League
범위는 `IV/page-1 → III/page-1 → II/page-1 → I/page-1 → IV/page-2 ...` 순서로 진행합니다.
디비전을 여러 개 설정한 경우에는 명시한 순서대로 순환한 뒤 다음 페이지로 이동합니다. 매치 조회
위치는 기본 설정에서 `0 → 20 → 40 ...`처럼 증가합니다.
수집 도중 실패한 범위는 증가시키지 않고 다음 스케줄에서 다시 시도합니다. 이 진행 위치는
서버 메모리에만 있으므로 서버를 재시작하면 설정된 초기 범위부터 다시 시작하며, 이미 저장된
매치와 타임라인은 저장소의 중복 방지 조건에 의해 다시 저장되지 않습니다.

자동 실행 순서는 다음과 같습니다.

현재 자동 수집 대상 큐는 기존 매치·통계 집계 계약과 동일한 `RANKED_SOLO_5x5`로 고정되어 있습니다. Flex 큐 자동화는 Match-v5 queue ID와 통계 completion scope를 함께 일반화해야 하므로 이번 범위에는 포함하지 않습니다.

1. 챔피언 메타데이터
2. 코어 아이템 메타데이터
3. 설정된 티어·디비전의 플레이어와 cohort
4. 저장된 플레이어의 미수집 raw match와 timeline
5. 기존 raw match의 누락 timeline 보완
6. 신규 매치 정규화 및 저장
7. 설정된 티어별로 방금 생성한 정규화 객체를 즉시 통계 집계

스케줄 경로는 새로 생성한 정규화 결과를 메모리에서 바로 전달하므로 DB pending을
다시 훑지 않습니다. DB를 기준으로 한 미집계 복구·백필은
`POST /admin/riot/matches/stats?tier=...` 관리자 API의 후속 단계에서만 실행합니다.

스케줄 작업은 내부 HTTP API를 거치지 않고 정규화·통계 Application Service를 직접
호출합니다. 관리자 API만 이 경로 뒤에 DB 복구·백필 단계를 추가로 실행합니다.
현재는 단일 애플리케이션 인스턴스 운영을 전제로 하며 별도의 분산 lock을 사용하지
않습니다. 다중 인스턴스로 확장할 때는 동일 cron이 각 인스턴스에서 실행되므로
ShedLock이나 PostgreSQL advisory lock 같은 실행 조정 장치를 추가해야 합니다.

raw match, timeline, cohort는 `ON CONFLICT DO NOTHING` 또는 upsert로 저장하고, 통계는
`matchId + puuid + queue + tier + revision` completion을 먼저 claim합니다. 실패 항목은 completion이
남지 않아 다음 관리자 복구·백필 실행에서 다시 대상이 되며, 이미 완료된 항목은 중복 집계되지 않습니다.
