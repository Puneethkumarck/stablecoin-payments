# S11 Merchant Onboarding — Changelog

All changes made to the Merchant Onboarding service across sessions.

---

## Session 1–3 (Pre-existing)

- Scaffolded S11 with full hexagonal architecture (domain/infrastructure/application)
- 41 unit tests, 22 integration tests, 1 business test
- Custom state machine (12 transitions, 8 states)
- Transactional outbox pattern for Kafka event publishing
- Flyway migrations V1–V7
- Docker Compose dev stack (PostgreSQL, Kafka/Redpanda, Temporal, Vault, etc.)

---

## Session 4 — Sandbox Profile & Webhook Flow

### Sandbox Adapters (real external APIs for manual testing)

- **OnfidoKybAdapter** (`@Profile("sandbox")`) — Real Onfido REST API v3.6 adapter. Creates applicants and checks. Maps results: `clear` → PASSED, `consider` → MANUAL_REVIEW, other → FAILED. Sandbox deterministic: `last_name = "Consider"` triggers review.
- **CompaniesHouseAdapter** (`@Profile("sandbox")`) — Real Companies House API with Basic auth. Looks up UK companies by registration number. Returns company name, status, type, address. Only handles country=GB, returns empty for others.
- **OnfidoProperties** — `@ConfigurationProperties(prefix = "onfido")` record: apiToken, baseUrl, webhookSecret, region.
- **CompaniesHouseProperties** — `@ConfigurationProperties(prefix = "companies-house")` record: apiKey, baseUrl.
- **application-sandbox.yml** — Profile config with env var placeholders for ONFIDO_SANDBOX_TOKEN, COMPANIES_HOUSE_API_KEY, ONFIDO_WEBHOOK_SECRET.

### Domain Port: CompanyRegistryProvider

- New outbound port interface in domain layer: `CompanyRegistryProvider.lookup(registrationNumber, country)` returns `Optional<CompanyProfile>`.
- `CompanyProfile` record: companyName, registrationNumber, country, companyStatus, companyType, dateOfCreation, registeredOfficeAddress.
- **MockCompanyRegistryAdapter** — Returns fixed "Mock Company Ltd" data for any input.

### Webhook Flow

- **KybWebhookController** — `POST /api/internal/webhooks/onfido`. Validates HMAC-SHA256 signature via `X-SHA2-Signature` header. Parses payload, delegates to KybProvider.handleWebhook(), resolves merchantId from result or payload tags.
- **OnfidoWebhookValidator** — HMAC-SHA256 signature validation. Skips validation when webhook secret is placeholder (`sandbox-webhook-secret`) or empty/null. Uses `MessageDigest.isEqual()` for constant-time comparison.
- **SecurityConfig** — Permits `/api/internal/webhooks/**` and `/actuator/**` without authentication. All other requests require auth. CSRF disabled.

### Application Service: processKybResult

- Added `processKybResult(UUID merchantId, KybVerification kybResult)` to MerchantApplicationService.
- Handles three outcomes: PASSED (calculates risk tier, publishes MerchantKybPassedEvent), FAILED (publishes MerchantKybFailedEvent), MANUAL_REVIEW (transitions state only).
- Added RiskTierCalculator dependency.

### Unit Tests Added (Session 4)

- OnfidoWebhookValidatorTest — 10 tests: valid HMAC, invalid HMAC, null/blank signature rejection, placeholder/blank/null secret skip, tampered body, JSON parsing, invalid JSON.
- KybWebhookControllerTest — 5 tests: 401 on invalid sig, valid webhook processing, non-check skip, merchantId resolution from tags, unresolvable merchantId.
- OnfidoKybAdapterTest — 5 tests: required documents, webhook filtering for non-check/non-completed events.
- MockCompanyRegistryAdapterTest — 3 tests: mock profile, echo registration/country, always present.
- MerchantApplicationServiceTest — 3 new tests: processKybResult for PASSED, FAILED, MANUAL_REVIEW outcomes. Added @Mock RiskTierCalculator.

---

## Session 5 — @ConditionalOnMissingBean Refactor

### Problem

Mock adapters (MockKybAdapter, MockCompanyRegistryAdapter, MockDocumentStoreAdapter) used `@Profile({"local","test","integration-test"})` in `src/main`. This is a code smell — test infrastructure in production source, and `@Profile` proliferation.

### Solution

