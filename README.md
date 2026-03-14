<h1 align="center">
  <br>
  StableBridge Platform
  <br>
</h1>

<h4 align="center">Enterprise-grade cross-border B2B payments using a fiat &rarr; stablecoin &rarr; fiat "sandwich" model.</h4>

<p align="center">
  <a href="https://github.com/Puneethkumarck/stablebridge-platform/actions/workflows/ci.yml"><img src="https://github.com/Puneethkumarck/stablebridge-platform/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Puneethkumarck/stablebridge-platform" alt="License"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white" alt="Java 25">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.3-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Gradle-9.0-02303A?logo=gradle&logoColor=white" alt="Gradle">
  <img src="https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/Redis-8-DC382D?logo=redis&logoColor=white" alt="Redis">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Apache%20Kafka-231F20?logo=apachekafka&logoColor=white" alt="Kafka">
  <img src="https://img.shields.io/badge/Temporal-000000?logo=temporal&logoColor=white" alt="Temporal">
  <img src="https://img.shields.io/badge/Elasticsearch-005571?logo=elasticsearch&logoColor=white" alt="Elasticsearch">
  <img src="https://img.shields.io/badge/HashiCorp%20Vault-FFEC6E?logo=vault&logoColor=black" alt="Vault">
  <img src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white" alt="Docker">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Base%20L2-0052FF?logo=coinbase&logoColor=white" alt="Base L2">
  <img src="https://img.shields.io/badge/USDC-2775CA?logo=circle&logoColor=white" alt="USDC">
  <img src="https://img.shields.io/badge/Testcontainers-2496ED?logo=docker&logoColor=white" alt="Testcontainers">
  <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?logo=githubactions&logoColor=white" alt="GitHub Actions">
  <img src="https://img.shields.io/badge/SonarCloud-F3702A?logo=sonarcloud&logoColor=white" alt="SonarCloud">
</p>

<p align="center">
  <a href="#overview">Overview</a> &bull;
  <a href="#architecture">Architecture</a> &bull;
  <a href="#services">Services</a> &bull;
  <a href="#getting-started">Getting Started</a> &bull;
  <a href="#contributing">Contributing</a>
</p>

---

## Overview

StableBridge eliminates the latency and cost of traditional correspondent banking by using stablecoins as the settlement rail. It converts sender fiat to USDC on-chain, transfers via Base L2, and converts back to recipient fiat.

```
  Sender (USD)                                              Recipient (EUR)
      |                                                          ^
      v                                                          |
 +-----------+     +----------------+     +-----------+     +-----------+
 |  Fiat     |     |  Blockchain    |     |  Fiat     |     |  Local    |
 |  On-Ramp  | --> |  Transfer      | --> |  Off-Ramp | --> |  Payout   |
 | (Stripe)  |     |  (Base L2)     |     | (Modulr)  |     |  (SEPA)   |
 +-----------+     +----------------+     +-----------+     +-----------+
       |                  |                     |
       v                  v                     v
   USD -> USDC     USDC Transfer          USDC -> EUR
```

> **MVP Corridor:** US &rarr; DE (USD &rarr; EUR) via Stripe ACH + Base/USDC + Modulr SEPA

---

## Architecture

<table>
<tr><td><b>Pattern</b></td><td>Hexagonal Architecture (Ports & Adapters) with DDD</td></tr>
<tr><td><b>Workflows</b></td><td>Temporal durable execution for saga orchestration</td></tr>
<tr><td><b>Messaging</b></td><td>Event-driven with Kafka (transactional outbox, at-least-once delivery)</td></tr>
<tr><td><b>Data</b></td><td>PostgreSQL per service, TimescaleDB for FX time-series, Redis caching</td></tr>
<tr><td><b>Security</b></td><td>OAuth2 + API keys, mTLS service-to-service, HashiCorp Vault</td></tr>
<tr><td><b>Observability</b></td><td>OpenTelemetry tracing, structured JSON logging, SonarCloud</td></tr>
</table>

### Payment Flow

