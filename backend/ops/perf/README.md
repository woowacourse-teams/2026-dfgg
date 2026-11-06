# 부하 테스트

v3 추천(`POST /api/recommendations/v3`)에 동시 부하를 걸어 병목을 찾고, 개선 전후를 **같은 조건**으로 비교한다.

| 파일 | 하는 일 |
|---|---|
| `k6/v3-load.js` | k6 시나리오. `smoke` / `target` / `ramp` |
| `run.sh` | 측정 1회 = pg_stat_statements 초기화 → k6 → 결과 수집. 결과는 `results/<UTC>-<tag>/` |
| `results/` | 측정 원자료. **커밋한다** — 서버 홈에만 두었다가 원자료를 잃은 적이 있다 |

전후 비교의 "같은 조건"은 세 가지 고정물로 보인다: **요청 파일**(저장소), **서빙 데이터 덤프**(S3), **perf 환경 설정**(이 문서).

## 구성

```
[모니터링 EC2 10.0.0.68]  k6 + run.sh ──HTTP :8080──▶ [perf EC2 10.0.0.231]  nginx → dfgg@blue(:8081)
        │                                                     │ JDBC
        ├── psql (pg_stat_statements) ──────────────────────▶ [perf RDS dfgg-db-perf]
        ├── AWS CLI (RDS·EC2 켜기/끄기, CloudWatch, S3) ────▶      ▲ 스키마: Flyway V1~V9 (앱 첫 기동)
        └── Prometheus ── :9090 /blue /node ────────────────▶      │ 데이터: S3 고정 덤프 = main 서빙 테이블 8개
                                                                   │
                                    [dfgg-db-main] ── pg_dump --data-only (처음 한 번) ──▶ s3://techcourse-project-2026/dfgg/backup/perf/
```

- prod EC2와 `dfgg-db-prod`에는 **부하를 걸지 않는다.**
- perf는 prod와 같아야 병목도 같게 나온다. 아래 "prod 기준값"을 그대로 맞춘다.
- perf EC2와 RDS는 **측정할 때만 켠다**("켜기·끄기").

## 어떤 데이터로 재나

패치 자동 대응(`spec-patch-pipeline.md` B)이 돌면 prod의 서빙 테이블은 main에서 복제한 것으로 바뀐다.
그래서 perf는 지금 prod(9월 사본)가 아니라 **복제 후의 prod = main의 서빙 테이블 8개**를 싣는다.

| 참가자 테이블 (2026-10-12) | 지금 prod | main (= 복제 후 prod) |
|---|---:|---:|
| 행 수 | 약 176만 | 약 425만 (추정) |
| 테이블 본체 | 약 356MB | 911MB |
| 커버링 인덱스 `idx_nmp_build_transition` | 약 83MB | 242MB |

RDS(db.t4g.micro) 메모리는 1GB, DB 버퍼는 약 250MB다. 데이터 크기가 병목을 가르므로 이 차이가 중요하다.

- 서빙 테이블 8개(복제 대상과 같다): `champions` · `champion_tags` · `items` · `champion_item_stats` · `champion_item_rollup` · `champion_pair_item_stats` · `item_meta_stats` · `normalized_match_participants`
- **스키마는 저장소의 Flyway(V1~V9)로 만들고 데이터만 싣는다.** 인덱스가 main 상태가 아니라 저장소 정의대로 생긴다.
- **덤프는 한 번 떠서 S3에 고정한다.** 측정 도중 main은 계속 수집되고 prod는 복제로 바뀐다. 전후 비교는 모두 같은 덤프로 한다.

---

## 환경 구성 (처음 한 번)

### prod 기준값 (2026-10-12 실측)

perf는 이 값에 맞춘다. **다르게 두는 것은 표 아래 항목뿐이다.**

| 항목 | prod | 확인한 곳 |
|---|---|---|
| EC2 | t4g.small, Ubuntu 26.04 LTS arm64, AMI `ami-019e063f3fff5d27f`, ap-northeast-2a | 인스턴스 메타데이터 |
| 메모리 | 1.8GB + swap 2GB(`/swapfile`), `vm.swappiness=60` | `free -m`, `/etc/fstab` |
| Java | Amazon Corretto `21.0.12.8` (`java-21-amazon-corretto-jdk`) | `java -version` |
| 앱 유닛 | `/etc/systemd/system/dfgg@.service` — `-Xms128m -Xmx384m`, `MemoryMax=650M` | `systemctl cat` |
| 슬롯 환경 | `/etc/dfgg/blue.env` — `SERVER_PORT=8081`, `SPRING_JPA_HIBERNATE_DDL_AUTO=none` 등 | 파일 |
| nginx | 1.28.3, `:8080` → upstream `127.0.0.1:8081` (`keepalive 16`), `/actuator` 404 | `/etc/nginx` |
| node exporter | 1.10.2, `127.0.0.1:9100`에만 바인딩 | `/etc/default/prometheus-node-exporter` |
| RDS | `db.t4g.micro`, PostgreSQL 16.14, gp2 20GB, 암호화(기본 KMS), Single-AZ(2b), 파라미터 그룹 `default.postgres16`, 옵션 그룹 `default:postgres-16`, SG `project-db`, 서브넷 그룹 `project-rds-subnet-group`, 마스터 사용자 `postgres`, 앱 DB `dfgg` | `describe-db-instances` |
| pg_stat_statements | 기본 파라미터 그룹에 이미 preload(`shared_preload_libraries = pg_stat_statements,pg_tle`) | `describe-db-parameters` |

perf에서만 다른 것:

| 항목 | perf | 이유 |
|---|---|---|
| DB 데이터 | main 서빙 테이블 8개의 고정 덤프 | 복제 후의 prod를 잰다(위 "어떤 데이터로 재나") |
| DB 비밀번호 | 새로 만든다 | prod 비밀번호를 다른 곳에 퍼뜨리지 않는다 |
| DB 자동 백업·마이너 업그레이드 | 끈다 | 임시 DB다. 측정 기간에 엔진 버전이 바뀌지 않게 한다 |
| 수집 스케줄러 | `COLLECTION_SCHEDULER_ENABLED=false` | 부하 중 쓰기가 섞이지 않게 |
| Riot API 키 | 비운다 | 스케줄러가 꺼져 있어 쓰지 않는다. 비밀값을 늘리지 않는다 |
| nginx `:8080` | 모니터링 EC2(10.0.0.68)만 허용 | `project-public` SG가 8080을 전 세계에 연다. 데이터 사본과 인증 없는 `/admin/*`을 노출하지 않는다 |

