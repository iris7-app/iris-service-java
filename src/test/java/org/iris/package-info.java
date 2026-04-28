/**
 * Test source root — mirrors the structure of {@code src/main/java/org/iris}.
 *
 * <h2>Test-type conventions</h2>
 * <p>Two kinds of tests live here, identified by filename suffix:
 * <ul>
 *   <li><b>{@code *Test.java}</b> — <em>unit tests</em>. Run by Maven Surefire
 *       in the {@code test} phase. Fast (no Docker, no Spring context,
 *       single-responsibility mocks via Mockito).</li>
 *   <li><b>{@code *ITest.java}</b> — <em>integration tests</em>. Run by Maven
 *       Failsafe in the {@code integration-test} phase. Typically use
 *       {@code @SpringBootTest} + Testcontainers (Postgres, Kafka, Redis)
 *       for close-to-prod fidelity. Slower (30-60 s startup).</li>
 * </ul>
 *
 * <h2>Package parallelism</h2>
 * <p>Every sub-package here mirrors a main-code package 1:1
 * ({@code org.iris.customer} tests → {@code src/test/java/org/iris/customer}).
 * Don't cross-pollinate — test code for {@code customer} must NOT reach into
 * {@code observability} except through the public API of the target class.
 *
 * <h2>Shared utilities</h2>
 * <ul>
 *   <li>{@code IrisTestBase} — base class for integration tests, starts
 *       the shared Testcontainers network once per test JVM via {@code @TestInstance(PER_CLASS)}.</li>
 *   <li>{@link org.iris.config} — Spring test configuration overrides
 *       (e.g. disable retry, shorter timeouts).</li>
 * </ul>
 *
 * <h2>Coverage</h2>
 * <p>JaCoCo minimum: 70 % line coverage on the merged unit + IT report. Classes
 * excluded from coverage (infra wiring only, no business logic): see
 * {@code <jacoco.xmlReportPaths>} in {@code pom.xml}.
 */
package org.iris;