```
                          +---------------------+
                          |    S10 API Gateway   |
                          +----------+----------+
                                     |
                                     v
                     +-------------------------------+
                     |    S1 Payment Orchestrator     |
                     |        (Temporal Saga)         |
                     +---+----------+----------+-----+
                         |          |          |
              +----------+    +-----+-----+    +----------+
              |               |           |               |
              v               v           v               v
      +--------------+  +-----------+  +--------+  +------------+
      | S2 Compliance|  | S6 FX     |  | S7     |  | S9 Notify  |
      | & Travel Rule|  | Engine    |  | Ledger |  |            |
      +--------------+  +-----------+  +--------+  +------------+
              |               |
              v               v
      +--------------+  +-----------+  +--------------+
      | S3 Fiat      |  | S4 Block- |  | S5 Fiat      |
      | On-Ramp      +->| chain &   +->| Off-Ramp     |
      | (Stripe ACH) |  | Custody   |  | (Modulr SEPA)|
      +--------------+  +-----------+  +--------------+
```

### Sandwich Flow &mdash; Service-to-Service Communication

The payment lifecycle uses three communication patterns:

| Pattern | Usage | Example |
|---------|-------|---------|
| **Temporal Activity** (sync REST) | Critical-path orchestration with retries & timeouts | S1 &rarr; S2 compliance check |
| **Kafka Event** (async outbox) | State propagation, audit trail, fan-out | S3 &rarr; S7 ledger entry |
| **Temporal Signal** (async webhook relay) | External confirmations routed back to workflow | Stripe webhook &rarr; S1 |

<details>
<summary><b>Happy Path Sequence (USD &rarr; EUR)</b></summary>

```
 Client              S1 Orchestrator       S2 Compliance      S6 FX Engine
   |                 (Temporal Saga)             |                  |
   | POST /payments        |                    |                  |
   |---------------------->|                    |                  |
   |                       |                    |                  |
   |   201 {payment_id}    |  [1] REST -------->|                  |
   |<----------------------|  checkCompliance() |                  |
   |                       |<-- PASSED ---------|                  |
   |                       |                    |                  |
   |                       |  [2] REST -------------------------------->|
   |                       |  lockFxRate()      |                  |
   |                       |<-- LOCKED --------------------------------|
   :                       :                    :                  :

 S3 Fiat On-Ramp     S1 Orchestrator       S4 Blockchain      S5 Fiat Off-Ramp
   |                       |                    |                  |
   |  [3] Stripe webhook   |                    |                  |
   |  fiat.collected ~~~~> |                    |                  |
   |  (Kafka + Signal)     |                    |                  |
   |                       |  [4] REST -------->|                  |
   |                       |  initiateTransfer()|                  |
   |                       |                    |                  |
   |                       |  [5] Chain confirm |                  |
   |                       | <~~~~ (Signal)     |                  |
   |                       |                    |                  |
   |                       |  [6] REST -------------------------------->|
   |                       |  initiatePayout()  |                  |
   |                       |                    |                  |
   |                       |  [7] Settlement    |                  |
   |                       | <~~~~ (Signal) --------------------------------|
   |                       |                    |                  |
   :                       : STATE = COMPLETED  :                  :

 Legend:   ------>  Synchronous REST (Temporal Activity)
           ~~~~~~>  Asynchronous (Kafka event + Temporal Signal)
```

</details>

<details>
<summary><b>Kafka Event Map</b></summary>

Every event is published via the **transactional outbox** pattern (Namastack) &mdash; guaranteed at-least-once delivery.

```
  S1 Orchestrator ──publish──> payment.initiated ──────> S7 Ledger (audit)
        |                      payment.completed ──────> S6 (consume lock), S7, S9, S12
        |                      payment.failed ─────────> S6 (release lock), S7, S9
        |
  S2 Compliance ───publish──> compliance.result ───────> S1 (workflow), S7
        |
  S3 On-Ramp ──────publish──> fiat.collected ──────────> S1 (signal), S7 (debit leg)
        |
  S4 Blockchain ───publish──> chain.transfer.submitted > S7 (mint leg), S9
        |                     chain.transfer.confirmed > S1 (signal), S7, S9
        |
  S5 Off-Ramp ─────publish──> fiat.payout.completed ──> S1 (signal), S7 (payout leg), S9
        |
  S6 FX Engine ────publish──> fx.rate.locked ──────────> S7 (FX fee leg)
        |
  S7 Ledger ───────publish──> reconciliation.discrepancy > Ops alerting
```

