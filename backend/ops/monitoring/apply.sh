#!/usr/bin/env bash
# monitoring 서버(dfgg-be-monitoring)에 저장소의 Prometheus·Grafana·nginx 설정을 반영한다.
#
#   사용 (monitoring 서버의 저장소 루트에서):
#     git pull
#     sudo ./backend/ops/monitoring/apply.sh --dry-run   # 무엇이 바뀌는지만 본다
#     sudo ./backend/ops/monitoring/apply.sh
#
# 1. 아래 표의 파일을 저장소와 서버에서 비교한다. 같으면 건드리지 않는다
# 2. 설치 전에 검사한다 — JSON·YAML 문법, promtool
# 3. 바뀐 파일만 백업(/var/backups/dfgg-monitoring/<시각>/)하고 설치한다
# 4. 바뀐 것에 필요한 만큼만 반영한다. 대시보드만 바뀌면 아무것도 재시작하지 않는다(Grafana가 30초마다 읽는다)
# 5. 반영 뒤 살아 있는지 확인하고, 실패하면 백업으로 되돌린다
#
# 저장소에 없는 것 — 서버에서 직접 관리한다
#   /etc/grafana/secrets/slack-webhook     알림 웹훅 (비밀값)
#   /etc/grafana/grafana.ini               기본값에서 http_addr = 127.0.0.1
#   /home/ubuntu/secrets/grafana-admin.env 관리자 계정

set -Eeuo pipefail
die() { echo "❌ $*" >&2; exit 1; }
say() { echo "▶ $*"; }

DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    *) die "모르는 옵션: $arg" ;;
  esac
done

OPS=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
# 설치 위치 앞에 붙는 경로. 로컬 시험에서만 쓴다 — 서버에서는 비워 둔다.
ROOT="${DFGG_APPLY_ROOT:-}"
[[ -n "$ROOT" || $EUID -eq 0 ]] || die "sudo로 실행한다"

# 저장소 경로 | 서버 경로 | 소유자:그룹 | 권한 | 바뀌면 할 일
MANIFEST="
prometheus/prometheus.yml                   | /etc/prometheus/prometheus.yml                   | root:root    | 644 | prometheus-reload
prometheus/prometheus.default               | /etc/default/prometheus                          | root:root    | 644 | prometheus-restart
grafana/provisioning/datasources/dfgg.yaml  | /etc/grafana/provisioning/datasources/dfgg.yaml  | root:grafana | 640 | grafana-restart
grafana/provisioning/dashboards/dfgg.yaml   | /etc/grafana/provisioning/dashboards/dfgg.yaml   | root:grafana | 640 | grafana-restart
grafana/provisioning/alerting/dfgg.yaml     | /etc/grafana/provisioning/alerting/dfgg.yaml     | root:grafana | 640 | grafana-restart
grafana/dashboards/dfgg-service-health.json | /etc/grafana/dashboards/dfgg-service-health.json | root:grafana | 640 | none
nginx/dfgg-grafana.conf                     | /etc/nginx/sites-available/grafana               | root:root    | 644 | nginx-reload
"
# 예전 위치. 반영이 끝난 뒤 남아 있으면 백업하고 지운다.
RETIRED="
/var/lib/grafana/dashboards/dfgg-service-health.json
"
NGINX_SITE_LINK=/etc/nginx/sites-enabled/grafana
SLACK_SECRET=/etc/grafana/secrets/slack-webhook

trim() { local s="$1"; s="${s#"${s%%[![:space:]]*}"}"; echo "${s%"${s##*[![:space:]]}"}"; }

# ── 0. 준비 ────────────────────────────────────────────────────────────────
for tool in cmp install systemctl curl jq promtool nginx python3 journalctl flock; do
  command -v "$tool" > /dev/null || die "$tool 이 없다"
done
exec 9> "$ROOT/run/dfgg-monitoring-apply.lock"
flock -n 9 || die "다른 apply.sh가 실행 중이다"

[[ -f "$ROOT$SLACK_SECRET" ]] || die "$SLACK_SECRET 이 없다 — 알림 규칙이 이 파일을 읽는다. 서버에서 직접 만든다"

SRCS=() DSTS=() OWNERS=() MODES=() ACTIONS=()
while IFS='|' read -r src dst owner mode action; do
  src=$(trim "$src")
  [[ -n "$src" ]] || continue
  SRCS+=("$OPS/$src") DSTS+=("$ROOT$(trim "$dst")") OWNERS+=("$(trim "$owner")") MODES+=("$(trim "$mode")") ACTIONS+=("$(trim "$action")")
done <<< "$MANIFEST"

# ── 1. 설치 전 검사 — 저장소 파일이 깨져 있으면 서버를 건드리기 전에 멈춘다 ───────
say "저장소 파일 검사"
for src in "${SRCS[@]}"; do
  [[ -r "$src" ]] || die "저장소에 없다: $src"
  case "$src" in
    *.json) jq empty "$src" || die "JSON 문법 오류: $src" ;;
    *.yaml) python3 -c 'import sys, yaml; yaml.safe_load(open(sys.argv[1]))' "$src" || die "YAML 문법 오류: $src" ;;
  esac
