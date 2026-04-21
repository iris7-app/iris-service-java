# Where each quality report lives — Maven Site vs SonarCloud

Mirador produces a lot of quality / security artefacts from CI and
from local `mvn` runs. Readers periodically ask "wait, isn't that
already in Sonar?" — this map answers that.

The short version:

- **SonarCloud** is the **dashboard** — one place to see quality gate
  status, trends, issues per-PR, security hotspots, and to review
  findings from all scanners in a uniform UI. Historical records.
- **Maven Site** (`mvn site`, served by the `maven-site` Compose
  container on `http://localhost:8083` under the `docs` profile) is
  the **raw-HTML archive** — every plugin report the Maven build
  generates, rendered exactly as the plugin authors shipped them. No
  aggregation, no trend, no gate — just the full output. Useful when
  you need the full JaCoCo line-by-line coverage or Pitest's
  mutation annotations that Sonar can't ingest.

## Side-by-side map

| Signal | Generator | Goes to SonarCloud? | Goes to Maven Site? | Why this split |
|---|---|---|---|---|
| **Unit test coverage (line + branch)** | JaCoCo (`jacoco-maven-plugin`) | ✅ via `sonar.coverage.jacoco.xmlReportPaths` → `target/site/jacoco/jacoco.xml` | ✅ `target/site/jacoco/` (HTML drill-down) | Sonar = coverage %, trend, gate. Site = "which exact lines aren't covered?" |
| **Integration test coverage** | JaCoCo merged report (`jacoco-it.xml`) | ✅ same config | ✅ `target/site/jacoco-it/` | Unit + IT merged into a single Sonar metric |
| **Unit test results** | Maven Surefire | ✅ via `sonar.junit.reportPaths` → JUnit XML | ✅ `target/site/surefire-report.html` (HTML by class) | Sonar = count + pass/fail rate. Site = full stack trace of each failure |
| **IT test results** | Maven Failsafe | ✅ | ✅ `target/site/failsafe-report.html` | Same split as Surefire |
| **Static analysis (Java bugs/smells)** | Sonar's own analyzer | ✅ native — this is Sonar's core | ❌ (Maven Site has PMD/Checkstyle/SpotBugs HTML as a second opinion, see below) | Sonar's analyzer is deeper than the OSS trio; Maven Site's PMD/Checkstyle/SpotBugs provide the raw plugin output for drill-in |
| **PMD findings** | `maven-pmd-plugin` | ❌ not fed to Sonar (Sonar has equivalent rules natively) | ✅ `target/site/pmd.html` | Historical artefact; if you want a second opinion on a specific rule |
| **Checkstyle findings** | `maven-checkstyle-plugin` | ❌ (same reason) | ✅ `target/site/checkstyle.html` | Same |
| **SpotBugs findings** | `spotbugs-maven-plugin` | ❌ (Sonar has FindBugs/SpotBugs-equivalent rules built-in) | ✅ `target/site/spotbugs.html` (+ SARIF file for GitLab Code Quality widget) | Same — also consumed by GitLab's "Code Quality" MR widget via the `code-quality` CI job |
| **Mutation coverage (Pitest)** | `pitest-maven-plugin` | ❌ **NOT WIRED** — `sonar-pitest-plugin` abandoned in 2014 (v0.5 on Maven Central), no maintained Sonar bridge exists (see ADR-0042 Routing B + TASKS.md "Reduce shields" decisions) | ✅ `target/pit-reports/index.html` (rendered into Maven Site) | Mutation results LIVE only in Maven Site. The Pitest HTML is interactive (per-line kill/survive annotations) and doesn't fit Sonar's model |
| **Dependency CVEs (OWASP)** | `dependency-check-maven` | ❌ not fed to Sonar; Sonar consumes Trivy/Grype SARIF instead | ✅ `target/dependency-check-report.html` | Historical — Trivy + Grype (SARIF → Sonar External Issues) cover the same ground with better DBs |
| **Image CVEs — OS + runtime** | Trivy (SARIF) | ✅ via `sonar.sarifReportPaths` → `trivy-report.sarif` | ❌ | Image-level scan, no Maven plugin equivalent |
| **Image CVEs — Java coordinates** | Grype (scans SBOM from Syft) | ❌ currently — grype output is JSON + CycloneDX, not SARIF | ❌ | Could be wired to Sonar later via SARIF conversion; for now the report is a CI artifact only |
| **SAST (pattern-based)** | Semgrep (daily schedule — SARIF) | ✅ via `sonar.sarifReportPaths` → `semgrep-report.sarif` | ❌ | Sonar is the only consumer |
| **OpenAPI spec drift (Spectral)** | Spectral (openapi-lint CI job — SARIF since 2026-04-21) | ✅ via `sonar.sarifReportPaths` → `spectral-report.sarif` | ❌ | Sonar External Issues, co-located with Java findings |
| **ESLint (UI repo only)** | ESLint 9 + angular-eslint (SARIF via `@microsoft/eslint-formatter-sarif` — wired 2026-04-21) | ✅ on the UI project in SonarCloud | ❌ | Sonar's own TypeScript analyzer overlaps; ESLint adds Angular-specific + RxJS subscribe-leak rules |
| **Container image hygiene (Dockle, Hadolint)** | Dockle + Hadolint | ❌ Dockle output is JSON/text, Hadolint is text | ❌ | CI artefacts only; could be SARIF-converted to reach Sonar |
| **SBOM** | Syft → CycloneDX + SPDX | ❌ SBOMs aren't findings, they're artefacts | ❌ | CI artifact, 90-day retention. GitLab's Dependency List widget ingests the CycloneDX |
| **API docs / code docs** | Javadoc (Java) + Compodoc (Angular) | ❌ — docs, not quality findings | ✅ Javadoc under `target/site/apidocs/`; Compodoc on `http://localhost:8086` | Docs go to Maven Site + Compodoc container, not Sonar |
| **Quality Gate status** | Sonar (aggregates the above) | ✅ — Sonar's core feature | ❌ | Maven Site doesn't do trends or gates |
| **Pull Request / MR decoration** | Would be Sonar Developer Edition (PAID) | ❌ free tier has NO PR analysis — `sonar-analysis` scoped to main only (see ADR-0041 Rule 1 — scope-out pattern) | ❌ | Per-MR feedback comes from GitLab `code-quality` widget (PMD/Checkstyle/SpotBugs annotations) |