| Topic | Producer | Key Consumers | Partition Key |
|-------|----------|---------------|---------------|
| `payment.initiated` | S1 | S7 (audit) | `payment_id` |
| `compliance.result` | S2 | S1, S7 | `payment_id` |
| `fx.rate.locked` | S6 | S7 | `payment_id` |
| `fiat.collected` | S3 | S1 (signal), S7 | `payment_id` |
| `chain.transfer.submitted` | S4 | S7, S9 | `payment_id` |
| `chain.transfer.confirmed` | S4 | S1 (signal), S7, S9 | `payment_id` |
| `fiat.payout.completed` | S5 | S1 (signal), S7, S9 | `payment_id` |
| `payment.completed` | S1 | S6, S7, S9, S12 | `payment_id` |
| `payment.failed` | S1 | S6, S7, S9 | `payment_id` |
| `audit.event` | All | S7 (append-only journal) | `correlation_id` |

</details>

<details>
<summary><b>Compensation &amp; Saga Rollback</b></summary>

The Temporal workflow maintains a LIFO compensation stack. On failure at any step, compensations unwind in reverse order:

```
  Step failed at S4 (blockchain transfer)
        |
        v
  +----------------------------------------------------------+
  | Compensation Stack (LIFO)                                 |
  |                                                           |
  |  [3] Refund fiat collection ──> S3 POST /refund           |
  |  [2] Release FX lock ─────────> S6 DELETE /fx/lock/{id}   |
  |  [1] Void compliance result ──> S2 (event: voided)        |
  +----------------------------------------------------------+
        |
        v
  S1 publishes: payment.failed (error_code, failed_step)
        |
        +──> S7 Ledger: reversal journal entries
        +──> S9 Notify: failure webhook to merchant
```

</details>

---

## Services

