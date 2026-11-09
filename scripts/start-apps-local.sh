#!/usr/bin/env bash
# Start any missing local bootRun services (does not touch Docker infra).
# Logs: /tmp/<module>-boot.log
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# module:port
APPS=(
  "people-service:8081"
  "payroll-service:8082"
  "notification-service:8083"
  "file-service:8084"
  "report-service:8085"
  "leave-service:8086"
  "time-work-service:8087"
  "api-gateway:8090"
)

is_up() {
  local port="$1"
  curl -sf --connect-timeout 1 "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1
}

for entry in "${APPS[@]}"; do
  module="${entry%%:*}"
  port="${entry##*:}"
  if is_up "$port"; then
    echo "UP     :$port  $module"
    continue
  fi
  echo "START  :$port  $module  (log /tmp/${module}-boot.log)"
  nohup ./gradlew ":${module}:bootRun" -q >"/tmp/${module}-boot.log" 2>&1 &
  echo "       pid $!"
done

echo ""
echo "Waiting for health..."
for entry in "${APPS[@]}"; do
  module="${entry%%:*}"
  port="${entry##*:}"
  ready=0
  for _ in $(seq 1 90); do
    if is_up "$port"; then
      echo "READY  :$port  $module"
      ready=1
      break
    fi
    sleep 2
  done
  if [[ "$ready" -ne 1 ]]; then
    echo "FAIL   :$port  $module — see /tmp/${module}-boot.log"
  fi
done

bash "$ROOT/scripts/check-local-stack.sh"