> 표기: **[로컬]** = 내 노트북(**저장소 루트 `2026-dfgg`에서 실행한다** — `backend/ops/…` 상대 경로를 쓴다), **[perf]** = `ssh dfgg-be-perf`, **[prod]** = `ssh dfgg-be-prod`(읽기만), **[dev]** = `ssh dfgg-be-dev`(읽기만), **[모니터링]** = `ssh dfgg-be-monitoring`.
> AWS 작업(RDS 생성·켜기·끄기·삭제, EC2 켜기·끄기, S3, CloudWatch)은 **[모니터링]**에서 AWS CLI v2로 한다. 인스턴스 역할 `ec2-project`에 권한이 있다(2026-10-12, 조회·대기·CloudWatch·S3·EC2 DryRun·RDS 생성/삭제를 자원을 만들지 않는 방식으로 확인). 로컬 AWS CLI는 자격 증명이 없어 쓰지 않는다.

### 1. 모니터링 EC2 준비 — [모니터링]

**psql·pg_dump·pg_restore** — Ubuntu 패키지(18.x). 덤프·복원 도구가 서버(16)보다 새것이어야 한다:

```bash
sudo apt-get update && sudo apt-get install -y postgresql-client
```

기대: `pg_dump --version`이 `18.x`.

**k6** — GitHub 릴리스의 공식 arm64 바이너리. **k6 apt 저장소(`dl.k6.io/deb`)는 arm64를 지원하지 않는다**(모니터링 EC2는 Graviton) — `doesn't support architecture 'arm64'`, `Unable to locate package k6`로 실패한다.
로컬에서 B2·B3를 검증한 버전(v2.3.0)과 맞춘다. 체크섬이 맞을 때만 설치한다:

```bash
cd /tmp && V=v2.3.0 && curl -fsSLO "https://github.com/grafana/k6/releases/download/$V/k6-$V-linux-arm64.tar.gz" && curl -fsSLO "https://github.com/grafana/k6/releases/download/$V/k6-$V-checksums.txt" && grep " k6-$V-linux-arm64.tar.gz$" "k6-$V-checksums.txt" | sha256sum -c - && tar -xzf "k6-$V-linux-arm64.tar.gz" && sudo install -m 755 "k6-$V-linux-arm64/k6" /usr/local/bin/k6 && rm -rf "k6-$V-linux-arm64" "k6-$V-linux-arm64.tar.gz" "k6-$V-checksums.txt"
```

기대: `k6-v2.3.0-linux-arm64.tar.gz: OK`, 그리고 `k6 version`이 `k6 v2.3.0 … linux/arm64`.

**AWS CLI v2** — 공식 설치 파일(arm64). 예전 기록의 CLI 버그(2.31.35)는 이 버전에서 재현되지 않는다:

```bash
cd /tmp && curl -sSo awscliv2.zip https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip && unzip -q awscliv2.zip && sudo ./aws/install && rm -rf aws awscliv2.zip
```

리전과 페이저를 고정한다(이후 모든 `aws` 명령이 쓴다):

```bash
echo 'export AWS_DEFAULT_REGION=ap-northeast-2 AWS_PAGER=""' >> ~/.bashrc && . ~/.bashrc
```

```bash
aws --version && aws sts get-caller-identity --query Arn --output text
```

기대: `aws-cli/2.x`, `…assumed-role/ec2-project/i-0dddeedbf8bf284a5`.

**저장소** (측정 스크립트·요청 SQL·`apply.sh`용). 기본 브랜치(`dev`)에는 백엔드 운영 파일이 없다 — **이 파일들이 있는 브랜치로 맞춘다**(아래 `<브랜치>`):

```bash
git clone https://github.com/woowacourse-teams/2026-dfgg.git ~/2026-dfgg
```

```bash
cd ~/2026-dfgg && git fetch origin && git checkout -B <브랜치> origin/<브랜치> && ls backend/ops/perf
```

기대: `README.md  k6  run.sh`. 저장소 파일이 바뀌면(로컬에서 push) 같은 명령으로 다시 맞춘다.

### 2. perf RDS 만들기 — [모니터링]

**비밀번호를 만들어 `~/.pgpass`에만 둔다.** 화면·명령 기록에 남기지 않는다. 호스트는 RDS가 생긴 뒤 채운다.

```bash
touch ~/.pgpass && chmod 600 ~/.pgpass && sed -i '/^PERF_HOST:/d' ~/.pgpass && echo "PERF_HOST:5432:*:postgres:postgres" >> ~/.pgpass
```

**RDS 생성** — prod 기준값 그대로, 위 표의 차이만 둔다:

```bash
aws rds create-db-instance \
  --db-instance-identifier dfgg-db-perf \
  --db-instance-class db.t4g.micro \
  --engine postgres --engine-version 16.14 \
  --allocated-storage 20 --storage-type gp2 --storage-encrypted \
  --master-username postgres --master-user-password "$(grep -m1 '^PERF_HOST:' ~/.pgpass | cut -d: -f5)" --db-name dfgg \
  --db-subnet-group-name project-rds-subnet-group \
  --vpc-security-group-ids sg-0ce905a3e798a6a78 \
  --availability-zone ap-northeast-2b --no-multi-az --no-publicly-accessible \
  --db-parameter-group-name default.postgres16 --option-group-name default:postgres-16 \
  --ca-certificate-identifier rds-ca-rsa2048-g1 \
  --backup-retention-period 0 --no-auto-minor-version-upgrade --no-deletion-protection \
  --no-enable-performance-insights --monitoring-interval 0 \
  --tags Key=Name,Value=dfgg-db-perf Key=Purpose,Value=dfgg-load-test \
  --query 'DBInstance.DBInstanceStatus' --output text
```

기대: `creating`. available까지 5~15분 걸린다. (`sg-0ce905a3e798a6a78` = `project-db`. 비밀번호는 명령 기록이 아니라 `.pgpass`에서 읽는다)

**available까지 기다리고 엔드포인트를 `~/.pgpass`에 채운다:**

```bash
aws rds wait db-instance-available --db-instance-identifier dfgg-db-perf && host=$(aws rds describe-db-instances --db-instance-identifier dfgg-db-perf --query 'DBInstances[0].Endpoint.Address' --output text) && sed -i "s/^PERF_HOST:/${host}:/" ~/.pgpass && echo "$host"
```

대기는 최대 30분이다. 시간이 넘어 끝나면(`Max attempts exceeded`) 같은 명령을 다시 실행한다.

접속 문자열을 `~/.bashrc`에 넣어 **새 셸에서도 자동으로** 잡히게 한다(주소만 들어간다. 비밀번호는 `.pgpass`).
접속 문자열이 비면 psql·pg_restore가 로컬 소켓으로 붙으려다 `/var/run/postgresql/.s.PGSQL.5432` 오류로 실패한다 — 새 SSH 접속마다 환경 변수가 사라져 실제로 두 번 겪었다:

