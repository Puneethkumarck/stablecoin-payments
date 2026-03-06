# ─────────────────────────────────────────────
# Cluster outputs
# ─────────────────────────────────────────────

output "kubeconfig_path" {
  description = "Path to the kubeconfig file for the kind cluster"
  value       = kind_cluster.local.kubeconfig_path
}

output "cluster_name" {
  description = "Name of the kind cluster"
  value       = kind_cluster.local.name
}

output "app_namespace" {
  description = "Kubernetes namespace for application services"
  value       = kubernetes_namespace.app.metadata[0].name
}

# ─────────────────────────────────────────────
# Access URLs (path-based routing)
# ─────────────────────────────────────────────

output "gateway_url" {
  description = "S10 API Gateway URL"
  value       = "http://localhost/gateway"
}

output "onboarding_url" {
  description = "S11 Merchant Onboarding URL"
  value       = "http://localhost/onboarding"
}

output "iam_url" {
  description = "S13 Merchant IAM URL"
  value       = "http://localhost/iam"
}

output "portal_url" {
  description = "Merchant Portal URL"
  value       = "http://localhost"
}

output "usage" {
  description = "Quick start commands"
  value       = <<-EOT

    # Set kubeconfig
    export KUBECONFIG="${kind_cluster.local.kubeconfig_path}"

    # Check cluster
    kubectl get nodes
    kubectl get pods -n ${var.app_namespace}

    # Access services (path-based routing):
    curl http://localhost/gateway/actuator/health
    curl http://localhost/onboarding/api/v1/merchants
    curl http://localhost/iam/v1/auth/health
    curl http://localhost  # merchant portal

    # OpenAPI docs:
    # http://localhost/gateway/swagger-ui.html
    # http://localhost/onboarding/swagger-ui.html
    # http://localhost/iam/swagger-ui.html
  EOT
}
