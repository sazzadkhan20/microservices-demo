#!/usr/bin/env bash
# Quick health probe for the local IDE/bootRun stack (fixed single-company layout).
set -euo pipefail

pass=0
fail=0

check_http() {
  local name="$1" url="$2"
  local code
  code=$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 5 "$url" 2>/dev/null || echo 000)
  if [[ "$code" == "200" ]]; then
    echo "OK   $name  ($url)"
    pass=$((pass + 1))
  else
    echo "DOWN $name  ($url) http=$code"
    fail=$((fail + 1))
  fi
}

check_listen() {
  local name="$1" port="$2"
  if netstat -ano 2>/dev/null | awk -v p=":$port" '$0 ~ p && /LISTENING/ {found=1} END{exit !found}'; then
    echo "OK   $name  (listen :$port)"
    pass=$((pass + 1))
  else
    echo "DOWN $name  (listen :$port)"
    fail=$((fail + 1))
  fi
}

echo "==> Local stack health"
check_http "api-gateway"        "http://127.0.0.1:8090/actuator/health"
check_http "people-service"     "http://127.0.0.1:8081/actuator/health"
check_http "payroll-service"    "http://127.0.0.1:8082/actuator/health"
check_http "notification"       "http://127.0.0.1:8083/actuator/health"
check_http "file-service"       "http://127.0.0.1:8084/actuator/health"
check_http "report-service"     "http://127.0.0.1:8085/actuator/health"
check_http "leave-service"      "http://127.0.0.1:8086/actuator/health"
check_http "time-work-service"  "http://127.0.0.1:8087/actuator/health"
check_listen "people-grpc" 9081

echo ""
echo "Note: people :9081 is gRPC only — health is http://127.0.0.1:8081/actuator/health (never curl :9081)."
echo "Result: $pass ok, $fail down"
if [[ "$fail" -gt 0 ]]; then
  echo "Hint: after scripts/release-service-ports.sh, restart missing modules with ./gradlew :<module>:bootRun"
  echo "      or bring the whole stack up with: bash scripts/up-local.sh"
  exit 1
fi