- Removed `@Component` and `@Profile` from all 3 mock adapters. They are now plain POJOs.
- Created **FallbackAdaptersConfig** (`@Configuration`) with 3 `@Bean @ConditionalOnMissingBean` methods. Mock adapters are registered only when no real adapter exists.
- `@ConditionalOnMissingBean` on `@Bean` methods (not `@Component` classes) is reliable because `@Configuration` beans are processed after component scanning.
- `@Profile("sandbox")` remains on OnfidoKybAdapter and CompaniesHouseAdapter — the only valid `@Profile` use.

### Bean wiring strategy

```
Default / local / test:  No sandbox profile → no real adapters → FallbackAdaptersConfig registers mocks
Sandbox profile:         @Profile("sandbox") adapters scanned → FallbackAdaptersConfig skips mocks
Integration tests:       No sandbox → fallback mocks registered automatically
```

### Test Infrastructure Changes

- **TestSecurityConfig** — Bean name changed from `testSecurityFilterChain` to `securityFilterChain` to override main SecurityConfig bean.
- **application-integration-test.yml** — Added `spring.main.allow-bean-definition-overriding: true` for bean name override.

---

## Session 5 — Kafka Testcontainer

### Problem

Integration tests used `spring-cloud-stream-test-binder` which stubs out Kafka entirely. The OutboxRelayJob (which uses KafkaTemplate directly) was never tested end-to-end.

### Solution

- Removed `spring-cloud-stream-test-binder` dependency.
- Added `org.testcontainers:kafka` dependency.
- Added `KafkaContainer` (confluentinc/cp-kafka:7.6.0) to `AbstractIntegrationTest` as a static singleton alongside PostgreSQL.
- Wired `spring.kafka.bootstrap-servers` and `spring.cloud.stream.kafka.binder.brokers` via `@DynamicPropertySource`.
- Added `spring.kafka.producer.key-serializer` and `value-serializer` as `StringSerializer` in integration test config (Spring Cloud Stream binder defaults to ByteArraySerializer).

### Kafka E2E Relay Test

`OutboxIntegrationIT.shouldRelayOutboxEventToKafka`:
1. Creates a merchant via POST (produces outbox event).
2. Waits (Awaitility, 10s) for OutboxRelayJob to mark the event as processed.
3. Creates a KafkaConsumer, subscribes to `merchant.applied`, polls with `auto.offset.reset=earliest`.
4. Searches all consumed records for one containing "Kafka E2E Corp".
5. Asserts topic name matches.

Consumer uses a unique group ID per test run to avoid offset interference.

### Check Task Wiring

- Added `tasks.named("check") { dependsOn("integrationTest", "businessTest") }` so `./gradlew build` runs all test suites (unit → integration → business).

---

## Session 5 — Temporal Workflow

### Problem

The KYB onboarding flow was a direct synchronous call: `startKyb()` → KybProvider.submit() → wait for webhook → processKybResult(). No durable waits, no timeouts, no manual review escalation.

### Solution: MerchantOnboardingWorkflow

A Temporal durable workflow that orchestrates the full KYB flow with signal-based async waits.

#### New Files

| File | Package | Purpose |
|------|---------|---------|
| MerchantOnboardingWorkflow.java | infrastructure.temporal.workflow | @WorkflowInterface with @WorkflowMethod, 3 @SignalMethod, 1 @QueryMethod |
| MerchantOnboardingWorkflowImpl.java | infrastructure.temporal.workflow | Workflow implementation with 6-step flow |
| OnboardingResult.java | infrastructure.temporal.workflow | Workflow result record (ACTIVE, REJECTED, TIMED_OUT) |
| MerchantOnboardingActivities.java | infrastructure.temporal.activity | @ActivityInterface with 9 activity methods |
| MerchantOnboardingActivitiesImpl.java | infrastructure.temporal.activity | Activity implementations wrapping domain services |
| KafkaEventActivities.java | infrastructure.temporal.activity | @ActivityInterface for Kafka publishing |
| KafkaEventActivitiesImpl.java | infrastructure.temporal.activity | Publishes to Kafka via KafkaTemplate |
| TemporalWorkerConfig.java | infrastructure.temporal.config | Registers workflow + activities on task queue |
| KybResultSignal.java | infrastructure.temporal.signal | Signal payload for KYB results |
| DocumentUploadedSignal.java | infrastructure.temporal.signal | Signal payload for document uploads |
| ReviewDecisionSignal.java | infrastructure.temporal.signal | Signal payload for ops review decisions |

#### Workflow Flow

