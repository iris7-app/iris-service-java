# ADR-0052 — Backend stays ignorant of build/quality tools (tightens ADR-0026)

- Status: Accepted
- Date: 2026-04-22
- Deciders: @benoit.besson
- Related: [ADR-0026](0026-spring-boot-scope-limit-no-third-party-tool-awareness.md)
  (the "Spring Boot scope" ADR this tightens)

## Context

ADR-0026 (2026-04-18) set the rule: "Spring Boot is not aware of
third-party tools". It explicitly allowed `/actuator/quality` as part
of the "self-admin surface".

Over the 4 sessions since, that one allowance grew into a category
violation of the ADR it was written under. As of 2026-04-22 the
`QualityReportEndpoint` (1179 LOC even after the Phase B-1 split) is
coupled to:

| Coupling class | What the backend does | Violation severity |
|---|---|---|
| **File-based tool outputs** | Parses Surefire XML, JaCoCo CSV, SpotBugs XML, PMD XML, Checkstyle XML, OWASP JSON, PIT XML at runtime | Medium — backend knows each tool's output shape |
| **External REST calls** | `GET https://sonarcloud.io/api/measures/component` + `GET https://gitlab.com/api/v4/projects/:id/pipelines` at every `/actuator/quality` poll | **High** — prod runtime has Sonar/GitLab tokens in env, makes outbound HTTPS to third parties to serve a dashboard |
| **Build layout knowledge** | Hard-codes `target/` + `META-INF/build-reports/` paths, knows that JaCoCo merged report lives under `target/site/jacoco-merged/` | Medium |
| **Config surface** | `@Value("${sonar.host.url}")`, `@Value("${gitlab.host.url}")`, `@Value("${gitlab.api.token}")` on the app wiring | High — secret surface |

The audit at
[docs/audit/clean-code-architecture-2026-04-22.md](../audit/clean-code-architecture-2026-04-22.md)
explicitly flagged the endpoint as an outlier; the 2026-04-22 Phase B-1
split extracted the parsers into `org.iris.observability.quality
.{parsers,providers}` but kept the runtime coupling. The user called
this out: "QualityReportEndpoint ne respecte pas le principe que le
backend ne devrait pas être lié avec les outils d'infrastructure".

## Decision

**Partition `/actuator/quality` by data-source nature.**

The endpoint keeps ONLY the sections whose source is genuinely the
running JVM / local git / Spring bean graph. Everything else moves
out of the runtime path.

### Three buckets

**Bucket 1 — Removed from backend entirely**