```bash
grep -q 'export PERF_DB_URL=' ~/.bashrc || echo 'export PERF_DB_URL="postgresql://postgres@$(grep -m1 "^dfgg-db-perf\." ~/.pgpass | cut -d: -f1):5432/dfgg"' >> ~/.bashrc; . ~/.bashrc && psql "$PERF_DB_URL" -XAtc "SELECT version()"
```

기대: `PostgreSQL 16.14 …`. 비밀번호를 묻지 않으면 `.pgpass`가 맞게 된 것이다.

계정이 공유다. 식별자에 `dfgg-` 접두사를 붙이고, 비슷한 이름의 옛 RDS(`dfgg-test`, `dfgg-db` 등)와 헷갈리지 않는다.

### 3. perf EC2 기본 — [perf]

**swap 2GB** (prod와 같다. t4g.small 1.8GB에서 힙 384MB + 메타스페이스 + nginx가 돈다):

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
```

```bash
echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
```

기대: `swapon --show`에 `/swapfile 2G`.

**Corretto 21** (Amazon 공식 apt 저장소) 외 패키지:

```bash
wget -O - https://apt.corretto.aws/corretto.key | sudo gpg --dearmor -o /usr/share/keyrings/corretto-keyring.gpg
```

```bash
echo "deb [signed-by=/usr/share/keyrings/corretto-keyring.gpg] https://apt.corretto.aws stable main" | sudo tee /etc/apt/sources.list.d/corretto.list
```

```bash
sudo apt-get update && sudo apt-get install -y java-21-amazon-corretto-jdk nginx prometheus-node-exporter postgresql-client jq
```

기대: `java -version`이 `Corretto-21.0.12`. 더 새 버전이 깔렸으면 버전을 적어 두고 meta에 남긴다
(`apt-cache madison java-21-amazon-corretto-jdk`로 `1:21.0.12.8-1`이 있으면 `=1:21.0.12.8-1`을 붙여 맞춘다).

**node exporter를 로컬에만 바인딩** (prod와 같다. SG 안의 다른 호스트가 긁지 못하게):

```bash
echo 'ARGS="--web.listen-address=127.0.0.1:9100"' | sudo tee /etc/default/prometheus-node-exporter && sudo systemctl restart prometheus-node-exporter
```

기대: `ss -tlnp | grep 9100`이 `127.0.0.1:9100`만 보인다. (systemd drop-in의 `Environment=`는 이 파일이 덮어써서 먹지 않는다.)

**디렉터리**:

```bash
sudo mkdir -p /opt/dfgg/blue /etc/dfgg && sudo chown ubuntu:ubuntu /opt/dfgg /opt/dfgg/blue && mkdir -p ~/logs ~/app-config
```

### 4. 앱 설치와 스키마 생성 — [perf] · [로컬]

**systemd 유닛과 슬롯 환경** — prod 파일을 그대로 옮긴다. [로컬]에서:

```bash
ssh dfgg-be-prod 'cat /etc/systemd/system/dfgg@.service' | ssh dfgg-be-perf 'sudo tee /etc/systemd/system/dfgg@.service > /dev/null'
```

```bash
ssh dfgg-be-prod 'cat /etc/dfgg/blue.env' | ssh dfgg-be-perf 'sudo tee /etc/dfgg/blue.env > /dev/null'
```

기대: [perf]에서 `systemctl cat dfgg@.service`에 `-Xms128m -Xmx384m`, `MemoryMax=650M`. `/etc/dfgg/blue.env`에 `SERVER_PORT=8081`, `SPRING_JPA_HIBERNATE_DDL_AUTO=none`.

**앱 설정(.env)** — prod 것을 옮긴 뒤 perf에 맞게 네 줄을 바꾼다. 비밀값이 노트북 디스크에 남지 않도록 파이프로 바로 넘긴다. [로컬]에서:

```bash
ssh dfgg-be-prod 'cat /home/ubuntu/app-config/.env' | ssh dfgg-be-perf 'install -m 600 /dev/stdin /home/ubuntu/app-config/.env'
```

DB 주소·비밀번호를 perf RDS 것으로 바꾼다. 비밀번호는 모니터링 EC2의 `~/.pgpass`(2단계)에서 바로 넘긴다. [로컬]에서:

```bash
ssh dfgg-be-monitoring "grep '^dfgg-db-perf\.' ~/.pgpass | cut -d: -f1,5" | ssh dfgg-be-perf 'IFS=: read -r host password; if [ -z "$host" ] || [ -z "$password" ]; then echo "실패: perf RDS 주소·비밀번호를 못 받았다 — 2단계에서 엔드포인트를 .pgpass에 채웠는지 확인"; exit 1; fi; sed -i -E "s#(DB_URL=jdbc:postgresql://)[^:/]+#\1${host}#; s#^DB_PASSWORD=.*#DB_PASSWORD=${password}#" ~/app-config/.env && grep -q "^DB_URL=jdbc:postgresql://dfgg-db-perf\." ~/app-config/.env && echo "OK: $host"'
```

기대: `OK: dfgg-db-perf.…`. **`실패`가 나오면 여기서 멈춘다.** 이 단계가 조용히 건너뛰어지면 `.env`가 prod를 가리킨 채로 남는다(2026-10-12 실제로 일어났다 — perf 앱이 prod DB에 접속해 Flyway 검증까지 했다).

[perf]에서 수집을 끄고 Riot 키를 쓰지 않는 값으로 바꾼다. **비우면 안 된다** — 앱이 `riot.api.key`를 `@NotBlank`로 검증해 기동에 실패한다:

```bash
sed -i -E 's#^COLLECTION_SCHEDULER_ENABLED=.*#COLLECTION_SCHEDULER_ENABLED=false#; s#^RIOT_API_KEY=.*#RIOT_API_KEY=perf-unused#' ~/app-config/.env
```

```bash
grep -E '^(DB_URL|DB_USERNAME|COLLECTION_SCHEDULER_ENABLED|RIOT_API_KEY)=' ~/app-config/.env && grep -c '^DB_PASSWORD=.\+' ~/app-config/.env
```

기대: `DB_URL`이 `dfgg-db-perf…:5432/dfgg`, `DB_USERNAME=postgres`, 스케줄러 `false`, Riot 키 `perf-unused`, 마지막 줄 `1`(비밀번호가 들어 있다). **`dfgg-db-prod`나 `dfgg-db-main`이 남아 있으면 절대 앱을 띄우지 않는다** — 첫 기동이 그 DB에 Flyway를 돌린다.

앱은 작업 디렉터리의 `.env`를 읽는다(prod도 같은 심볼릭 링크다):

```bash
ln -sfn /home/ubuntu/app-config/.env /opt/dfgg/blue/.env
```

**JAR** — prod에서 지금 돌고 있는 파일을 그대로 쓴다. 같은 커밋·같은 모델이 들어 있어 "prod와 같은 앱"이 확실하다. [로컬]에서 활성 슬롯부터 본다:

```bash
ssh dfgg-be-prod 'cat /etc/nginx/conf.d/dfgg-upstream.conf'
```

`8081`이면 blue, `8082`면 green이다. 아래는 blue일 때:

```bash
ssh dfgg-be-prod 'cat /opt/dfgg/blue/dfgg-blue.jar' | ssh dfgg-be-perf 'cat > /opt/dfgg/blue/dfgg-blue.jar'
```

```bash
ssh dfgg-be-prod 'sha256sum /opt/dfgg/blue/dfgg-blue.jar'; ssh dfgg-be-perf 'sha256sum /opt/dfgg/blue/dfgg-blue.jar'
```

기대: 두 해시가 같다. 이 해시를 `APP_COMMIT`으로 남긴다(예: `prod-jar sha256:1104c290…`).

**첫 기동 = 스키마 생성** — 빈 DB에서 앱이 뜨면 Flyway가 V1~V9를 적용한다(CI가 매번 하는 것과 같다). [perf]:

**`DB_URL`이 perf가 아니면 시작하지 않는다:**

```bash
if grep -q '^DB_URL=jdbc:postgresql://dfgg-db-perf\.' ~/app-config/.env; then sudo systemctl daemon-reload && sudo systemctl enable --now dfgg@blue; else echo "멈춤: DB_URL이 dfgg-db-perf가 아니다 — 앱을 띄우지 않는다"; grep '^DB_URL=' ~/app-config/.env; fi
```

기동을 기다린다. 실패하면(systemd가 3번 재시도 후 포기) 바로 끝난다:

```bash
for i in $(seq 1 90); do if curl -sf http://127.0.0.1:8081/actuator/health/readiness > /dev/null; then echo ready; break; fi; if systemctl is-failed --quiet dfgg@blue; then echo "기동 실패"; journalctl -u dfgg@blue --no-pager -n 30 | grep -A8 'APPLICATION FAILED'; break; fi; sleep 2; done
```

기동 로그의 접속 주소도 확인한다: `journalctl -u dfgg@blue --no-pager | grep 'Database: jdbc' | tail -1` → `dfgg-db-perf`여야 한다.

**스키마 확인 — [모니터링]에서** (perf EC2가 아니다. `PERF_DB_URL`과 `~/.pgpass`는 모니터링 EC2에만 있다. perf에서 실행하면 접속 대상이 비어 로컬 소켓 `/var/run/postgresql/.s.PGSQL.5432` 오류가 난다).
접속 문자열은 2단계에서 `~/.bashrc`에 넣었다. `echo "$PERF_DB_URL"`이 비어 있으면 `. ~/.bashrc`를 먼저 한다.

```bash
psql "$PERF_DB_URL" -XAtc "SELECT max(version::int) FROM flyway_schema_history WHERE success" -c "SELECT indisvalid FROM pg_index WHERE indexrelid = 'idx_nmp_build_transition'::regclass" -c "SELECT count(*) FROM normalized_match_participants"
```

기대: `9`, `t`, `0`(아직 빈 테이블). 데이터를 싣는 동안은 앱을 멈춘다. [perf]:

```bash
sudo systemctl stop dfgg@blue
```

### 5. 서빙 데이터 적재 — [모니터링] · [로컬]

#### 5-1. 고정 덤프 만들기 (처음 한 번만)

이미 S3에 쓸 덤프가 있으면 건너뛰고 5-2로 간다(`dfgg/backup/perf/` 목록은 5-2 첫 명령으로 본다).

**main 접속** — dev `.env`(main을 가리킨다)에서 주소·계정·비밀번호를 모니터링 EC2의 `~/.pgpass`로 바로 넘긴다. [로컬]에서:

```bash
ssh dfgg-be-dev 'v(){ grep -m1 "^$1=" ~/app-config/.env | cut -d= -f2-; }; u=$(v DB_URL); h=${u#jdbc:postgresql://}; echo "${h%%[:/]*}:$(v DB_USERNAME):$(v DB_PASSWORD)"' | ssh dfgg-be-monitoring 'IFS=: read -r host user password && password=$(printf %s "$password" | sed "s/[\\\\:]/\\\\&/g") && touch ~/.pgpass && chmod 600 ~/.pgpass && sed -i "/^dfgg-db-main\./d" ~/.pgpass && echo "$host:5432:*:$user:$password" >> ~/.pgpass && echo "main: $host ($user)"'
```

[모니터링]에서 (main은 읽기만 한다 — 세션을 읽기 전용으로 연다):

```bash
export MAIN_DB_URL="postgresql://$(grep -m1 '^dfgg-db-main\.' ~/.pgpass | cut -d: -f4)@$(grep -m1 '^dfgg-db-main\.' ~/.pgpass | cut -d: -f1):5432/postgres" PGOPTIONS='-c default_transaction_read_only=on'
```

**덤프 전 검사** — main의 Flyway 버전이 perf(=저장소)와 같고, 서빙 테이블 인덱스가 모두 정상이어야 한다:

```bash
psql "$MAIN_DB_URL" -XAtc "SELECT max(version::int) FROM flyway_schema_history WHERE success" -c "SELECT count(*) FROM pg_index x JOIN pg_class c ON c.oid = x.indrelid WHERE c.relname IN ('champions','champion_tags','items','champion_item_stats','champion_item_rollup','champion_pair_item_stats','item_meta_stats','normalized_match_participants') AND NOT (x.indisvalid AND x.indisready)"
```

기대: `9`, `0`. 다르면 멈춘다 — 스키마가 다른 데이터를 perf 스키마에 실을 수 없다.

**덤프** — 수집 DB(t4g.micro)에 부하가 가니 실사용·수집이 적은 시간에 한다. 패치 파이프라인의 05:00 복제와 겹치지 않게 한다:

```bash
mkdir -p ~/perf-data && export PERF_DATA="serving-$(date -u +%y%m%d).dump" && sed -i '/^export PERF_DATA=/d' ~/.bashrc && echo "export PERF_DATA=$PERF_DATA" >> ~/.bashrc && pg_dump --dbname="$MAIN_DB_URL" --data-only --format=custom --compress=6 -t public.champions -t public.champion_tags -t public.items -t public.champion_item_stats -t public.champion_item_rollup -t public.champion_pair_item_stats -t public.item_meta_stats -t public.normalized_match_participants --file ~/perf-data/$PERF_DATA && ls -lh ~/perf-data/
```

**S3에 고정** — 공유 버킷이다. `dfgg/` 밖에는 쓰지 않는다. 같은 이름이 있으면 덮어쓰지 않고 멈춘다:

```bash
key="dfgg/backup/perf/$PERF_DATA"
if aws s3api head-object --bucket techcourse-project-2026 --key "$key" > /dev/null 2>&1; then
  echo "이미 있다: $key — 덮어쓰지 않는다"
