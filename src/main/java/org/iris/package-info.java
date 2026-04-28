/**
 * Iris Service — Spring Boot backend root package.
 *
 * <p>The application is split into feature-oriented sub-packages rather than
 * layered ones (controllers/services/repositories). Each sub-package owns an
 * entire slice of functionality, from HTTP endpoint to persistence to
 * messaging, so that cross-cutting changes (e.g. adding a field to a domain
 * object) happen in one folder.
 *
 * <h2>Sub-packages</h2>
 * <dl>
 *   <dt>{@link org.iris.customer}</dt>
 *   <dd>Core domain — Customer CRUD, aggregation, batch import, cursor pagination. Entry point of most user traffic.</dd>
 *
 *   <dt>{@link org.iris.auth}</dt>
 *   <dd>Authentication and authorization: local users, JWT issuance + validation, Auth0/Keycloak OIDC bridge, API-key filter.</dd>
 *
 *   <dt>{@link org.iris.api}</dt>
 *   <dd>HTTP cross-cutting concerns: Problem+JSON error responses, OpenAPI config.</dd>
 *
 *   <dt>{@link org.iris.messaging}</dt>
 *   <dd>Kafka producers/consumers + WebSocket broadcast for live customer-event streaming.</dd>
 *
 *   <dt>{@link org.iris.integration}</dt>
 *   <dd>Outbound HTTP calls to external services (JSONPlaceholder for demo enrichment).</dd>
 *
 *   <dt>{@link org.iris.resilience}</dt>
 *   <dd>Reliability primitives: idempotency filter, rate limiter, distributed scheduled jobs (ShedLock).</dd>
 *
 *   <dt>{@link org.iris.observability}</dt>
 *   <dd>Health checks, audit log, request tracing, maintenance endpoints, quality-report endpoint.</dd>
 * </dl>
 *
 * <h2>Architecture principles</h2>
 * <ul>
 *   <li><b>Feature-sliced packages</b> — changes stay local.</li>
 *   <li><b>Constructor injection only</b> — no field injection, no setter injection.</li>
 *   <li><b>No Lombok for business classes</b> — records and explicit accessors preferred.</li>
 *   <li><b>Problem+JSON for errors</b> — structured error responses via {@link org.iris.api.ApiExceptionHandler}.</li>
 *   <li><b>Everything observable</b> — every external call produces a metric + trace span.</li>
 * </ul>
 *
 * @see org.iris.IrisApplication
 */
package org.iris;
