# Claude Code — Stablecoin Payments Platform

## Project Identity

| Attribute | Value |
|-----------|-------|
| **Platform** | Fiat → Stablecoin → Fiat cross-border B2B payments ("sandwich") |
| **Stack** | Java 25 / Spring Boot 3.4.5 · Hexagonal Architecture · Temporal · Kafka · PostgreSQL · Redis |
| **Environment** | **Local dev & testing** — Docker Compose (`docker-compose.dev.yml`) |
| **Services** | 14 microservices — see `services/00-index.md` |
| **Architecture** | `playbook/ARCHITECTURE.md` · `stablecoin-sandwich-architecture.md` |
| **Roadmap** | `services/00-implementation-roadmap.md` |
| **Dev alternatives** | `services/00-dev-sandbox-alternatives.md` — $0 cost local stack |
| **Base package** | `com.stablecoin.payments.<service>` |

## Local Dev Stack (Docker Compose)

| Service | Local Replacement | Port |
|---------|-------------------|------|
| AWS MSK Kafka | Redpanda | `9092` · Console: `9090` |
| RDS PostgreSQL | postgres:16-alpine | `5432` (14 DBs via `infra/local/postgres/init.sql`) |
| FX TimescaleDB | timescale/timescaledb | `5433` |
| ElastiCache Redis | redis:7-alpine | `6379` |
| Temporal cluster | temporalio/auto-setup | `7233` · UI: `8233` |
| HashiCorp Vault | vault dev mode | `8200` (token: `dev-root-token`) |
| Elasticsearch | elastic:8.11 | `9200` |
| SendGrid / email | Mailhog | SMTP `1025` · UI `8025` |
| Stripe / Onfido / etc | WireMock | `4444` (stubs: `infra/local/wiremock/`) |
| Fireblocks MPC | `DevCustodyAdapter` (`@Profile("dev")`) | — |
| Chainalysis AML | `MockAmlProvider` (`@Profile("dev")`) | — |
| Blockchain | Base Sepolia testnet + Alchemy free tier | — |

```bash
# Start everything
docker compose -f docker-compose.dev.yml up -d

# Verify
docker compose -f docker-compose.dev.yml ps
```

## Implementation Phases

| # | Name | Services | Status |
|---|------|----------|--------|
| 0 | Infrastructure Foundation | K8s, Kafka, DBs, Temporal, CI/CD | Done (local Docker Compose) |
| 1 | Identity & Merchant | S10 API Gateway, S11 Onboarding, S13 IAM | **S11 done** (64 tests green) · S13, S10 not started |
| 2 | Core Payment Logic | S1 Orchestrator, S2 Compliance, S6 FX | Not started |
| 3 | Value Movement MVP | S3 On-Ramp, S4 Blockchain, S5 Off-Ramp, S7 Ledger | Not started |
| 4 | Operational Maturity | S8 Partner Mgmt, S9 Notifications | Not started |
| 5 | Merchant Experience | S12 Transaction History | Not started |
| 6 | Intelligence & Scale | S14 Agentic Gateway + multi-chain/corridor | Not started |

## Coding Preferences (Non-Negotiable)

These override playbook defaults. Apply every time without being asked.

| Preference | Rule |
|-----------|------|
| **Project structure** | Flat single multi-module Gradle — all services as top-level submodules (`include("service-name")` in root `settings.gradle`). No nested module nesting. |
| **Java version** | **Java 25** + Gradle 9.0.0 |
| **Gradle DSL** | **Kotlin DSL** (`build.gradle.kts` / `settings.gradle.kts`) |
| **Imports** | **No wildcard imports** — every import must be fully qualified and explicit |
| **Static imports** | **Use static imports** wherever they improve readability (assertions, enum constants, factory methods) |
| **Package — no `web/`** | Controllers live in `application.controller` — NEVER create a `web/` package |
| **Outbox pattern** | Custom JPA outbox: `OutboxEvent` entity + `OutboxEventRepository` + `OutboxRelayJob` (@Scheduled → Spring Cloud Stream). See S11 for reference implementation. |
| **Build cache** | Always include `buildCache { local { isEnabled = true } }` in `settings.gradle` |

