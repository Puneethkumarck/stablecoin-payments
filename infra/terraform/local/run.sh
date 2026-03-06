#!/usr/bin/env bash
set -euo pipefail

#
# StableBridge Platform — Local Infrastructure & Services Manager
#
# Usage:
#   ./run.sh up                Start infrastructure + build & launch all services
#   ./run.sh up --skip-build   Start infra + launch services (use existing JARs)
#   ./run.sh up --infra-only   Start only infrastructure containers
#   ./run.sh down              Stop everything and destroy containers
#   ./run.sh status            Show container and service status
#   ./run.sh restart           Destroy and recreate everything
#   ./run.sh logs              Tail logs from all containers
#   ./run.sh urls              Print all service URLs
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ─────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────
info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }

check_prerequisites() {
  local missing=false

  if ! command -v terraform &>/dev/null; then
    err "terraform not found. Install: brew install terraform"
    missing=true
  fi

  if ! command -v docker &>/dev/null; then
    err "docker not found. Install Docker Desktop."
    missing=true
  elif ! docker info &>/dev/null 2>&1; then
    err "Docker daemon is not running. Start Docker Desktop."
    missing=true
  fi

  if [[ "$missing" == "true" ]]; then
    exit 1
  fi
}

ensure_tfvars() {
  local tfvars="$SCRIPT_DIR/terraform.tfvars"
  if grep -q "REPLACE" "$tfvars" 2>/dev/null; then
    info "Setting project_root in terraform.tfvars..."
    if [[ "$(uname)" == "Darwin" ]]; then
      sed -i '' "s|/REPLACE/WITH/YOUR/PATH/TO/stablebridge-platform|${PROJECT_ROOT}|g" "$tfvars"
    else
      sed -i "s|/REPLACE/WITH/YOUR/PATH/TO/stablebridge-platform|${PROJECT_ROOT}|g" "$tfvars"
    fi
    ok "project_root set to: $PROJECT_ROOT"
  fi
}

wait_for_port() {
  local port=$1
  local name=$2
  local max_wait=${3:-60}
  local elapsed=0

  echo -n "  Waiting for $name (port $port) "
  while ! nc -z localhost "$port" 2>/dev/null; do
    if [[ $elapsed -ge $max_wait ]]; then
      echo -e " ${RED}TIMEOUT${NC}"
      return 1
    fi
    echo -n "."
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo -e " ${GREEN}UP${NC}"
}

wait_for_healthy() {
  local container=$1
  local max_wait=${2:-90}
  local elapsed=0

  echo -n "  Waiting for $container to be healthy "
  while true; do
    local health
    health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")

    if [[ "$health" == "healthy" ]]; then
      echo -e " ${GREEN}HEALTHY${NC}"
      return 0
    fi

    if [[ $elapsed -ge $max_wait ]]; then
      echo -e " ${YELLOW}TIMEOUT (status: $health)${NC}"
      return 1
    fi

    echo -n "."
    sleep 3
    elapsed=$((elapsed + 3))
  done
}

build_services() {
  info "Building service JARs..."
  cd "$PROJECT_ROOT"
  ./gradlew :merchant-onboarding:merchant-onboarding:bootJar \
            :merchant-iam:merchant-iam:bootJar \
            :api-gateway-iam:api-gateway-iam:bootJar \
            --parallel -q
  ok "Build complete."
  echo ""
}

check_jars_exist() {
  local missing=false
  for module in merchant-onboarding merchant-iam api-gateway-iam; do
    local jar
    jar=$(find "$PROJECT_ROOT/$module/$module/build/libs" -name "*.jar" ! -name "*-plain.jar" ! -name "*-test-fixtures.jar" 2>/dev/null | head -1)
    if [[ -z "$jar" ]]; then
      err "JAR not found for $module. Run without --skip-build."
      missing=true
    fi
  done
  if [[ "$missing" == "true" ]]; then
    exit 1
  fi
}

