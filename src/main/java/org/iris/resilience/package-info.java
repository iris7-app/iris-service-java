/**
 * Reliability primitives — idempotency, rate limiting, distributed scheduling.
 *
 * <p>These are low-level cross-cutting concerns that make the service safe to
 * run at scale behind a load balancer.
 *
 * <dl>
 *   <dt>{@link IdempotencyFilter}</dt>
 *   <dd>Caches successful {@code POST} / {@code PATCH} responses keyed by
 *       the {@code Idempotency-Key} header (RFC draft, widely used by Stripe
 *       etc.). A retry of a previously successful mutation returns the cached
 *       response instead of running the mutation again. Bounded cache
 *       (~10k entries) with LRU eviction to avoid memory leaks.</dd>
 *
 *   <dt>{@link RateLimitingFilter}</dt>
 *   <dd>Per-IP token bucket (Bucket4j). Defaults: 100 req/min. The client IP
 *       is resolved from {@code X-Forwarded-For} when behind a proxy, with
 *       format validation (IPv4/IPv6 regex) to prevent spoofing from
 *       creating unbounded buckets. Total bucket map capped at 50k entries.
 *       Returns HTTP 429 with {@code Retry-After} and {@code X-Rate-Limit-Remaining}
 *       headers so clients can back off gracefully.</dd>
 *
 *   <dt>{@link ShedLockConfig}</dt>
 *   <dd>Wires net.javacrumbs.shedlock for distributed scheduled-job locking.
 *       Uses the PostgreSQL lock table so running multiple app replicas
 *       behind a load balancer never fires the same {@code @Scheduled} job
 *       twice concurrently.</dd>
 *
 *   <dt>{@link ScheduledJobController} / {@link ScheduledJobDto}</dt>
 *   <dd>Read-only admin endpoints exposing the current state of all scheduled
 *       jobs (last run, next run, lock holder) for observability dashboards.</dd>
 * </dl>
 *
 * <h2>Filter chain order</h2>
 * <p>The two filters run BEFORE authentication so malicious traffic never
 * reaches the auth layer. Order defined in {@code SecurityConfig} via
 * {@code @Order}:
 * <pre>
 *   RateLimitingFilter  (order 10)    — throttle first
 *   IdempotencyFilter   (order 20)    — dedupe retries
 *   ApiKeyAuthenticationFilter (30)   — auth
 *   JwtAuthenticationFilter (40)      — auth
 * </pre>
 */
package org.iris.resilience;