done
promtool check config --syntax-only "$OPS/prometheus/prometheus.yml" > /dev/null \
  || die "promtool 검사 실패: prometheus/prometheus.yml"
# 크래시 루프 전례(2026-09-29): __dashboardUid__는 __panelId__ 없이 쓰면 Grafana가 아예 뜨지 않는다.
if grep -q '__dashboardUid__' "$OPS/grafana/provisioning/alerting/dfgg.yaml" \
  && ! grep -q '__panelId__' "$OPS/grafana/provisioning/alerting/dfgg.yaml"; then
  die "알림 규칙에 __dashboardUid__만 있다 — __panelId__ 없이 쓰면 Grafana가 기동하지 못한다"
fi

# ── 2. 비교 ────────────────────────────────────────────────────────────────
CHANGED=()
NEED_PROMETHEUS="" NEED_GRAFANA=false NEED_NGINX=false DASHBOARD_ONLY=false
for i in "${!SRCS[@]}"; do
  src=${SRCS[$i]} dst=${DSTS[$i]}
  cmp -s "$src" "$dst" && continue
  CHANGED+=("$i")
  if [[ -e "$dst" ]]; then
    say "바뀜: ${dst#"$ROOT"}"
    diff -u "$dst" "$src" | sed -n '3,40p' | sed 's/^/    /' || true
  else
    say "새 파일: ${dst#"$ROOT"}"
  fi
  case "${ACTIONS[$i]}" in
    prometheus-restart) NEED_PROMETHEUS=restart ;;
    prometheus-reload) [[ -n "$NEED_PROMETHEUS" ]] || NEED_PROMETHEUS=reload ;;
    grafana-restart) NEED_GRAFANA=true ;;
    nginx-reload) NEED_NGINX=true ;;
    none) DASHBOARD_ONLY=true ;;
  esac
done
if [[ ! -L "$ROOT$NGINX_SITE_LINK" ]]; then
  say "nginx 사이트 링크가 없다: $NGINX_SITE_LINK"
  NEED_NGINX=true
fi

