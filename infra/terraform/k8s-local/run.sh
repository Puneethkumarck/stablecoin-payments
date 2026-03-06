#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# StableBridge Platform — Local K8s Setup (Docker Desktop Kubernetes)
#
# Prerequisites: Docker Desktop with Kubernetes enabled
#
# Usage:
#   ./run.sh up      # Build images, install ingress, deploy services
#   ./run.sh down    # Remove all stablebridge resources (keeps cluster)
#   ./run.sh deploy  # Re-build images and re-deploy manifests
#   ./run.sh build   # Build all container images (BE via Jib, FE via Docker)
#   ./run.sh status  # Show cluster and pod status
#   ./run.sh logs    # Tail logs from all stablebridge pods
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
FE_ROOT="$(cd "$PROJECT_ROOT/../stablebridge-web" 2>/dev/null && pwd || echo "")"

IMAGES=(
  "stablebridge/api-gateway-iam:latest"
  "stablebridge/merchant-onboarding:latest"
  "stablebridge/merchant-iam:latest"
  "stablebridge/merchant-portal:latest"
  "stablebridge/admin-portal:latest"
)

INGRESS_NGINX_VERSION="v1.12.2"
INGRESS_NGINX_MANIFEST="https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-${INGRESS_NGINX_VERSION}/deploy/static/provider/cloud/deploy.yaml"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ─────────────────────────────────────────────
# Prerequisites check
# ─────────────────────────────────────────────
check_prerequisites() {
  local missing=false

  if ! command -v kubectl &>/dev/null; then
    err "kubectl not found. Enable Kubernetes in Docker Desktop."
    missing=true
  fi

  if ! command -v docker &>/dev/null; then
    err "docker not found. Install Docker Desktop."
    missing=true
  elif ! docker info &>/dev/null 2>&1; then
    err "Docker daemon is not running. Start Docker Desktop."
    missing=true
  fi

  if ! kubectl cluster-info &>/dev/null 2>&1; then
    err "Kubernetes cluster not reachable. Enable Kubernetes in Docker Desktop Settings."
    missing=true
  fi

  # Verify we're on Docker Desktop context
  local ctx
  ctx=$(kubectl config current-context 2>/dev/null || echo "none")
  if [[ "$ctx" != "docker-desktop" ]]; then
    warn "Current context is '$ctx' — expected 'docker-desktop'"
    warn "Run: kubectl config use-context docker-desktop"
  fi

  if [[ "$missing" == "true" ]]; then
    exit 1
  fi
}

# ─────────────────────────────────────────────
# Build BE container images via Jib
# ─────────────────────────────────────────────
build_be_images() {
  info "Building BE container images via Jib..."
  cd "$PROJECT_ROOT"
  ./gradlew :api-gateway-iam:api-gateway-iam:jibDockerBuild \
            :merchant-onboarding:merchant-onboarding:jibDockerBuild \
            :merchant-iam:merchant-iam:jibDockerBuild \
            --parallel --no-daemon
  ok "BE images built successfully"
}

# ─────────────────────────────────────────────
# Build FE container images via Docker
# ─────────────────────────────────────────────
build_fe_images() {
  if [ -z "$FE_ROOT" ] || [ ! -f "$FE_ROOT/Dockerfile" ]; then
    warn "stablebridge-web repo or Dockerfile not found at $PROJECT_ROOT/../stablebridge-web — skipping FE build"
    return 0
  fi
  info "Building FE container images via Docker..."
  docker build --build-arg APP_NAME=merchant-portal \
    -t stablebridge/merchant-portal:latest \
    "$FE_ROOT"
  docker build --build-arg APP_NAME=admin-portal \
    -t stablebridge/admin-portal:latest \
    "$FE_ROOT"
  ok "FE images built successfully"
}

# ─────────────────────────────────────────────
# Build all images
# ─────────────────────────────────────────────
build_images() {
  build_be_images
  build_fe_images
}

