/**
 * Customer domain — core business feature.
 *
 * <p>Owns everything related to the {@code customer} resource: REST endpoints
 * ({@link CustomerController}), persistence ({@link Customer} entity + its
 * Spring Data repository), DTOs for two API versions ({@link CustomerDto},
 * {@link CustomerDtoV2}), aggregation, batch import, and cursor-based pagination.
 *
 * <h2>Key classes</h2>
 * <dl>
 *   <dt>{@link CustomerController}</dt>
 *   <dd>HTTP endpoints for CRUD, bulk operations, and analytics. Versioned via the
 *       Spring Boot 4 {@code @GetMapping(version=...)} feature; in SB3 compat mode
 *       the same behaviour is achieved by an overlay in
 *       {@code src/main/java-overlays/sb3} that dispatches on an {@code X-API-Version}
 *       header.</dd>
 *   <dt>{@link AggregationService}</dt>
 *   <dd>Stats endpoints ({@code /customers/aggregate/*}) computing counts, averages,
 *       and histograms directly via projection queries — no in-memory materialization.</dd>
 *   <dt>{@link CursorPage}</dt>
 *   <dd>Opaque cursor pagination. The cursor encodes the last {@code id} of the
 *       previous page + the sort order, avoiding the classic offset/limit
 *       N-th-page problem where late pages scan entire tables.</dd>
 *   <dt>{@link BatchImportResult}</dt>
 *   <dd>Response payload for bulk POST operations — summarises created/updated/skipped
 *       entries with line-level error details.</dd>
 * </dl>
 *
 * <h2>Persistence</h2>
 * <p>PostgreSQL via Spring Data JPA. The schema is managed by Flyway migrations
 * under {@code src/main/resources/db/migration}. Do not modify the {@code Customer}
 * fields without a corresponding {@code V*.sql} file — Hibernate {@code ddl-auto}
 * is set to {@code validate}, so a mismatch fails fast at startup.
 *
 * <h2>Observability</h2>
 * <p>Every endpoint in {@link CustomerController} pre-registers Micrometer counters
 * and timers in its constructor so Grafana dashboards render from the first scrape,
 * not the first request.
 */
package org.iris.customer;
