#!/usr/bin/env bash
set -euo pipefail

# Phase 2 Integration Test Runner
# Builds services, starts Docker Compose stack, runs tests, tears down.
#
# Usage:
#   ./scripts/run-phase2-tests.sh          # saga tests only
#   ./scripts/run-phase2-tests.sh --load   # include load test
#   ./scripts/run-phase2-tests.sh --skip-build  # skip Gradle build
#   ./scripts/run-phase2-tests.sh --keep   # don't tear down after

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/docker-compose.phase2-test.yml"

RUN_LOAD=false
SKIP_BUILD=false
KEEP_RUNNING=false

for arg in "$@"; do
    case $arg in
        --load) RUN_LOAD=true ;;
        --skip-build) SKIP_BUILD=true ;;
        --keep) KEEP_RUNNING=true ;;
        --help)
            echo "Usage: $0 [--load] [--skip-build] [--keep]"
            echo "  --load        Include load test (100 concurrent payments)"
            echo "  --skip-build  Skip Gradle build and Docker image creation"
            echo "  --keep        Keep Docker Compose running after tests"
            exit 0
            ;;
    esac
done

cleanup() {
    if [ "$KEEP_RUNNING" = false ]; then
        echo ">>> Tearing down Docker Compose..."
        docker compose -f "$COMPOSE_FILE" down -v 2>/dev/null || true
    else
        echo ">>> Stack left running (--keep). Tear down with:"
        echo "    docker compose -f $COMPOSE_FILE down -v"
    fi
}

trap cleanup EXIT

cd "$PROJECT_DIR"

# Step 1: Build service JARs and Docker images
if [ "$SKIP_BUILD" = false ]; then
    echo ">>> Building service JARs and Docker images..."
    ./gradlew \
        :compliance-travel-rule:compliance-travel-rule:jibDockerBuild \
        :fx-liquidity-engine:fx-liquidity-engine:jibDockerBuild \
        :payment-orchestrator:payment-orchestrator:jibDockerBuild \
        --parallel
    echo ">>> Docker images built successfully"
else
    echo ">>> Skipping build (--skip-build)"
fi

# Step 2: Stop any existing stack
echo ">>> Stopping existing containers..."
docker compose -f "$COMPOSE_FILE" down -v 2>/dev/null || true

# Step 3: Start Docker Compose
echo ">>> Starting Docker Compose stack..."
docker compose -f "$COMPOSE_FILE" up -d

# Step 4: Wait for services to be healthy
echo ">>> Waiting for services to become healthy..."

wait_for_health() {
    local service=$1
    local url=$2
    local max_wait=${3:-120}
    local elapsed=0

    while [ $elapsed -lt $max_wait ]; do
        if curl -sf "$url" > /dev/null 2>&1; then
            echo "  ✓ $service is healthy"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "  ✗ $service failed to start within ${max_wait}s"
    echo "  Logs:"
    docker compose -f "$COMPOSE_FILE" logs "$service" --tail=50
    return 1
}

wait_for_health "compliance-travel-rule" "http://localhost:8083/compliance/actuator/health" 120
wait_for_health "fx-liquidity-engine"    "http://localhost:8084/fx/actuator/health" 120
wait_for_health "payment-orchestrator"   "http://localhost:8082/orchestrator/actuator/health" 120

echo ">>> All services are healthy"

# Step 5: Run saga integration tests
echo ">>> Running Phase 2 saga integration tests..."
./gradlew :phase2-integration-tests:test --info

# Step 6: Optionally run load test
if [ "$RUN_LOAD" = true ]; then
    echo ">>> Running Phase 2 load test (100 concurrent payments)..."
    ./gradlew :phase2-integration-tests:loadTest --info
fi

echo ">>> Phase 2 integration tests completed successfully!"
