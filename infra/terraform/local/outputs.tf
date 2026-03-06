# ─────────────────────────────────────────────
# Connection strings for all services
# ─────────────────────────────────────────────

output "postgres_url" {
  description = "PostgreSQL JDBC connection URL"
  value       = "jdbc:postgresql://localhost:${var.postgres_port}/${var.postgres_db}"
}

output "postgres_user" {
  description = "PostgreSQL username"
  value       = var.postgres_user
}

output "timescaledb_url" {
  description = "TimescaleDB JDBC connection URL"
  value       = "jdbc:postgresql://localhost:${var.timescaledb_port}/${var.timescaledb_db}"
}

output "timescaledb_user" {
  description = "TimescaleDB username"
  value       = var.timescaledb_user
}

output "redis_url" {
  description = "Redis connection URL"
  value       = "redis://localhost:${var.redis_port}"
}

output "kafka_bootstrap" {
  description = "Kafka (Redpanda) bootstrap servers"
  value       = "localhost:${var.redpanda_kafka_port}"
}

output "redpanda_admin_url" {
  description = "Redpanda Admin API URL"
  value       = "http://localhost:${var.redpanda_admin_port}"
}

output "redpanda_console_url" {
  description = "Redpanda Console URL"
  value       = "http://localhost:${var.redpanda_console_port}"
}

output "elasticsearch_url" {
  description = "Elasticsearch URL"
  value       = "http://localhost:${var.elasticsearch_port}"
}

output "temporal_address" {
  description = "Temporal gRPC address"
  value       = "localhost:${var.temporal_port}"
}

output "temporal_ui_url" {
  description = "Temporal UI URL"
  value       = "http://localhost:${var.temporal_ui_port}"
}

output "vault_url" {
  description = "Vault URL"
  value       = "http://localhost:${var.vault_port}"
}

output "vault_token" {
  description = "Vault dev root token"
  value       = var.vault_dev_root_token
  sensitive   = true
}

output "mailpit_smtp_url" {
  description = "Mailpit SMTP address"
  value       = "smtp://localhost:${var.mailpit_smtp_port}"
}

output "mailpit_ui_url" {
  description = "Mailpit Web UI URL"
  value       = "http://localhost:${var.mailpit_ui_port}"
}

output "wiremock_url" {
  description = "WireMock URL"
  value       = "http://localhost:${var.wiremock_port}"
}

# ─────────────────────────────────────────────
# Application services (only when start_services = true)
# ─────────────────────────────────────────────
output "s11_url" {
  description = "S11 Merchant Onboarding URL"
  value       = var.start_services ? "http://localhost:${var.s11_port}" : "not started"
}

output "s13_url" {
  description = "S13 Merchant IAM URL"
  value       = var.start_services ? "http://localhost:${var.s13_port}" : "not started"
}

output "s10_url" {
  description = "S10 API Gateway IAM URL"
  value       = var.start_services ? "http://localhost:${var.s10_port}" : "not started"
}

# ─────────────────────────────────────────────
# Observability
# ─────────────────────────────────────────────
output "grafana_url" {
  description = "Grafana URL"
  value       = "http://localhost:3000"
}

output "prometheus_url" {
  description = "Prometheus URL"
  value       = "http://localhost:9091"
}

output "loki_url" {
  description = "Loki URL"
  value       = "http://localhost:3100"
}

output "tempo_url" {
  description = "Tempo URL"
  value       = "http://localhost:3200"
}