| # | Service | Description | Tests | Status |
|:---:|---------|-------------|:-----:|:------:|
| S1 | [Payment Orchestrator](payment-orchestrator/) | Temporal-based payment lifecycle & saga engine | `237` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S2 | [Compliance & Travel Rule](compliance-travel-rule/) | AML/KYT screening, sanctions, Travel Rule (FATF) | `258` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S3 | [Fiat On-Ramp](fiat-on-ramp/) | Stripe ACH fiat collection | `302` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S4 | [Blockchain & Custody](blockchain-custody/) | USDC transfers, MPC custody (Fireblocks) | `418` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S5 | [Fiat Off-Ramp](fiat-off-ramp/) | Modulr SEPA fiat payout | `272` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S6 | [FX & Liquidity Engine](fx-liquidity-engine/) | Real-time FX quotes with margin, rate locking | `231` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S7 | [Ledger & Accounting](ledger-accounting/) | Double-entry bookkeeping, reconciliation | `337` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S8 | Partner Management | Bank/PSP partner lifecycle | - | ![Planned](https://img.shields.io/badge/-Planned-lightgrey?style=flat-square) |
| S9 | Notification Service | Email/webhook delivery | - | ![Planned](https://img.shields.io/badge/-Planned-lightgrey?style=flat-square) |
| S10 | [API Gateway & IAM](api-gateway-iam/) | Authentication, rate limiting, API key management | `298` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S11 | [Merchant Onboarding](merchant-onboarding/) | KYB verification, merchant lifecycle | `104` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S12 | Transaction History | Payment query & search service | - | ![Planned](https://img.shields.io/badge/-Planned-lightgrey?style=flat-square) |
| S13 | [Merchant IAM](merchant-iam/) | Roles, permissions, API key management | `248` | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| S14 | Agentic Gateway | AI-powered payment routing | - | ![Planned](https://img.shields.io/badge/-Planned-lightgrey?style=flat-square) |

> **2,700+ tests** across 10 implemented services

---

## Getting Started

### Prerequisites

- **Java 25+** &mdash; [Adoptium Temurin](https://adoptium.net/)
- **Docker 24+** with Compose v2
- **Git 2.x**

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/Puneethkumarck/stablebridge-platform.git
cd stablebridge-platform

# 2. Start all infrastructure
make infra-up

# 3. Build all modules
make build

# 4. Run all tests
make test

# 5. Run a specific service
make run-merchant-onboarding
```

<details>
<summary><b>Makefile Reference</b></summary>

Run `make help` for the full list.

| Target | Description |
|--------|-------------|
| `make build` | Build all modules (skip tests) |
| `make build-<service>` | Build a single service |
| `make test` | Run all tests (unit + integration + business) |
| `make test-unit` | Unit tests only |
| `make test-integration` | Integration tests only (requires infra) |
| `make test-business` | Business tests only (requires infra) |
| `make test-<service>-all` | All tests for one service |
| `make format` | Apply Spotless formatting |
| `make lint` | Check formatting (CI) |
| `make ci` | Full CI pipeline (lint + all tests) |
| `make infra-up` | Start Docker Compose infrastructure |
| `make infra-down` | Stop infrastructure |
| `make infra-destroy` | Stop + remove volumes (full reset) |
| `make infra-logs` | Tail all container logs |
| `make db-psql` | Open psql shell |
| `make run-<service>` | Run a service with dev profile |

</details>

---

## Local Infrastructure

All external dependencies are replaced with local equivalents via Docker Compose &mdash; **$0 cost for development**.

| Service | Local Replacement | Port(s) |
|---------|-------------------|---------|
| PostgreSQL | `postgres:18-alpine` | `5432` |
| TimescaleDB | `timescale/timescaledb` | `5433` |
| Kafka | Redpanda | `9092` &bull; Console: `9090` |
| Redis | `redis:8-alpine` | `6379` |
| Temporal | `temporalio/auto-setup` | `7233` &bull; UI: `8233` |
| Vault | vault dev mode | `8200` |
| Elasticsearch | `elasticsearch:9.x` | `9200` |
| Email | Mailpit | SMTP: `1025` &bull; UI: `8025` |
| External APIs | WireMock | `4444` |

```bash
docker compose -f docker-compose.dev.yml up -d    # Start
docker compose -f docker-compose.dev.yml ps        # Status
make infra-logs                                    # Logs
make infra-destroy                                 # Full reset
```

---

## Project Structure

```
stablebridge-platform/
├── api-gateway-iam/              # S10 - API Gateway & IAM
├── blockchain-custody/           # S4  - Blockchain & Custody
├── compliance-travel-rule/       # S2  - Compliance & Travel Rule
├── fiat-off-ramp/                # S5  - Fiat Off-Ramp
├── fiat-on-ramp/                 # S3  - Fiat On-Ramp
├── fx-liquidity-engine/          # S6  - FX & Liquidity Engine
├── ledger-accounting/            # S7  - Ledger & Accounting
├── merchant-iam/                 # S13 - Merchant IAM
├── merchant-onboarding/          # S11 - Merchant Onboarding
├── payment-orchestrator/         # S1  - Payment Orchestrator
├── phase2-integration-tests/     # Cross-service integration tests
│
├── infra/local/
│   ├── postgres/init.sql         # Multi-database init script
│   └── wiremock/                 # WireMock stubs for external APIs
│
├── services/                     # Service specification docs
├── playbook/                     # Architecture & coding standards
├── docker-compose.dev.yml        # Local development stack
├── Makefile                      # Build, test, and infra shortcuts
├── build.gradle.kts              # Root build config
└── settings.gradle.kts           # Multi-module settings
```

<details>
<summary><b>Service Module Layout (Hexagonal Architecture)</b></summary>

Each service follows a tri-module structure:

```
<service>/
├── <service>-api/        # Request/response DTOs (shared contract)
├── <service>-client/     # Feign client for inter-service calls
└── <service>/            # Spring Boot application
    └── src/main/java/com/stablecoin/payments/<service>/
        ├── application/
        │   └── controller/       # REST controllers (thin HTTP handlers)
        ├── domain/
        │   ├── model/            # Aggregates, value objects, enums
        │   ├── port/             # Inbound & outbound port interfaces
        │   └── service/          # Command handlers (business logic)
        └── infrastructure/
            ├── adapter/          # Port implementations (DB, Kafka, HTTP)
            ├── config/           # Spring configuration
            └── persistence/      # JPA entities, repositories
```

</details>

---

## Testing Strategy

The project uses a three-tier testing approach with **2,700+ tests**:

```
┌──────────────────────────────────────────────────┐
│               Business Tests                     │  End-to-end user scenarios
│              (Testcontainers)                    │  src/business-test/
├──────────────────────────────────────────────────┤
│            Integration Tests                     │  DB, Kafka, REST endpoints
│             (Testcontainers)                     │  src/integration-test/
├──────────────────────────────────────────────────┤
│               Unit Tests                         │  Domain logic, handlers, mappers
│              (Mocks only)                        │  src/test/
└──────────────────────────────────────────────────┘
```

**Quality gates:** ArchUnit (architecture boundaries) &bull; JaCoCo (coverage) &bull; Spotless (formatting) &bull; SonarCloud (static analysis)

```bash
make test-unit                    # Unit tests only
make test-integration             # Integration tests (requires infra)
make test-business                # Business tests (requires infra)
make test-merchant-iam-all        # All tiers for one service
```

---

## CI/CD

GitHub Actions runs on every push and PR:

```
Push / PR
    │
    ├── Spotless Check ─────── Code formatting validation
    │
    ├── Path Filter ────────── Detect changed services
    │
    ├── Test Matrix ────────── Per-service: unit + integration + business
    │       │
    │       └── JaCoCo ─────── Coverage report
    │
    └── SonarCloud ─────────── Static analysis (push only)
```

---

## Roadmap

| Phase | Name | Services | Status |
|:-----:|------|----------|:------:|
| 0 | Infrastructure Foundation | K8s, Kafka, DBs, Temporal, CI/CD | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| 1 | Identity & Merchant | S10 API Gateway, S11 Onboarding, S13 IAM | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| 2 | Core Payment Logic | S1 Orchestrator, S2 Compliance, S6 FX | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| 3 | Value Movement MVP | S3 On-Ramp, S4 Blockchain, S5 Off-Ramp, S7 Ledger | ![Done](https://img.shields.io/badge/-Done-success?style=flat-square) |
| 4 | Operational Maturity | S8 Partner Mgmt, S9 Notifications | ![Planned](https://img.shields.io/badge/-Planned-lightgrey?style=flat-square) |
| 5 | Merchant Experience | S12 Transaction History | ![Planned](https://img.shields.io/badge/-Planned-lightgrey?style=flat-square) |
| 6 | Intelligence & Scale | S14 Agentic Gateway + multi-chain/corridor | ![Planned](https://img.shields.io/badge/-Planned-lightgrey?style=flat-square) |

---

## Contributing

Contributions are welcome! Please follow these steps:

1. **Fork** the repository
2. **Create a feature branch** from `main`
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Follow the coding standards** in [`playbook/01-coding-standards.md`](playbook/01-coding-standards.md)
4. **Write tests** &mdash; all three tiers where applicable
5. **Ensure CI passes**
   ```bash
   make ci
   ```
6. **Open a Pull Request** against `main`

<details>
<summary><b>Code Style</b></summary>

- Java 25 with Lombok and MapStruct
- Hexagonal architecture &mdash; domain must not depend on infrastructure
- Single-assert test pattern with recursive comparison
- No wildcard imports, static imports for readability
- Spotless formatting enforced (`make format` to auto-fix)

</details>

---

## Security

If you discover a security vulnerability, please **do not** open a public issue. Instead, email the maintainer directly.

---

## License

This project is licensed under the MIT License &mdash; see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <sub>Built with Java 25 &bull; Spring Boot 4 &bull; Temporal &bull; Kafka &bull; Base L2</sub>
</p>
