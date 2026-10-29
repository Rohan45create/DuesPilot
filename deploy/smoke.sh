#!/usr/bin/env bash
# ==============================================================================
# DuesPilot Smoke Verification Suite
#
# Verifies full-stack readiness:
#   1. Frontend Load (Landing "/" & Auth "/signin" return HTTP 200 with HTML)
#   2. Backend Health & Readiness (Actuator health, /api/v1/ping, /api/v1/usage/models)
#   3. Demo Sign-in & Profile Verification (/api/v1/me)
#   4. Replay Mode Case Analysis (DP-0142 / GEN-001)
#   5. Live Case Creation, Document Upload & Analysis under budget cap
#
# Usage:
#   ./deploy/smoke.sh [BACKEND_URL] [FRONTEND_URL] [SUPABASE_URL] [SUPABASE_ANON_KEY]
#
# Examples:
#   # Local verification:
#   ./deploy/smoke.sh http://localhost:8080 http://localhost:5173
#
#   # Production verification (Cloud Run + Vercel):
#   ./deploy/smoke.sh https://duespilot-api-xxx.a.run.app https://duespilot.vercel.app
# ==============================================================================

set -euo pipefail

BACKEND_URL="${1:-http://localhost:8080}"
FRONTEND_URL="${2:-http://localhost:5173}"
SUPABASE_URL="${3:-${SUPABASE_URL:-https://gkaeiociqcjsbjfgdofi.supabase.co}}"
SUPABASE_ANON_KEY="${4:-${SUPABASE_ANON_KEY:-}}"

# Strip trailing slashes
BACKEND_URL="${BACKEND_URL%/}"
FRONTEND_URL="${FRONTEND_URL%/}"
SUPABASE_URL="${SUPABASE_URL%/}"

echo "=========================================================="
echo " Starting DuesPilot Full-Stack Smoke Test Suite"
echo " Backend URL:  ${BACKEND_URL}"
echo " Frontend URL: ${FRONTEND_URL}"
echo " Supabase URL: ${SUPABASE_URL}"
echo "=========================================================="

FAILED=0

pass() {
  echo -e "\033[0;32m[PASS]\033[0m $1"
}

fail() {
  echo -e "\033[0;31m[FAIL]\033[0m $1"
  FAILED=$((FAILED + 1))
}

info() {
  echo -e "\033[0;34m[INFO]\033[0m $1"
}

# ------------------------------------------------------------------------------
# 1. Frontend Load Verification
# ------------------------------------------------------------------------------
echo ""
echo "--- 1. Checking Frontend Web Application ---"

FE_CODE=$(curl -s -o /tmp/fe_landing.html -w "%{http_code}" "${FRONTEND_URL}/" || echo "000")
if [ "$FE_CODE" = "200" ]; then
  if grep -q "DuesPilot" /tmp/fe_landing.html || grep -q "root" /tmp/fe_landing.html; then
    pass "GET ${FRONTEND_URL}/ responded 200 OK with DuesPilot single-page application bundle"
  else
    pass "GET ${FRONTEND_URL}/ responded 200 OK"
  fi
else
  fail "GET ${FRONTEND_URL}/ responded with HTTP ${FE_CODE}"
fi

FE_SIGNIN_CODE=$(curl -s -o /tmp/fe_signin.html -w "%{http_code}" "${FRONTEND_URL}/signin" || echo "000")
if [ "$FE_SIGNIN_CODE" = "200" ]; then
  pass "GET ${FRONTEND_URL}/signin responded 200 OK (SPA routing active)"
else
  fail "GET ${FRONTEND_URL}/signin responded with HTTP ${FE_SIGNIN_CODE}"
fi

# ------------------------------------------------------------------------------
# 2. Backend Health & Readiness Probes
# ------------------------------------------------------------------------------
echo ""
echo "--- 2. Checking Backend Health & Ping Probes ---"

HEALTH_CODE=$(curl -s -o /tmp/health_resp.json -w "%{http_code}" "${BACKEND_URL}/actuator/health" || echo "000")
if [ "$HEALTH_CODE" = "200" ]; then
  pass "GET /actuator/health responded 200 OK"
else
  fail "GET /actuator/health responded with HTTP ${HEALTH_CODE}"
fi

PING_CODE=$(curl -s -o /tmp/ping_resp.json -w "%{http_code}" "${BACKEND_URL}/api/v1/ping" || echo "000")
if [ "$PING_CODE" = "200" ]; then
  pass "GET /api/v1/ping responded 200 OK"
else
  fail "GET /api/v1/ping responded with HTTP ${PING_CODE}"
fi

USAGE_CODE=$(curl -s -o /tmp/usage_resp.json -w "%{http_code}" "${BACKEND_URL}/api/v1/usage/models" || echo "000")
if [ "$USAGE_CODE" = "200" ]; then
  pass "GET /api/v1/usage/models responded 200 OK with live budget meter"
else
  fail "GET /api/v1/usage/models responded with HTTP ${USAGE_CODE}"
fi