- `sonar` section — hits the SonarCloud REST API from the prod JVM.
  Removed. UI dashboard replaces the Sonar panel with a **link** to
  the public SonarCloud project page
  ([sonarcloud.io/project/overview?id=Iris_iris-service](https://sonarcloud.io/project/overview?id=Iris_iris-service)).
  Users who want the numbers see the single source of truth.
- `pipeline` section — hits the GitLab REST API. Removed. UI
  dashboard replaces it with a link to the GitLab pipelines page
  (`https://gitlab.com/iris-7/iris-service/-/pipelines`).
- `@Value` entries `sonar.host.url`, `sonar.projectKey`,
  `sonar.token`, `gitlab.host.url`, `gitlab.project.id`,
  `gitlab.api.token` removed — the prod JVM no longer needs these
  secrets. One less env-var surface to rotate.
- `java.net.http.HttpClient` import removed from the endpoint.

**Bucket 2 — Moved to build-time JSON (Phase Q-2, follow-up)**

The 7 file-based parsers + the 2 file-based providers (deps,
licenses) run at **Maven `prepare-package` phase** via a
`QualityReportGenerator` CLI, not at HTTP request time. Output:
`target/classes/META-INF/quality-build-report.json`. At runtime the
endpoint does a single classpath resource read (`~10 KB` opaque
JSON), no parsing, no framework dependency on XML/CSV/JSON tool
shapes. Tool awareness stays in the Maven build phase where it
belongs.

Sections moving: `tests`, `coverage`, `bugs`, `pmd`, `checkstyle`,
`owasp`, `pitest`, `dependencies`, `licenses`.

Phase Q-2 is scheduled as a follow-up in TASKS.md — it requires an
`exec-maven-plugin` execution + removing `@Component` from the parser
classes + a `QualityReportGenerator` main class.

**Bucket 3 — Stays runtime (legitimate self-admin)**

- `build` (reads `META-INF/build-info.properties` — Spring Boot's
  own build metadata, shipped in every Spring Boot JAR by
  convention).
- `git` (reads `git log` from the running container's cwd — git
  metadata about the DEPLOYED version).
- `api` (walks Spring's `RequestMappingHandlerMapping` — pure JVM
  introspection).
- `metrics` (walks Micrometer's `MeterRegistry` — pure JVM).
- `runtime` (JVM uptime, active profiles, heap).
- `branches` (runs `git for-each-ref` LOCALLY — info about local
  deployment, not GitLab-aware).
- `jarLayers` (reads the JAR's own `BOOT-INF/layers.idx`).

These are self-describing JVM/VCS state — legitimate actuator
content per ADR-0026's "self-admin surface" clause.

## Consequences

### Positive

- **Prod runtime drops 2 outbound HTTPS call classes.** The
  `/actuator/quality` endpoint no longer contacts sonarcloud.io or
  gitlab.com. If those services are down, blocked by egress
  firewall, or slow, the dashboard just loads without them instead
  of hanging.
- **Secret surface shrinks.** `SONAR_TOKEN`, `GITLAB_API_TOKEN` no
  longer need to be env vars on the prod JVM. They stay at the CI
  runner level where they're already needed for the actual build.
- **Backend binary size shrinks** (once Q-2 lands) — removing
  parser code dead-weight. Not huge in absolute terms (~30 KB
  compiled) but meaningful for the principle.
- **ADR-0026 alignment restored.** The endpoint's existence no
  longer contradicts "no awareness of third-party tools".
- **Faster dashboard.** No more 500 ms+ wait on sonarcloud.io API
  rate-limited responses.

### Negative (accepted)

- **UI loses two inline panels.** Users who liked seeing the Sonar
  grade on the dashboard now click a link instead of reading 6
  numbers. Trade: one click for "we don't pretend Sonar is our
  data". The link is visually prominent with the Sonar project
  badge.
- **Pipeline section gone from dashboard.** Same reasoning. GitLab
  already has a perfectly good pipelines page; aggregating it was
  never the value — the GitLab UI shows the same thing.
- **Build-time JSON is stale between pushes.** `tests`, `coverage`,
  etc. reflect the state at build time, not live (Phase Q-2 scope).
  That's fine because those numbers DON'T CHANGE at runtime —
  running the app doesn't run tests, doesn't run PMD. The stale
  number is the right number until the next `mvn verify`.

### What we lose that we DON'T care about

The original vision of `/actuator/quality` was "one endpoint to see
every quality signal at once". That's a nice idea at a single-repo
demo scale, but in practice:
- SonarCloud is already "the place" for Sonar data.
- GitLab is already "the place" for pipeline data.
- The backend impersonating them added indirection, not insight.

The dashboard's value is the LOCAL story (tests, coverage, bugs,
runtime health). External services link to themselves.

## Alternatives considered

| Option | Rejected because |
|---|---|
| Keep Sonar + Pipeline REST calls but move to a sidecar | Operational overhead for a display-only aggregation. One more deployment. |
| Keep Sonar + Pipeline but cache at build time via CI | Adds CI complexity (embed the cached JSON in the JAR at pipeline time). Maybe revisit if the dashboard actually needs them, but today the "link out" is fine. |
| Move the whole `/actuator/quality` endpoint to a separate deployable | Multi-module Maven + deployment changes. Overkill to fix two sections out of 17. |
| Profile-gate (`@Profile("dev")`) | Leaves prod *JAR* carrying the dead code + config surface. Doesn't actually remove the coupling, just masks it. |
| Do nothing, live with the violation | "Self-admin" clause in ADR-0026 becomes meaningless if anything with an `@Endpoint` annotation is grandfathered. |

## Implementation plan

### Phase Q-1 (this ADR's primary decision, ships now)

1. Remove `buildSonarSection` + `buildPipelineSection` methods from
   `QualityReportEndpoint.java`.
2. Remove the 6 `@Value` injections (sonar.\*, gitlab.\*) + the
   `HttpClient` import + any related helpers
   (`fetchSonarMetrics`, …).
3. Remove `result.put("sonar", …)` + `result.put("pipeline", …)`
   from the `report()` method.
4. Remove the environment variables from `application.yml` defaults
   + `README` env-var matrix (if documented).
5. UI: replace the Sonar panel with a link card pointing at
   sonarcloud.io; replace the Pipeline panel with a link card
   pointing at the GitLab pipelines page.
6. Endpoint drops from 1179 → ~1000 LOC.

### Phase Q-2 (follow-up, scheduled as a TASKS.md item)

1. Create `org.iris.observability.quality.QualityReportGenerator`
   with `public static void main(String[] args)`.
2. Remove `@Component` from the 7 parsers + the 2 file-based
   providers (deps, licenses). They become pure POJOs invoked
   manually by the generator.
3. Add `exec-maven-plugin` binding to `prepare-package` that runs
   the generator and writes
   `target/classes/META-INF/quality-build-report.json`.
4. Simplify `QualityReportEndpoint` — file-based sections become a
   single classpath-resource read (the pre-generated JSON) merged
   into the response; no parsing at runtime.
5. Endpoint drops from ~1000 → ~250 LOC (true thin aggregator per
   the original Phase B-1b goal).

### Phase Q-3 (optional, if scope drifts back)

If some future feature needs Sonar/GitLab data live in the
dashboard, revisit via a build-time cached JSON, NOT a runtime REST
call. Document that choice with its own ADR rather than reopening
this one.

## Revisit triggers

- If a second runtime coupling class sneaks in (e.g. "grafana
  dashboard metadata" endpoint, "ArgoCD sync state" endpoint),
  reinforce ADR-0026 — this ADR stands on its own.
- If a multi-module Maven split becomes attractive for OTHER
  reasons (native images, CDI, deployable slicing), re-evaluate
  whether the build-time generator should become its own module
  rather than a `main` class in the app module.

## References

- [ADR-0026](0026-spring-boot-scope-limit-no-third-party-tool-awareness.md)
  — parent scope decision
- [Clean Code + Clean Architecture audit 2026-04-22](../audit/clean-code-architecture-2026-04-22.md)
  — originally flagged the infrastructure-tool coupling
- [Phase B-1 commits (MR !135)](https://gitlab.com/iris-7/iris-service/-/merge_requests/135)
  — earlier split that this ADR builds on
