/**
 * HTTP cross-cutting concerns — error handling and API documentation.
 *
 * <p>This package holds infrastructure that every controller depends on but
 * shouldn't itself implement:
 *
 * <dl>
 *   <dt>{@link ApiExceptionHandler}</dt>
 *   <dd>Global {@code @RestControllerAdvice} that converts every exception into
 *       a {@link org.springframework.http.ProblemDetail} (RFC 7807 / RFC 9457).
 *       All errors carry a stable {@code type} URI, a human-readable {@code title},
 *       an HTTP {@code status}, and optional {@code detail}. Clients match on
 *       the {@code type} for programmatic handling — never parse {@code detail}.</dd>
 *
 *   <dt>{@link ApiError}</dt>
 *   <dd>Stable catalog of error type URIs used by {@link ApiExceptionHandler}.
 *       Adding a new error means adding an entry here first, then throwing
 *       the appropriate exception in the domain code.</dd>
 *
 *   <dt>{@link OpenApiConfig}</dt>
 *   <dd>springdoc-openapi configuration: API title/description/version, security
 *       schemes (Bearer JWT + X-API-Key), tags, and external links to
 *       {@code /actuator/quality} and the Grafana dashboards.</dd>
 * </dl>
 *
 * <h2>Why Problem+JSON</h2>
 * <p>Spring's default error response is a stack trace behind a flat
 * {@code {"timestamp","status","error","message","path"}} body — untyped,
 * unpaginated, unusable by a client beyond showing a toast. Problem+JSON gives:
 * <ul>
 *   <li><b>Stable type URIs</b> → clients match on {@code about:blank#customer-duplicate}
 *       instead of parsing English.</li>
 *   <li><b>Consistent shape</b> → every error is a {@code ProblemDetail}; no
 *       special-casing per endpoint.</li>
 *   <li><b>Extensibility</b> → custom fields (e.g. {@code fieldErrors} on a 400)
 *       live alongside standard ones without breaking the contract.</li>
 * </ul>
 */
package org.iris.api;
