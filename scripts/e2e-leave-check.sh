#!/usr/bin/env bash
set -u
GW="${GW:-http://localhost:8090}"
DEMO="${DEMO:-local-demo-auth-key-change-me}"
pass=0; fail=0

ok(){ echo "OK  $*"; pass=$((pass+1)); }
bad(){ echo "FAIL $*"; fail=$((fail+1)); }

mint() {
  curl -s -X POST "$GW/api/auth/token" \
    -H "Content-Type: application/json" -H "X-Demo-Auth-Key: $DEMO" \
    -d "{\"employeeId\":$1,\"subject\":\"$2\"}" \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

# Top-level fields only (avoid nested approvals[].status / id).
json_status(){ grep -o '"status":"[^"]*"' | head -1 | sed 's/"status":"//;s/"$//'; }
json_id(){ grep -o '"id":[0-9][0-9]*' | head -1 | sed 's/"id"://'; }

TOK_EMP=$(mint 6 tania)
TOK_MGR=$(mint 4 lina)
TOK_HR=$(mint 2 nadia)
echo "tokens emp=${#TOK_EMP} mgr=${#TOK_MGR} hr=${#TOK_HR}"
[ -n "$TOK_EMP" ] && [ -n "$TOK_MGR" ] && [ -n "$TOK_HR" ] && ok "mint tokens" || bad "mint tokens"

AE=(-H "Authorization: Bearer $TOK_EMP" -H "Content-Type: application/json")
AM=(-H "Authorization: Bearer $TOK_MGR" -H "Content-Type: application/json")
AH=(-H "Authorization: Bearer $TOK_HR" -H "Content-Type: application/json")

BODY=$(curl -s "${AE[@]}" "$GW/hrms/api/auth/me")
echo "$BODY" | grep -q '"employeeId":"6"\|"employeeId":6' && ok "emp /me" || bad "emp /me $BODY"

CODE=$(curl -s -o /tmp/lv_bal.json -w '%{http_code}' "${AE[@]}" "$GW/hrms/api/leave-balances/employee/6")
[ "$CODE" = "200" ] && ok "balances" || bad "balances code=$CODE"

# Orphan seed self-heal: if app1 still awaiting without steps, pending/approve rebuilds from config.
CODE=$(curl -s -o /tmp/lv_g1.json -w '%{http_code}' "${AE[@]}" "$GW/hrms/api/leave-applications/1")
STAT=$(json_status < /tmp/lv_g1.json)
if [ "$STAT" = "PENDING_APPROVAL" ] || [ "$STAT" = "PENDING_CANCEL" ]; then
  BODY=$(curl -s "${AM[@]}" "$GW/hrms/api/approvals/pending")
  echo "$BODY" | grep -q '"id":1' && ok "orphan app1 in mgr pending (self-heal)" || bad "orphan app1 not healed"
  CODE=$(curl -s -o /tmp/lv_a1.json -w '%{http_code}' -X POST "${AM[@]}" "$GW/hrms/api/leave-applications/1/approve" -d '{}')
  STAT=$(json_status < /tmp/lv_a1.json)
  if [ "$CODE" = "200" ] && { [ "$STAT" = "APPROVED" ] || [ "$STAT" = "CANCELLED" ]; }; then ok "decide healed app1 → $STAT"; else bad "decide app1 code=$CODE status=$STAT"; fi
else
  ok "app1 already settled ($STAT)"
fi

# --- CASUAL single-step (2026 dates, free slots) ---
START=2026-12-10
CODE=$(curl -s -o /tmp/lv_prev.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications/preview" \
  -d "{\"leaveTypeCode\":\"CASUAL\",\"startDate\":\"$START\",\"endDate\":\"$START\",\"reason\":\"E2E preview\"}")
[ "$CODE" = "200" ] && ok "preview CASUAL" || bad "preview CASUAL code=$CODE $(head -c 200 /tmp/lv_prev.json)"

CODE=$(curl -s -o /tmp/lv_apply.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications" \
  -d "{\"leaveTypeCode\":\"CASUAL\",\"startDate\":\"$START\",\"endDate\":\"$START\",\"reason\":\"E2E casual apply\"}")
BODY=$(cat /tmp/lv_apply.json)
APP1=$(echo "$BODY" | json_id)
STAT=$(echo "$BODY" | json_status)
[ "$CODE" = "201" ] && [ -n "$APP1" ] && ok "apply CASUAL id=$APP1 status=$STAT" || bad "apply CASUAL code=$CODE $BODY"
[ "$STAT" = "PENDING_APPROVAL" ] && ok "CASUAL pending" || bad "CASUAL status=$STAT"

BODY=$(curl -s "${AM[@]}" "$GW/hrms/api/approvals/pending")
echo "$BODY" | grep -q "\"id\":$APP1" && ok "mgr pending has CASUAL" || bad "mgr pending missing CASUAL"

BODY=$(curl -s "${AH[@]}" "$GW/hrms/api/approvals/pending")
echo "$BODY" | grep -q "\"id\":$APP1" && bad "HR wrongly sees mgr-step CASUAL" || ok "HR does not see mgr-step CASUAL"

CODE=$(curl -s -o /tmp/lv_apr.json -w '%{http_code}' -X POST "${AM[@]}" "$GW/hrms/api/leave-applications/$APP1/approve" -d '{}')
STAT=$(json_status < /tmp/lv_apr.json)
[ "$CODE" = "200" ] && [ "$STAT" = "APPROVED" ] && ok "approve CASUAL" || bad "approve CASUAL code=$CODE status=$STAT"

# cancel → withdraw → cancel → reject → cancel → approve
CODE=$(curl -s -o /tmp/lv_c1.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications/$APP1/cancel")
STAT=$(json_status < /tmp/lv_c1.json)
[ "$CODE" = "200" ] && [ "$STAT" = "PENDING_CANCEL" ] && ok "request cancel" || bad "cancel code=$CODE status=$STAT $(head -c 200 /tmp/lv_c1.json)"

CODE=$(curl -s -o /tmp/lv_gc.json -w '%{http_code}' "${AE[@]}" "$GW/hrms/api/leave-applications/$APP1")
[ "$CODE" = "200" ] && ok "get PENDING_CANCEL (no NonUnique)" || bad "get cancel code=$CODE $(head -c 200 /tmp/lv_gc.json)"

BODY=$(curl -s "${AM[@]}" "$GW/hrms/api/approvals/pending")
echo "$BODY" | grep -q "\"id\":$APP1" && ok "mgr pending has cancel" || bad "mgr pending missing cancel"

CODE=$(curl -s -o /tmp/lv_wd.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications/$APP1/withdraw")
STAT=$(json_status < /tmp/lv_wd.json)
[ "$CODE" = "200" ] && [ "$STAT" = "APPROVED" ] && ok "withdraw cancel → APPROVED" || bad "withdraw cancel code=$CODE status=$STAT"

CODE=$(curl -s -o /tmp/lv_c2.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications/$APP1/cancel")
[ "$CODE" = "200" ] && ok "re-cancel" || bad "re-cancel code=$CODE"
CODE=$(curl -s -o /tmp/lv_rj.json -w '%{http_code}' -X POST "${AM[@]}" "$GW/hrms/api/leave-applications/$APP1/reject" \
  -d '{"comment":"Keep the leave as planned"}')
STAT=$(json_status < /tmp/lv_rj.json)
[ "$CODE" = "200" ] && [ "$STAT" = "APPROVED" ] && ok "reject cancel → APPROVED" || bad "reject cancel code=$CODE status=$STAT $(head -c 200 /tmp/lv_rj.json)"

CODE=$(curl -s -o /tmp/lv_c3.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications/$APP1/cancel")
[ "$CODE" = "200" ] && ok "cancel for final" || bad "cancel final code=$CODE"
CODE=$(curl -s -o /tmp/lv_ac.json -w '%{http_code}' -X POST "${AM[@]}" "$GW/hrms/api/leave-applications/$APP1/approve" -d '{}')
STAT=$(json_status < /tmp/lv_ac.json)
[ "$CODE" = "200" ] && [ "$STAT" = "CANCELLED" ] && ok "approve cancel → CANCELLED" || bad "approve cancel code=$CODE status=$STAT"

# --- ANNUAL two-step ---
START2=2026-12-14
END2=2026-12-15
CODE=$(curl -s -o /tmp/lv_ann.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications" \
  -d "{\"leaveTypeCode\":\"ANNUAL\",\"startDate\":\"$START2\",\"endDate\":\"$END2\",\"reason\":\"E2E annual multi-step\"}")
BODY=$(cat /tmp/lv_ann.json)
APP2=$(echo "$BODY" | json_id)
[ "$CODE" = "201" ] && [ -n "$APP2" ] && ok "apply ANNUAL id=$APP2" || bad "apply ANNUAL code=$CODE $BODY"

BODY=$(curl -s "${AM[@]}" "$GW/hrms/api/approvals/pending")
echo "$BODY" | grep -q "\"id\":$APP2" && ok "mgr sees ANNUAL step1" || bad "mgr missing ANNUAL"

CODE=$(curl -s -o /tmp/lv_a1s.json -w '%{http_code}' -X POST "${AM[@]}" "$GW/hrms/api/leave-applications/$APP2/approve" -d '{}')
STAT=$(json_status < /tmp/lv_a1s.json)
[ "$CODE" = "200" ] && [ "$STAT" = "PENDING_APPROVAL" ] && ok "ANNUAL after mgr still PENDING" || bad "ANNUAL after mgr code=$CODE status=$STAT"

BODY=$(curl -s "${AH[@]}" "$GW/hrms/api/approvals/pending")
echo "$BODY" | grep -q "\"id\":$APP2" && ok "HR sees ANNUAL step2" || bad "HR missing ANNUAL step2"

BODY=$(curl -s "${AM[@]}" "$GW/hrms/api/approvals/pending")
echo "$BODY" | grep -q "\"id\":$APP2" && bad "mgr still sees HR-step ANNUAL" || ok "mgr gone at HR step"

CODE=$(curl -s -o /tmp/lv_a2s.json -w '%{http_code}' -X POST "${AH[@]}" "$GW/hrms/api/leave-applications/$APP2/approve" -d '{}')
STAT=$(json_status < /tmp/lv_a2s.json)
[ "$CODE" = "200" ] && [ "$STAT" = "APPROVED" ] && ok "ANNUAL APPROVED" || bad "ANNUAL HR approve code=$CODE status=$STAT"

CODE=$(curl -s -o /tmp/lv_anc.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications/$APP2/cancel")
STAT=$(json_status < /tmp/lv_anc.json)
[ "$CODE" = "200" ] && [ "$STAT" = "PENDING_CANCEL" ] && ok "ANNUAL PENDING_CANCEL" || bad "ANNUAL cancel code=$CODE status=$STAT $(head -c 200 /tmp/lv_anc.json)"

CODE=$(curl -s -o /tmp/lv_ancg.json -w '%{http_code}' "${AE[@]}" "$GW/hrms/api/leave-applications/$APP2")
[ "$CODE" = "200" ] && ok "get multi-step PENDING_CANCEL" || bad "get multi cancel code=$CODE"

STEPS=$(grep -o '"stepOrder":[0-9]*' /tmp/lv_ancg.json | wc -l | tr -d ' ')
[ "${STEPS:-0}" = "2" ] && ok "cancel chain has 2 unique steps" || bad "cancel steps=$STEPS want=2"
CODE=$(curl -s -o /tmp/lv_anc1.json -w '%{http_code}' -X POST "${AM[@]}" "$GW/hrms/api/leave-applications/$APP2/approve" -d '{}')
STAT=$(json_status < /tmp/lv_anc1.json)
[ "$CODE" = "200" ] && [ "$STAT" = "PENDING_CANCEL" ] && ok "cancel after mgr still PENDING_CANCEL" || bad "cancel step1 code=$CODE status=$STAT"

CODE=$(curl -s -o /tmp/lv_anc2.json -w '%{http_code}' -X POST "${AH[@]}" "$GW/hrms/api/leave-applications/$APP2/approve" -d '{}')
STAT=$(json_status < /tmp/lv_anc2.json)
[ "$CODE" = "200" ] && [ "$STAT" = "CANCELLED" ] && ok "ANNUAL cancel → CANCELLED" || bad "cancel step2 code=$CODE status=$STAT"

# withdraw pending apply
START3=2026-12-22
CODE=$(curl -s -o /tmp/lv_wda.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications" \
  -d "{\"leaveTypeCode\":\"CASUAL\",\"startDate\":\"$START3\",\"endDate\":\"$START3\",\"reason\":\"will withdraw\"}")
APP3=$(json_id < /tmp/lv_wda.json)
CODE=$(curl -s -o /tmp/lv_wd3.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications/$APP3/withdraw")
STAT=$(json_status < /tmp/lv_wd3.json)
[ "$CODE" = "200" ] && [ "$STAT" = "WITHDRAWN" ] && ok "withdraw pending apply" || bad "withdraw apply code=$CODE status=$STAT"

# reject apply
START4=2026-12-23
CODE=$(curl -s -o /tmp/lv_rja.json -w '%{http_code}' -X POST "${AE[@]}" "$GW/hrms/api/leave-applications" \
  -d "{\"leaveTypeCode\":\"CASUAL\",\"startDate\":\"$START4\",\"endDate\":\"$START4\",\"reason\":\"will reject\"}")
APP4=$(json_id < /tmp/lv_rja.json)
CODE=$(curl -s -o /tmp/lv_rj4.json -w '%{http_code}' -X POST "${AM[@]}" "$GW/hrms/api/leave-applications/$APP4/reject" \
  -d '{"comment":"Coverage insufficient this week"}')
STAT=$(json_status < /tmp/lv_rj4.json)
[ "$CODE" = "200" ] && [ "$STAT" = "REJECTED" ] && ok "reject apply" || bad "reject apply code=$CODE status=$STAT $(head -c 200 /tmp/lv_rj4.json)"

# FE aliases
CODE=$(curl -s -o /tmp/lv_my.json -w '%{http_code}' "${AE[@]}" "$GW/hrms/api/leave/my?page=0&size=5")
[ "$CODE" = "200" ] && ok "FE /leave/my" || bad "FE /leave/my code=$CODE"

CODE=$(curl -s -o /tmp/lv_pend.json -w '%{http_code}' "${AM[@]}" "$GW/hrms/api/approvals/pending")
[ "$CODE" = "200" ] && ok "approvals/pending" || bad "approvals/pending code=$CODE"

# finish mid-chain app16 if still pending (cleanup)
CODE=$(curl -s -o /tmp/lv16.json -w '%{http_code}' "${AE[@]}" "$GW/hrms/api/leave-applications/16")
if [ "$CODE" = "200" ] && grep -q 'PENDING_APPROVAL' /tmp/lv16.json; then
  curl -s -o /dev/null -X POST "${AH[@]}" "$GW/hrms/api/leave-applications/16/approve" -d '{}' || true
  ok "cleaned mid-chain app16 (best-effort)"
fi

echo ""
echo "======== RESULTS pass=$pass fail=$fail ========"
[ "$fail" -eq 0 ]
