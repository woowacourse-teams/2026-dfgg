#!/usr/bin/env bash
# 프론트 서버(dev·prod)를 Amazon Linux 2023 (arm64) 에 처음부터 세운다.
#
#   dev : sudo ROLE=dev  RUNNER_TOKEN=xxx bash setup.sh
#   prod: sudo ROLE=prod RUNNER_TOKEN=xxx SENTRY_HOOK_PATH=/hooks/sentry/... bash setup.sh
#
# 하는 일
#   1. nginx·rsync·러너 의존성 설치, /var/www/dfgg 준비 (CD 가 여기에 rsync 한다)
#   2. nginx 설정. prod 는 백엔드 프록시(/api/recommendations, /feedback)와 Sentry 훅 경로 포함
#   3. GitHub Actions 러너를 dfgg-fe-<ROLE> 이름표로 등록하고 서비스로 띄운다 (fe-deploy.yaml 그대로 동작)
#   4. prod 만: certbot 설치, Sentry → Slack 중계 서비스 등록
#
# 비밀값(러너 토큰, Sentry 훅 경로, Slack 웹훅)은 저장소에 두지 않고 실행할 때만 넘긴다.
# 여러 번 실행해도 같은 결과가 되도록 썼다.
set -euo pipefail

ROLE="${ROLE:?ROLE=dev 또는 ROLE=prod 를 넘겨 주세요}"
RUNNER_TOKEN="${RUNNER_TOKEN:-}"
BACKEND="${BACKEND:-http://43.203.222.190:8080}"
REPO_URL="https://github.com/woowacourse-teams/2026-dfgg"
RUNNER_USER="ec2-user"
RUNNER_DIR="/home/${RUNNER_USER}/actions-runner"
WEB_ROOT="/var/www/dfgg"

[[ "$ROLE" == dev || "$ROLE" == prod ]] || { echo "ROLE 은 dev 또는 prod"; exit 1; }
[[ $EUID -eq 0 ]] || { echo "sudo 로 실행해 주세요"; exit 1; }
if [[ "$ROLE" == prod ]]; then
  SENTRY_HOOK_PATH="${SENTRY_HOOK_PATH:?prod 는 SENTRY_HOOK_PATH (옛 서버 nginx 의 /hooks/sentry/... 경로) 가 필요합니다}"
fi

echo "== 1. 패키지"
dnf install -y nginx rsync libicu tar gzip python3
mkdir -p "$WEB_ROOT"
chown "$RUNNER_USER:$RUNNER_USER" "$WEB_ROOT"
chmod 755 "$WEB_ROOT"

