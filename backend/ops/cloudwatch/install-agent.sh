#!/usr/bin/env bash
#
# CloudWatch Agent를 설치하고 앱·nginx 로그를 CloudWatch Logs로 보낸다.
# 앱 서버(dev/prod)에서 앱을 띄우는 사용자로 실행한다. 앱 로그는 그 사용자의 ~/logs에 있다.
#
#   ./install-agent.sh dev
#   ./install-agent.sh prod

set -Eeuo pipefail

ENV_NAME="${1:-}"
# 보존 기간. prod는 장애를 되짚어야 하므로 길게, dev는 어제 것까지만 있으면 충분하다.
case "$ENV_NAME" in
  prod) RETENTION=30 ;;
  dev)  RETENTION=7 ;;
  *) echo "사용법: $0 <dev|prod>" >&2; exit 2 ;;
esac

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
TEMPLATE="$SCRIPT_DIR/amazon-cloudwatch-agent.json.template"
AGENT_DIR=/opt/aws/amazon-cloudwatch-agent
AGENT_CONFIG="$AGENT_DIR/etc/amazon-cloudwatch-agent.json"
CTL="$AGENT_DIR/bin/amazon-cloudwatch-agent-ctl"
LOG_DIR="$HOME/logs"

[[ -r "$TEMPLATE" ]] || { echo "설정 템플릿이 없다: $TEMPLATE" >&2; exit 1; }

for f in "$LOG_DIR/dfgg-blue.log" "$LOG_DIR/dfgg-green.log"; do
  [[ -e "$f" ]] || echo "경고: $f 가 아직 없다. 그 슬롯이 한 번도 안 떴다면 정상이다." >&2
done

if [[ ! -x "$CTL" ]]; then
  echo "== CloudWatch Agent 설치 (arm64) =="
  if command -v dnf > /dev/null; then
    # Amazon Linux는 기본 저장소에 패키지가 있다.
    sudo -n dnf install -y -q amazon-cloudwatch-agent
  else
    TMP=$(mktemp -d)
    trap 'rm -rf "$TMP"' EXIT
    curl -fsSL -o "$TMP/agent.deb" \
      "https://amazoncloudwatch-agent.s3.amazonaws.com/ubuntu/arm64/latest/amazon-cloudwatch-agent.deb"
    sudo -n dpkg -i -E "$TMP/agent.deb"
  fi
else
  echo "== 이미 설치돼 있다. 설정만 갱신한다 =="
fi

# 앱 로그는 JSON 한 줄(@timestamp가 맨 앞)이다. 템플릿은 JSON이라 주석을 못 달아 이유를 여기 둔다.
# - timestamp_format에 시간대를 넣지 않는다. Agent의 %z는 +0900만 읽고 앱은 +09:00을 쓴다.
#   컨테이너(TZ=Asia/Seoul)와 서버 시간대가 같으므로 Local로 읽는다.
# - multi_line_start_pattern은 '{'로 시작하는 JSON 줄과 날짜로 시작하는 옛 텍스트 줄을 모두 새 이벤트로 본다.
#   JSON 전환 배포 직전에 적용해도 남아 있는 텍스트 줄이 한 이벤트로 합쳐지지 않는다.
#   텍스트 줄은 시각 형식이 맞지 않아 수집 시각으로 들어간다.
echo "== 설정 적용 (환경: $ENV_NAME) =="
sed -e "s/__ENV__/$ENV_NAME/g" -e "s/__RETENTION__/$RETENTION/g" -e "s#__LOG_DIR__#$LOG_DIR#g" "$TEMPLATE" | sudo -n tee "$AGENT_CONFIG" > /dev/null
sudo -n "$CTL" -a fetch-config -m ec2 -s -c "file:$AGENT_CONFIG"

echo "== 상태 =="
sudo -n "$CTL" -a status | sed 's/^/  /'

cat <<EOF

로그 그룹: /dfgg/$ENV_NAME  (보존 ${RETENTION}일)
스트림   : {instance_id}/app-blue , {instance_id}/app-green ,
           {instance_id}/nginx-access , {instance_id}/nginx-error
EOF
