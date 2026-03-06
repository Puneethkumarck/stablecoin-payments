# Security Policy

## Supported Versions

We are actively maintaining the following versions of `stablebridge-platform`:

| Version | Supported          |
|---------|--------------------|
| main    | ✅ Yes             |
| < 1.0   | ❌ No              |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

`stablebridge-platform` handles financial transactions and merchant identity data. We take all security disclosures seriously and ask that you follow responsible disclosure practices.

### How to Report

1. **Use GitHub's Private Vulnerability Reporting** (preferred):
   Go to [Security Advisories](../../security/advisories/new) and submit a private report. This keeps the details confidential until a fix is ready.

2. **Email fallback**: If you are unable to use GitHub's reporting tool, email **punith.564@gmail.com** with:
   - Subject: `[SECURITY] stablebridge-platform - <brief description>`
   - A clear description of the vulnerability
   - Steps to reproduce
   - Potential impact assessment
   - Any suggested mitigations (optional)

### What to Include

Please provide as much of the following as possible:

- Type of vulnerability (e.g. SQL injection, IDOR, auth bypass, data leak)
- Affected component or service (`merchant-onboarding`, `merchant-iam`, etc.)
- Full path to the vulnerable source file(s)
- Steps to reproduce the issue
- Proof-of-concept or exploit code (if applicable)
- Impact — what data or functionality is at risk

### What to Expect

| Milestone                        | Target Timeframe |
|----------------------------------|------------------|
| Acknowledgement of report        | Within 48 hours  |
| Initial assessment and triage    | Within 5 days    |
| Fix developed and tested         | Within 30 days   |
| Public disclosure (coordinated)  | After fix is deployed |

We will keep you informed throughout the process and credit you in the security advisory (unless you prefer to remain anonymous).

## Scope

### In Scope

- Authentication and authorisation flaws in merchant onboarding or IAM flows
- Injection vulnerabilities (SQL, JNDI, expression language)
- Sensitive data exposure (KYB data, merchant PII, payment data)
- Business logic vulnerabilities in FX, on/off-ramp, or payment processing
- Insecure direct object references (IDOR)
- Misconfigured secrets or credentials in code

### Out of Scope

- Vulnerabilities in third-party dependencies (report those upstream; we use Renovate to track them)
- Issues that require physical access to infrastructure
- Social engineering attacks
- Denial of service (DoS/DDoS) attacks
- Issues in demo or sandbox environments with no real data

## Security Measures

This project uses the following security controls:

- **SonarCloud** — continuous code quality and security scanning on every PR
- **CodeQL** — GitHub-native static analysis for vulnerability detection
- **Renovate** — automated dependency updates with security patching
- **Dependabot Alerts** — vulnerability notifications for known CVEs
- **Branch protection** — all changes to `main` require PR review and passing CI
- **Secret scanning** — GitHub-native detection of accidentally committed secrets

## Disclosure Policy

We follow a **coordinated disclosure** model. We ask that you:

- Give us reasonable time to patch before public disclosure
- Avoid accessing, modifying, or deleting data that isn't yours
- Do not perform actions that could degrade service availability

We commit to not pursuing legal action against researchers who act in good faith and follow this policy.
