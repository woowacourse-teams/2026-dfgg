#!/usr/bin/env bash
# ml 서버가 할 일 없이 켜져 있으면 끈다. dfgg-idle-stop.timer가 5분마다 부른다.
# 워크플로의 끄기 잡이 돌지 못한 경우(러너가 안 붙음, Actions 장애)의 안전망이다.
#
#   러너 작업(Runner.Worker)이 IDLE_MINUTES 동안 없으면 끈다. 켜진 지 BOOT_GRACE_MINUTES 안에는 끄지 않는다.
#   작업 중이어도 켜진 지 MAX_UPTIME_MINUTES가 지나면 끈다. 재학습 잡 제한(5시간)보다 길게 둔다.
#   인스턴스의 종료 동작이 stop(기본값)이어야 꺼진 뒤 다시 켤 수 있다.

set -Eeuo pipefail

idle_minutes=${IDLE_MINUTES:-30}
boot_grace_minutes=${BOOT_GRACE_MINUTES:-20}
max_uptime_minutes=${MAX_UPTIME_MINUTES:-360}
state_file=${STATE_FILE:-/run/dfgg-idle-since}
shutdown_cmd=${SHUTDOWN_CMD:-/usr/sbin/shutdown -h now}
uptime_file=${UPTIME_FILE:-/proc/uptime}

uptime_minutes=$(awk '{ print int($1 / 60) }' "$uptime_file")
now=$(date +%s)

stop() {
  echo "dfgg-idle-stop: $1, shutting down"
  rm -f "$state_file"
  $shutdown_cmd
  exit 0
}

if ((uptime_minutes >= max_uptime_minutes)); then
  stop "up for ${uptime_minutes}m (max ${max_uptime_minutes}m)"
fi
if pgrep -f Runner.Worker > /dev/null; then
  rm -f "$state_file"
  exit 0
fi
if ((uptime_minutes < boot_grace_minutes)); then
  exit 0
fi
if [[ ! -s "$state_file" ]]; then
  echo "$now" > "$state_file"
  exit 0
fi
idle_since=$(< "$state_file")
if (((now - idle_since) / 60 >= idle_minutes)); then
  stop "no runner job for $(((now - idle_since) / 60))m"
fi
