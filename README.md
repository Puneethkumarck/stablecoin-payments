# StableBridge Platform

Cross-border B2B payment platform using a fiat-to-stablecoin-to-fiat ("sandwich") model. Converts sender fiat currency to USDC on-chain, transfers via blockchain, and converts back to recipient fiat currency.

## Architecture

- **Pattern**: Hexagonal (Ports & Adapters)
- **Stack**: Java 25, Spring Boot 3.4.5, Gradle 9.0 (Kotlin DSL)
- **Infrastructure**: Temporal workflows, Kafka (event-driven), PostgreSQL, Redis
- **Blockchain**: Base (L2) with USDC
- **MVP Corridor**: US → DE (USD → EUR) via Stripe ACH + Base/USDC + Modulr SEPA

## Services

| # | Service | Description | Status |
|---|---------|-------------|--------|
| S1 | Payment Orchestrator | Temporal-based payment workflow engine | Not started |
| S2 | Compliance Engine | AML/KYT screening (Chainalysis) | Not started |
| S3 | Fiat On-Ramp | Stripe ACH collection | Not started |
| S4 | Blockchain Gateway | USDC minting, transfers, redemption (Fireblocks MPC) | Not started |
| S5 | Fiat Off-Ramp | Modulr SEPA payout | Not started |
| S6 | FX Rate Engine | Real-time FX quotes with margin | Not started |
| S7 | Ledger Service | Double-entry bookkeeping | Not started |
| S8 | Partner Management | Bank/PSP partner lifecycle | Not started |
| S9 | Notification Service | Email/webhook notifications | Not started |
| S10 | API Gateway | Auth, rate limiting, routing | Not started |
| S11 | Merchant Onboarding | KYB verification, merchant lifecycle | Done |
| S12 | Transaction History | Query service for payment history | Not started |
| S13 | Merchant IAM | Roles, permissions, API keys | Not started |
| S14 | Agentic Gateway | AI-powered payment routing | Not started |

## Project Structure

```
stablebridge-platform/
├── merchant-onboarding/           # S11 - Merchant Onboarding
│   ├── merchant-onboarding-api/   # Request/response DTOs
│   ├── merchant-onboarding-client/# Feign client for inter-service calls
│   └── merchant-onboarding/       # Spring Boot application
├── infra/local/                   # Docker Compose support files
│   ├── postgres/init.sql          # Multi-database init script
│   └── wiremock/                  # WireMock stubs for external APIs
├── docker-compose.dev.yml         # Local development stack
├── build.gradle.kts               # Root build config
└── settings.gradle.kts            # Multi-module settings
```

## Local Development

### Prerequisites

- Java 25
- Docker & Docker Compose

### Start Infrastructure

```bash
docker compose -f docker-compose.dev.yml up -d
```

This starts:

| Service | Port |
|---------|------|
| PostgreSQL | `5432` |
| Redpanda (Kafka) | `9092` (Console: `9090`) |
| Redis | `6379` |
| Temporal | `7233` (UI: `8233`) |
| Vault | `8200` |
| Elasticsearch | `9200` |
| Mailhog | SMTP `1025`, UI `8025` |
| WireMock | `4444` |

### Build & Test

```bash
# Build all modules
./gradlew build

# Run S11 unit tests
./gradlew :merchant-onboarding:merchant-onboarding:test

# Run S11 integration tests (requires Docker for TestContainers)
./gradlew :merchant-onboarding:merchant-onboarding:integrationTest

# Run S11 business tests
./gradlew :merchant-onboarding:merchant-onboarding:businessTest

# Start S11 service
./gradlew :merchant-onboarding:merchant-onboarding:bootRun
```

S11 runs on port `8081`.

## License

Proprietary. All rights reserved.
