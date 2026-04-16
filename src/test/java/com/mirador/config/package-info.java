/**
 * Test-only Spring configuration overrides.
 *
 * <p>Beans declared here replace production beans via {@code @TestConfiguration}
 * or {@code @Import}, typically to:
 * <ul>
 *   <li>Disable long retries / backoffs that would make tests slow.</li>
 *   <li>Stub external HTTP calls with deterministic responses.</li>
 *   <li>Short-circuit rate limiting for concurrent test runs.</li>
 * </ul>
 *
 * <p>Everything in this package is test-scope; it never runs in production.
 */
package com.mirador.config;