if (( ${#CHANGED[@]} == 0 )) && ! $NEED_NGINX; then
  say "바뀐 것 없음"
  exit 0
fi
say "반영할 것: prometheus=${NEED_PROMETHEUS:-없음} grafana=$($NEED_GRAFANA && echo 재시작 || echo 없음) nginx=$($NEED_NGINX && echo reload || echo 없음)"
if $DRY_RUN; then
  say "--dry-run: 여기서 멈춘다"
  exit 0
fi

# ── 3. 백업 후 설치 ───────────────────────────────────────────────────────
BACKUP="$ROOT/var/backups/dfgg-monitoring/$(date +%Y%m%d-%H%M%S)"
INSTALLED=()

rollback() {
  trap - ERR
  set +e
  echo "↩︎ 되돌린다 (백업: $BACKUP)" >&2
  for i in "${INSTALLED[@]}"; do
    dst=${DSTS[$i]}
    if [[ -e "$BACKUP${dst#"$ROOT"}" ]]; then
      cp -p "$BACKUP${dst#"$ROOT"}" "$dst"
    else
      rm -f "$dst"
    fi
  done
  [[ -z "$NEED_PROMETHEUS" ]] || systemctl restart prometheus
  ! $NEED_GRAFANA || systemctl restart grafana-server
  ! $NEED_NGINX || { nginx -t && systemctl reload nginx; }
  echo "❌ 반영 실패 — 이전 설정으로 되돌렸다. 위 메시지와 journalctl로 원인을 본다" >&2
  exit 1
}
trap rollback ERR

for i in "${CHANGED[@]}"; do
  src=${SRCS[$i]} dst=${DSTS[$i]}
  if [[ -e "$dst" ]]; then
    mkdir -p "$BACKUP$(dirname "${dst#"$ROOT"}")"
    cp -p "$dst" "$BACKUP${dst#"$ROOT"}"
  fi
  # 없을 때만 만든다 — 이미 있는 디렉터리의 권한은 건드리지 않는다.
  [[ -d "$(dirname "$dst")" ]] || install -d -m 755 "$(dirname "$dst")"
  if [[ -n "$ROOT" ]]; then
    install -m "${MODES[$i]}" "$src" "$dst"
  else
    install -o "${OWNERS[$i]%%:*}" -g "${OWNERS[$i]##*:}" -m "${MODES[$i]}" "$src" "$dst"
  fi
  INSTALLED+=("$i")
done
ln -sfn /etc/nginx/sites-available/grafana "$ROOT$NGINX_SITE_LINK"

# ── 4. 반영과 확인 ─────────────────────────────────────────────────────────
wait_for() {  # 설명, 시도 횟수, 명령...
  local what=$1 tries=$2; shift 2
  for ((n = 0; n < tries; n++)); do
    "$@" > /dev/null 2>&1 && return 0
    sleep 2
  done
  echo "❌ $what 확인 실패" >&2
  return 1
}
prometheus_healthy() { curl -fsS http://127.0.0.1:9090/-/healthy; }
prometheus_config_loaded() { curl -fsS http://127.0.0.1:9090/metrics | grep -q '^prometheus_config_last_reload_successful 1'; }
grafana_healthy() { curl -fsS http://127.0.0.1:3000/api/health | jq -e '.database == "ok"'; }
nginx_proxies_grafana() { curl -fsS http://127.0.0.1/api/health | jq -e '.database == "ok"'; }

if [[ -n "$NEED_PROMETHEUS" ]]; then
  say "Prometheus $NEED_PROMETHEUS"
  promtool check config "$ROOT/etc/prometheus/prometheus.yml" > /dev/null
  systemctl "$NEED_PROMETHEUS" prometheus
  wait_for "Prometheus 기동" 15 prometheus_healthy
  wait_for "Prometheus 설정 적용" 5 prometheus_config_loaded
fi

if $NEED_GRAFANA; then
  say "Grafana 재시작 (규칙·데이터소스·대시보드 목록은 기동할 때 읽는다)"
  started_at=$(date '+%Y-%m-%d %H:%M:%S')
  systemctl restart grafana-server
  wait_for "Grafana 기동" 30 grafana_healthy
  # 떴다가 죽는 크래시 루프를 잡으려고 조금 더 지켜본다.
  restarts=$(systemctl show grafana-server -p NRestarts --value)
  sleep 10
  [[ "$(systemctl show grafana-server -p NRestarts --value)" == "$restarts" ]]
  [[ "$(systemctl is-active grafana-server)" == active ]]
  if journalctl -u grafana-server --since "$started_at" --no-pager | grep -E 'level=error' | grep -iE 'provision|alert rule'; then
    echo "❌ Grafana가 provisioning 오류를 냈다" >&2
    false
  fi
fi

if $NEED_NGINX; then
  say "nginx 검사 후 reload"
  nginx -t
  systemctl reload nginx
fi
wait_for "nginx(80) → Grafana" 10 nginx_proxies_grafana

trap - ERR

# ── 5. 정리 ────────────────────────────────────────────────────────────────
while read -r old; do
  [[ -n "$old" && -e "$ROOT$old" ]] || continue
  mkdir -p "$BACKUP$(dirname "$old")"
  mv "$ROOT$old" "$BACKUP$old"
  say "예전 위치 정리: $old (백업으로 옮김)"
done <<< "$RETIRED"

echo
echo "✅ 반영 완료 — 바뀐 파일 ${#CHANGED[@]}개, 백업: ${BACKUP#"$ROOT"}"
$DASHBOARD_ONLY && ! $NEED_GRAFANA && echo "   대시보드는 30초 안에 반영된다. 브라우저를 새로고침한다"
exit 0