echo "== 2. nginx"
# 기본 nginx.conf 에는 80 번 기본 서버가 박혀 있어서 우리 설정과 겹친다. 우분투 서버와 같은 최소 설정으로 바꾼다.
cat > /etc/nginx/nginx.conf <<'NGINX'
user nginx;
worker_processes auto;
pid /run/nginx.pid;
error_log /var/log/nginx/error.log;
include /usr/share/nginx/modules/*.conf;

events {
    worker_connections 768;
}

http {
    sendfile on;
    tcp_nopush on;
    types_hash_max_size 2048;
    server_tokens off;

    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers off;

    access_log /var/log/nginx/access.log;
    gzip on;

    include /etc/nginx/conf.d/*.conf;
}
NGINX

# 정적 파일 공통 부분. 없는 주소(/ko/features 등)는 index.html 로 돌려 앱이 라우팅한다.
STATIC_BLOCK="
    root ${WEB_ROOT};
    index index.html;

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;

    location / {
        try_files \$uri \$uri/ /index.html;
    }

    location = /index.html {
        add_header Cache-Control \"no-cache, must-revalidate\";
    }"

if [[ "$ROLE" == dev ]]; then
  cat > /etc/nginx/conf.d/dfgg.conf <<NGINX
server {
    listen 80 default_server;
    server_name _;
${STATIC_BLOCK}
}
NGINX
else
  # prod: 처음엔 80 번으로만 띄우고, 도메인이 이 서버를 가리킨 뒤 certbot --nginx 가 443 을 붙인다.
  cat > /etc/nginx/conf.d/dfgg.conf <<NGINX
server {
    listen 80;
    server_name dfgg.pro www.dfgg.pro;

    access_log /var/log/nginx/dfgg.access.log;
    error_log  /var/log/nginx/dfgg.error.log;
${STATIC_BLOCK}

    location /api/recommendations {
        proxy_pass ${BACKEND};
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 30s;
    }

    location /feedback {
        proxy_pass ${BACKEND};
        proxy_set_header Host              \$host;
        proxy_set_header X-Real-IP         \$remote_addr;
        proxy_set_header X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    # Sentry → Slack 중계. 경로 자체가 비밀이라 저장소에 적지 않고 실행할 때 받는다.
    location = ${SENTRY_HOOK_PATH} {
        access_log off;
        proxy_pass http://127.0.0.1:9100;
    }
}
NGINX
fi
nginx -t
systemctl enable --now nginx
systemctl reload nginx

echo "== 3. GitHub Actions 러너 (dfgg-fe-${ROLE})"
if [[ -z "$RUNNER_TOKEN" ]]; then
  echo "RUNNER_TOKEN 이 없어서 러너는 건너뜁니다."
  echo "  저장소 Settings > Actions > Runners > New self-hosted runner 의 --token 값을 넣고 다시 실행하세요."
elif [[ -f "${RUNNER_DIR}/.runner" ]]; then
  echo "러너가 이미 등록돼 있어 건너뜁니다."
else
  VERSION=$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"].lstrip("v"))')
  sudo -u "$RUNNER_USER" mkdir -p "$RUNNER_DIR"
  curl -fsSL "https://github.com/actions/runner/releases/download/v${VERSION}/actions-runner-linux-arm64-${VERSION}.tar.gz" \
    | sudo -u "$RUNNER_USER" tar xz -C "$RUNNER_DIR"
  # config.sh 는 root 로 돌리면 거부한다. 러너 계정으로 등록한다.
  sudo -u "$RUNNER_USER" bash -c "cd '$RUNNER_DIR' && ./config.sh --unattended --replace \
    --url '$REPO_URL' --token '$RUNNER_TOKEN' \
    --labels 'dfgg-fe-${ROLE}' --name 'dfgg-fe-${ROLE}-al2023'"
  (cd "$RUNNER_DIR" && ./svc.sh install "$RUNNER_USER" && ./svc.sh start)
fi

if [[ "$ROLE" == prod ]]; then
  echo "== 4. certbot"
  # AL2023 기본 저장소에 certbot 이 없을 수 있어 공식 안내대로 venv 에 설치한다.
  if ! command -v certbot >/dev/null; then
    python3 -m venv /opt/certbot
    /opt/certbot/bin/pip install --quiet --upgrade pip certbot certbot-nginx
    ln -sf /opt/certbot/bin/certbot /usr/bin/certbot
  fi
  # 하루 두 번 갱신 시도. 만료 30일 전이 아니면 아무 일도 하지 않는다.
  cat > /etc/systemd/system/certbot-renew.service <<'UNIT'
[Unit]
Description=certbot renew

[Service]
Type=oneshot
ExecStart=/usr/bin/certbot renew --quiet --deploy-hook "systemctl reload nginx"
UNIT
  cat > /etc/systemd/system/certbot-renew.timer <<'UNIT'
[Unit]
Description=certbot renew twice a day

[Timer]
OnCalendar=*-*-* 03,15:00:00
RandomizedDelaySec=1h
Persistent=true

[Install]
WantedBy=timers.target
UNIT
  systemctl daemon-reload
  systemctl enable --now certbot-renew.timer

  echo "== 5. Sentry → Slack 중계"
  if [[ -f /opt/dfgg-sentry-relay/relay.py && -f /etc/dfgg-sentry-relay.env ]]; then
    cat > /etc/systemd/system/dfgg-sentry-relay.service <<'UNIT'
[Unit]
Description=Sentry -> Slack webhook relay
After=network-online.target

[Service]
EnvironmentFile=/etc/dfgg-sentry-relay.env
ExecStart=/usr/bin/python3 /opt/dfgg-sentry-relay/relay.py
DynamicUser=yes
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
UNIT
    chmod 600 /etc/dfgg-sentry-relay.env
    systemctl daemon-reload
    systemctl enable --now dfgg-sentry-relay
  else
    echo "중계 파일이 없어 건너뜁니다. README 의 '중계 파일 옮기기'를 하고 다시 실행하세요."
  fi
fi

echo
echo "== 끝. 확인:"
echo "  curl -sI http://localhost/ | head -1     # 200 이면 nginx OK (배포 전이면 404 도 정상)"
echo "  systemctl status 'actions.runner.*' --no-pager | head -3"
[[ "$ROLE" == prod ]] && echo "  도메인을 이 서버로 옮긴 뒤: sudo certbot --nginx -d dfgg.pro -d www.dfgg.pro"
exit 0
