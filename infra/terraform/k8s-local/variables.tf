# ─────────────────────────────────────────────
# Cluster
# ─────────────────────────────────────────────
variable "cluster_name" {
  description = "Name of the kind cluster"
  type        = string
  default     = "stablebridge-local"
}

variable "kubernetes_version" {
  description = "Kubernetes version (kind node image tag)"
  type        = string
  default     = "v1.32.3"
}

# ─────────────────────────────────────────────
# Ingress
# ─────────────────────────────────────────────
variable "ingress_http_port" {
  description = "Host port mapped to ingress controller HTTP"
  type        = number
  default     = 80
}

variable "ingress_https_port" {
  description = "Host port mapped to ingress controller HTTPS"
  type        = number
  default     = 443
}

# ─────────────────────────────────────────────
# Namespace
# ─────────────────────────────────────────────
variable "app_namespace" {
  description = "Kubernetes namespace for application services"
  type        = string
  default     = "stablebridge"
}

# ─────────────────────────────────────────────
# Project paths
# ─────────────────────────────────────────────
variable "project_root" {
  description = "Absolute path to the stablebridge-platform repository root"
  type        = string
}

variable "kustomize_overlay" {
  description = "Path to kustomize overlay relative to project_root"
  type        = string
  default     = "infra/k8s/overlays/local"
}
