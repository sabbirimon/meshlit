# Audit telemetry, OpenTelemetry and Grafana
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Meshlit provides opt-in encrypted local audit metadata and optional OTLP/HTTP
traces/metrics. Open Settings → Audit and telemetry. Both audit collection and
tracing default off. Enabling local collection does not select a remote vendor.

## Collection coverage

| Source | Actual observations | Limits |
| --- | --- | --- |
| Inference | Model load/unload, generation start/result, backend duration and reported token usage | Unknown token counts are absent; suppressed/private browser inference events are not copied into the audit feed |
| Models | Library phase transitions, actual transferred/total bytes | Observed StateFlow transitions can coalesce; journal is not a byte-by-byte download ledger |
| Human/agent backend | Typed operation start/success/failure/denial/cancellation, actor, duration, hashed request ID; delegation changes | Direct legacy tool paths do not all pass through this facade; partial side effects remain possible on failure |
| Tasks | Actual task create/revision/delete observations | Planning state does not prove task execution; direct changes are attributed to SYSTEM unless made through an audited typed command |
| Cluster | Worker/coordinator/starting/error transitions | No remote unpaired device telemetry, distributed consensus or physical phone-sharding proof |
| Device | Android RAM, app heap, free storage, battery/thermal, UID network totals, actual active transfers/jobs/task counts | 15–300s process-lifetime samples, no background service; unknown sensors omitted; network totals may reset and are not per-request billing |
| Logs | Source/severity event metadata from the app LogBuffer | Message, context and error text are not ingested; core SLF4J and vendor SDK paths outside the buffer are not complete coverage |

The closed audit schema excludes prompts/replies, arguments, credentials, paths,
URLs and free-form error messages. Object identifiers use SHA-256 pseudonyms;
they can still correlate activity and are not anonymization. Each installation's
OpenTelemetry resource has a random persisted `service.instance.id` for node
comparison without exporting phone names or hardware serial numbers. The exporter
also strips arbitrary span attributes, exception events, links and status text.
Collector authentication headers use Android Keystore-backed encrypted preferences;
Headers are bound to the exact configured endpoint and are not reused after an endpoint change; save replacement headers for the new endpoint. Legacy plaintext tracing headers are migrated and removed from DataStore.

## Local history and exports

Collection retains 100–5,000 records for 1–90 days (UI presets). Defaults: 2,000
records, seven days, 30-second device sampling. An encrypted preferences journal
is excluded from automatic backup. It commits bounded batches on IO and reports
storage failures plus dropped/failed records for the current process. A 512-event
queue bounds memory; older retention records are evicted. This is best-effort
observability, **not a transactionally complete, tamper-proof compliance ledger**.
A crash can lose the queue or a partial batch. App uninstall removes local keys.

The [audit-schema.json](observability/audit-schema.json) describes exported JSONL metadata records.
Search action/hashed ID; filter source, actor, outcome and retained/hour/day time.
The newest 200 matches render; JSONL or CSV export includes the entire matching
snapshot via Android's document picker. Exports are plaintext metadata and need
appropriate destination protection. Deleting local history does not delete
previous exports or collector data. Retention is enforced on startup/configuration
and subsequent writes; no always-on purge is promised when the process is stopped.

## Collector setup

1. Enable Local audit collection for the durable dashboard.
2. Set an **HTTPS OTLP base URL**, not an individual signal endpoint. Example:
   `https://collector.example/otlp`. The app adds `/v1/traces` and `/v1/metrics`.
   URL userinfo/query/fragments are rejected. HTTP is restricted to loopback;
   use an authenticated HTTPS proxy for LAN or internet collectors.
3. Save optional replacement headers (`Authorization=...`, one per line). Values
   never appear in exports. Configure least-privilege ingestion credentials.
4. Choose OTLP collector tracing. Local tracing has no collector; Off creates no
   Meshlit OTel export. Audit collection and tracing mode are separate controls.
5. Existing gRPC collector settings need an OTLP/HTTP base URL after this update.
6. Use Flush pending telemetry. Trace acknowledgment means ingestion at that HTTP
   endpoint, not proof of storage or a rendered Grafana dashboard. Metric flush
   failure is surfaced by the flush result; the live status counter counts trace
   batches only. Disabled/reconfigured exporters close their previous SDK; an in-flight batch or shutdown flush can finish. Collection disable stops newly queued work, and an already committing batch can finish.

[collector.yaml](observability/collector.yaml) is a host-side configuration example
using environment-provided backend URL/auth. It has no embedded keys, installation
or automatic deployment. Bind ingress privately and configure TLS/auth/rate limits
in your own reverse proxy. Use Grafana Cloud's connection-provided OTLP base URL,
Grafana Alloy/OpenTelemetry Collector, self-hosted Tempo and a compatible metrics
backend, or another standards-compliant OTLP destination.

Current exports: metadata audit **spans** plus event counter, operation-duration
histogram and device gauges. Local JSONL/CSV are audit records, not OTLP wire JSON.
OTLP logs/Loki ingestion, alert delivery, signed audit anchors, durable remote
retry/outbox, per-request costs, fleet query APIs and complete adapter coverage
remain follow-up work. No hosted Grafana account or remote dashboard was exercised
without operator credentials. Metric labels are low-cardinality source/outcome;
record IDs and model names are not metric labels.

## Dashboard and alerts

Import [grafana-dashboard.json](observability/grafana-dashboard.json), choose your
Prometheus-compatible metrics datasource; use Tempo Explore separately for traces. Panels query real
OTel metrics; missing data remains empty. Exporter/collector translation can change
metric names, so verify names in Explore. Start with audit event rate, failure/denial
rate, operation duration, RAM availability, battery, thermal state and active jobs.
Use the random installation resource label to group devices if your collector
promotes resource attributes. Define alert thresholds on the collector/backend;
unknown values are not zero. The app does not deploy or send alerts itself.

## Vendor SDK limitation

RunAnywhere SDK development mode attempted its own development telemetry URL in
an earlier real generation. DNS failed; no successful payload/transmission was
established. Meshlit's audit switches control Meshlit's OTel subsystem. They do
not establish a vendor SDK opt-out. See [remaining-engine-bugs.md](remaining-engine-bugs.md).
Do not advertise zero-network SDK behavior without supported opt-out evidence.

## References

- [OTLP protocol and HTTP signal paths](https://opentelemetry.io/docs/specs/otlp/)
- [OTLP exporter configuration](https://opentelemetry.io/docs/languages/sdk-configuration/otlp-exporter/)
- [Grafana OpenTelemetry Collector setup](https://grafana.com/docs/opentelemetry/collector/opentelemetry-collector/)
