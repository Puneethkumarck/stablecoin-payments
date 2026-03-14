# StableBridge Platform

Cross-border B2B payment platform using a fiat-to-stablecoin-to-fiat ("sandwich") model. Converts sender fiat currency to USDC on-chain, transfers via blockchain, and converts back to recipient fiat currency.

## Architecture

- **Pattern**: Hexagonal (Ports & Adapters)
- **Stack**: Java 25, Spring Boot 4.0.3, Gradle 9.0 (Kotlin DSL)
- **Infrastructure**: Temporal workflows, Kafka (event-driven), PostgreSQL, Redis
- **Blockchain**: Base (L2) with USDC
- **MVP Corridor**: US → DE (USD → EUR) via Stripe ACH + Base/USDC + Modulr SEPA

## Services

| # | Service | Description | Status |
|---|---------|-------------|--------|
| S1 | Payment Orchestrator | Temporal-based payment workflow engine | Done |
| S2 | Compliance Engine | AML/KYT screening (Chainalysis) | Done |
| S3 | Fiat On-Ramp | Stripe ACH collection | Done |
| S4 | Blockchain Gateway | USDC minting, transfers, redemption (Fireblocks MPC) | Done |
| S5 | Fiat Off-Ramp | Modulr SEPA payout | Done |
| S6 | FX Rate Engine | Real-time FX quotes with margin | Done |
| S7 | Ledger Service | Double-entry bookkeeping | Done |
| S8 | Partner Management | Bank/PSP partner lifecycle | Not started |
| S9 | Notification Service | Email/webhook notifications | Not started |
| S10 | API Gateway | Auth, rate limiting, routing | Done |
| S11 | Merchant Onboarding | KYB verification, merchant lifecycle | Done |
| S12 | Transaction History | Query service for payment history | Not started |
| S13 | Merchant IAM | Roles, permissions, API keys | Done |
| S14 | Agentic Gateway | AI-powered payment routing | Not started |

## Project Structure

```
stablebridge-platform/
├── api-gateway-iam/                 # S10 - API Gateway & IAM
├── blockchain-custody/              # S4  - Blockchain & Custody
├── compliance-travel-rule/          # S2  - Compliance & Travel Rule
├── fiat-off-ramp/                   # S5  - Fiat Off-Ramp
├── fiat-on-ramp/                    # S3  - Fiat On-Ramp
├── fx-liquidity-engine/             # S6  - FX & Liquidity Engine
├── ledger-accounting/               # S7  - Ledger & Accounting
├── merchant-iam/                    # S13 - Merchant IAM
├── merchant-onboarding/             # S11 - Merchant Onboarding
├── payment-orchestrator/            # S1  - Payment Orchestrator
├── phase2-integration-tests/        # Cross-service integration tests
├── infra/local/                     # Docker Compose support files
│   ├── postgres/init.sql            # Multi-database init script
│   └── wiremock/                    # WireMock stubs for external APIs
├── docker-compose.dev.yml           # Local development stack
├── Makefile                         # Build, test, and infra shortcuts
├── build.gradle.kts                 # Root build config
└── settings.gradle.kts              # Multi-module settings
```

Each service follows a tri-module structure:

```
<service>/
├── <service>-api/       # Request/response DTOs (shared contract)
├── <service>-client/    # Feign client for inter-service calls
└── <service>/           # Spring Boot application
```

## Local Development

### Prerequisites

- Java 25
- Docker & Docker Compose

### Quick Start

```bash
# Start all infrastructure
make infra-up

# Build everything
make build

# Run all tests
make test
```

### Makefile Targets

Run `make help` to see all targets. Key ones:

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
| `make infra-logs-<name>` | Tail a specific container's logs |
| `make db-psql` | Open psql shell |
| `make run-<service>` | Run a service with dev profile |

### Infrastructure Services

| Service | Port |
|---------|------|
| PostgreSQL | `5432` |
| TimescaleDB (FX rates) | `5433` |
| Redpanda (Kafka) | `9092` (Console: `9090`) |
| Redis | `6379` |
| Temporal | `7233` (UI: `8233`) |
| Vault | `8200` |
| Elasticsearch | `9200` |
| Mailpit | SMTP `1025`, UI `8025` |
| WireMock | `4444` |

### Running a Service

```bash
# Start infrastructure first
make infra-up

# Run a specific service
make run-merchant-onboarding
```

## License

This project is licensed under the [MIT License](LICENSE).
