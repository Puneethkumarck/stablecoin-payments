# ─────────────────────────────────────────────────────────────────────────────
# StableBridge Platform — Local K8s (kind + NGINX Ingress)
# ─────────────────────────────────────────────────────────────────────────────

# ─────────────────────────────────────────────
# kind Cluster — single-node with ingress port mappings
# ─────────────────────────────────────────────
resource "kind_cluster" "local" {
  name           = var.cluster_name
  node_image     = "kindest/node:${var.kubernetes_version}"
  wait_for_ready = true

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"

      # Map host ports to ingress controller
      extra_port_mappings {
        container_port = 80
        host_port      = var.ingress_http_port
        protocol       = "TCP"
      }

      extra_port_mappings {
        container_port = 443
        host_port      = var.ingress_https_port
        protocol       = "TCP"
      }

      # Label for ingress controller nodeSelector
      kubeadm_config_patches = [
        <<-EOT
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
        EOT
      ]
    }
  }
}

# ─────────────────────────────────────────────
# Application Namespace
# ─────────────────────────────────────────────
resource "kubernetes_namespace" "app" {
  metadata {
    name = var.app_namespace
  }

  depends_on = [kind_cluster.local]
}

# ─────────────────────────────────────────────
# NGINX Ingress Controller — via Helm
# ─────────────────────────────────────────────
resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  wait             = true
  timeout          = 300

  # kind-specific: use hostPort instead of LoadBalancer
  set {
    name  = "controller.hostPort.enabled"
    value = "true"
    type  = "string"
  }

  set {
    name  = "controller.service.type"
    value = "NodePort"
  }

  set {
    name  = "controller.nodeSelector.ingress-ready"
    value = "true"
    type  = "string"
  }

  set {
    name  = "controller.tolerations[0].key"
    value = "node-role.kubernetes.io/control-plane"
  }

  set {
    name  = "controller.tolerations[0].operator"
    value = "Exists"
  }

  set {
    name  = "controller.tolerations[0].effect"
    value = "NoSchedule"
  }

  # Enable snippet annotations for CORS
  set {
    name  = "controller.allowSnippetAnnotations"
    value = "true"
    type  = "string"
  }

  depends_on = [kind_cluster.local]
}

# ─────────────────────────────────────────────
# Deploy application services via Kustomize
# ─────────────────────────────────────────────
resource "null_resource" "kustomize_apply" {
  triggers = {
    # Re-apply when kustomize files change
    kustomize_hash = sha256(join("", [
      for f in fileset("${var.project_root}/${var.kustomize_overlay}", "**") :
      filesha256("${var.project_root}/${var.kustomize_overlay}/${f}")
    ]))
    base_hash = sha256(join("", [
      for f in fileset("${var.project_root}/infra/k8s/base", "**") :
      filesha256("${var.project_root}/infra/k8s/base/${f}")
    ]))
  }

  provisioner "local-exec" {
    command = <<-EOT
      export KUBECONFIG="${kind_cluster.local.kubeconfig_path}"
      kubectl apply -k "${var.project_root}/${var.kustomize_overlay}"
    EOT
  }

  depends_on = [
    kubernetes_namespace.app,
    helm_release.ingress_nginx,
  ]
}
