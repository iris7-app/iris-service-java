# Mirador Service — Claude Instructions

## Project overview

Spring Boot 4 + Java 25 backend API with full observability stack.

- **Main package:** `com.mirador`
- **Entry point:** `src/main/java/com/mirador/MiradorApplication.java`
- **Tests:** `src/test/java/com/mirador/`
- **Migrations:** `src/main/resources/db/migration/` (Flyway, V1–V7)
- **Config:** `src/main/resources/application.yml`

## Build — Maven profiles

| Command | Stack |
|---|---|
| `./mvnw verify` | SB4 + Java 25 (default) |
| `./mvnw verify -Dcompat` | SB4 + Java 21 |
| `./mvnw verify -Dcompat -Djava17` | SB4 + Java 17 |
| `./mvnw verify -Dsb3` | SB3 + Java 21 |
| `./mvnw verify -Dsb3 -Djava17` | SB3 + Java 17 |

Always run the default `./mvnw verify` after any change unless testing a specific compatibility profile. Do not ask for permission to run Maven.

## Git workflow

- Branch: `dev`. One commit per logical change.
- Push with `git push origin dev`.
- If an MR exists: `glab mr merge <id> --auto-merge --squash=false`.
- Never push directly to `main`.

## Known gotchas

- **`/mvn/jvm.config`** — never add comments (`#`), they break Maven.
- **Flyway** — migration versions must be unique. Never add `V_N` if `V_N` already exists.
- **Spring AI** — version pinned at `1.0.0-M6`. DO NOT upgrade to `1.0.0` GA: the Ollama starter was renamed (`spring-ai-ollama-spring-boot-starter` → `spring-ai-starter-model-ollama`) and the pom would need manual migration. See the comment in `pom.xml`.
- **Page serialisation** — `Page<T>` is returned as-is (flat JSON: `totalElements`, `totalPages` at root). Do NOT add `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` — it changes the JSON shape to nested (`page.totalElements`) which breaks both integration tests and the Angular `Page<T>` interface in `api.service.ts`. The warning is suppressed via `logback-spring.xml`.
- **SB3 overlay files** — files under `src/main/java-sb3-compat/` and `src/test/java-sb3-compat/` are compiled only when `-Dsb3` is active. Do not cover them in tests for the default (SB4) profile.

## Security architecture

Request lifecycle (filter order matters — do not reorder without understanding the impact):

```
Request
  → RateLimitingFilter         (Bucket4j, 100 req/min per IP)
  → RequestIdFilter            (generates X-Request-Id, uses ScopedValue for virtual threads)
  → IdempotencyFilter          (LRU cache, 10k entries, POST/PATCH only)
  → SecurityHeadersFilter      (CSP, HSTS, X-Frame-Options, etc.)
  → JwtAuthenticationFilter    (validates JWT, sets SecurityContext)
  → ApiKeyAuthenticationFilter (fallback for Prometheus scraper and admin tools)
  → Spring Security chain
```

## Test conventions

- Unit tests mock collaborators via Mockito; use `MockHttpServletRequest/Response` for filter tests.
- Integration tests (`*ITest.java`) use `@SpringBootTest` with Testcontainers or `@DataJpaTest`.
- Do NOT use `@MockBean` in integration tests where the real bean is available.
- JaCoCo merged report: unit (`jacoco.exec`) + IT (`jacoco-it.exec`) → `jacoco-merged.exec`. Current minimum: **70 %**.
- Excluded from coverage (infra wiring only, no business logic): `SecurityConfig`, `KeycloakConfig`, `KafkaConfig`, `WebSocketConfig`, `OpenApiConfig`, `ObservabilityConfig`, `QualityReportEndpoint`, `OtelLogbackInstaller`, `PyroscopeConfig`, `MiradorApplication`.

## Observability

- Distributed traces: OpenTelemetry → Tempo (LGTM container on port 3001).
- Metrics: Micrometer → Prometheus → Grafana.
- Logs: Logback → OTel log exporter → Loki. Trace ID is injected in every log line.
- Custom Micrometer meters registered in `CustomerController` constructor (pre-registered so Grafana shows data from first scrape, not first request).

## CVE status (last checked 2026-04-14)

| Dependency | Version | Remaining CVEs | Notes |
|---|---|---|---|
| Spring AI | 1.0.0-M6 | 5 (incl. 2 HIGH) | Cannot upgrade — artifact renamed in GA |
| protobuf-java | 4.34.1 | 1 (CVE-2026-0994) | NVD has no patched version yet |
| OTel SDK | latest BOM | several | Transitive, no override available |

Full details in `pom.xml` property comments and the cached report at `src/main/resources/META-INF/build-reports/dependency-check-report.json`.

## Docker / infrastructure

Two compose files — never merge them:
- `docker-compose.yml` — infra (PostgreSQL, Kafka, Redis, Ollama, Keycloak, admin tools)
- `docker-compose.observability.yml` — observability (Grafana, Prometheus, Tempo, Loki, Zipkin, Pyroscope)

Loki, Tempo, Grafana are already inside the LGTM container — do NOT add them as separate services.

## Code review checklist (run proactively after significant changes)

- [ ] Unused Java imports
- [ ] `@SuppressWarnings` — justified and minimal scope?
- [ ] New Flyway migration — unique version number?
- [ ] New timer/counter/gauge — pre-registered in constructor, not lazily?
- [ ] Exception handlers — nothing silently swallowed (empty `catch` blocks)?
- [ ] Javadoc on public methods with non-obvious parameters or return values
