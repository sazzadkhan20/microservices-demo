#!/usr/bin/env bash
# Runs ON the target server (dev or production), invoked by .github/workflows/release.yml.
#   - pins every image to the exact digest in release-manifest.env (written by CI)
#   - rolls the stack forward with `docker compose up --wait` (health-gated)
#   - on failure, rolls back to the previous manifest automatically (guide §10 release recovery)
# Requires: docker compose v2.20+, ./.env with the environment's secrets (mode 600, from the secrets manager).
set -euo pipefail
cd "$(dirname "$0")"

COMPOSE=(docker compose -f docker-compose.deploy.yml --env-file .env)
MANIFEST=release-manifest.env
PREVIOUS=release-manifest.previous.env
CURRENT=release-manifest.current.env

[[ -f .env ]] || { echo "missing .env (provision it from the secrets manager first)"; exit 1; }
[[ -f "$MANIFEST" ]] || { echo "missing $MANIFEST"; exit 1; }
chmod 600 .env

load_manifest() {
  # api-gateway=ghcr.io/owner/qii-hrms-api-gateway@sha256:...  →  IMAGE_API_GATEWAY=...
  while IFS='=' read -r svc ref; do
    [[ -z "${svc}" || "${svc}" == \#* ]] && continue
    [[ "$ref" == *@sha256:* ]] || { echo "refusing non-digest image for $svc: $ref"; exit 1; }
    var="IMAGE_$(echo "$svc" | tr '[:lower:]-' '[:upper:]_')"
    export "$var=$ref"
  done < "$1"
}

deploy() {
  load_manifest "$1"
  "${COMPOSE[@]}" pull --quiet
  # Stateful infra first (dev profile only), then services; --wait blocks until every healthcheck passes.
  "${COMPOSE[@]}" up -d --remove-orphans --wait --wait-timeout "${DEPLOY_WAIT_SECONDS:-420}"
}

echo "==> Deploying ${RELEASE_TAG:-unknown} ($(wc -l < "$MANIFEST") images)"
if deploy "$MANIFEST"; then
  [[ -f "$CURRENT" ]] && cp "$CURRENT" "$PREVIOUS"
  cp "$MANIFEST" "$CURRENT"
  echo "${RELEASE_TAG:-unknown} $(date -u +%FT%TZ)" >> release-history.log
  docker image prune -f --filter "until=168h" >/dev/null || true
  echo "==> ${RELEASE_TAG:-release} is live and healthy"
else
  echo "!! deployment failed health checks"
  "${COMPOSE[@]}" ps || true
  if [[ -f "$CURRENT" ]]; then
    echo "==> Rolling back to the last healthy release"
    deploy "$CURRENT" && echo "==> rolled back" || echo "!! rollback also failed — follow docs/RUNBOOK.md"
  fi
  exit 1
fi
