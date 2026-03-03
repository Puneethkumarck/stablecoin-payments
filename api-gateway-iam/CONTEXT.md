# S10 API Gateway & IAM — Context

> Read this file instead of crawling source files. Updated per PR.

## Purpose

S10 is the platform's single entry point for external traffic. It:
- Authenticates requests (OAuth2 client_credentials + API keys)
- Manages merchant access at the platform level
- Rate-limits callers per merchant tier
- Logs all traffic immutably to an audit log

For local dev, this is a pure Spring Boot service (no Kong).

## Module Structure

| Module | Purpose |
|--------|---------|
| `api-gateway-iam-api` | Shared DTOs (Jakarta Validation + Jackson) |
| `api-gateway-iam-client` | Feign client for inter-service calls |
| `api-gateway-iam` | Spring Boot application |

## Base Package

`com.stablecoin.payments.gateway.iam`

## Port

8080 (gateway is main entry point)

## Database

`s10_api_gateway_iam` on PostgreSQL (port 5432)

### Tables (7 Flyway migrations)

| Table | Description |
|-------|-------------|
| `merchants` | Platform-level merchant registry (UUID PK, TEXT[] scopes, JSONB corridors, VARCHAR status/kyb/tier with CHECK) |
| `api_keys` | SHA-256 hashed keys (unique index on hash, TEXT[] scopes + allowed_ips) |
| `oauth_clients` | OAuth2 clients for client_credentials grant (bcrypt secret hash) |
| `access_tokens` | Issued JWTs tracked by JTI (partial index on non-revoked) |
| `rate_limit_events` | Partitioned by occurred_at (default partition for local dev) |
| `gateway_audit_log` | Partitioned, append-only (UPDATE/DELETE revoked with IF EXISTS guard) |
| `gatewayiam_outbox_*` | Namastack outbox tables (record, instance, partition) |

## Dependencies

- **S13 Merchant IAM**: JWKS endpoint for user JWT validation
- **S11 Merchant Onboarding**: Publishes `merchant.activated`, `merchant.suspended`, `merchant.closed` events

## Kafka

- **Consumes**: `merchant.activated`, `merchant.suspended`, `merchant.closed` (from S11)
- **Produces**: `api-key.revoked`, `rate-limit.exceeded` (via Namastack outbox)

## Security

- Permits: `/actuator/**`, `/v1/auth/**`, `/.well-known/**`
- All other endpoints require authentication
- Stateless sessions, CSRF disabled

## JWT

- ES256 asymmetric (separate keypair from S13)
- Claims: `merchant_id`, `scope`, `env`
- Config under `api-gateway-iam.jwt.*`

## Rate Limiting

| Tier | Requests/min | Requests/day |
|------|-------------|-------------|
| STARTER | 60 | 10,000 |
| GROWTH | 300 | 100,000 |
| ENTERPRISE | 1,000 | 1,000,000 |
| UNLIMITED | unlimited | unlimited |

## Key Patterns

- **Outbox**: Namastack JDBC (`gatewayiam_` prefix)
- **Hexagonal**: Domain has zero Spring/JPA imports
- **JPA updates**: EntityUpdater (MapStruct @MappingTarget) for in-place updates
- **API key storage**: Only SHA-256 hash stored; raw key returned once at creation
- **Redis**: Token revocation cache, API key cache (60s TTL), rate limiter (Lua script)

## Test Infrastructure

- 4 source sets: test, integrationTest, businessTest, testFixtures
- JaCoCo 50% minimum on unit tests only (disabled on IT/business — Java 25)
- TestContainers: PostgreSQL + Kafka + Redis singletons
- AbstractIntegrationTest with FK-safe cleanup

## Current Status

- **PR 1 (STA-40)**: Scaffold complete — 3 modules compile, 7 Flyway migrations, SecurityConfig, application.yml