## Rule of thumb for "where should this live?"

1. **Is it a finding (bug / smell / CVE / vulnerability)?**
   - If it speaks SARIF → wire it into `sonar.sarifReportPaths`.
   - If it emits XML/JSON in a custom format → write a SARIF
     converter (or accept it lives in Maven Site only).
2. **Is it coverage / test data?**
   - Already wired via `sonar.coverage.jacoco.xmlReportPaths` +
     `sonar.junit.reportPaths`.
3. **Is it a rendered HTML report humans want to browse?**
   - Maven Site (`maven-site` Compose container, `docs` profile).
4. **Is it raw binary artefact (SBOM, built JAR, Docker image)?**
   - CI artifact, not Sonar, not Maven Site — GitLab's own
     artifact browser + registry.

## Why Pitest stays outside SonarCloud

(Referenced from README "What this demonstrates" + TASKS.md
"Reduce shields" rationale.)

Pitest mutation coverage tells you whether your tests actually
assert behaviour (vs merely exercising lines). JaCoCo's 80% line
coverage can mean 80% of lines ran with `assertNotNull` only —
Pitest's mutation score shows how many of those lines are
genuinely protected by assertions.

The natural consumer of this data is SonarCloud (co-located with
the coverage it complements). But:

- **`sonar-pitest-plugin`** (org.codehaus.sonar-plugins) — last
  release v0.5 in **December 2014**. Abandoned, doesn't work on
  SonarQube ≥ 7 and certainly not SonarCloud 2026.
- **Community forks** — `VinodAnandan/sonar-pitest` (last push
  2020-05-16), `Naveen-496/pitest-sonar` (personal fork, 0 stars,
  2025) — none are maintained or widely adopted.
- **SonarCloud native support** — not on the roadmap as of
  2026-04-21.

Decision 2026-04-21 (recorded in this document + TASKS.md): Pitest
reports stay in the Maven Site UI — rendered at
`http://localhost:8083` when the `docs` Compose profile is active.
The `report` Maven profile (`mvn verify -Preport`) writes
`target/pit-reports/index.html`, which `maven-site-plugin` includes
in the generated site. Anyone wanting mutation data reads the HTML
directly; it's not going into the SonarCloud gate.

This matches the broader "non-dashboardable reports live in Maven
Site" principle — the map above is the single authoritative view.
