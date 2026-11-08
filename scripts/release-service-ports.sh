#!/usr/bin/env bash
# Free local IDE / bootRun ports so services can restart cleanly.
# Does NOT stop Docker infra (Postgres/Redis/RabbitMQ/MinIO).
set -euo pipefail

PORTS=(8080 8081 8082 8083 8084 8085 8086 8087 8090 9081)
# Optional: include frontend with RELEASE_FRONTEND=1
if [[ "${RELEASE_FRONTEND:-0}" == "1" ]]; then
  PORTS+=(3000)
fi

kill_pid() {
  local pid="$1"
  local port="$2"
  [[ -z "$pid" || "$pid" == "0" ]] && return 0
  local name
  name=$(powershell.exe -NoProfile -Command "(Get-Process -Id $pid -ErrorAction SilentlyContinue).ProcessName" 2>/dev/null | tr -d '\r' || true)
  # Only kill java/node app listeners — never touch docker-proxy / com.docker
  case "${name:-}" in
    java|node|"")
      echo "Releasing :$port pid=$pid name=${name:-unknown}"
      powershell.exe -NoProfile -Command "Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
      ;;
    *)
      echo "Skip :$port pid=$pid name=$name (not a local app process)"
      ;;
  esac
}

for port in "${PORTS[@]}"; do
  # Windows netstat
  while read -r pid; do
    kill_pid "$pid" "$port"
  done < <(netstat -ano 2>/dev/null | awk -v p=":$port" '
    $0 ~ p && /LISTENING/ {
      print $NF
    }' | sort -u)
done

sleep 1
echo "==> Remaining listeners on service ports:"
for port in "${PORTS[@]}"; do
  hit=$(netstat -ano 2>/dev/null | awk -v p=":$port" '$0 ~ p && /LISTENING/ {print}' | head -1 || true)
  if [[ -n "${hit:-}" ]]; then
    echo "  STILL HELD :$port — $hit"
  else
    echo "  free :$port"
  fi
done
