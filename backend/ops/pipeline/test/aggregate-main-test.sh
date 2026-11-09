#!/usr/bin/env bash
# aggregate-main.sh를 가짜 앱(python http.server)으로 시험한다. curl·jq·python3가 필요하다.
#
#   사용법 (Docker):
#   docker run --rm -v "$PWD/backend/ops/pipeline:/w:ro" amazonlinux:2023 \
#     bash -c 'dnf -q -y install jq > /dev/null 2>&1; /w/test/aggregate-main-test.sh'

set -Eeuo pipefail

here=$(cd "$(dirname "$0")" && pwd)
pipeline=$(dirname "$here")
work=$(mktemp -d)
port=8082
server_pid=""
trap 'kill "$server_pid" 2> /dev/null || true; rm -rf "$work"' EXIT

# 응답은 $work/mode 파일로 고른다: ok | fail | notready
cat > "$work/app.py" <<'EOF'
import json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer
work = sys.argv[1]

def mode():
    return open(f"{work}/mode").read().strip()

class App(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        status = "DOWN" if mode() == "notready" else "UP"
        self.reply(200, {"status": status})

    def do_POST(self):
        open(f"{work}/posted", "a").write(self.path + "\n")
        if mode() == "fail":
            self.reply(500, {"detail": "집계 단계 실패: champion-positions",
                             "failedStep": "champion-positions", "completedSteps": ["item-stats"]})
            return
        self.reply(200, {"steps": [{"name": "item-stats", "durationMillis": 1200, "result": {}},
                                   {"name": "champion-positions", "durationMillis": 30, "result": {}}],
                         "durationMillis": 1230})

HTTPServer(("127.0.0.1", 8082), App).serve_forever()
EOF
echo ok > "$work/mode"
python3 "$work/app.py" "$work" &
server_pid=$!
for _ in $(seq 1 50); do curl -s "http://127.0.0.1:$port/" > /dev/null && break; sleep 0.1; done

printf 'upstream dfgg_backend {\n    server 127.0.0.1:%s;\n    keepalive 16;\n}\n' "$port" > "$work/upstream.conf"
export UPSTREAM_CONF="$work/upstream.conf"

passed=0
failed=0
check() {
  local name=$1
  shift
  if "$@"; then
    passed=$((passed + 1)); echo "ok   $name"
    return
  fi
  failed=$((failed + 1)); echo "FAIL $name"
}
has() { grep -qF -- "$1" <<< "$2"; }
fails() { ! "$@" > /dev/null 2>&1; }
run() { "$pipeline/aggregate-main.sh" "$@" 2>&1; }
posts() { [[ -f "$work/posted" ]] && wc -l < "$work/posted" || echo 0; }

# 1. 성공
out=$(run)
check "활성 슬롯 포트로 전체 집계를 부른다" has "POST http://127.0.0.1:8082/admin/mining/all" "$out"
check "단계별 소요 시간을 남긴다" has "step item-stats: 1200ms" "$out"
check "끝나면 단계 수를 알린다" has "aggregate done: 2 steps" "$out"
check "엔드포인트는 /admin/mining/all이다" test "$(head -1 "$work/posted")" = "/admin/mining/all"

# 2. dry-run은 부르지 않는다
before=$(posts)
check "dry-run은 성공한다" has "would POST http://127.0.0.1:8082/admin/mining/all" "$(run --dry-run)"
check "dry-run은 집계를 부르지 않는다" test "$(posts)" = "$before"

# 3. 실패
echo fail > "$work/mode"
check "집계가 실패하면 0이 아닌 코드로 끝난다" fails "$pipeline/aggregate-main.sh"
out=$(run || true)
check "실패한 단계와 끝난 단계를 알린다" has "step=champion-positions completed=[item-stats]" "$out"

echo notready > "$work/mode"
before=$(posts)
check "활성 슬롯이 준비되지 않았으면 부르지 않고 실패한다" fails "$pipeline/aggregate-main.sh"
check "준비 안 됨일 때 집계 요청이 없다" test "$(posts)" = "$before"

echo ok > "$work/mode"
printf 'upstream dfgg_backend {\n    server 127.0.0.1:9999;\n}\n' > "$work/upstream.conf"
check "알 수 없는 upstream 포트면 실패한다" fails "$pipeline/aggregate-main.sh"

echo "passed=$passed failed=$failed"
[[ "$failed" == 0 ]]