Full preference details: `memory/preferences.md`

## Playbook — Read On-Demand (not all at once)

| Topic | File |
|-------|------|
| Naming, code style, logging | `playbook/01-coding-standards.md` |
| Package & multi-module structure | `playbook/02-project-structure.md` |
| Patterns: saga, outbox, ports/adapters | `playbook/03-design-patterns-catalog.md` |
| Testing: unit/integration/business/ArchUnit | `playbook/04-testing-standards.md` |
| ADRs (hexagonal, records, state machine) | `playbook/05-architecture-decisions.md` |
| New service bootstrap checklist | `playbook/06-new-project-bootstrap-checklist.md` |
| Checkstyle & formatting | `playbook/checkstyle-recommendations.md` |

## Service Context Files

Each scaffolded service has a `<service>/CONTEXT.md` — read it instead of crawling source files.

| Service | File |
|---------|------|
| S11 Merchant Onboarding | `merchant-onboarding/CONTEXT.md` |
| S13 Merchant IAM | `merchant-iam/CONTEXT.md` |
| S10 API Gateway & IAM | `api-gateway-iam/CONTEXT.md` |

> Add a row here whenever a new service is scaffolded.

## Session Start (Mandatory)

1. Read `IMPLEMENTATION_STATE.md` and `IMPLEMENTATION_STATE.json`.
2. Read `tasks/lessons.md` if it exists.
3. For any service being worked on, read its `CONTEXT.md` — do NOT crawl source files first.
4. Resume from current state — re-plan only if state is invalid or stale.

## Execution Rules

- Read the relevant `services/NN-<name>.md` spec + relevant playbook files before coding.
- Create/update `tasks/todo.md` checklist before any non-trivial work.
- Keep exactly **one** task `in_progress` at a time.
- Prefer root-cause fixes. Keep diffs minimal and localized.
- After any correction: document in `tasks/lessons.md` as `mistake → fix → prevention`.

## Completion Rules

- Never mark done without evidence (tests pass, output verified, logs checked).
- Update **both** `IMPLEMENTATION_STATE.md` and `IMPLEMENTATION_STATE.json` after each task.
- Keep the resume snapshot current: 5–10 lines sufficient for a cold start.

## State File Schema

`IMPLEMENTATION_STATE.json` must follow:
```json
{
  "last_updated_utc": "",
  "current_phase": "",
  "current_milestone": "",
  "current_task": {
    "id": "", "title": "", "status": "not_started|in_progress|blocked|done",
    "depends_on": [], "acceptance_criteria": [], "artifacts": [], "notes": ""
  },
  "completed_tasks": [],
  "pending_tasks": [],
  "decisions": [],
  "risks_blockers": [],
  "next_actions": [],
  "resume_snapshot": []
}
```

## Workflow — Linear + GitHub + Claude Code

| Tool | Role |
|------|------|
| **Linear** | Plan, prioritize, track issues (team: Stablecoin Payments) |
| **GitHub** | Code, CI, PRs — auto-linked to Linear |
| **Claude Code** | Implement, test, create PRs — reads Linear via MCP |

### Branch naming

```
feature/LIN-<id>-<short-description>
```

Linear auto-detects `LIN-<id>` in branch names, PR titles, and commit messages.

### PR body

Always include `Closes LIN-<id>` to auto-close the Linear issue on merge.

### Before starting work

Read the Linear issue for acceptance criteria and context.

### MCP

Linear MCP server configured in `.claude/settings.json`. Use `/mcp` to authenticate on first use.

## Conflict Resolution Order

1. Direct user instruction → 2. This file → 3. `playbook/` standards → 4. Generic best practices
