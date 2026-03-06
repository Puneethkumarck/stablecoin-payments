# Contributing to stablebridge-platform

Thank you for your interest in contributing. This document covers everything you need to get the project running locally, understand the development workflow, and submit high-quality pull requests.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Local Setup](#local-setup)
- [Project Structure](#project-structure)
- [Branch Naming Convention](#branch-naming-convention)
- [Development Workflow](#development-workflow)
- [Running Tests](#running-tests)
- [Code Style](#code-style)
- [Commit Message Convention](#commit-message-convention)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Reporting Issues](#reporting-issues)
- [Security Vulnerabilities](#security-vulnerabilities)

---

## Prerequisites

Ensure you have the following installed:

| Tool         | Version  | Notes                                  |
|--------------|----------|----------------------------------------|
| JDK          | 21+      | Temurin recommended                    |
| Docker       | 24+      | Required for local infra and integration tests |
| Docker Compose | v2+    | Bundled with Docker Desktop            |
| Git          | Any      |                                        |

> **Note:** The Gradle wrapper (`./gradlew`) is checked in — you do **not** need to install Gradle separately.

---

## Local Setup

```bash
# 1. Clone the repository
git clone https://github.com/Puneethkumarck/stablebridge-platform.git
cd stablebridge-platform

# 2. Start local infrastructure (Postgres, Temporal, etc.)
docker compose -f docker-compose.dev.yml up -d

# 3. Build the project
./gradlew build -x test

# 4. Run the merchant-onboarding service
./gradlew :merchant-onboarding:merchant-onboarding:bootRun
```

The service will start on `http://localhost:8080` by default.

---

## Project Structure

```
stablebridge-platform/
├── merchant-onboarding/      # Merchant onboarding service (KYB, onboarding workflows)
├── merchant-iam/             # Identity and access management service
├── infra/local/              # Local infrastructure configuration (Docker, DB migrations)
├── gradle/                   # Gradle wrapper files
├── .github/
│   ├── workflows/            # CI/CD GitHub Actions
│   ├── ISSUE_TEMPLATE/       # Bug and feature request templates
│   ├── CODEOWNERS            # Auto-assign reviewers by path
│   └── pull_request_template.md
├── CHANGELOG.md
├── SECURITY.md
└── build.gradle.kts          # Root Gradle build file
```

---

## Branch Naming Convention

All branches must follow this pattern:

```
<type>/<ticket-id>-<short-description>
```

| Type      | When to use                                        |
|-----------|----------------------------------------------------|
| `feature` | New functionality                                  |
| `fix`     | Bug fixes                                          |
| `chore`   | Dependency updates, tooling, config                |
| `refactor`| Code restructuring with no functional change       |
| `docs`    | Documentation only                                 |
| `test`    | Adding or fixing tests only                        |

**Examples:**

```
feature/STA-42-merchant-kyb-webhook
fix/STA-18-duplicate-onboarding-idempotency
chore/STA-55-upgrade-spring-boot-4
```

---

## Development Workflow

1. **Pick or create an issue** — all work should be tracked in GitHub Issues.
2. **Create a branch** from `main` using the naming convention above.
3. **Implement your change** with tests.
4. **Run the full quality suite locally** (see below) before pushing.
5. **Open a PR** against `main` using the PR template.
6. **Address review feedback** — keep commits clean and focused.
7. **Squash merge** (preferred) to keep `main` history linear.

---

## Running Tests

```bash
# Unit tests + JaCoCo coverage report
./gradlew :merchant-onboarding:merchant-onboarding:test \
          :merchant-onboarding:merchant-onboarding:jacocoTestReport

# Integration tests (requires Docker for Testcontainers)
./gradlew :merchant-onboarding:merchant-onboarding:integrationTest

# Business / acceptance tests
./gradlew :merchant-onboarding:merchant-onboarding:businessTest

# Run all tests in one go
./gradlew test integrationTest businessTest
```

> Integration tests use **Testcontainers** and spin up a real Postgres instance — make sure Docker is running.

---

## Code Style

This project enforces code style automatically using **Spotless**.

```bash
# Check for style violations
./gradlew spotlessCheck

# Auto-fix style violations
./gradlew spotlessApply
```

Key conventions enforced:

- No wildcard imports (`import com.example.*`)
- Consistent formatting via `ktlint` (Kotlin) / `google-java-format` (Java)
- Trailing newlines on all files

Run `spotlessCheck` locally before pushing — CI will fail if it is not clean.

---

## Commit Message Convention

We follow **Conventional Commits**:

```
<type>(<scope>): <short summary>

[optional body]

[optional footer — e.g. Closes #42]
```

**Types:** `feat`, `fix`, `chore`, `refactor`, `docs`, `test`, `build`, `ci`

**Scope:** the affected service or module (e.g. `merchant-onboarding`, `merchant-iam`, `ci`)

**Examples:**

```
feat(merchant-onboarding): add KYB document upload endpoint

fix(merchant-iam): resolve JWT token expiry not propagating correctly

chore(deps): upgrade Spring Boot to 4.0.3

Closes #27
```

---

## Submitting a Pull Request

1. Ensure all tests pass locally and CI is green.
2. Fill in the PR template completely — especially the security considerations section for any change touching payment or IAM flows.
3. Link the PR to the relevant issue (`Closes #<number>`).
4. Keep PRs focused — one logical change per PR. Large PRs are harder to review safely for a financial system.
5. Update `CHANGELOG.md` for any user-facing change.

---

## Reporting Issues

Use the GitHub Issue templates:

- **Bug report** — for defects and unexpected behaviour
- **Feature request** — for new capabilities or improvements

Please search existing issues before opening a new one.

---

## Security Vulnerabilities

**Do not open a public issue for security vulnerabilities.**

Please follow the process in [SECURITY.md](./SECURITY.md) to report vulnerabilities privately. This is especially important given the financial nature of this platform.
