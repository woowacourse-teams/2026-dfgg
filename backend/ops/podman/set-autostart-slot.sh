#!/usr/bin/env bash
# 재부팅 때 자동으로 띄울 슬롯을 하나로 정한다. 실행 중인 컨테이너는 건드리지 않는다.
#
# Quadlet이 만든 유닛은 systemctl enable을 쓸 수 없다. 대신 템플릿을 가리키는
# 인스턴스 링크(dfgg@<slot>.container)와 [Install] drop-in을 두면 그 슬롯만
# default.target에 걸린다.
#
#   사용법: set-autostart-slot.sh blue|green|none
#   none은 두 슬롯 모두 자동 시작하지 않는다. 서버를 점검하거나 내릴 때 쓴다.
#   QUADLET_DIR  Quadlet 파일 위치 (기본값 ~/.config/containers/systemd)

set -Eeuo pipefail

target=${1:-}
[[ "$target" =~ ^(blue|green|none)$ ]] || { echo "usage: $0 blue|green|none" >&2; exit 2; }

dir=${QUADLET_DIR:-$HOME/.config/containers/systemd}
test -f "$dir/dfgg@.container" || { echo "template not found: $dir/dfgg@.container" >&2; exit 1; }

disable() {
  rm -f "$dir/dfgg@$1.container" "$dir/dfgg@$1.container.d/autostart.conf"
  rmdir --ignore-fail-on-non-empty "$dir/dfgg@$1.container.d" 2> /dev/null || true
}

enable() {
  ln -sfn dfgg@.container "$dir/dfgg@$1.container"
  mkdir -p "$dir/dfgg@$1.container.d"
  printf '[Install]\nWantedBy=default.target\n' > "$dir/dfgg@$1.container.d/autostart.conf"
}

for slot in blue green; do
  [[ "$slot" == "$target" ]] || disable "$slot"
done
expected=''
if [[ "$target" != none ]]; then
  enable "$target"
  expected="dfgg@$target.service"
fi
systemctl --user daemon-reload

wants=$(systemctl --user list-dependencies --plain --no-pager default.target | grep -oE 'dfgg@[a-z]+\.service' | sort -u || true)
[[ "$wants" == "$expected" ]] || { echo "unexpected autostart units: ${wants:-none}" >&2; exit 1; }
echo "autostart slot: $target"
