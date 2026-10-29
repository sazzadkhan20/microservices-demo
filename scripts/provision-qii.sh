#!/usr/bin/env bash
# Provision (or upgrade+reseed) Qii Creative tenant against local Gateway.
set -euo pipefail
GATEWAY="${GATEWAY:-http://127.0.0.1:8090}"
TENANT="${TENANT_ID:-qii_creative}"
ADMIN_KEY="${PLATFORM_ADMIN_KEY:-local-platform-admin-change-me}"

echo "Provisioning $TENANT via $GATEWAY ..."
code=$(curl -s -o /tmp/qii-provision.json -w "%{http_code}" -X POST "$GATEWAY/api/registry/provision" \
  -H 'Content-Type: application/json' \
  -H "X-Platform-Admin-Key: $ADMIN_KEY" \
  -d "{\"tenantId\":\"$TENANT\",\"businessPack\":\"qii_creative\"}" || true)

if [[ "$code" == "200" || "$code" == "201" ]]; then
  echo "Provision OK ($code)"
  cat /tmp/qii-provision.json
  exit 0
fi

echo "Provision returned $code — trying upgrade-hrms reseed..."
curl -s -X POST "$GATEWAY/api/registry/tenants/$TENANT/upgrade-hrms?reseed=true&businessPack=qii_creative" \
  -H 'Content-Type: application/json' \
  -H "X-Platform-Admin-Key: $ADMIN_KEY" | tee /tmp/qii-upgrade.json
echo