```
POST /merchants/{id}/kyb/start
  → MerchantApplicationService.startKyb()
  → WorkflowClient.start(workflow::runOnboarding, merchantId)

Temporal workflow:
  Step 1: verifyCompanyRegistry(merchantId)
          → CompanyRegistryProvider.lookup() (Companies House for GB)
          → If NOT_FOUND or not active → reject early (no Onfido cost)
  Step 2: startKyb(merchantId)
          → KybProvider.submit() → Onfido
          → Transitions merchant to KYB_IN_PROGRESS
  Step 3: Await signal kybResultReceived [7-day timeout]
          → Onfido webhook → KybWebhookController → workflowClient.signal()
          → If timeout → auto-reject
  Step 4: Process KYB result
          → PASSED → continue
          → FAILED → reject, publish merchant.kyb.failed
          → MANUAL_REVIEW → notify ops, await reviewDecision signal
            → [5-day timeout → escalate → 5-day timeout → auto-reject]
  Step 5: calculateRiskTier(riskSignals)
          → RiskTierCalculator: 0-25=LOW, 26-50=MEDIUM, 51+=HIGH
  Step 6: markKybPassed(merchantId, riskTier)
          → Transitions merchant to PENDING_APPROVAL
  Step 7: publishEvent("merchant.kyb.passed") → Kafka
  Step 8: Return OnboardingResult(ACTIVE, merchantId, riskTier)
```

#### Signals

| Signal | Source | Payload |
|--------|--------|---------|
| kybResultReceived | KybWebhookController (Onfido webhook relay) | KybResultSignal (kybId, provider, status, riskSignals, reviewNotes) |
| documentUploaded | Future: document upload endpoint | DocumentUploadedSignal (documentType, fileName, s3Key) |
| reviewDecision | Future: ops portal | ReviewDecisionSignal (APPROVED/REJECTED, reason, reviewedBy) |

#### Query

`getOnboardingStatus()` returns: STARTED, VERIFYING_COMPANY, COMPANY_NOT_FOUND, COMPANY_NOT_ACTIVE, KYB_SUBMITTING, AWAITING_KYB_RESULT, MANUAL_REVIEW, KYB_PASSED, KYB_REJECTED, PENDING_APPROVAL, TIMED_OUT, REVIEW_TIMED_OUT.

#### Modified Files

| File | Change |
|------|--------|
| MerchantApplicationService.startKyb() | Now starts Temporal workflow instead of calling KybProvider directly. Uses WorkflowClient.start() with workflow ID `onboarding-<merchantId>`. |
| KybWebhookController | Now signals Temporal workflow instead of calling processKybResult(). Creates KybResultSignal from KybVerification and signals via workflowClient.newWorkflowStub(). |
| build.gradle.kts | Added `io.temporal:temporal-testing` for workflow unit tests. |
| application.yml | Added `spring.temporal.namespace` and `spring.temporal.connection.target` config. Added explicit Kafka serializer config. |
| application-integration-test.yml | Added `spring.temporal.workers.enabled: false` to skip TemporalWorkerConfig in tests. |

#### TemporalWorkerConfig

- Registers MerchantOnboardingWorkflowImpl + activity implementations on task queue `onboarding-workflow`.
- Conditional on `spring.temporal.workers.enabled` (default: true, set to false in integration tests).
- `@ConditionalOnProperty(name = "spring.temporal.workers.enabled", havingValue = "true", matchIfMissing = true)`.

#### Test Infrastructure for Temporal

- **TestTemporalConfig** — `@TestConfiguration` providing a mock WorkflowClient for integration/business tests. Configured to return a no-op workflow stub for any newWorkflowStub() call.
- **AbstractIntegrationTest** — `@Import({TestSecurityConfig.class, TestTemporalConfig.class})`.

#### Event Publishing: Two Paths

- **KYB events** (merchant.kyb.passed, merchant.kyb.failed): Published by Temporal KafkaEventActivities — Temporal guarantees at-least-once.
- **Non-KYB events** (merchant.applied, merchant.activated, merchant.suspended, merchant.closed, merchant.corridor.approved): Still published via transactional outbox (OutboxMerchantEventPublisher → outbox_events → OutboxRelayJob → Kafka).

#### What Stays Unchanged

- Merchant aggregate root and state machine (domain purity preserved).
- Domain events (records with TOPIC/EVENT_TYPE constants).
- All non-KYB endpoints (apply, activate, suspend, reactivate, close, approveCorridor, updateMerchant, uploadDocument, updateRateLimitTier).
- MerchantRepository, KybProvider interface, RiskTierCalculator, MerchantActivationPolicy, CorridorEntitlementService.
- Outbox infrastructure (retained for non-workflow events).

#### Activity Design Note

`@ActivityMethod` annotations intentionally omitted from interface methods — `@ActivityInterface` on the class is sufficient and avoids compatibility issues with Mockito proxies in Temporal TestWorkflowExtension tests.

