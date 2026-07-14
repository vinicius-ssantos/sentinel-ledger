# Security Policy

## Scope

Sentinel Ledger is an educational portfolio project and does not process real payments. Never submit real cardholder data, credentials, secrets, access tokens, production URLs, or personally identifiable information to the repository or demo.

## Reporting a vulnerability

Do not disclose a suspected vulnerability in a public issue. Use GitHub's private vulnerability reporting feature when available, or contact the repository owner privately through the contact method listed on the owner's GitHub profile.

Include:

- affected component and revision;
- reproduction steps or proof of concept;
- expected and observed impact;
- suggested mitigation, if known;
- whether any secret or personal data may have been exposed.

## Response expectations

Reports will be acknowledged as capacity permits. Confirmed issues will be tracked privately until a fix or mitigation is available. No guaranteed response or remediation SLA is offered for this non-production project.

## Security principles

- no PAN, CVV, real payment token, or production PSP credential;
- authenticated merchant context rather than trusted client headers;
- least-privilege operator authorization;
- persistent audit evidence for sensitive actions;
- secret scanning and dependency review in CI when implementation begins;
- signed webhooks with timestamp and replay validation in the reliability phase;
- safe, generic authentication errors;
- no secret values in logs, traces, metrics, fixtures, or screenshots.

## Supported versions

Only the default branch is considered current during active development. A formal supported-version table will be added after the first tagged release.
