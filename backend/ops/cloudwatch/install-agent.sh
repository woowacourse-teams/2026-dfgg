#!/usr/bin/env bash
#
# CloudWatch Agent를 설치하고 앱·nginx 로그를 CloudWatch Logs로 보낸다.
# 앱 서버(dev/prod)에서 ubuntu 사용자로 실행한다.
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

[[ -r "$TEMPLATE" ]] || { echo "설정 템플릿이 없다: $TEMPLATE" >&2; exit 1; }

for f in /home/ubuntu/logs/dfgg-blue.log /home/ubuntu/logs/dfgg-green.log; do
  [[ -e "$f" ]] || echo "경고: $f 가 아직 없다. 그 슬롯이 한 번도 안 떴다면 정상이다." >&2
done

if [[ ! -x "$CTL" ]]; then
  echo "== CloudWatch Agent 설치 (arm64) =="
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  curl -fsSL -o "$TMP/agent.deb" \
    "https://amazoncloudwatch-agent.s3.amazonaws.com/ubuntu/arm64/latest/amazon-cloudwatch-agent.deb"
  sudo -n dpkg -i -E "$TMP/agent.deb"
else
  echo "== 이미 설치돼 있다. 설정만 갱신한다 =="
fi

echo "== 설정 적용 (환경: $ENV_NAME) =="
sed -e "s/__ENV__/$ENV_NAME/g" -e "s/__RETENTION__/$RETENTION/g" "$TEMPLATE" | sudo -n tee "$AGENT_CONFIG" > /dev/null
sudo -n "$CTL" -a fetch-config -m ec2 -s -c "file:$AGENT_CONFIG"

echo "== 상태 =="
sudo -n "$CTL" -a status | sed 's/^/  /'

cat <<EOF

로그 그룹: /dfgg/$ENV_NAME  (보존 ${RETENTION}일)
스트림   : {instance_id}/app-blue , {instance_id}/app-green ,
           {instance_id}/nginx-access , {instance_id}/nginx-error
EOF
