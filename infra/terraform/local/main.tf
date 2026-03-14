# ─────────────────────────────────────────────────────────────────────────────
# StableBridge Platform — Local Infrastructure (mirrors docker-compose.dev.yml)
# ─────────────────────────────────────────────────────────────────────────────

# ─────────────────────────────────────────────
# Locals — Docker Desktop project grouping
# ─────────────────────────────────────────────
locals {
  project_name = "stablebridge-platform"
}

# ─────────────────────────────────────────────
# Docker Network
# ─────────────────────────────────────────────
resource "docker_network" "sp_network" {
  name = "sp-network"
}

# ─────────────────────────────────────────────
# Docker Volumes
# ─────────────────────────────────────────────
resource "docker_volume" "pgdata" {
  name = "sp-pgdata"
}

resource "docker_volume" "tsdata" {
  name = "sp-tsdata"
}

# ─────────────────────────────────────────────
# PostgreSQL — per-service databases
# ─────────────────────────────────────────────
resource "docker_image" "postgres" {
  name         = var.postgres_image
  keep_locally = true
}

resource "docker_container" "postgres" {
  name   = "sp-postgres"
  image  = docker_image.postgres.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 5432
    external = var.postgres_port
  }

  env = [
    "POSTGRES_USER=${var.postgres_user}",
    "POSTGRES_PASSWORD=${var.postgres_password}",
    "POSTGRES_DB=${var.postgres_db}",
  ]

  volumes {
    volume_name    = docker_volume.pgdata.name
    container_path = "/var/lib/postgresql/data"
  }

  volumes {
    host_path      = "${var.project_root}/infra/local/postgres/init.sql"
    container_path = "/docker-entrypoint-initdb.d/init.sql"
    read_only      = true
  }

  healthcheck {
    test         = ["CMD-SHELL", "pg_isready -U dev"]
    interval     = "5s"
    timeout      = "5s"
    retries      = 10
    start_period = "0s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# TimescaleDB — FX rate history (S6)
# ─────────────────────────────────────────────
resource "docker_image" "timescaledb" {
  name         = var.timescaledb_image
  keep_locally = true
}

resource "docker_container" "timescaledb" {
  name   = "sp-timescaledb"
  image  = docker_image.timescaledb.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 5432
    external = var.timescaledb_port
  }

  env = [
    "POSTGRES_USER=${var.timescaledb_user}",
    "POSTGRES_PASSWORD=${var.timescaledb_password}",
    "POSTGRES_DB=${var.timescaledb_db}",
  ]

  volumes {
    volume_name    = docker_volume.tsdata.name
    container_path = "/var/lib/postgresql/data"
  }

  healthcheck {
    test         = ["CMD-SHELL", "pg_isready -U dev"]
    interval     = "5s"
    timeout      = "5s"
    retries      = 10
    start_period = "0s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Redis — caching, sessions, rate limits
# ─────────────────────────────────────────────
resource "docker_image" "redis" {
  name         = var.redis_image
  keep_locally = true
}

resource "docker_container" "redis" {
  name   = "sp-redis"
  image  = docker_image.redis.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 6379
    external = var.redis_port
  }

  healthcheck {
    test         = ["CMD", "redis-cli", "ping"]
    interval     = "5s"
    timeout      = "5s"
    retries      = 10
    start_period = "0s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Redpanda — Kafka-compatible event bus
# ─────────────────────────────────────────────
resource "docker_image" "redpanda" {
  name         = var.redpanda_image
  keep_locally = true
}

resource "docker_container" "redpanda" {
  name   = "sp-redpanda"
  image  = docker_image.redpanda.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 9092
    external = var.redpanda_kafka_port
  }

  ports {
    internal = 9644
    external = var.redpanda_admin_port
  }

  command = [
    "redpanda",
    "start",
    "--smp=1",
    "--memory=512M",
    "--overprovisioned",
    "--kafka-addr=INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092",
    "--advertise-kafka-addr=INTERNAL://sp-redpanda:29092,EXTERNAL://localhost:9092",
  ]

  healthcheck {
    test         = ["CMD", "rpk", "cluster", "health", "--exit-when-healthy"]
    interval     = "10s"
    timeout      = "10s"
    retries      = 10
    start_period = "0s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Redpanda Console — Kafka UI
# ─────────────────────────────────────────────
resource "docker_image" "redpanda_console" {
  name         = var.redpanda_console_image
  keep_locally = true
}

resource "docker_container" "redpanda_console" {
  name   = "sp-redpanda-console"
  image  = docker_image.redpanda_console.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 8080
    external = var.redpanda_console_port
  }

  env = [
    "KAFKA_BROKERS=sp-redpanda:29092",
  ]

  depends_on = [
    docker_container.redpanda,
  ]

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Kafka Init — creates all required topics (run-once)
# ─────────────────────────────────────────────
resource "docker_container" "kafka_init" {
  name   = "sp-kafka-init"
  image  = docker_image.redpanda.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  entrypoint = ["/bin/bash", "-c"]

  command = [
    <<-EOT
    echo 'Waiting for Redpanda to be ready...' &&
    until rpk --brokers sp-redpanda:29092 cluster info 2>/dev/null; do sleep 2; done &&
    rpk --brokers sp-redpanda:29092 topic create payment.initiated --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create compliance.result --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create fx.rate.locked --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create fiat.collected --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create chain.transfer.submitted --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create chain.transfer.confirmed --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create fiat.payout.completed --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create payment.completed --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create payment.failed --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create audit.event --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create reconciliation.discrepancy --partitions 1 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create merchant.activated --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create merchant.suspended --partitions 3 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create partner.degraded --partitions 1 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create partner.recovered --partitions 1 --replicas 1 &&
    rpk --brokers sp-redpanda:29092 topic create agent.payment.approved --partitions 3 --replicas 1 &&
    echo 'All topics created successfully'
    EOT
  ]

  depends_on = [
    docker_container.redpanda,
  ]

  restart = "on-failure"
}

# ─────────────────────────────────────────────
# Elasticsearch — transaction search (S12)
# ─────────────────────────────────────────────
resource "docker_image" "elasticsearch" {
  name         = var.elasticsearch_image
  keep_locally = true
}

resource "docker_container" "elasticsearch" {
  name   = "sp-elasticsearch"
  image  = docker_image.elasticsearch.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 9200
    external = var.elasticsearch_port
  }

  env = [
    "discovery.type=single-node",
    "xpack.security.enabled=false",
    "ES_JAVA_OPTS=-Xms256m -Xmx256m",
  ]

  healthcheck {
    test         = ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -qv '\"status\":\"red\"'"]
    interval     = "15s"
    timeout      = "10s"
    retries      = 10
    start_period = "0s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Temporal — durable workflow engine (S1, S11, S14)
# ─────────────────────────────────────────────
resource "docker_image" "temporal" {
  name         = var.temporal_image
  keep_locally = true
}

resource "docker_container" "temporal" {
  name   = "sp-temporal"
  image  = docker_image.temporal.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 7233
    external = var.temporal_port
  }

  env = [
    "DB=postgres12",
    "DB_PORT=5432",
    "POSTGRES_USER=${var.postgres_user}",
    "POSTGRES_PWD=${var.postgres_password}",
    "POSTGRES_SEEDS=sp-postgres",
    "SKIP_DYNAMIC_CONFIG_UPDATE=true",
  ]

  depends_on = [
    docker_container.postgres,
  ]

  healthcheck {
    test         = ["CMD-SHELL", "temporal operator cluster health 2>/dev/null | grep -q 'OK' || exit 0"]
    interval     = "15s"
    timeout      = "10s"
    retries      = 15
    start_period = "0s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Temporal UI
# ─────────────────────────────────────────────
resource "docker_image" "temporal_ui" {
  name         = var.temporal_ui_image
  keep_locally = true
}

resource "docker_container" "temporal_ui" {
  name   = "sp-temporal-ui"
  image  = docker_image.temporal_ui.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 8080
    external = var.temporal_ui_port
  }

  env = [
    "TEMPORAL_ADDRESS=sp-temporal:7233",
    "TEMPORAL_CORS_ORIGINS=http://localhost:3000",
  ]

  depends_on = [
    docker_container.temporal,
  ]

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Vault — secrets management (dev mode)
# ─────────────────────────────────────────────
resource "docker_image" "vault" {
  name         = var.vault_image
  keep_locally = true
}

resource "docker_container" "vault" {
  name   = "sp-vault"
  image  = docker_image.vault.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 8200
    external = var.vault_port
  }

  env = [
    "VAULT_DEV_ROOT_TOKEN_ID=${var.vault_dev_root_token}",
    "VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200",
  ]

  capabilities {
    add = ["IPC_LOCK"]
  }

  healthcheck {
    test         = ["CMD-SHELL", "VAULT_ADDR=http://127.0.0.1:8200 vault status"]
    interval     = "5s"
    timeout      = "5s"
    retries      = 10
    start_period = "0s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Mailpit — captures outbound email (replaces SendGrid in dev)
# ─────────────────────────────────────────────
resource "docker_image" "mailpit" {
  name         = var.mailpit_image
  keep_locally = true
}

resource "docker_container" "mailpit" {
  name   = "sp-mailpit"
  image  = docker_image.mailpit.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 1025
    external = var.mailpit_smtp_port
  }

  ports {
    internal = 8025
    external = var.mailpit_ui_port
  }

  env = [
    "MP_SMTP_AUTH_ACCEPT_ANY=1",
    "MP_SMTP_AUTH_ALLOW_INSECURE=1",
  ]

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# WireMock — stubs for external providers
# (Stripe, Onfido, Alchemy, Modulr, Fireblocks, etc.)
# ─────────────────────────────────────────────
resource "docker_image" "wiremock" {
  name         = var.wiremock_image
  keep_locally = true
}

resource "docker_container" "wiremock" {
  name   = "sp-wiremock"
  image  = docker_image.wiremock.image_id
  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 8080
    external = var.wiremock_port
  }

  volumes {
    host_path      = "${var.project_root}/infra/local/wiremock/mappings"
    container_path = "/home/wiremock/mappings"
    read_only      = true
  }

  volumes {
    host_path      = "${var.project_root}/infra/local/wiremock/__files"
    container_path = "/home/wiremock/__files"
    read_only      = true
  }

  command = ["--verbose", "--global-response-templating"]

  restart = "unless-stopped"
}

# ═════════════════════════════════════════════════════════════════════════════
# Application Services (conditionally started via start_services variable)
# ═════════════════════════════════════════════════════════════════════════════

resource "docker_image" "jre" {
  count        = var.start_services ? 1 : 0
  name         = var.jre_image
  keep_locally = true
}

# ─────────────────────────────────────────────
# S11 — Merchant Onboarding (port 8081)
# ─────────────────────────────────────────────
resource "docker_container" "s11" {
  count  = var.start_services ? 1 : 0
  name   = "sp-s11-merchant-onboarding"
  image  = docker_image.jre[0].image_id

  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 8081
    external = var.s11_port
  }

  env = [
    "SPRING_PROFILES_ACTIVE=local,sandbox",
    "ONFIDO_BASE_URL=http://sp-wiremock:8080/v3.6",
    "ONFIDO_API_TOKEN=sandbox-token",
    "ONFIDO_WEBHOOK_SECRET=sandbox-webhook-secret",
    "COMPANIES_HOUSE_BASE_URL=http://sp-wiremock:8080",
    "COMPANIES_HOUSE_API_KEY=sandbox-key",
    "SPRING_DATASOURCE_URL=jdbc:postgresql://sp-postgres:5432/s11_merchant_onboarding",
    "SPRING_DATASOURCE_USERNAME=${var.postgres_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.postgres_password}",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS=sp-redpanda:29092",
    "SPRING_DATA_REDIS_HOST=sp-redis",
    "SPRING_DATA_REDIS_PORT=6379",
    "TEMPORAL_HOST=sp-temporal",
    "TEMPORAL_PORT=7233",
    "OTEL_EXPORTER_OTLP_ENDPOINT=http://sp-tempo:4318/v1/traces",
  ]

  volumes {
    host_path      = "${var.project_root}/merchant-onboarding/merchant-onboarding/build/libs"
    container_path = "/libs"
    read_only      = true
  }

  entrypoint = ["/bin/sh", "-c"]
  command    = ["JAR=$(find /libs -name '*.jar' ! -name '*-plain.jar' ! -name '*-test-fixtures.jar' | head -1) && exec java -jar $JAR"]

  depends_on = [
    docker_container.postgres,
    docker_container.redis,
    docker_container.redpanda,
    docker_container.temporal,
    docker_container.wiremock,
  ]

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8081/onboarding/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 20
    start_period = "30s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# S13 — Merchant IAM (port 8083)
# ─────────────────────────────────────────────
resource "docker_container" "s13" {
  count  = var.start_services ? 1 : 0
  name   = "sp-s13-merchant-iam"
  image  = docker_image.jre[0].image_id

  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 8083
    external = var.s13_port
  }

  env = [
    "SPRING_PROFILES_ACTIVE=local",
    "SPRING_DATASOURCE_URL=jdbc:postgresql://sp-postgres:5432/s13_merchant_iam",
    "SPRING_DATASOURCE_USERNAME=${var.postgres_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.postgres_password}",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS=sp-redpanda:29092",
    "SPRING_DATA_REDIS_HOST=sp-redis",
    "SPRING_DATA_REDIS_PORT=6379",
    "SPRING_MAIL_HOST=sp-mailpit",
    "SPRING_MAIL_PORT=1025",
    "OTEL_EXPORTER_OTLP_ENDPOINT=http://sp-tempo:4318/v1/traces",
  ]

  volumes {
    host_path      = "${var.project_root}/merchant-iam/merchant-iam/build/libs"
    container_path = "/libs"
    read_only      = true
  }

  entrypoint = ["/bin/sh", "-c"]
  command    = ["JAR=$(find /libs -name '*.jar' ! -name '*-plain.jar' ! -name '*-test-fixtures.jar' | head -1) && exec java -jar $JAR"]

  depends_on = [
    docker_container.postgres,
    docker_container.redis,
    docker_container.redpanda,
    docker_container.mailpit,
  ]

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8083/iam/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 20
    start_period = "30s"
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# S10 — API Gateway IAM (port 8080)
# ─────────────────────────────────────────────
resource "docker_container" "s10" {
  count  = var.start_services ? 1 : 0
  name   = "sp-s10-api-gateway-iam"
  image  = docker_image.jre[0].image_id

  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 8080
    external = var.s10_port
  }

  env = [
    "SPRING_PROFILES_ACTIVE=local",
    "SPRING_DATASOURCE_URL=jdbc:postgresql://sp-postgres:5432/s10_api_gateway_iam",
    "SPRING_DATASOURCE_USERNAME=${var.postgres_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.postgres_password}",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS=sp-redpanda:29092",
    "SPRING_DATA_REDIS_HOST=sp-redis",
    "SPRING_DATA_REDIS_PORT=6379",
    "MERCHANT_IAM_BASE_URL=http://sp-s13-merchant-iam:8083/iam",
    "OTEL_EXPORTER_OTLP_ENDPOINT=http://sp-tempo:4318/v1/traces",
  ]

  volumes {
    host_path      = "${var.project_root}/api-gateway-iam/api-gateway-iam/build/libs"
    container_path = "/libs"
    read_only      = true
  }

  entrypoint = ["/bin/sh", "-c"]
  command    = ["JAR=$(find /libs -name '*.jar' ! -name '*-plain.jar' ! -name '*-test-fixtures.jar' | head -1) && exec java -jar $JAR"]

  depends_on = [
    docker_container.postgres,
    docker_container.redis,
    docker_container.redpanda,
    docker_container.s13,
  ]

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8080/gateway/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 20
    start_period = "30s"
  }

  restart = "unless-stopped"
}

# ═════════════════════════════════════════════════════════════════════════════
# Observability Stack (Prometheus, Loki, Promtail, Tempo, Grafana)
# ═════════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────
# Prometheus — metrics collection
# ─────────────────────────────────────────────
resource "docker_image" "prometheus" {
  name         = "prom/prometheus:latest"
  keep_locally = true
}

resource "docker_container" "prometheus" {
  name   = "sp-prometheus"
  image  = docker_image.prometheus.image_id

  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 9090
    external = 9091
  }

  command = [
    "--config.file=/etc/prometheus/prometheus.yml",
    "--web.enable-remote-write-receiver"
  ]

  volumes {
    host_path      = "${var.project_root}/monitoring/prometheus.yml"
    container_path = "/etc/prometheus/prometheus.yml"
    read_only      = true
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Loki — log aggregation
# ─────────────────────────────────────────────
resource "docker_image" "loki" {
  name         = "grafana/loki:latest"
  keep_locally = true
}

resource "docker_container" "loki" {
  name   = "sp-loki"
  image  = docker_image.loki.image_id

  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 3100
    external = 3100
  }

  volumes {
    host_path      = "${var.project_root}/monitoring/loki.yml"
    container_path = "/etc/loki/local-config.yaml"
    read_only      = true
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Promtail — ships Docker logs to Loki
# ─────────────────────────────────────────────
resource "docker_image" "promtail" {
  name         = "grafana/promtail:latest"
  keep_locally = true
}

resource "docker_container" "promtail" {
  name   = "sp-promtail"
  image  = docker_image.promtail.image_id

  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  volumes {
    host_path      = "/var/run/docker.sock"
    container_path = "/var/run/docker.sock"
    read_only      = true
  }

  volumes {
    host_path      = "${var.project_root}/monitoring/promtail.yml"
    container_path = "/etc/promtail/config.yml"
    read_only      = true
  }

  depends_on = [
    docker_container.loki,
  ]

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Tempo — distributed tracing (OTLP)
# ─────────────────────────────────────────────
resource "docker_image" "tempo" {
  name         = "grafana/tempo:latest"
  keep_locally = true
}

resource "docker_container" "tempo" {
  name   = "sp-tempo"
  image  = docker_image.tempo.image_id

  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 3200
    external = 3200
  }

  ports {
    internal = 4318
    external = 4318
  }

  command = ["-config.file=/etc/tempo/config.yaml"]

  volumes {
    host_path      = "${var.project_root}/monitoring/tempo.yml"
    container_path = "/etc/tempo/config.yaml"
    read_only      = true
  }

  restart = "unless-stopped"
}

# ─────────────────────────────────────────────
# Grafana — dashboards & visualization
# ─────────────────────────────────────────────
resource "docker_image" "grafana" {
  name         = "grafana/grafana:latest"
  keep_locally = true
}

resource "docker_container" "grafana" {
  name   = "sp-grafana"
  image  = docker_image.grafana.image_id

  labels {
    label = "com.docker.compose.project"
    value = local.project_name
  }

  networks_advanced {
    name = docker_network.sp_network.name
  }

  ports {
    internal = 3000
    external = 3000
  }

  env = [
    "GF_SECURITY_ADMIN_USER=admin",
    "GF_SECURITY_ADMIN_PASSWORD=admin",
  ]

  volumes {
    host_path      = "${var.project_root}/monitoring/grafana/provisioning"
    container_path = "/etc/grafana/provisioning"
    read_only      = true
  }

  volumes {
    host_path      = "${var.project_root}/monitoring/grafana/dashboards"
    container_path = "/var/lib/grafana/dashboards"
    read_only      = true
  }

  depends_on = [
    docker_container.prometheus,
    docker_container.loki,
    docker_container.tempo,
  ]

  restart = "unless-stopped"
}
