# 프론트 서버 다시 세우기 (Amazon Linux 2023)

프론트 서버는 nginx 로 랜딩(`/var/www/dfgg`)을 내주고, GitHub Actions 러너가 `fe-deploy.yaml` 의 결과물을 그 폴더로 `rsync` 한다.
러너 이름표(`dfgg-fe-dev`, `dfgg-fe-prod`)만 같으면 **CD 파일은 바꿀 필요가 없다.**

| | dev | prod |
| --- | --- | --- |
| 인스턴스 | t4g.micro | t4g.small |
| 도메인 | 없음 (IP 로 접속) | dfgg.pro, www.dfgg.pro (HTTPS) |
| 백엔드 프록시 | 없음 | `/api/recommendations`, `/feedback` → `43.203.222.190:8080` |
| 그 밖에 | 없음 | Sentry → Slack 중계 (`dfgg-sentry-relay`, 127.0.0.1:9100) |

AMI 는 arm64 라서 **t4g** 계열만 뜬다.

## 순서 (dev 먼저, 같은 순서로 prod)

### 1. 새 인스턴스 띄우기

- AMI: 공유받은 Amazon Linux 2023 arm64 (`ami-0e576970884d94ada`)
- 서브넷·보안 그룹·**IAM 인스턴스 프로파일은 옛 서버와 같게**. 인스턴스 프로파일이 같아야 Session Manager 로 들어갈 수 있다.

### 2. (prod 만) 중계 파일 옮기기

중계 스크립트와 비밀값은 저장소에 없고 옛 prod 서버에만 있다. 옛 서버에서:

```bash
sudo tar czf - /opt/dfgg-sentry-relay /etc/dfgg-sentry-relay.env | base64 -w0; echo
```

나온 한 줄을 복사해서 새 서버에서:

```bash
echo '<붙여넣기>' | base64 -d | sudo tar xzf - -C /
```

⚠️ 이 문자열에는 Slack 웹훅이 들어 있다. 메신저·PR·이슈에 붙이지 말 것.

### 3. 설치 스크립트 실행

러너 토큰: 저장소 Settings → Actions → Runners → New self-hosted runner 화면의 `--token` 값 (1시간 유효).

```bash
curl -fsSLO https://raw.githubusercontent.com/woowacourse-teams/2026-dfgg/FE/dev/frontend/infra/server/setup.sh

# dev
sudo ROLE=dev RUNNER_TOKEN=<토큰> bash setup.sh

# prod (SENTRY_HOOK_PATH 는 옛 서버 nginx 설정의 location = /hooks/sentry/... 경로 그대로)
sudo ROLE=prod RUNNER_TOKEN=<토큰> SENTRY_HOOK_PATH=/hooks/sentry/<...> bash setup.sh
```

### 4. 옛 러너 끄기

같은 이름표 러너가 둘이면 배포가 옛 서버로 갈 수 있다. 옛 서버에서:

```bash
sudo systemctl stop 'actions.runner.*' && sudo systemctl disable 'actions.runner.*'
```

그리고 GitHub Runners 목록에서 옛 러너(`ip-10-0-...`)를 지운다.

### 5. 트래픽 옮기기

- 옛 서버에 Elastic IP 가 붙어 있으면 새 인스턴스로 옮겨 붙인다. DNS 는 그대로 둔다.
- 아니면 dfgg.pro, www.dfgg.pro 의 A 레코드를 새 IP 로 바꾼다.

### 6. (prod 만) HTTPS

도메인이 새 서버를 가리킨 뒤:

```bash
sudo certbot --nginx -d dfgg.pro -d www.dfgg.pro
```

80 → 443 넘김과 인증서 설정을 certbot 이 붙인다. 갱신은 `certbot-renew.timer` 가 하루 두 번 확인한다.

### 7. 확인 뒤 옛 서버 삭제

- dev: `FE/dev` 에 프론트 변경을 머지해 DEPLOY-DEV 가 새 러너에서 도는지 본다.
  Run workflow(수동 실행)는 dev·prod 에 바로 같이 나가니 prod 준비 전에는 쓰지 않는다.
- prod: https://dfgg.pro 접속, 피드백 전송, Sentry 테스트 알림이 Slack 에 오는지 본다.
- 팀 스레드에 조치 방식(AMI 교체)과 새 인스턴스 ID 를 남긴다.
