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
정규화 데이터 생성
        ↓
ChampionBuildStats 집계
```

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

이 API는 외부 API에서 새 데이터를 수집하지 않습니다. 저장된 원본 데이터를 읽어 다음 파생 데이터를 정규화하고, 기존 통계를 유지하면서 신규 sample을 반영합니다.

- `normalized_match_participants`
- `composition_stats`
- `composition_stats_items`
- `composition_stats_samples`

`composition_stats`에는 패치, 큐, 티어, 챔피언, 포지션, 조합 조건, 코어 아이템 구매 순서가 저장됩니다.

응답에는 전체 원본 매치 수, 처리한 매치 수, Timeline 누락 수, 실패 수와 사유, 새로 기록한 sample 수가 포함됩니다.

```json
{
  "totalMatches": 1131,
  "processedMatches": 1131,
  "skippedMissingTimeline": 0,
  "failedMatches": 0,
  "recordedSamples": 32,
  "failures": []
}
```

각 매치는 독립 트랜잭션으로 처리됩니다. 한 매치가 실패하면 그 매치에서 변경한 정규화·통계 데이터만 롤백하고 다음 매치를 계속 처리합니다. 실패한 매치 ID와 사유는 `failures`에 포함되며, `processedMatches`에는 커밋까지 성공한 매치만 포함됩니다.

같은 데이터를 다시 집계하면 중복 sample은 추가되지 않으므로 `recordedSamples`는 `0`이 될 수 있습니다. 실행 중에는 애플리케이션 로그에서 시작, 약 10% 단위 진행 상황, 실패 건수, 완료 결과와 소요 시간을 확인할 수 있습니다.

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
4. 모든 매치 수집이 끝난 후 `/admin/riot/matches/stats?tier=...`로 정규화 데이터와 통계를 반영합니다.
