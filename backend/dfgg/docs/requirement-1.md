# 핵심 개발 환경 세팅

## 1. 개발 환경 구성 및 실행 방법

## 1.1 준비물

| 항목     | 기준                                         |
| ------ | ------------------------------------------ |
| JDK    | Java 21                                    |
| Docker | Docker Engine과 Docker Compose를 사용할 수 있는 환경 |
| Git    | GitHub 저장소를 복제할 수 있는 버전                    |

Gradle은 별도로 설치하지 않으며 프로젝트에 포함된 Gradle Wrapper를 사용한다.

```bash
git clone https://github.com/woowacourse-teams/2026-dfgg.git
cd 2026-dfgg/backend/dfgg
java --version
docker --version
docker compose version
```

## 1.2 환경변수 준비

기본값 및 예시는 `.env.example`에 기록하고 Git에 커밋한다. 
Riot API 키 등 개인 값은 `.env`에 기록하며 Git에 커밋하지 않는다.

```bash
cp .env.example .env
```

| 환경변수           | 용도                   |
| -------------- | -------------------- |
| `DB_USERNAME`  | 로컬 PostgreSQL 계정     |
| `DB_PASSWORD`  | 로컬 PostgreSQL 비밀번호   |
| `RIOT_API_KEY` | Riot API 연동을 위한 발급 키 |

## 1.3 PostgreSQL 실행 (로컬 개발용)

Docker Compose를 이용해 로컬 개발용 DB(`dfgg_local`)를 실행한다.

```bash
docker compose up -d postgres
docker compose ps
```
`postgres` 상태가 실행 중(Up)이면 준비가 완료된 것이다.

## 1.4 테스트와 빌드

테스트 환경은 애플리케이션 테스트 전용 데이터베이스(`dfgg_test`)를 바라보도록 설정되어 있다. 
따라서 테스트를 실행하기 전 해당 PostgreSQL 데이터베이스가 준비되어 있어야 한다.

```bash
./gradlew test
./gradlew clean build --no-daemon
```
- 두 명령 모두 `BUILD SUCCESSFUL`로 끝나야 한다.
- 컴파일과 모든 테스트가 성공해야 하며, 빌드를 통과시키기 위해 실패한 테스트를 임의로 비활성화하지 않는다.

## 1.6 종료와 초기화

기본 종료는 로컬 데이터 볼륨을 유지한다.
```bash
docker compose down
```

볼륨(로컬 DB 데이터)까지 초기화해야 할 때는 다음 명령을 사용한다.
> 주의: 다음 명령은 로컬 PostgreSQL 데이터를 모두 삭제한다.
```bash
docker compose down --volumes
```

## 1.7 재현 완료 체크리스트

- [ ] `git clone ...` 이후 Java, Docker, Docker Compose의 version을 확인하였다.
- [ ] `docker compose ps`에서 PostgreSQL이 동작 중임을 확인했다.
- [ ] `./gradlew test`가 `dfgg_test` DB와 연결되어 성공적으로 동작한다.
- [ ] `./gradlew clean build --no-daemon`이 성공한다.


## 2. 주요 기술 선택과 근거

### 2.1. 언어: Java 21
- **선택 이유**:
  - 팀원들의 이전 경험(우테코 레벨 1, 2)을 바탕으로 학습 비용 없이 즉시 개발에 착수할 수 있는 가장 친숙하고 안정적인 LTS 버전입니다.
  - 향후 외부 API(Riot API) 통신 및 데이터베이스 연동과 같은 I/O 바운드 작업이 많아질 경우, Virtual Thread를 도입하여 적은 리소스로 동시성을 극대화할 수 있는 기반을 제공합니다.
- **고려한 대안**:
  - `Java 17`: 구현상 문제는 없으나 지원 종료 시점이 상대적으로 일찍 도래하므로 장기적 관점에서 배제했습니다.
  - `Java 25`: 최신 LTS로 수명이 길지만, 도입 초기 단계이므로 라이브러리 호환성 및 운영 검증 사례 부족이라는 리스크가 존재해 선택하지 않았습니다.

### 2.2. 프레임워크: Spring Boot 4.1.0
- **선택 이유**:
  - 기존 레거시 시스템을 유지보수하는 것이 아닌 신규 프로젝트이므로, 기술 부채를 최소화하고 향후 버전 업그레이드 비용을 절감하기 위해 최신 버전을 채택했습니다.
  - Java 21과 최신 기술 스택(Spring Framework 7 등)을 가장 완벽하게 지원합니다.
- **고려한 대안**:
  - `Spring Boot 3.5.x`: 검증된 자료가 많아 안정적이지만, 요구되는 핵심 기술(Data JPA, REST Client, PostgreSQL 연동)이 4.1.0에서도 완벽히 지원되므로 하위 버전을 택할 이유가 없다고 판단했습니다.

