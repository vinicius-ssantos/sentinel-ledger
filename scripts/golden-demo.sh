#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${SENTINEL_DEMO_BASE_URL:-http://localhost:8080}"
MERCHANT_USERNAME="${SENTINEL_MERCHANT_API_KEY_ID:-sentinel-dev-merchant}"
MERCHANT_PASSWORD="${SENTINEL_MERCHANT_API_KEY_SECRET:-sentinel-dev-secret}"
RESET_DATABASE=false
APP_STARTED_BY_SCRIPT=false
APP_PID=""
TMP_DIR="$(mktemp -d)"
APP_LOG="$TMP_DIR/sentinel-golden-demo-app.log"
REQUEST_INDEX=0
HTTP_STATUS=""
RESPONSE_BODY=""
RESPONSE_HEADERS=""

usage() {
  cat <<'EOF'
Usage: bash scripts/golden-demo.sh [--reset]

Runs the executable Sentinel Ledger core golden path:
  1. persistent idempotency replay;
  2. idempotency-key conflict detection;
  3. authorization through the simulated PSP;
  4. full capture and partial refund;
  5. final-state and timeline evidence assertions.

Options:
  --reset     Stop Compose services and remove local volumes before starting.
              Refuses to reset while an existing Sentinel application is healthy.
  -h, --help  Show this help.

Environment overrides:
  SENTINEL_DEMO_BASE_URL
  SENTINEL_MERCHANT_API_KEY_ID
  SENTINEL_MERCHANT_API_KEY_SECRET
EOF
}

log() {
  printf '\n==> %s\n' "$*"
}

fail() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

new_idempotency_key() {
  printf 'sentinel-demo-%s-%s-%s' "$(date +%s)" "$RANDOM" "$RANDOM"
}

header_value() {
  local name="$1"
  printf '%s\n' "$RESPONSE_HEADERS" \
    | awk -v expected="$name" 'tolower(substr($0, 1, length(expected) + 1)) == tolower(expected ":") { sub(/^[^:]+:[[:space:]]*/, ""); value=$0 } END { print value }'
}

assert_status() {
  local expected="$1"
  [[ "$HTTP_STATUS" == "$expected" ]] || fail "Expected HTTP $expected but received $HTTP_STATUS. Body: $RESPONSE_BODY"
}

assert_json_equals() {
  local expression="$1"
  local expected="$2"
  local actual
  actual="$(jq -er "$expression" <<<"$RESPONSE_BODY")" || fail "Response did not satisfy jq expression: $expression. Body: $RESPONSE_BODY"
  [[ "$actual" == "$expected" ]] || fail "Expected $expression to equal '$expected' but got '$actual'. Body: $RESPONSE_BODY"
}

assert_body_contains() {
  local needle="$1"
  grep -Fq "$needle" <<<"$RESPONSE_BODY" || fail "Expected response body to contain '$needle'. Body: $RESPONSE_BODY"
}

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local idempotency_key="${4:-}"
  local header_file body_file

  REQUEST_INDEX=$((REQUEST_INDEX + 1))
  header_file="$TMP_DIR/headers-$REQUEST_INDEX.txt"
  body_file="$TMP_DIR/body-$REQUEST_INDEX.json"

  local -a curl_args=(
    --silent
    --show-error
    --user "$MERCHANT_USERNAME:$MERCHANT_PASSWORD"
    --dump-header "$header_file"
    --output "$body_file"
    --write-out '%{http_code}'
    --request "$method"
  )

  if [[ -n "$idempotency_key" ]]; then
    curl_args+=(--header "Idempotency-Key: $idempotency_key")
  fi

  if [[ -n "$body" ]]; then
    curl_args+=(--header 'Content-Type: application/json' --data "$body")
  fi

  HTTP_STATUS="$(curl "${curl_args[@]}" "$BASE_URL$path")" || fail "Request failed: $method $path"
  RESPONSE_BODY="$(cat "$body_file")"
  RESPONSE_HEADERS="$(tr -d '\r' <"$header_file")"
}

application_is_healthy() {
  curl --silent --fail "$BASE_URL/actuator/health" | jq -e '.status == "UP"' >/dev/null 2>&1
}

wait_for_application() {
  local attempts=90
  local attempt

  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if application_is_healthy; then
      return 0
    fi
    if [[ -n "$APP_PID" ]] && ! kill -0 "$APP_PID" 2>/dev/null; then
      tail -n 80 "$APP_LOG" >&2 || true
      fail "Spring Boot exited before becoming healthy"
    fi
    sleep 1
  done

  tail -n 80 "$APP_LOG" >&2 || true
  fail "Sentinel Ledger did not become healthy within ${attempts}s"
}