# ─────────────────────────────────────────────
# Commands
# ─────────────────────────────────────────────
cmd_up() {
  local skip_build=false
  local infra_only=false
  for arg in "$@"; do
    case $arg in
      --skip-build)  skip_build=true ;;
      --infra-only)  infra_only=true ;;
    esac
  done

  info "Starting local environment..."
  echo ""

  check_prerequisites
  ensure_tfvars

  cd "$SCRIPT_DIR"

  # Init (only if .terraform doesn't exist)
  if [[ ! -d ".terraform" ]]; then
    info "Running terraform init..."
    terraform init -input=false
    echo ""
  fi

  if [[ "$infra_only" == "false" ]]; then
    # Build JARs before terraform apply (so containers can mount them)
    if [[ "$skip_build" == "false" ]]; then
      build_services
    fi
    check_jars_exist
  fi

  # Return to Terraform dir after build
  cd "$SCRIPT_DIR"

  # Plan and apply
  local tf_vars=()
  if [[ "$infra_only" == "false" ]]; then
    tf_vars+=("-var" "start_services=true")
  fi

  info "Running terraform apply..."
  terraform apply -auto-approve -input=false "${tf_vars[@]}"
  echo ""

  # Wait for core infrastructure to be healthy
  info "Waiting for infrastructure to become healthy..."
  echo ""

  wait_for_healthy "sp-postgres" 60
  wait_for_healthy "sp-redis" 30
  wait_for_healthy "sp-redpanda" 90
  wait_for_port 9090 "Redpanda Console" 30
  wait_for_healthy "sp-temporal" 120
  wait_for_port 8233 "Temporal UI" 30
  wait_for_healthy "sp-vault" 30
  wait_for_port 1025 "Mailpit SMTP" 15
  wait_for_port 8025 "Mailpit UI" 15
  wait_for_port 4444 "WireMock" 15

  echo ""
  ok "All infrastructure is up and healthy!"
  echo ""

  # Wait for application services
  if [[ "$infra_only" == "false" ]]; then
    info "Waiting for application services to become healthy..."
    echo ""

    wait_for_healthy "sp-s11-merchant-onboarding" 120
    wait_for_healthy "sp-s13-merchant-iam" 120
    wait_for_healthy "sp-s10-api-gateway-iam" 120

    echo ""
    ok "All services are up and healthy!"
    echo ""
  fi

  cmd_urls
}

cmd_down() {
  info "Destroying everything..."
  echo ""

  check_prerequisites
  cd "$SCRIPT_DIR"

  if [[ ! -d ".terraform" ]]; then
    warn "Terraform not initialized. Nothing to destroy."
    exit 0
  fi

  terraform destroy -auto-approve -input=false
  echo ""
  ok "All infrastructure and services destroyed."
}

cmd_status() {
  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║           StableBridge Platform — Infrastructure             ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""

  local containers=(
    "sp-postgres:PostgreSQL:5432"
    "sp-timescaledb:TimescaleDB:5433"
    "sp-redis:Redis:6379"
    "sp-redpanda:Redpanda (Kafka):9092"
    "sp-redpanda-console:Redpanda Console:9090"
    "sp-elasticsearch:Elasticsearch:9200"
    "sp-temporal:Temporal:7233"
    "sp-temporal-ui:Temporal UI:8233"
    "sp-vault:Vault:8200"
    "sp-mailpit:Mailpit:8025"
    "sp-wiremock:WireMock:4444"
    "sp-prometheus:Prometheus:9091"
    "sp-loki:Loki:3100"
    "sp-promtail:Promtail:-"
    "sp-tempo:Tempo:3200"
    "sp-grafana:Grafana:3000"
  )

  printf "  %-22s %-14s %-10s %s\n" "SERVICE" "STATUS" "HEALTH" "PORT"
  printf "  %-22s %-14s %-10s %s\n" "───────────────────" "──────────" "────────" "────"

  for entry in "${containers[@]}"; do
    IFS=':' read -r container name port <<< "$entry"

    local state health
    state=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "not found")
    health=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}' "$container" 2>/dev/null || echo "-")

    local state_color health_color
    case "$state" in
      running) state_color="${GREEN}" ;;
      *)       state_color="${RED}" ;;
    esac
    case "$health" in
      healthy) health_color="${GREEN}" ;;
      n/a)     health_color="${CYAN}" ;;
      *)       health_color="${YELLOW}" ;;
    esac

    printf "  %-22s ${state_color}%-14s${NC} ${health_color}%-10s${NC} %s\n" "$name" "$state" "$health" ":$port"
  done

  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║           StableBridge Platform — Application Services       ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""

  local services=(
    "sp-s11-merchant-onboarding:S11 Merchant Onboarding:8081"
    "sp-s13-merchant-iam:S13 Merchant IAM:8083"
    "sp-s10-api-gateway-iam:S10 API Gateway IAM:8080"
  )

  printf "  %-28s %-14s %-10s %s\n" "SERVICE" "STATUS" "HEALTH" "PORT"
  printf "  %-28s %-14s %-10s %s\n" "────────────────────────" "──────────" "────────" "────"

  for entry in "${services[@]}"; do
    IFS=':' read -r container name port <<< "$entry"

    local state health
    state=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "not found")
    health=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}' "$container" 2>/dev/null || echo "-")

    local state_color health_color
    case "$state" in
      running) state_color="${GREEN}" ;;
      *)       state_color="${RED}" ;;
    esac
    case "$health" in
      healthy) health_color="${GREEN}" ;;
      n/a)     health_color="${CYAN}" ;;
      *)       health_color="${YELLOW}" ;;
    esac

    printf "  %-28s ${state_color}%-14s${NC} ${health_color}%-10s${NC} %s\n" "$name" "$state" "$health" ":$port"
  done
  echo ""
}

