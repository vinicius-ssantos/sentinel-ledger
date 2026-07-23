# Observability

This document describes what is implemented and measured today. Distributed tracing (OpenTelemetry spans connecting a request to its asynchronous recovery) is deferred to a follow-up issue; nothing below claims a trace exists. No latency or throughput number is published anywhere in this repository, and none should be added without a reproducible benchmark (see [ROADMAP.md](ROADMAP.md) Phase 4).

## Correlation

Every HTTP request carries an `X-Correlation-Id` header. `observability.internal.CorrelationIdFilter` reads the caller's own value if present, otherwise generates one, echoes it back on the response, and places it in the SLF4J MDC (`correlationId`) for the request's lifetime — including for a request Spring Security rejects before it reaches a controller, since the filter runs ahead of the security filter chain. A scheduled worker or queue consumer (the reconciliation sweep, the outbox dispatcher, the messaging consumer) has no inbound HTTP request to correlate against; investigating one of those follows the resource identifier already present in its own evidence trail (the payment intent id in the audit log and timeline, the outbox/webhook delivery id) rather than a correlation id.

## Structured logs

Console logging is JSON (`logging.structured.format.console=logstash` by default, overridable with `SENTINEL_LOG_FORMAT`), so every field already on the log record — including the MDC's `correlationId` — appears as a JSON key rather than free text that needs parsing. Log statements in this codebase only ever include identifiers, enum names, and short technical error messages (see the call sites in `outbox.internal`, `integration.messaging.internal`, and `webhooks.internal`); no log statement formats a full request/response body, a credential, or an HTTP `Authorization` header value. `StructuredLoggingRedactionTests` (in the `observability` module's test tree) is an automated, executable assertion of this: it captures every log line written during a real authenticated request and fails if the merchant's API key secret or a raw `Authorization: Basic` value appears in any of them.

## Business metrics

Exposed via Micrometer at `/actuator/prometheus` (Prometheus text format), unauthenticated like `/actuator/health`. Every metric below is tagged only by a fixed, small-cardinality value — an enum name, a boolean-like outcome, or a severity/action label — never a payment, merchant, operator, case, or delivery identifier. `MetricCardinalitySafetyTests` enforces this structurally: it drives one representative flow through every instrumented code path and then asserts no `sentinel.*` meter carries a tag key that looks like an identifier or a tag value shaped like a UUID.

| Metric | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `sentinel.payments.authorization.result` | Counter | `outcome` (`AUTHORIZED`\|`DECLINED`\|`AUTHORIZATION_UNKNOWN`\|`AUTHORIZATION_FAILED`) | Authorization attempts resolved. `AUTHORIZATION_UNKNOWN` covers both a provider timeout and an ambiguous provider response — this codebase does not distinguish "timeout" from other transport uncertainty as a separate tag value, since both are represented identically by `PspAuthorizationResult.Unknown`/`RetryableFailure` (see [FAILURE_MODEL.md](FAILURE_MODEL.md)). |
| `sentinel.payments.authorization.recovery` | Counter | `outcome` | Uncertain authorizations resolved through reconciliation (on-demand check or the stale-authorization sweep) rather than a client retry. |
| `sentinel.payments.capture.result` | Counter | `outcome` (`SUCCESS`\|`DENIED`), `reason` (a `PaymentIntentErrorCode` name, empty on success) | Capture requests resolved. |
| `sentinel.payments.refund.result` | Counter | `outcome`, `reason` | Refund requests resolved, same shape as capture. |
| `sentinel.reconciliation.cases.opened` | Counter | `severity` (`LOW`\|`HIGH`) | Newly opened reconciliation cases (a fingerprint-deduplicated re-detection does not increment this). |
| `sentinel.reconciliation.cases.resolved` | Counter | `action` (`ACKNOWLEDGE_NO_ACTION`\|`COMPENSATE`\|`IGNORE`) | Reconciliation case resolutions, by operator action. |
| `sentinel.reconciliation.cases.open` | Gauge | `severity` | Cases currently `OPEN` or `INVESTIGATING`, re-queried on every scrape. |
| `sentinel.reconciliation.case.age` | Timer | `action` | Time between a case's detection and its resolution. |
| `sentinel.outbox.publish.result` | Counter | `result` (`SUCCESS`\|`FAILURE`), `eventType` (a fixed outbox event type such as `payment.captured`) | Outbox dispatch attempts. |
| `sentinel.messaging.dead_letter_queue.depth` | Gauge | — | Messages currently in the dead-letter queue. `0` (and only `0`) when `sentinel.messaging.enabled=false`, since there is no broker to ask. |
| `sentinel.messaging.dispatch.duplicate` | Counter | — | Redeliveries of an outbox message whose webhook delivery had already succeeded. |
| `sentinel.webhooks.delivery.result` | Counter | `result` (`DELIVERED`\|`FAILED`) | Webhook HTTP delivery attempts. |

## Reconciliation dashboard (dashboard-as-code)

`docs/observability/reconciliation-dashboard.json` is a Grafana dashboard definition checked into the repository, not a running dashboard — there is no Grafana instance in `compose.yaml`. It is provided so the panel definitions (case count, severity split, age distribution, resolution outcome) are reviewable and importable into a real Grafana pointed at this application's `/actuator/prometheus` endpoint, when one exists.

## Alerts

`docs/observability/alerts.yml` is a Prometheus alerting-rule file, also not deployed (no Alertmanager runs today). Each rule's `annotations.description` names a concrete operator action or investigation path — for example, "check `/actuator/prometheus` for `sentinel_reconciliation_cases_open{severity="HIGH"}` and review the oldest case in the reconciliation API" — rather than only naming the symptom, so the acceptance bar ("every alert names an operator action") is met by the artifact's content even before it has somewhere to run.

## Distinguishing outcome classes

The acceptance bar "business decline, technical failure, uncertain outcome, and invariant conflict are distinguishable" maps onto existing, already-tested types rather than a new taxonomy:

- **Business decline**: `PaymentIntentState.DECLINED`, `sentinel.payments.authorization.result{outcome="DECLINED"}`.
- **Technical failure**: `PspAuthorizationResult.PermanentFailure` / `PaymentIntentState.AUTHORIZATION_FAILED`.
- **Uncertain outcome**: `PspAuthorizationResult.Unknown` / `RetryableFailure`, `PaymentIntentState.AUTHORIZATION_UNKNOWN`, `sentinel.payments.authorization.result{outcome="AUTHORIZATION_UNKNOWN"}`, later resolved and counted again via `sentinel.payments.authorization.recovery`.
- **Invariant conflict**: a denied capture/refund (`CAPTURE_EXCEEDS_AUTHORIZED_AMOUNT`, `REFUND_EXCEEDS_CAPTURED_AMOUNT`, `CONCURRENT_PAYMENT_MODIFICATION`), each its own `reason` tag value on `sentinel.payments.capture.result` / `refund.result`, and a `PROBLEM_DETAIL` `code` in the API response.

## Cardinality and retention policy

- **Cardinality**: every tag value above comes from a fixed enum, a boolean-like outcome, or a short label — never a UUID, a free-text field, or anything whose distinct-value count grows with traffic. `MetricCardinalitySafetyTests` is the executable form of this rule.
- **Log volume and retention**: not yet operated. This repository runs locally and in CI only; log shipping, indexing, and retention are deployment-environment decisions with no reproducible measurement here, so none is claimed.
- **Metric retention**: `/actuator/prometheus` exposes current values only; retention is whatever a scraping Prometheus (not part of this repository) is configured to keep.