else
  cd ~/perf-data && sha256sum "$PERF_DATA" > "$PERF_DATA.sha256" \
    && aws s3 cp "$PERF_DATA" "s3://techcourse-project-2026/$key" \
    && aws s3 cp "$PERF_DATA.sha256" "s3://techcourse-project-2026/$key.sha256" \
    && cat "$PERF_DATA.sha256"
fi
```

main 접속 정보는 이제 필요 없다:

```bash
sed -i '/^dfgg-db-main\./d' ~/.pgpass && unset MAIN_DB_URL PGOPTIONS
```

#### 5-2. perf에 싣기

**고정 덤프 받기** — 목록을 보고 쓸 덤프를 고른다(전후 비교 내내 같은 것):

```bash
aws s3 ls s3://techcourse-project-2026/dfgg/backup/perf/
```

아직 덤프가 없으면 아무것도 나오지 않고 종료 코드가 1이다(오류가 아니다). 그때는 5-1부터 한다.

```bash
export PERF_DATA=serving-<YYMMDD>.dump && sed -i '/^export PERF_DATA=/d' ~/.bashrc && echo "export PERF_DATA=$PERF_DATA" >> ~/.bashrc && mkdir -p ~/perf-data && aws s3 cp "s3://techcourse-project-2026/dfgg/backup/perf/$PERF_DATA" ~/perf-data/ && aws s3 cp "s3://techcourse-project-2026/dfgg/backup/perf/$PERF_DATA.sha256" ~/perf-data/ && cd ~/perf-data && sha256sum -c "$PERF_DATA.sha256"
```

기대: `serving-….dump: OK`. 5-1에서 방금 만들었다면 받지 않고 `sha256sum`만 확인해도 된다.

**적재** — 데이터만 넣는다. 스키마·인덱스는 4단계 Flyway가 만든 것을 쓴다.

pg_restore가 DB에 직접 넣지 않고 **SQL을 뽑기만 하고, psql이 한 트랜잭션으로 넣는다.** 이유:
- 덤프를 pg_dump 18로 만들어 pg_restore도 18이어야 한다(16은 이 파일을 못 읽는다). 그런데 pg_restore 18은 시작할 때 `SET transaction_timeout = 0;`을 보내고, **16 서버는 이 설정(17부터)을 몰라 오류를 낸다** — `--exit-on-error`면 초기화에서 멈춘다(2026-10-12 실제로 겪었다).
  뽑은 SQL에서 16이 모르는 구문은 이 한 줄뿐이라 그 줄만 뺀다. `\restrict` 줄은 psql 18이 처리한다.
- `ON_ERROR_STOP` — 다른 오류는 즉시 멈춘다. `--single-transaction` — 중간에 실패하면 전부 되돌아가 빈 상태로 남는다(`TRUNCATE` 없이 다시 실행하면 된다).
- 덤프 순서대로 하나씩 넣는다(`champion_tags → champions` 외래키 — 참조되는 쪽이 먼저 담겨 있다).

인덱스가 있는 테이블에 넣으므로 오래 걸린다(참가자 약 430만 행). 백그라운드로 돌린다:

```bash
if [ -n "$PERF_DB_URL" ] && [ -n "$PERF_DATA" ] && [ -s ~/perf-data/"$PERF_DATA" ]; then nohup bash -c 'set -o pipefail; pg_restore --data-only -f - ~/perf-data/"$PERF_DATA" | sed "/^SET transaction_timeout = 0;$/d" | psql "$PERF_DB_URL" -X -q -v ON_ERROR_STOP=1 --single-transaction; echo "restore exit=$?"' > ~/perf-data/restore.log 2>&1 & echo "시작: PID $!"; else echo "멈춤: PERF_DB_URL='$PERF_DB_URL' PERF_DATA='$PERF_DATA' — . ~/.bashrc 후 다시"; fi
```

접속이나 덤프 이름이 비어 있으면 시작하지 않는다. 진행 중에는 로그가 거의 비어 있다(`-q`). 어디까지 왔는지는 DB에서 본다:

```bash
psql "$PERF_DB_URL" -XAtc "SELECT relid::regclass, tuples_processed FROM pg_stat_progress_copy"
```

끝나면 결과를 본다:

```bash
cat ~/perf-data/restore.log
```

기대: 마지막 줄 `restore exit=0`(그 위의 `set_config`·`setval` 결과 몇 줄은 정상 출력이다). `ERROR`와 함께 `restore exit=3`이면 트랜잭션이 되돌려져 빈 상태다 — 원인을 고치고 그대로 다시 실행한다.

적재는 디스크 쓰기가 많아 gp2 버스트 크레딧(`BurstBalance`)을 쓴다. **측정은 크레딧이 회복된 뒤에 한다**(20 미만이면 무효 — "무효로 하고 다시 재는 회차").

**VACUUM ANALYZE** — 막 넣은 테이블은 통계와 visibility map이 비어 있다. 통계가 없으면 실행 계획이 prod와 달라지고,
visibility map이 비면 커버링 인덱스(V9)의 index-only scan이 힙을 다시 읽는다 — **prod와 다른 병목이 나온다.**

```bash
vacuumdb --dbname="$PERF_DB_URL" --analyze --jobs=2
```

**확인** — 행 수를 남기고, 커버링 인덱스가 힙을 읽지 않는지 본다:

```bash
for t in champion_item_rollup champion_item_stats champion_pair_item_stats champion_tags champions item_meta_stats items normalized_match_participants; do echo "$t $(psql "$PERF_DB_URL" -XAtc "SELECT count(*) FROM $t")"; done | tee ~/perf-data/$PERF_DATA.counts.txt
```

```bash
psql "$PERF_DB_URL" -Xc "EXPLAIN (ANALYZE, BUFFERS) SELECT core_item_purchase_order, patch, win FROM normalized_match_participants WHERE champion_id = 64 AND position = 'JUNGLE' AND core_item_purchase_order_complete"
```

기대: 참가자 약 400만 행대. 실행 계획이 `Index Only Scan using idx_nmp_build_transition`이고 `Heap Fetches`가 0에 가깝다(리 신/정글, 챔피언 ID 64).
처음 만든 덤프라면 행 수 파일도 S3에 남긴다 — 나중에 다시 실을 때 이 숫자와 같아야 한다:

```bash
aws s3 cp ~/perf-data/$PERF_DATA.counts.txt "s3://techcourse-project-2026/dfgg/backup/perf/$PERF_DATA.counts.txt"
```

**pg_stat_statements** — preload는 기본 파라미터 그룹이 이미 하므로 확장만 만든다:

```bash
psql "$PERF_DB_URL" -Xc "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" -c "SELECT count(*) FROM pg_stat_statements"
```

**앱 다시 켜기** — [perf]:

```bash
sudo systemctl start dfgg@blue && for i in $(seq 1 90); do if curl -sf http://127.0.0.1:8081/actuator/health/readiness > /dev/null; then echo ready; break; fi; if systemctl is-failed --quiet dfgg@blue; then echo "기동 실패 — journalctl -u dfgg@blue -n 50"; break; fi; sleep 2; done
```

모니터링 EC2 디스크는 Prometheus와 같이 쓴다. 덤프 파일은 S3에 있으니 지운다(`rm ~/perf-data/*.dump`).

### 6. nginx — [perf]

CD가 설치하는 설정 두 개는 저장소 파일을 그대로 쓴다. [로컬]에서:

```bash
for f in dfgg-log-format.conf dfgg-metrics.conf; do ssh dfgg-be-perf "sudo tee /etc/nginx/conf.d/$f > /dev/null" < backend/ops/nginx/$f; done
```

upstream과 사이트 설정은 prod에서 옮긴다:

```bash
ssh dfgg-be-prod 'cat /etc/nginx/conf.d/dfgg-upstream.conf' | ssh dfgg-be-perf 'sudo tee /etc/nginx/conf.d/dfgg-upstream.conf > /dev/null'
```

```bash
ssh dfgg-be-prod 'cat /etc/nginx/sites-available/dfgg-backend' | ssh dfgg-be-perf 'sudo tee /etc/nginx/sites-available/dfgg-backend > /dev/null'
```

[perf]에서 upstream이 `127.0.0.1:8081`인지 확인하고(prod가 green이었다면 8081로 고친다), **perf에만** 접근 제한을 넣는다:

```bash
sudo sed -i 's#^\(\s*server_tokens off;\)#\1\n    allow 10.0.0.68;\n    deny all;#' /etc/nginx/sites-available/dfgg-backend
```

```bash
sudo ln -sfn /etc/nginx/sites-available/dfgg-backend /etc/nginx/sites-enabled/dfgg-backend && sudo rm -f /etc/nginx/sites-enabled/default && sudo nginx -t && sudo systemctl reload nginx
```

기대:
- [perf] `curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/champions` → `403` (perf 자신도 허용 목록 밖이다 — 정상)
- [모니터링] `curl -s -o /dev/null -w '%{http_code}\n' http://10.0.0.231:8080/api/champions` → `200`
- [모니터링] `curl -s -o /dev/null -w '%{http_code}\n' http://10.0.0.231:8080/actuator/health` → `404`
- [모니터링] `curl -s http://10.0.0.231:9090/blue | head -3` → Prometheus 지표 텍스트

### 7. Prometheus 수집 — [저장소] · [모니터링]

`backend/ops/prometheus/prometheus.yml`의 `dfgg-app` job(`/blue`)과 `dfgg-node` job(`/node`)에 perf 대상이 들어 있다(`10.0.0.231:9090`, 라벨 `env: perf`, 앱에는 `slot: blue`도). perf에는 green 슬롯이 없으니 `dfgg-app-green`에는 넣지 않았다.

[로컬]에서 커밋·push한 브랜치를 [모니터링] 저장소에 받고(1단계의 `git fetch … checkout` 명령), **저장소 안에서** 반영한다.
`BE/dev`로 바로 push하지 않는다 — `be-cd.yml`이 `BE/dev` push마다 dev 배포와 prod 승인 요청을 띄운다. 운영 스크립트만 바뀌는 커밋은 기능 브랜치로 올리고 PR로 합친다.

```bash
cd ~/2026-dfgg && sudo ./backend/ops/monitoring/apply.sh --dry-run
```

기대: 바뀌는 파일이 `prometheus/prometheus.yml` 하나다. 다른 파일이 바뀐다고 나오면 서버가 저장소와 어긋나 있다는 뜻이니 반영하지 말고 먼저 확인한다.
(2026-10-12 확인: 서버 설치본은 저장소 HEAD와 수집 대상·라벨이 같고 설명 주석·공백만 다르다 — 덮어쓰면 주석만 사라진다.)

```bash
sudo ./backend/ops/monitoring/apply.sh
```

기대: Grafana에서 `env="perf"`의 HTTP p95, Hikari, CPU, JVM 지표가 보인다. perf가 꺼져 있는 동안은 대상이 down으로 보인다 — 정상이다.

### 8. 요청 파일 [로컬]

만들어둔 /ops/perf/k6/data/requests.json 을 사용한다.

### 9. 원격 smoke — [모니터링]

```bash
export BASE_URL=http://10.0.0.231:8080 PERF_DB_INSTANCE=dfgg-db-perf APP_COMMIT='prod-jar sha256:<해시 앞 8자리>' PERF_DATA=serving-<YYMMDD>.dump
```

```bash
cd ~/2026-dfgg && ./backend/ops/perf/run.sh env-check -e SCENARIO=smoke
```

기대: 에러 0건, `results/…-env-check/`에 `pg_stat_statements.csv`와 `cloudwatch.txt`가 생긴다. 측정 중 모니터링 EC2의 CPU가 여유 있는지(`top`) 함께 본다 — 부하 발생기가 먼저 막히면 안 된다.

여기까지 되면 환경 구성 끝이다. 측정하지 않을 때는 바로 끈다(다음 절).

---

## 켜기·끄기 — 측정할 때만 켠다

꺼져 있는 동안 EC2는 디스크(EBS)만, RDS는 스토리지만 비용이 나온다. 설치해 둔 것과 데이터는 그대로 남는다.

| 주의 | 대응 |
|---|---|
| EC2를 다시 켜면 **공인 IP가 바뀐다**(사설 IP 10.0.0.231은 그대로) | 켜는 명령이 새 IP를 출력한다. `~/.ssh/config`의 `dfgg-be-perf`를 갱신한다 |
| RDS는 **멈춘 지 7일이 지나면 AWS가 자동으로 다시 켠다** | 측정 사이가 일주일을 넘으면 RDS를 지우고, 다음에 2·4(첫 기동)·5-2단계로 다시 만든다(고정 덤프가 있어 main은 필요 없다) |
| 앱은 부팅 때 자동으로 뜨는데, DB가 아직 없으면 실패한다 | **RDS를 먼저 켜고 available을 기다린 뒤** EC2를 켠다. 끌 때는 EC2 먼저 |
| 켠 직후 RDS 캐시가 비어 있다 | 측정 전에 **버리는 워밍업 1회**를 한다(아래 "측정 1회의 순서") |

**켜기** — [모니터링]:

RDS 먼저:

```bash
status=$(aws rds describe-db-instances --db-instance-identifier dfgg-db-perf --query 'DBInstances[0].DBInstanceStatus' --output text) && echo "RDS: $status"
```

```bash
if [ "$status" = stopped ]; then aws rds start-db-instance --db-instance-identifier dfgg-db-perf --query 'DBInstance.DBInstanceStatus' --output text; fi; aws rds wait db-instance-available --db-instance-identifier dfgg-db-perf && echo "RDS available"
```

EC2는 이름과 사설 IP가 둘 다 맞을 때만 켠다(권한이 계정 전체 인스턴스에 열려 있다):

```bash
read -r id ip <<< "$(aws ec2 describe-instances --filters Name=tag:Name,Values=dfgg-be-perf Name=instance-state-name,Values=pending,running,stopping,stopped --query 'Reservations[0].Instances[0].[InstanceId,PrivateIpAddress]' --output text)" && echo "$id $ip"
```

```bash
if [ "$ip" != 10.0.0.231 ]; then echo "멈춤: dfgg-be-perf가 아니다 ($id $ip)"; else aws ec2 start-instances --instance-ids "$id" --query 'StartingInstances[0].CurrentState.Name' --output text && aws ec2 wait instance-running --instance-ids "$id" && echo "공인 IP: $(aws ec2 describe-instances --instance-ids "$id" --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)"; fi
```

[로컬]에서 SSH 설정의 IP를 바꾼다(macOS sed):

```bash
sed -i '' "/^Host dfgg-be-perf$/,/^Host /s/^\([[:space:]]*HostName[[:space:]]\).*/\1<새 공인 IP>/" ~/.ssh/config && grep -A1 '^Host dfgg-be-perf$' ~/.ssh/config
```

[perf]에서 앱이 떴는지 본다:

```bash
for i in $(seq 1 90); do if curl -sf http://127.0.0.1:8081/actuator/health/readiness > /dev/null; then echo ready; break; fi; if systemctl is-failed --quiet dfgg@blue; then echo "기동 실패 — journalctl -u dfgg@blue -n 50"; break; fi; sleep 2; done
```

**끄기** — [모니터링]:

EC2 먼저(켜기의 `read -r id ip …` 명령으로 `id`·`ip`를 잡은 뒤):

```bash
if [ "$ip" != 10.0.0.231 ]; then echo "멈춤: dfgg-be-perf가 아니다 ($id $ip)"; else aws ec2 stop-instances --instance-ids "$id" --query 'StoppingInstances[0].CurrentState.Name' --output text && aws ec2 wait instance-stopped --instance-ids "$id" && echo "EC2 stopped"; fi
```

그다음 RDS. RDS에는 "stopped까지 대기"가 없어(대기는 `db-instance-available`·`db-instance-deleted`뿐) 상태를 직접 본다:

```bash
aws rds stop-db-instance --db-instance-identifier dfgg-db-perf --query 'DBInstance.DBInstanceStatus' --output text && until [ "$(aws rds describe-db-instances --db-instance-identifier dfgg-db-perf --query 'DBInstances[0].DBInstanceStatus' --output text)" = stopped ]; do sleep 30; done && echo "RDS stopped — 7일 뒤 AWS가 자동으로 켠다"
```

---

## 측정 1회의 순서

회차마다 앱을 재기동해 같은 출발점에서 시작한다. 워밍업은 k6가 한다.

**켠 직후 한 번** — RDS 캐시를 데우는 버리는 회차. 결과는 비교에 쓰지 않는다:

```bash
./backend/ops/perf/run.sh discard-warmup -e SCENARIO=target -e TARGET_RPS=10 -e WARMUP=1m -e DURATION=3m
```

**① [perf] 앱 재기동**

```bash
sudo systemctl restart dfgg@blue && for i in $(seq 1 90); do if curl -sf http://127.0.0.1:8081/actuator/health/readiness > /dev/null; then echo ready; break; fi; if systemctl is-failed --quiet dfgg@blue; then echo "기동 실패 — journalctl -u dfgg@blue -n 50"; break; fi; sleep 2; done
```

**② [모니터링] 측정** (`BASE_URL`, `PERF_DB_URL`, `PERF_DB_INSTANCE`, `APP_COMMIT`, `PERF_DATA`가 잡혀 있어야 한다 — 9단계)

```bash
./backend/ops/perf/run.sh before-smoke -e SCENARIO=smoke
```

smoke는 에러 0건이어야 다음으로 간다. target과 ramp는 회차마다 ①을 다시 하고 실행한다.

```bash
./backend/ops/perf/run.sh before-1 -e SCENARIO=target -e TARGET_RPS=10 -e WARMUP=2m -e DURATION=5m -e P95_MS=1000 -e ERROR_RATE=0.01
```

```bash
./backend/ops/perf/run.sh before-ramp -e SCENARIO=ramp -e TARGET_RPS=10
```

- target은 **3회**(`before-1`, `before-2`, `before-3`) 하고 중앙값을 쓴다. 개선 후는 `after-1~3`, `after-ramp`.
- 회차 사이에 RDS `BurstBalance`가 회복될 시간을 둔다.
- `run.sh`의 종료 코드 99는 목표(threshold) 미달이다. 측정은 끝났고 결과도 다 남는다.
- k6가 끝난 뒤 `run.sh`는 **CloudWatch 지표가 올라올 때까지 2~4분 더 기다린다**(`CloudWatch 지표 대기` 출력). RDS 지표는 늦게 올라와서, 바로 읽으면 측정 구간이 비어 엉뚱한 값이 남는다. `cloudwatch.txt`의 `points`가 구간 분 수보다 적으면 빠진 분이 있다는 뜻이다.

## 개선 후 앱 배포 — [perf]

개선 커밋을 CD와 같은 방식으로 perf에서 빌드한다. 모델은 prod 것을 쓴다(CD도 app-config의 모델로 빌드한다).

```bash
git clone https://github.com/woowacourse-teams/2026-dfgg.git ~/build && cd ~/build && git checkout <개선 커밋>
```

[로컬]에서 prod 모델을 넘긴다:

```bash
ssh dfgg-be-prod 'cat /home/ubuntu/app-config/model.json' | ssh dfgg-be-perf 'cat > ~/build/backend/dfgg/src/main/resources/ltr/model.json'
```

[perf]에서 빌드하고 교체한다. DB 변경이 있으면 **먼저** perf RDS에 SQL을 손으로 적용하고 `indisvalid = t`를 확인한다(앱이 떠도 Flyway가 `IF NOT EXISTS`로 건너뛰고 기록만 한다).
t4g.small은 앱과 Gradle이 함께 돌기에 메모리가 빠듯하다. 빌드 동안 앱을 멈춘다:

```bash
sudo systemctl stop dfgg@blue && cd ~/build/backend/dfgg && ./gradlew clean build --no-daemon -x test
```

```bash
cp build/libs/dfgg-0.0.1-SNAPSHOT.jar /opt/dfgg/blue/dfgg-blue.jar && sha256sum /opt/dfgg/blue/dfgg-blue.jar && sudo systemctl start dfgg@blue
```

`APP_COMMIT`을 개선 커밋으로 바꾸고 같은 순서로 `after-*`를 잰다. `PERF_DATA`는 그대로다.

## k6 변수

| 변수 | 기본값 | 의미 |
|---|---|---|
| `SCENARIO` | `smoke` | `smoke`(1 RPS × 1m) / `target` / `ramp` |
| `TARGET_RPS` | `10` | target의 초당 요청 수 |
| `WARMUP` | `2m` | target 측정 전 같은 RPS로 데우는 시간. 판정에서 뺀다 |
| `DURATION` | `5m` | target 측정 시간 |
| `RAMP_MAX_RPS` | `TARGET_RPS × 3` | ramp가 올라갈 최대 RPS |
| `RAMP_STEPS` | `6` | ramp 단계 수. 단계별 p95가 요약에 따로 나온다 |
| `RAMP_STEP` | `1m` | 단계마다 유지하는 시간(앞에 5초 상승 구간이 붙는다) |
| `V3_RATIO` | `1.0` | v3 비율. 나머지는 `GET /api/champions` |
| `P95_MS` | `1000` | 목표 v3 p95(ms) |
| `ERROR_RATE` | `0.01` | 허용 에러율 |
| `MAX_VUS` | `200` | VU 상한. `dropped_iterations`가 생기면 올린다 |

판정은 측정 구간(`phase:measure`)만 본다: v3 p95, 에러율, `dropped_iterations == 0`.

## 같은 조건 체크리스트

전후 비교 전에 두 쪽 `meta.txt`를 대조한다. 다른 항목은 비교표에 표시한다.

- [ ] `requests_sha256`이 같다
- [ ] `perf_data`(고정 덤프)가 같다. 다시 실었다면 행 수가 `<덤프>.counts.txt`와 같다
- [ ] `k6_args`가 같다(tag 제외)
- [ ] perf EC2 타입, Java 버전, 유닛(JVM 옵션), perf RDS 타입·파라미터 그룹이 같다
- [ ] 순서가 같다: (켠 직후 버리는 워밍업) → 앱 재기동 → (smoke) → target(워밍업 포함)
- [ ] 회차마다 3회씩, 중앙값과 편차를 함께 적었다

## 무효로 하고 다시 재는 회차

| 신호 | 이유 |
|---|---|
| `cloudwatch.txt`의 `BurstBalance Minimum`이 20 미만 | gp2 크레딧이 바닥나면 디스크가 느려져 결과가 달라진다(9/18 측정) |
| `dropped_iterations`가 0보다 크고, **서버는 여유가 있었다**(응답이 빠른데 VU가 모자랐다) | 부하 발생기 쪽 문제다. `MAX_VUS`를 올린다 |
| ~~`dropped_iterations` > 0~~ 이면서 응답이 느리고 VU가 `MAX_VUS`까지 찼다 | **무효가 아니다 — 서버 포화가 측정된 것이다.** 결과로 남긴다(`MAX_VUS`를 올리면 동시 요청만 더 쌓인다) |
| 측정 중 모니터링 EC2 CPU가 포화됐다 | 부하 발생기가 병목이다. Grafana와 같은 서버를 쓴다 |
| `requests_sha256` 또는 `perf_data`가 다르다 | 다른 요청·다른 데이터로 쟀다 |

## 결과 폴더

```
results/20261015T053000Z-before-1/
├── meta.txt                 # 시각, k6 인자·버전, requests sha256, 앱·데이터 식별자, 커밋, k6 종료 코드
├── k6-summary.json          # --summary-export (p50·p90·p95·p99·max, 에러율, RPS, 단계별 p95)
├── k6.log                   # k6 화면 출력
├── pg_stat_statements.csv   # 총 실행 시간 상위 20개 쿼리 (측정 직전에 초기화)
└── cloudwatch.txt           # RDS 지표: BurstBalance 최소, CPU·ReadIOPS·ReadLatency·DiskQueueDepth 최대 등
```

앱 쪽 지표(경로별 p95, Hikari, CPU, GC)는 Grafana에서 `env="perf"`로 같은 시각 구간을 본다.

결과는 모니터링 EC2에 생긴다. 회차가 끝날 때마다 [로컬]로 가져와 커밋한다:

```bash
rsync -av dfgg-be-monitoring:~/2026-dfgg/backend/ops/perf/results/ backend/ops/perf/results/
```

## 정리 (측정이 모두 끝나면)

비용이 나오는 것부터 지운다. RDS 삭제는 되돌릴 수 없다 — 아래 명령은 식별자와 태그가 맞지 않으면 멈춘다.
고정 덤프(S3 `dfgg/backup/perf/`)는 남긴다. 결과를 다시 확인하거나 재측정할 때 같은 데이터로 되살릴 수 있다.

**[모니터링] RDS 삭제** (최종 스냅샷 없이):

```bash
arn=$(aws rds describe-db-instances --db-instance-identifier dfgg-db-perf --query 'DBInstances[0].DBInstanceArn' --output text) && purpose=$(aws rds list-tags-for-resource --resource-name "$arn" --query "TagList[?Key=='Purpose'].Value | [0]" --output text) && echo "$arn Purpose=$purpose"
```

```bash
if [ "$purpose" = dfgg-load-test ] && [[ "$arn" == *":db:dfgg-db-perf" ]]; then aws rds delete-db-instance --db-instance-identifier dfgg-db-perf --skip-final-snapshot --delete-automated-backups --query 'DBInstance.DBInstanceStatus' --output text && aws rds wait db-instance-deleted --db-instance-identifier dfgg-db-perf && echo "삭제 완료: dfgg-db-perf"; else echo "멈춤: 부하 테스트용 RDS가 아니다"; fi
```

**[모니터링] perf EC2** — "끄기"의 EC2 부분만 실행해 중지해 둔다(다시 쓸 수 있다). 완전히 없앨지는 따로 정한다.

**[모니터링] 남은 것 정리**:

```bash
sed -i '/^dfgg-db-perf\./d; /^dfgg-db-main\./d' ~/.pgpass && sed -i '/export PERF_DB_URL=/d; /^export PERF_DATA=/d' ~/.bashrc && rm -rf ~/perf-data
```

**[저장소]** `prometheus.yml`에서 perf 대상을 빼고 `apply.sh`로 반영한다.