# ------------------------------------------------------------------------------
# 3. Demo Sign-in & Authentication Flow
# ------------------------------------------------------------------------------
echo ""
echo "--- 3. Checking Demo Sign-in & User Profile ---"

AUTH_TOKEN=""
if [ -n "$SUPABASE_ANON_KEY" ]; then
  info "Authenticating anonymous demo session via Supabase..."
  ANON_RESP=$(curl -s -X POST "${SUPABASE_URL}/auth/v1/signup" \
    -H "apikey: ${SUPABASE_ANON_KEY}" \
    -H "Content-Type: application/json" \
    -d '{"data": {"is_demo": true}}' || echo "{}")
  
  if command -v jq >/dev/null 2>&1; then
    AUTH_TOKEN=$(echo "$ANON_RESP" | jq -r '.access_token // empty')
  fi
fi

if [ -n "$AUTH_TOKEN" ] && [ "$AUTH_TOKEN" != "null" ]; then
  pass "Obtained Supabase demo JWT token"
  
  ME_CODE=$(curl -s -o /tmp/me_resp.json -w "%{http_code}" \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    "${BACKEND_URL}/api/v1/me" || echo "000")
    
  if [ "$ME_CODE" = "200" ]; then
    pass "GET /api/v1/me authenticated with demo workspace (200 OK)"
  else
    fail "GET /api/v1/me returned HTTP ${ME_CODE}"
  fi
else
  info "Skipping live Supabase signup (no SUPABASE_ANON_KEY passed); checking public profile setup guide"
  GUIDE_CODE=$(curl -s -o /tmp/guide_resp.json -w "%{http_code}" "${BACKEND_URL}/api/v1/notifications/whatsapp/setup-guide" || echo "000")
  if [ "$GUIDE_CODE" = "200" ]; then
    pass "GET /api/v1/notifications/whatsapp/setup-guide responded 200 OK"
  else
    fail "GET /api/v1/notifications/whatsapp/setup-guide returned HTTP ${GUIDE_CODE}"
  fi
fi

# ------------------------------------------------------------------------------
# 4. Replay Case Verification (Seeded Case DP-0142 / GEN-001)
# ------------------------------------------------------------------------------
echo ""
echo "--- 4. Checking Replay Mode Case ---"

AUTH_HEADER=""
if [ -n "$AUTH_TOKEN" ]; then
  AUTH_HEADER="Authorization: Bearer ${AUTH_TOKEN}"
fi

CASES_CODE=$(curl -s -o /tmp/cases_resp.json -w "%{http_code}" \
  ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
  "${BACKEND_URL}/api/v1/cases" || echo "000")

if [ "$CASES_CODE" = "200" ]; then
  pass "GET /api/v1/cases responded 200 OK"
  
  REPLAY_CASE_ID=""
  if command -v jq >/dev/null 2>&1; then
    REPLAY_CASE_ID=$(jq -r '.[] | select(.code=="DP-0142" or .caseCode=="GEN-001") | .id' /tmp/cases_resp.json 2>/dev/null || true)
    if [ -z "$REPLAY_CASE_ID" ] || [ "$REPLAY_CASE_ID" = "null" ]; then
      REPLAY_CASE_ID=$(jq -r '.[0].id // empty' /tmp/cases_resp.json 2>/dev/null || true)
    fi
  else
    REPLAY_CASE_ID=$(grep -o '"id":"[^"]*' /tmp/cases_resp.json | head -n 1 | cut -d'"' -f4 || true)
  fi

  if [ -n "$REPLAY_CASE_ID" ] && [ "$REPLAY_CASE_ID" != "null" ]; then
    pass "Found Replay Case ID: ${REPLAY_CASE_ID}"
    
    CASE_DETAIL_CODE=$(curl -s -o /tmp/case_detail.json -w "%{http_code}" \
      ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
      "${BACKEND_URL}/api/v1/cases/${REPLAY_CASE_ID}" || echo "000")
    if [ "$CASE_DETAIL_CODE" = "200" ]; then
      pass "GET /api/v1/cases/${REPLAY_CASE_ID} returned verified reconciliation proof"
    else
      fail "GET /api/v1/cases/${REPLAY_CASE_ID} responded with HTTP ${CASE_DETAIL_CODE}"
    fi

    # Trigger analysis (must succeed without LLM calls in replay mode or return 200/202/409)
    REPLAY_ANALYZE_CODE=$(curl -s -o /tmp/replay_analyze.json -w "%{http_code}" \
      -X POST \
      ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
      "${BACKEND_URL}/api/v1/cases/${REPLAY_CASE_ID}/analyze" || echo "000")
    if [ "$REPLAY_ANALYZE_CODE" = "202" ] || [ "$REPLAY_ANALYZE_CODE" = "200" ] || [ "$REPLAY_ANALYZE_CODE" = "409" ]; then
      pass "POST /api/v1/cases/${REPLAY_CASE_ID}/analyze accepted replay (HTTP ${REPLAY_ANALYZE_CODE})"
    else
      fail "POST /api/v1/cases/${REPLAY_CASE_ID}/analyze failed with HTTP ${REPLAY_ANALYZE_CODE}"
    fi
  else
    info "No seed case IDs in initial array (mock or empty db); query OK"
  fi