# ─────────────────────────────────────────────
# Install NGINX Ingress Controller
# ─────────────────────────────────────────────
install_ingress() {
  if kubectl get namespace ingress-nginx &>/dev/null 2>&1; then
    info "NGINX Ingress already installed — skipping"
    return 0
  fi

  info "Installing NGINX Ingress Controller..."
  kubectl apply -f "$INGRESS_NGINX_MANIFEST"

  info "Waiting for ingress controller to be ready..."
  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=180s
  ok "NGINX Ingress Controller ready"
}

# ─────────────────────────────────────────────
# Create namespace + deploy services
# ─────────────────────────────────────────────
deploy_services() {
  info "Ensuring namespace 'stablebridge' exists..."
  kubectl create namespace stablebridge --dry-run=client -o yaml | kubectl apply -f -

  info "Applying kustomize manifests..."
  kubectl apply -k "$PROJECT_ROOT/infra/k8s/overlays/local"

  # Stagger BE service startups to avoid CPU contention during class loading
  info "Starting BE services sequentially to avoid CPU contention..."

  for deploy in s11-merchant-onboarding s13-merchant-iam s10-api-gateway; do
    info "  Starting $deploy..."
    kubectl rollout status deployment "$deploy" -n stablebridge --timeout=300s || {
      warn "$deploy did not become ready in 5m — continuing"
    }
  done

  show_status
}

# ─────────────────────────────────────────────
# Up: build + install ingress + deploy
# ─────────────────────────────────────────────
cmd_up() {
  check_prerequisites
  build_images
  install_ingress
  deploy_services
}

# ─────────────────────────────────────────────
# Down: remove stablebridge resources (keeps cluster)
# ─────────────────────────────────────────────
cmd_down() {
  check_prerequisites
  info "Removing stablebridge resources..."
  kubectl delete namespace stablebridge --ignore-not-found
  ok "Stablebridge resources removed (cluster still running)"
}

# ─────────────────────────────────────────────
# Deploy: rebuild and redeploy
# ─────────────────────────────────────────────
cmd_deploy() {
  check_prerequisites
  build_images
  deploy_services
}

# ─────────────────────────────────────────────
# Logs: tail logs from all stablebridge pods
# ─────────────────────────────────────────────
cmd_logs() {
  local service="${2:-}"
  if [[ -n "$service" ]]; then
    kubectl logs -n stablebridge -l "app=$service" -f --tail=100
  else
    kubectl logs -n stablebridge --all-containers -f --tail=50 --max-log-requests=10
  fi
}

# ─────────────────────────────────────────────
# Show status
# ─────────────────────────────────────────────
show_status() {
  echo ""
  echo "=== Cluster Nodes ==="
  kubectl get nodes
  echo ""
  echo "=== Pods (stablebridge) ==="
  kubectl get pods -n stablebridge -o wide 2>/dev/null || warn "Namespace 'stablebridge' not found"
  echo ""
  echo "=== Services (stablebridge) ==="
  kubectl get svc -n stablebridge 2>/dev/null || true
  echo ""
  echo "=== Ingress ==="
  kubectl get ingress -n stablebridge 2>/dev/null || true
  echo ""
  echo "=== Access URLs (via NGINX Ingress) ==="
  echo "  Merchant Portal:       http://localhost"
  echo "  Admin Portal:          http://localhost/admin"
  echo "  API Gateway (S10):     http://localhost/gateway"
  echo "  Onboarding (S11):      http://localhost/onboarding"
  echo "  IAM (S13):             http://localhost/iam"
  echo ""
  echo "  OpenAPI docs:"
  echo "    http://localhost/gateway/swagger-ui.html"
  echo "    http://localhost/onboarding/swagger-ui.html"
  echo "    http://localhost/iam/swagger-ui.html"
}

# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────
case "${1:-help}" in
  up)
    cmd_up
    ;;
  down)
    cmd_down
    ;;
  deploy)
    cmd_deploy
    ;;
  build)
    build_images
    ;;
  status)
    check_prerequisites
    show_status
    ;;
  logs)
    cmd_logs "$@"
    ;;
  *)
    echo "Usage: $0 {up|down|deploy|build|status|logs [service]}"
    exit 1
    ;;
esac