cmd_restart() {
  cmd_down
  echo ""
  cmd_up "$@"
}

cmd_logs() {
  local containers=(
    sp-postgres sp-redis sp-redpanda sp-temporal
    sp-vault sp-mailpit sp-wiremock sp-elasticsearch
    sp-s11-merchant-onboarding sp-s13-merchant-iam sp-s10-api-gateway-iam
  )

  info "Tailing logs from all containers (Ctrl+C to stop)..."
  echo ""

  local pids=()
  for c in "${containers[@]}"; do
    if docker inspect "$c" &>/dev/null; then
      docker logs -f --tail 20 "$c" 2>&1 | sed "s/^/[$c] /" &
      pids+=($!)
    fi
  done

  # Wait for Ctrl+C
  trap 'kill "${pids[@]}" 2>/dev/null; exit 0' INT
  wait
}

cmd_urls() {
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║              Service URLs & Dashboards                     ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""
  echo "  ── Infrastructure ──"
  echo ""
  echo "  PostgreSQL        jdbc:postgresql://localhost:5432/postgres"
  echo "                    user: dev / password: dev"
  echo ""
  echo "  Redis             redis://localhost:6379"
  echo ""
  echo "  Kafka (Redpanda)  localhost:9092"
  echo "  Redpanda Console  http://localhost:9090"
  echo ""
  echo "  Temporal gRPC     localhost:7233"
  echo "  Temporal UI       http://localhost:8233"
  echo ""
  echo "  Elasticsearch     http://localhost:9200"
  echo ""
  echo "  Vault             http://localhost:8200"
  echo "                    token: dev-root-token"
  echo ""
  echo "  Mailpit SMTP      localhost:1025"
  echo "  Mailpit UI        http://localhost:8025"
  echo ""
  echo "  WireMock          http://localhost:4444"
  echo ""
  echo "  ── Observability ──"
  echo ""
  echo "  Grafana           http://localhost:3000  (admin/admin)"
  echo "  Prometheus        http://localhost:9091"
  echo "  Loki              http://localhost:3100"
  echo "  Tempo             http://localhost:3200"
  echo "  OTLP (traces)     http://localhost:4318"
  echo ""
  echo "  ── Application Services ──"
  echo ""
  echo "  S11 Merchant Onboarding   http://localhost:8081"
  echo "  S13 Merchant IAM          http://localhost:8083"
  echo "  S10 API Gateway IAM       http://localhost:8080"
  echo ""
}

# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────
usage() {
  echo "Usage: $0 {up|down|status|restart|logs|urls} [options]"
  echo ""
  echo "Commands:"
  echo "  up       Start infrastructure + build & launch all services"
  echo "  down     Stop everything and destroy all containers"
  echo "  status   Show container and service status"
  echo "  restart  Destroy and recreate everything"
  echo "  logs     Tail logs from all containers"
  echo "  urls     Print service URLs and connection strings"
  echo ""
  echo "Options for 'up':"
  echo "  --skip-build    Skip Gradle build (use existing JARs)"
  echo "  --infra-only    Start only infrastructure, no application services"
}

case "${1:-}" in
  up)       shift; cmd_up "$@" ;;
  down)     cmd_down ;;
  status)   cmd_status ;;
  restart)  shift; cmd_restart "$@" ;;
  logs)     cmd_logs ;;
  urls)     cmd_urls ;;
  *)        usage; exit 1 ;;
esac