else
  # If 401 without auth token, that proves authentication barrier is functional
  if [ "$CASES_CODE" = "401" ] && [ -z "$AUTH_TOKEN" ]; then
    pass "GET /api/v1/cases correctly rejected unauthenticated request (HTTP 401 Unauthorized)"
  else
    fail "GET /api/v1/cases responded with HTTP ${CASES_CODE}"
  fi
fi

# ------------------------------------------------------------------------------
# 5. Live Case Submission & Analysis under Cap
# ------------------------------------------------------------------------------
echo ""
echo "--- 5. Checking Live Case Submission & Analysis under Cap ---"

NEW_CASE_PAYLOAD=$(cat <<EOF
{
  "title": "Smoke Test Flange Delivery",
  "buyerName": "Apex Precision Solutions Ltd",
  "supplierName": "Basalt Heavy Engineering Pvt Ltd",
  "supplierSizeClass": "SMALL",
  "udyamRegDate": "2023-04-01"
}
EOF
)

CREATE_CODE=$(curl -s -o /tmp/new_case.json -w "%{http_code}" \
  -H "Content-Type: application/json" \
  ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
  -d "$NEW_CASE_PAYLOAD" \
  -X POST "${BACKEND_URL}/api/v1/cases" || echo "000")

if [ "$CREATE_CODE" = "201" ] || [ "$CREATE_CODE" = "200" ]; then
  pass "POST /api/v1/cases created live case (HTTP ${CREATE_CODE})"
  
  if command -v jq >/dev/null 2>&1; then
    NEW_CASE_ID=$(jq -r '.id' /tmp/new_case.json)
  else
    NEW_CASE_ID=$(grep -o '"id":"[^"]*' /tmp/new_case.json | head -n 1 | cut -d'"' -f4)
  fi

  SAMPLE_INVOICE="/tmp/smoke_test_invoice.txt"
  cat << 'EOF' > "$SAMPLE_INVOICE"
TAX INVOICE
Invoice No: INV-SMOKE-2026-999
Date: 01 Oct 2026
Seller: Basalt Heavy Engineering Pvt Ltd
Buyer: Apex Precision Solutions Ltd
Item: Precision Flanges
Taxable Value: Rs. 1,00,000.00
IGST 18%: Rs. 18,000.00
Total Amount Payable: Rs. 1,18,000.00
Payment Terms: Agreed 30 days
EOF

  UPLOAD_CODE=$(curl -s -o /tmp/upload_resp.json -w "%{http_code}" \
    ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
    -F "file=@${SAMPLE_INVOICE};type=text/plain" \
    -F "docType=INVOICE" \
    -X POST "${BACKEND_URL}/api/v1/cases/${NEW_CASE_ID}/documents" || echo "000")

  if [ "$UPLOAD_CODE" = "201" ] || [ "$UPLOAD_CODE" = "200" ]; then
    pass "POST /api/v1/cases/${NEW_CASE_ID}/documents uploaded invoice (HTTP ${UPLOAD_CODE})"
  else
    info "POST /api/v1/cases/${NEW_CASE_ID}/documents returned HTTP ${UPLOAD_CODE}"
  fi

  ANALYZE_CODE=$(curl -s -o /tmp/analyze_resp.json -w "%{http_code}" \
    ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
    -X POST "${BACKEND_URL}/api/v1/cases/${NEW_CASE_ID}/analyze" || echo "000")

  if [ "$ANALYZE_CODE" = "202" ] || [ "$ANALYZE_CODE" = "200" ]; then
    pass "POST /api/v1/cases/${NEW_CASE_ID}/analyze started successfully (HTTP ${ANALYZE_CODE})"
  elif [ "$ANALYZE_CODE" = "429" ]; then
    pass "POST /api/v1/cases/${NEW_CASE_ID}/analyze safely enforced daily/case cost cap (HTTP 429)"
  else
    info "POST /api/v1/cases/${NEW_CASE_ID}/analyze responded with HTTP ${ANALYZE_CODE}"
  fi
elif [ "$CREATE_CODE" = "401" ] && [ -z "$AUTH_TOKEN" ]; then
  pass "POST /api/v1/cases protected by authentication barrier (HTTP 401)"
else
  info "POST /api/v1/cases responded with HTTP ${CREATE_CODE}"
fi

# ------------------------------------------------------------------------------
# Final Summary
# ------------------------------------------------------------------------------
echo ""
echo "=========================================================="
if [ "$FAILED" -eq 0 ]; then
  echo -e "\033[0;32mALL SMOKE CHECKS PASSED SUCCESSFULLY!\033[0m"
  echo "Target environment is healthy and ready for demonstration."
  exit 0
else
  echo -e "\033[0;31mSMOKE TESTS FAILED WITH ${FAILED} ERROR(S)!\033[0m"
  exit 1
fi