---

## Session 5 — Companies House Wired into Workflow

### Problem

CompaniesHouseAdapter and CompanyRegistryProvider existed but nothing called `CompanyRegistryProvider.lookup()`. The intended use — validating company registration before paying for an Onfido KYB check — was not implemented.

### Solution

- Added `verifyCompanyRegistry(UUID merchantId)` to MerchantOnboardingActivities interface.
- Implemented in MerchantOnboardingActivitiesImpl: calls `CompanyRegistryProvider.lookup()`, returns company status string or "NOT_FOUND".
- Wired as Step 1 in the Temporal workflow (before startKyb):
  - If "NOT_FOUND" → reject early with "Company not found in official registry".
  - If status not in active set ("active", "Active", "ACTIVE", "good standing", "Good Standing") → reject with "Company registry status is not active: <status>".
  - If active → proceed to KYB submission.
- Added CompanyRegistryProvider as a constructor dependency in MerchantOnboardingActivitiesImpl.

### Tests Added

- `shouldRejectWhenCompanyNotFound` — verifyCompanyRegistry returns "NOT_FOUND", startKyb never called.
- `shouldRejectWhenCompanyDissolved` — verifyCompanyRegistry returns "dissolved", rejection with status in message.
- `shouldProceedWhenCompanyActive` — verifyCompanyRegistry returns "active", full flow completes.
- All existing workflow tests updated to stub verifyCompanyRegistry("active").
- Tests organized into @Nested groups: CompanyRegistryVerification, KybVerification, ManualReview.

---

## Session 5 — Spotless Plugin

### Configuration

- Plugin: `com.diffplug.spotless:7.0.2` declared in root build.gradle.kts, applied to all subprojects.
- Lightweight hygiene only (no code formatter):
  - `removeUnusedImports()` — removes dead imports.
  - `importOrder("", "java|javax", "\\#")` — consistent import ordering.
  - `trimTrailingWhitespace()` — removes trailing spaces.
  - `endWithNewline()` — ensures files end with newline.

### Why No Java Formatter

Google Java Format 1.25.2 and Palantir Java Format both crash on Java 25 with `NoSuchMethodError` on `com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()`. Eclipse JDT works but its formatting is too aggressive (crammed record parameters onto single lines). The lightweight config preserves the existing code style.

### Commands

```bash
./gradlew spotlessCheck    # CI gate — fails if violations found
./gradlew spotlessApply    # Auto-fix violations
./gradlew build            # spotlessCheck runs automatically via check task
```

---

## Final Test Counts

| Suite | Count | What |
|-------|-------|------|
| Unit tests | 80 | Domain (merchant, state machine, risk tier, activation policy), service, controller, webhook validator, adapters, Temporal workflow (7 tests) |
| Integration tests | 23 | MerchantController IT (8), MerchantRepository IT (10), ApprovedCorridor IT (3), Outbox IT (3 including Kafka E2E relay) |
| Business tests | 1 | Full lifecycle: apply → kyb start → suspend → reactivate → close |
| **Total** | **104** | |

### Test Infrastructure

- **PostgreSQL**: Testcontainers `postgres:16-alpine` singleton.
- **Kafka**: Testcontainers `confluentinc/cp-kafka:7.6.0` singleton.
- **Temporal**: Disabled in integration tests (`spring.temporal.workers.enabled: false`), WorkflowClient mocked via TestTemporalConfig. Workflow logic tested via `TestWorkflowExtension` in unit tests.
- **Security**: TestSecurityConfig permits all requests (overrides main SecurityConfig).

---

## Decision Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Mock adapter wiring | FallbackAdaptersConfig + @ConditionalOnMissingBean | @Profile on mocks is unreliable and pollutes production code |
| Webhook auth | HMAC-SHA256 (not JWT) | Industry standard; Onfido sends X-SHA2-Signature header |
| KYB orchestration | Temporal MerchantOnboardingWorkflow | Durable waits (7d KYB, 5d review), signal-based webhooks, automatic retries, built-in timeout + escalation |
| Kafka in tests | Testcontainers (not stream-test-binder) | Real Kafka; E2E test verifies full outbox → Kafka delivery |
| Event publishing | Two paths: outbox (non-KYB) + Temporal activities (KYB) | Incremental Temporal adoption; outbox removed when all events move to Temporal |
| Companies House | Pre-KYB step in workflow | Validates company exists and is active before paying for Onfido check |
| Java formatter | None (Spotless lightweight only) | Google/Palantir incompatible with Java 25; Eclipse too aggressive |
| D&B integration | WireMock stub | No free sandbox available |
