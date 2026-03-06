# ─────────────────────────────────────────────
# Project root — must point to the stablebridge-platform repo root
# ─────────────────────────────────────────────
variable "project_root" {
  description = "Absolute path to the stablebridge-platform repository root"
  type        = string
}

# ─────────────────────────────────────────────
# Container images
# ─────────────────────────────────────────────
variable "postgres_image" {
  description = "PostgreSQL image"
  type        = string
  default     = "postgres:18-alpine"
}

variable "timescaledb_image" {
  description = "TimescaleDB image"
  type        = string
  default     = "timescale/timescaledb:latest-pg17"
}

variable "redis_image" {
  description = "Redis image"
  type        = string
  default     = "redis:8-alpine"
}

variable "redpanda_image" {
  description = "Redpanda image"
  type        = string
  default     = "docker.redpanda.com/redpandadata/redpanda:latest"
}

variable "redpanda_console_image" {
  description = "Redpanda Console image"
  type        = string
  default     = "docker.redpanda.com/redpandadata/console:latest"
}

variable "elasticsearch_image" {
  description = "Elasticsearch image"
  type        = string
  default     = "docker.elastic.co/elasticsearch/elasticsearch:9.3.1"
}

variable "temporal_image" {
  description = "Temporal auto-setup image"
  type        = string
  default     = "temporalio/auto-setup:latest"
}

variable "temporal_ui_image" {
  description = "Temporal UI image"
  type        = string
  default     = "temporalio/ui:latest"
}

variable "vault_image" {
  description = "HashiCorp Vault image"
  type        = string
  default     = "hashicorp/vault:latest"
}

variable "mailpit_image" {
  description = "Mailpit image"
  type        = string
  default     = "axllent/mailpit:latest"
}

variable "wiremock_image" {
  description = "WireMock image"
  type        = string
  default     = "wiremock/wiremock:latest"
}

# ─────────────────────────────────────────────
# Ports
# ─────────────────────────────────────────────
variable "postgres_port" {
  description = "PostgreSQL external port"
  type        = number
  default     = 5432
}

variable "timescaledb_port" {
  description = "TimescaleDB external port"
  type        = number
  default     = 5433
}

variable "redis_port" {
  description = "Redis external port"
  type        = number
  default     = 6379
}

variable "redpanda_kafka_port" {
  description = "Redpanda Kafka API external port"
  type        = number
  default     = 9092
}

variable "redpanda_admin_port" {
  description = "Redpanda Admin API external port"
  type        = number
  default     = 9644
}

variable "redpanda_console_port" {
  description = "Redpanda Console external port"
  type        = number
  default     = 9090
}

variable "elasticsearch_port" {
  description = "Elasticsearch external port"
  type        = number
  default     = 9200
}

variable "temporal_port" {
  description = "Temporal gRPC external port"
  type        = number
  default     = 7233
}

variable "temporal_ui_port" {
  description = "Temporal UI external port"
  type        = number
  default     = 8233
}

variable "vault_port" {
  description = "Vault external port"
  type        = number
  default     = 8200
}

variable "mailpit_smtp_port" {
  description = "Mailpit SMTP external port"
  type        = number
  default     = 1025
}

variable "mailpit_ui_port" {
  description = "Mailpit Web UI external port"
  type        = number
  default     = 8025
}

variable "wiremock_port" {
  description = "WireMock external port"
  type        = number
  default     = 4444
}

# ─────────────────────────────────────────────
# Credentials
# ─────────────────────────────────────────────
variable "postgres_user" {
  description = "PostgreSQL superuser name"
  type        = string
  default     = "dev"
}

variable "postgres_password" {
  description = "PostgreSQL superuser password"
  type        = string
  default     = "dev"
  sensitive   = true
}

variable "postgres_db" {
  description = "PostgreSQL default database"
  type        = string
  default     = "postgres"
}

variable "timescaledb_user" {
  description = "TimescaleDB user"
  type        = string
  default     = "dev"
}

variable "timescaledb_password" {
  description = "TimescaleDB password"
  type        = string
  default     = "dev"
  sensitive   = true
}

variable "timescaledb_db" {
  description = "TimescaleDB default database"
  type        = string
  default     = "fx_rates"
}

variable "vault_dev_root_token" {
  description = "Vault dev mode root token"
  type        = string
  default     = "dev-root-token"
  sensitive   = true
}

# ─────────────────────────────────────────────
# Application services
# ─────────────────────────────────────────────
variable "jre_image" {
  description = "JRE base image for application services"
  type        = string
  default     = "eclipse-temurin:25-jre-alpine"
}

variable "s11_port" {
  description = "S11 Merchant Onboarding external port"
  type        = number
  default     = 8081
}

variable "s13_port" {
  description = "S13 Merchant IAM external port"
  type        = number
  default     = 8083
}

variable "s10_port" {
  description = "S10 API Gateway IAM external port"
  type        = number
  default     = 8080
}

variable "start_services" {
  description = "Whether to start application service containers"
  type        = bool
  default     = false
}
