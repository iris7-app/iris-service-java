/**
 * Observability — health checks, audit trail, tracing, quality reporting.
 *
 * <p>This package is the heaviest in the codebase because it implements a full
 * observability surface: every operational question ("is the DB up?", "who
 * did what when?", "what's the current test coverage?") has a dedicated
 * endpoint here. The Angular UI's Dashboard page is largely a visual
 * front-end for these endpoints.
 *
 * <h2>Health</h2>
 * <dl>
 *   <dt>{@link DatabaseReachabilityHealthIndicator}</dt>
 *   <dd>{@code /actuator/health/db} — a {@code SELECT 1} probe that doesn't
 *       log the failure stack (Spring's default one does). Used by the K8s
 *       readiness probe.</dd>
 *   <dt>{@link KafkaHealthIndicator} / {@link KeycloakHealthIndicator} / {@link OllamaHealthIndicator}</dt>
 *   <dd>Per-dependency health checks contributing to the composite
 *       {@code /actuator/health} response.</dd>
 * </dl>
 *
 * <h2>Audit</h2>
 * <dl>
 *   <dt>{@link AuditService}</dt>
 *   <dd>Persists an immutable log of security-relevant actions (login, logout,
 *       token refresh, admin mutations) to the {@code audit_event} table. Records
 *       include timestamp, actor, action, IP, user-agent, request-id, and outcome.</dd>
 *   <dt>{@link AuditController} / {@link AuditEventDto} / {@link AuditPage}</dt>
 *   <dd>Read-only endpoint exposing the audit log with cursor pagination,
 *       filter by actor/action/date range.</dd>
 * </dl>
 *
 * <h2>Tracing</h2>
 * <dl>
 *   <dt>{@link RequestIdFilter}</dt>
 *   <dd>Ensures every request has an {@code X-Request-Id} header (generated
 *       if absent), puts it into the MDC so every log line carries it, and
 *       returns it in the response for correlation.</dd>
 *   <dt>{@link TraceService} / {@link RequestContext}</dt>
 *   <dd>Propagates W3C Trace Context + Baggage across async boundaries.
 *       {@link RequestContext} wraps it in a {@code ScopedValue} (Java 25+)
 *       or {@code ThreadLocal} (Java ≤21 overlay).</dd>
 * </dl>
 *
 * <h2>Maintenance / Quality</h2>
 * <dl>
 *   <dt>{@link MaintenanceEndpoint}</dt>
 *   <dd>{@code /actuator/maintenance} — runs ad-hoc cleanup tasks (purge
 *       audit > N days, re-index DB). Behind ROLE_ADMIN.</dd>
 *   <dt>{@link QualityReportEndpoint}</dt>
 *   <dd>{@code /actuator/quality} — the biggest class in the package (~1800
 *       lines). Aggregates data from SonarCloud, GitLab pipelines, Maven
 *       test reports, JaCoCo, SpotBugs, PMD, Checkstyle, OWASP, dependency
 *       licences, and the current Git state into a single JSON payload
 *       consumed by the Angular Quality page.</dd>
 *   <dt>{@link TestReportInfoContributor}</dt>
 *   <dd>Surfaces the latest unit + integration test counts in
 *       {@code /actuator/info}.</dd>
 *   <dt>{@link ObservabilityConfig} / {@link PyroscopeConfig} / {@link OtelLogbackInstaller} / {@link StartupTimeTracker}</dt>
 *   <dd>Wiring: OTel SDK setup (if not already auto-configured), Pyroscope
 *       continuous profiler start, log shipping to Loki, startup-time metric.</dd>
 * </dl>
 */
package org.iris.observability;