cleanup() {
  local exit_code=$?

  if [[ "$exit_code" -ne 0 && "$APP_STARTED_BY_SCRIPT" == true ]]; then
    printf '\nLast application log lines:\n' >&2
    tail -n 80 "$APP_LOG" >&2 || true
  fi

  if [[ "$APP_STARTED_BY_SCRIPT" == true && -n "$APP_PID" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi

  rm -rf "$TMP_DIR"
  exit "$exit_code"
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --reset)
      RESET_DATABASE=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      fail "Unknown argument: $1"
      ;;
  esac
  shift
done

require_command curl
require_command jq
require_command docker

cd "$ROOT_DIR"

if application_is_healthy; then
  if [[ "$RESET_DATABASE" == true ]]; then
    fail "Refusing --reset because a Sentinel application is already healthy at $BASE_URL"
  fi
  log "Using the existing Sentinel Ledger application at $BASE_URL"
else
  require_command java

  if [[ "$RESET_DATABASE" == true ]]; then
    log "Resetting local Compose state"
    docker compose down --volumes --remove-orphans
  fi

  log "Starting PostgreSQL"
  docker compose up --detach --wait postgres

  log "Starting Sentinel Ledger"
  ./mvnw --batch-mode --no-transfer-progress spring-boot:run >"$APP_LOG" 2>&1 &
  APP_PID=$!
  APP_STARTED_BY_SCRIPT=true
  wait_for_application
fi

log "Creating a synthetic BRL 100.00 payment intent"
CREATE_KEY="$(new_idempotency_key)"
CREATE_BODY='{"amountInMinorUnits":"10000","currency":"BRL"}'
request POST '/api/v1/payment-intents' "$CREATE_BODY" "$CREATE_KEY"
assert_status 201
assert_json_equals '.state' 'CREATED'
assert_json_equals '.amountInMinorUnits' '10000'
FIRST_CREATE_BODY="$RESPONSE_BODY"
LOCATION="$(header_value 'Location')"
[[ "$LOCATION" =~ ^/api/v1/payment-intents/[0-9a-fA-F-]{36}$ ]] || fail "Unexpected Location header: $LOCATION"
PAYMENT_INTENT_ID="${LOCATION##*/}"

log "Replaying the same mutation with the same idempotency key"
request POST '/api/v1/payment-intents' "$CREATE_BODY" "$CREATE_KEY"
assert_status 201
[[ "$(header_value 'Idempotent-Replayed')" == 'true' ]] || fail "Expected Idempotent-Replayed: true"
[[ "$RESPONSE_BODY" == "$FIRST_CREATE_BODY" ]] || fail "Idempotent replay body differs from the original response"

log "Rejecting the same key with a different payload"
request POST '/api/v1/payment-intents' '{"amountInMinorUnits":"10001","currency":"BRL"}' "$CREATE_KEY"
assert_status 409
assert_json_equals '.code' 'IDEMPOTENCY_KEY_REUSED'

log "Authorizing through the deterministic simulated PSP"
request POST "/api/v1/payment-intents/$PAYMENT_INTENT_ID/authorize" '' "$(new_idempotency_key)"
assert_status 200
assert_json_equals '.state' 'AUTHORIZED'

log "Capturing the complete authorized amount"
request POST "/api/v1/payment-intents/$PAYMENT_INTENT_ID/captures" '{"amountInMinorUnits":"10000","currency":"BRL"}' "$(new_idempotency_key)"
assert_status 200
assert_json_equals '.state' 'CAPTURED'
assert_json_equals '.capturedAmountInMinorUnits' '10000'

log "Posting a partial BRL 30.00 compensating refund"
request POST "/api/v1/payment-intents/$PAYMENT_INTENT_ID/refunds" '{"amountInMinorUnits":"3000","currency":"BRL"}' "$(new_idempotency_key)"
assert_status 200
assert_json_equals '.state' 'PARTIALLY_REFUNDED'
assert_json_equals '.refundedAmountInMinorUnits' '3000'

log "Verifying the final authoritative payment state"
request GET "/api/v1/payment-intents/$PAYMENT_INTENT_ID"
assert_status 200
assert_json_equals '.state' 'PARTIALLY_REFUNDED'
assert_json_equals '.amountInMinorUnits' '10000'
assert_json_equals '.capturedAmountInMinorUnits' '10000'
assert_json_equals '.refundedAmountInMinorUnits' '3000'

log "Verifying audit, provider and ledger evidence on the timeline"
request GET "/api/v1/payment-intents/$PAYMENT_INTENT_ID/timeline"
assert_status 200
TIMELINE_SIZE="$(jq -er 'length' <<<"$RESPONSE_BODY")"
(( TIMELINE_SIZE >= 6 )) || fail "Expected at least 6 timeline entries but found $TIMELINE_SIZE"
assert_body_contains '"AUDIT_EVENT"'
assert_body_contains '"PROVIDER_RESULT"'
assert_body_contains '"LEDGER_TRANSACTION"'
assert_body_contains 'payment-intent.create'
assert_body_contains 'payment-intent.authorize'
assert_body_contains 'payment-intent.capture'
assert_body_contains 'payment-intent.refund'
assert_body_contains 'ledger.capture'
assert_body_contains 'ledger.refund'

printf '\nGolden demo passed.\n'
printf 'Payment intent: %s\n' "$PAYMENT_INTENT_ID"
printf 'Final state: PARTIALLY_REFUNDED\n'
printf 'Authorized/captured: BRL 100.00\n'
printf 'Refunded: BRL 30.00\n'
printf 'Timeline evidence entries: %s\n' "$TIMELINE_SIZE"
printf 'Inspect: %s/api/v1/payment-intents/%s/timeline\n' "$BASE_URL" "$PAYMENT_INTENT_ID"
