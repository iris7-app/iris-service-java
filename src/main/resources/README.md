# `src/main/resources/` — Classpath resources packaged into the JAR

Files here are copied verbatim into the final application JAR and loaded at
runtime via the classpath (e.g. `ClassPathResource("application.yml")`).
Layout follows Spring Boot conventions so `spring-boot-maven-plugin` picks
everything up without extra configuration.

## Layout

| Entry                      | Role                                                                                                                                                    |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `application.yml`          | Main application configuration (profiles, datasource, Kafka, Redis, security, observability). Environment variables override every value — never hard-code secrets here. |
| `logback-spring.xml`       | Logback configuration: JSON encoder for production, pretty console for local dev, OpenTelemetry appender that ships logs to Loki via OTLP/HTTP.           |
| [`db/migration/`](db/migration/) | Flyway SQL migrations. Names follow `V<N>__<snake_case_description>.sql`. Migrations are applied at app startup; never edit a committed migration — always add a new one. |
| [`META-INF/build-reports/`](META-INF/build-reports/) | Pre-computed CI artifacts packaged into the JAR: dependency graph, license inventory, third-party notices, cached OWASP report. Served by `/actuator/quality` so the Angular UI can display them without a live scan. |

## Conventions

- **No per-environment config files** (`application-dev.yml`, etc.) — everything
  is controlled by env vars (`DB_HOST`, `JWT_SECRET`, `OTEL_*`). This makes the
  same JAR immutable across dev, staging, and prod.
- **Flyway versions are unique and monotonic.** A gap (e.g. V3 missing between
  V2 and V4) is acceptable; a reused version (two V3 files) breaks startup.
- **Binary assets belong in `public/` of the frontend**, not here — this
  directory is for things the JVM loads, not things the browser serves.
- **No secrets** — the fallback values for `JWT_SECRET`, `API_KEY`, etc. are
  demo-grade and documented as such in `application.yml`. Production values
  come from K8s Secrets wired to env vars in the Deployment.

## Related

- `pom.xml` — the `<resource>` block (implicit via Spring Boot) and the
  `maven-resources-plugin` executions that copy CI reports into
  `META-INF/build-reports/`.
- `com.mirador.observability.QualityReportEndpoint` — reads files from
  `META-INF/build-reports/` to build the `/actuator/quality` payload.