### 2.3. 데이터베이스: PostgreSQL
- **선택 이유**:
  - 본 서비스의 핵심은 Riot API로부터 수집된 방대한 매치 데이터를 분석하고, 챔피언 및 티어별로 복잡한 아이템 추천 통계를 도출하는 것입니다.
  - PostgreSQL은 복잡한 집계 연산(`GROUP BY`, 윈도우 함수 등)과 대용량 분석 쿼리 최적화에 강점이 있어, 데이터 분석 중심의 애플리케이션에 가장 적합합니다.
- **고려한 대안**:
  - `MySQL`: 단순 읽기/쓰기(CRUD) 중심의 서비스에는 매우 훌륭하나, 본 프로젝트처럼 수집된 데이터를 가공하여 대규모 통계를 도출하는 작업에서는 PostgreSQL이 더 우수하다고 판단했습니다.

### 2.4. 데이터 액세스: Spring Data JPA & Spring JDBC
- **선택 이유**:
  - 작업 특성에 맞춰 두 가지 접근 방식을 조합하여 성능과 생산성을 모두 확보합니다.
  - **Spring Data JPA**: 챔피언, 아이템 등의 단순 엔티티 CRUD를 객체 지향적으로 다루어 개발 속도를 높입니다.
  - **Spring JDBC**: 대량의 매치 데이터 삽입(Batch Insert)이나 다중 조인이 포함된 복잡한 네이티브 통계 쿼리 등 JPA로 성능을 내기 어려운 지점에 직접 SQL을 제어하기 위해 사용합니다.

### 2.5. 외부 API 통신: Spring REST Client
- **선택 이유**:
  - Riot API와 Data Dragon의 데이터를 빠르고 안정적으로 연동하기 위함입니다.
  - Spring의 HTTP Message Converter 및 예외 처리 생태계와 기본적으로 통합되어 보일러플레이트 코드를 줄여주며, Fluent API를 통해 직관적인 코드를 작성할 수 있습니다.
- **고려한 대안**:
  - `Java Native HttpClient` 또는 `Apache HttpClient`: 작동은 충분히 가능하나, JSON 직렬화/역직렬화 설정 및 예외 핸들링을 직접 구성해야 하는 번거로움이 있어 배제했습니다.

### 2.6. 인프라 및 컨테이너: Docker
- **선택 이유**:
  - 로컬 개발 환경(PostgreSQL 등)을 컨테이너로 통일하여, 팀원 간 운영체제(OS)나 초기 설정 차이로 발생하는 문제를 원천 차단합니다.
  - `docker-compose.yml`과 초기화 스크립트(`init.sql`)를 통해 별도의 복잡한 설치 과정 없이 명령어 한 줄로 개발 및 테스트 인프라를 구축할 수 있습니다.
- **고려한 대안**:
  - `로컬 직접 설치`: 개별 팀원이 DBMS를 직접 설치하고 세팅하는 방식은 버전 불일치나 포트 충돌 등 관리 오버헤드가 크고 재현성이 떨어져 배제했습니다.


## 3. 검토한 대안과 트레이드오프

### 테스트 환경 데이터베이스: Testcontainers vs 외부 PostgreSQL 직접 연동

테스트 환경을 구성할 때 두 가지 대안을 검토했다.

- **Testcontainers (대안):** 매 테스트마다 독립적인 컨테이너를 생성하여 완벽히 격리된 환경을 제공한다. 하지만 컨테이너를 띄우는 과정에서 상당한 오버헤드가 발생하여 전체 테스트 실행 속도가 느려지며, 로컬과 CI 환경 모두에 Docker 데몬 인프라가 필수적으로 요구된다.
- **외부 PostgreSQL 직접 연동 (선택):** `application-test.yml`에 테스트 전용 데이터베이스(`dfgg_test`)를 분리하여 바라보도록 설정했다. 
  Testcontainers 방식에 비해 테스트 속도가 훨씬 빠르며, H2와 같은 인메모리 DB를 사용할 때 발생할 수 있는 데이터베이스 방언 불일치 문제나 네이티브 쿼리 미지원 문제 없이 '실제 운영 환경과 100% 동일한 DB 엔진' 으로 안전하게 검증할 수 있다.
  다만 이 방식은 개발자나 CI/CD 파이프라인에서 사전에 `dfgg_test`라는 테스트용 DB를 별도로 구비해 두어야 하는 초기 의존성(트레이드오프)이 존재한다. 우리는 빠른 피드백(속도)과 완벽한 DB 호환성을 위해 직접 연동 방식을 채택했다.

## 4. 환경 재현 또는 실행을 확인한 결과

1. `cp .env.example .env` 후 `RIOT_API_KEY` 환경변수 세팅
2. `docker compose up -d postgres` 수행 후 컨테이너 5432 포트 바인딩 확인
3. 사전 구성된 테스트 데이터베이스(`dfgg_test`, 계정 `tuise`)를 구비
4. `./gradlew test` 수행 시, 모든 테스트가 PostgreSQL 테스트 전용 DB와 성공적으로 연동되어 `BUILD SUCCESSFUL` 통과함 확인