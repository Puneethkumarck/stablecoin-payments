#!/usr/bin/env bash
set -euo pipefail

#
# Phase 1 E2E Integration Test Runner
#
# Prerequisites:
#   - Docker containers running (via Terraform or docker-compose)
#   - Newman installed: npm install -g newman
#   - Java 25 + Gradle available
#
# Usage:
#   ./infra/postman/run-e2e.sh [--skip-build] [--skip-infra-check]
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Service ports
S11_PORT=8081
S13_PORT=8083
S10_PORT=8080

# Infrastructure ports
MAILPIT_UI_PORT=8025
WIREMOCK_PORT=4444
POSTGRES_PORT=5432
REDIS_PORT=6379
KAFKA_PORT=9092

# PIDs for cleanup
S11_PID=""
S13_PID=""
S10_PID=""

SKIP_BUILD=false
SKIP_INFRA_CHECK=false

for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=true ;;
    --skip-infra-check) SKIP_INFRA_CHECK=true ;;
  esac
done

cleanup() {
  echo ""
  echo "=== Cleaning up ==="
  for pid_var in S11_PID S13_PID S10_PID; do
    pid="${!pid_var}"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "Stopping $pid_var (PID=$pid)"
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
  echo "Cleanup complete."
}
trap cleanup EXIT

check_port() {
  local port=$1
  local name=$2
  if ! nc -z localhost "$port" 2>/dev/null; then
    echo "ERROR: $name not reachable on port $port"
    return 1
  fi
  echo "  $name (port $port) ... OK"
}

wait_for_health() {
  local url=$1
  local name=$2
  local max_attempts=${3:-60}
  local attempt=0

  echo -n "Waiting for $name at $url "
  while [[ $attempt -lt $max_attempts ]]; do
    if curl -sf "$url" > /dev/null 2>&1; then
      echo " UP"
      return 0
    fi
    echo -n "."
    sleep 2
    attempt=$((attempt + 1))
  done
  echo " TIMEOUT"
  echo "ERROR: $name did not become healthy after $((max_attempts * 2))s"
  return 1
}

echo "============================================"
echo "  Phase 1 E2E Integration Test Runner"
echo "============================================"
echo ""

# Step 1: Check infrastructure
if [[ "$SKIP_INFRA_CHECK" == "false" ]]; then
  echo "=== Step 1: Checking infrastructure ==="
  INFRA_OK=true
  check_port $POSTGRES_PORT "PostgreSQL" || INFRA_OK=false
  check_port $REDIS_PORT "Redis" || INFRA_OK=false
  check_port $KAFKA_PORT "Kafka/Redpanda" || INFRA_OK=false
  check_port $MAILPIT_UI_PORT "Mailpit" || INFRA_OK=false
  check_port $WIREMOCK_PORT "WireMock" || INFRA_OK=false

  if [[ "$INFRA_OK" == "false" ]]; then
    echo ""
    echo "Infrastructure not fully running. Start it with:"
    echo "  cd infra/terraform/local && terraform apply"
    echo "  OR"
    echo "  docker compose -f docker-compose.dev.yml up -d"
    exit 1
  fi
  echo ""
fi

# Step 2: Build JARs
if [[ "$SKIP_BUILD" == "false" ]]; then
  echo "=== Step 2: Building service JARs ==="
  cd "$PROJECT_ROOT"
  ./gradlew :merchant-onboarding:merchant-onboarding:bootJar \
            :merchant-iam:merchant-iam:bootJar \
            :api-gateway-iam:api-gateway-iam:bootJar \
            --parallel -q
  echo "Build complete."
  echo ""
else
  echo "=== Step 2: Skipping build (--skip-build) ==="
  echo ""
fi

# Step 3: Clear Mailpit
echo "=== Step 3: Clearing Mailpit inbox ==="
curl -sf -X DELETE "http://localhost:$MAILPIT_UI_PORT/api/v1/messages" > /dev/null 2>&1 || true
echo "Mailpit inbox cleared."
echo ""

# Step 4: Start services
echo "=== Step 4: Starting services ==="

# Find the boot JARs
S11_JAR=$(find "$PROJECT_ROOT/merchant-onboarding/merchant-onboarding/build/libs" -name "*.jar" ! -name "*-plain.jar" | head -1)
S13_JAR=$(find "$PROJECT_ROOT/merchant-iam/merchant-iam/build/libs" -name "*.jar" ! -name "*-plain.jar" | head -1)
S10_JAR=$(find "$PROJECT_ROOT/api-gateway-iam/api-gateway-iam/build/libs" -name "*.jar" ! -name "*-plain.jar" | head -1)

if [[ -z "$S11_JAR" || -z "$S13_JAR" || -z "$S10_JAR" ]]; then
  echo "ERROR: One or more service JARs not found. Run without --skip-build."
  exit 1
fi

LOG_DIR="$PROJECT_ROOT/infra/postman/logs"
mkdir -p "$LOG_DIR"

echo "Starting S11 (Merchant Onboarding) on port $S11_PORT..."
java -jar "$S11_JAR" \
  --spring.profiles.active=local,sandbox \
  --onfido.api.base-url=http://localhost:$WIREMOCK_PORT \
  --onfido.api.token=sandbox-token \
  --onfido.webhook.secret=test-webhook-secret \
  > "$LOG_DIR/s11.log" 2>&1 &
S11_PID=$!

echo "Starting S13 (Merchant IAM) on port $S13_PORT..."
java -jar "$S13_JAR" \
  --spring.profiles.active=local \
  > "$LOG_DIR/s13.log" 2>&1 &
S13_PID=$!

echo "Starting S10 (API Gateway IAM) on port $S10_PORT..."
java -jar "$S10_JAR" \
  --spring.profiles.active=local \
  --merchant-iam.base-url=http://localhost:$S13_PORT/iam \
  > "$LOG_DIR/s10.log" 2>&1 &
S10_PID=$!

echo ""

# Step 5: Wait for health
echo "=== Step 5: Waiting for services to be healthy ==="
wait_for_health "http://localhost:$S11_PORT/onboarding/actuator/health" "S11" 60
wait_for_health "http://localhost:$S13_PORT/iam/actuator/health" "S13" 60
wait_for_health "http://localhost:$S10_PORT/gateway/actuator/health" "S10" 60
echo ""

# Step 6: Run Newman
echo "=== Step 6: Running Newman E2E tests ==="
echo ""

newman run "$SCRIPT_DIR/Phase1-E2E.postman_collection.json" \
  -e "$SCRIPT_DIR/Phase1-Local.postman_environment.json" \
  --timeout-request 30000 \
  --delay-request 500 \
  --reporters cli,json \
  --reporter-json-export "$LOG_DIR/newman-results.json" \
  --color on

NEWMAN_EXIT=$?

echo ""
echo "============================================"
if [[ $NEWMAN_EXIT -eq 0 ]]; then
  echo "  ALL E2E TESTS PASSED"
else
  echo "  SOME E2E TESTS FAILED (exit code: $NEWMAN_EXIT)"
  echo ""
  echo "  Check logs:"
  echo "    S11: $LOG_DIR/s11.log"
  echo "    S13: $LOG_DIR/s13.log"
  echo "    S10: $LOG_DIR/s10.log"
  echo "    Newman: $LOG_DIR/newman-results.json"
fi
echo "============================================"

exit $NEWMAN_EXIT
