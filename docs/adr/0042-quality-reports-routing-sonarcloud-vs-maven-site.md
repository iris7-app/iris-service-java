# ADR-0042 — Quality reports routing: SonarCloud vs Maven Site

- Status: Accepted
- Date: 2026-04-21
- Deciders: @benoit.besson
- Related: ADR-0009 (UI quality pipeline), ADR-0035 (Biome deferral),
  ADR-0037 (Spectral SARIF pattern), ADR-0041 (no PR analysis on free
  SonarCloud tier), `docs/reference/quality-reports-map.md` (full map)
- Supersedes: merges the two preceding ADRs 0042 "ESLint + SARIF UI"
  and 0045 "Pitest stays in Maven Site" into a single routing doctrine

## Context

Iris produces quality / security artefacts from many tools: JaCoCo
(coverage), Surefire/Failsafe (tests), Sonar (bugs + smells), PMD,
Checkstyle, SpotBugs, Pitest (mutation), Trivy + Grype (CVEs), Semgrep
(SAST), Spectral (OpenAPI lint), ESLint (Angular UI), Dockle +
Hadolint (image hygiene), Syft (SBOM).

Each report can live in one of two places:

- **SonarCloud** — the dashboard. Quality gate, trends, per-PR issues,
  historical tracking. Native ingest for: Sonar's own analyzer,
  JaCoCo coverage XML, JUnit test reports, SARIF (External Issues).
- **Maven Site** — self-hosted HTML at `http://localhost:8083` under
  the `docs` Compose profile. Raw plugin output, no aggregation, no
  gate. Useful for line-by-line drill-down and plugin output that
  doesn't fit SonarCloud's model (Pitest's per-line mutation
  annotations being the canonical case).

Several tools lack native SonarCloud plugins or ship outputs that
don't map cleanly to Sonar's issue model. Decision needed per tool.

## Decision

Three routing rules, one per class of tool. The canonical view of
every tool's destination is `docs/reference/quality-reports-map.md`;
this ADR captures the rationale.

### Routing A — UI lint (ESLint) → SARIF → SonarCloud

**Adopt ESLint 9 (flat config) with `angular-eslint` +
`typescript-eslint`. Emit findings in SARIF via
`@microsoft/eslint-formatter-sarif`. Feed into SonarCloud as External
Issues via `sonar.sarifReportPaths=eslint-report.sarif`.**

Pre-ESLint, UI static analysis relied on:

- `tsc` — type errors + some Angular checks (NG8113 unused imports).
- Prettier — formatting only.
- SonarCloud's TypeScript analyzer — bugs + smells after push to main
  (no PR analysis on free tier — see ADR-0041).

None of the three catch Angular-specific patterns reliably. A scan at
ADR time surfaced real gaps:

- **86 `.subscribe()` calls without `takeUntilDestroyed`** (RxJS
  memory-leak shape).
- 12 `console.*` calls on production paths.
- 19 unhandled `.then / .catch` (floating promises).
- 2 `*ngFor` without `trackBy`.

Concrete setup:

- Deps: `eslint@9`, `@eslint/js@9` (pinned — `@eslint/js@10` peer
  conflict caught at install), `typescript-eslint@8`,
  `angular-eslint@21`, `@microsoft/eslint-formatter-sarif`.
- Flat config at repo root: `eslint.config.mjs` (ESLint 9 only — no
  `.eslintrc.json`).
- npm scripts: `lint`, `lint:fix`, `lint:sarif`.
- CI job: `lint:eslint` (stage `validate`) runs `npm run lint` AND
  `npm run lint:sarif`, uploads `eslint-report.sarif` (1-week
  retention). `sonarcloud` job `needs: lint:eslint` with
  `optional: true` + `artifacts: true`.
- `config/sonar-project.properties`:
  `sonar.sarifReportPaths=eslint-report.sarif`.

**Rule severity philosophy:**

- **Error** on rules that guard NEW code only and don't grandfather
  legacy (`prefer-standalone`, `prefer-control-flow`,
  `click-events-have-key-events` after the info-tip fix).
- **Warn** on rules that flag existing tech debt (`no-explicit-any`,
  `no-console`, `array-type`, a11y rules on legacy templates, ReDoS
  edge cases).

Initial state: **0 errors, 47 warnings**. CI passes from day one;
warnings surface in SonarCloud External Issues without blocking.

### Routing B — Mutation coverage (Pitest) → Maven Site only

**Pitest output stays in HTML under Maven Site. Do NOT ingest into
SonarCloud via any plugin or custom bridge.**

Pitest (`pitest-maven-plugin`) produces **mutation coverage** — the
single most valuable complement to JaCoCo's line coverage. 80 % line
coverage + 40 % mutation score means "tests run the code but don't
actually check much". Mutation data is high-signal.

The natural consumer would be SonarCloud (co-located with coverage).
But as of 2026-04-21:

- `org.codehaus.sonar-plugins:sonar-pitest-plugin` — last release
  **v0.5 in December 2014**. 11 years abandoned. Doesn't work on
  SonarQube ≥ 7, certainly not SonarCloud 2026.
- `VinodAnandan/sonar-pitest` (community fork) — last push
  2020-05-16. Not maintained.
- `Naveen-496/pitest-sonar` (personal fork, 0 stars, 2025) — zero
  ecosystem traction.
- SonarCloud native support — not on roadmap.

Pitest runs on demand via `mvn verify -Preport` and writes
`target/pit-reports/index.html`, which `maven-site-plugin` includes
in the generated site. Developers who want mutation data open
`http://localhost:8083/pit-reports/` (documented in the quality
reports map).

### Routing C — SAST + image CVEs + OpenAPI lint (SARIF native) → SonarCloud

**Trivy (image CVEs), Semgrep (SAST daily schedule), Spectral
(OpenAPI lint) all emit SARIF and feed SonarCloud External Issues
via `sonar.sarifReportPaths`.**

`pom.xml` config:
```xml
<sonar.sarifReportPaths>
  semgrep-report.sarif,
  trivy-report.sarif,
  spectral-report.sarif,
  eslint-report.sarif
</sonar.sarifReportPaths>
```

This consolidates 4 SARIF producers + ESLint on one dashboard. Java
Sonar findings are co-located on the same Issues tab.

## Alternatives considered

### A) Continue without ESLint, rely on SonarCloud + tsc + Prettier

**Rejected.** Sonar's JS/TS analyzer doesn't cover Angular-specific
anti-patterns (RxJS subscribe-leak, Angular template a11y,
`prefer-standalone`, control-flow migration). The 86 potential
memory leaks alone justify the install.

### B) Use `ng add @angular-eslint/schematics` for ESLint wiring

**Partially adopted.** The schematic uses the same `angular-eslint`
family — kept the package choice. Skipped the wrapper because it
assumes an `angular.json` `lint` target and doesn't emit SARIF
natively; flat config + SARIF formatter is simpler.

### C) Use Biome instead of ESLint

**Rejected.** Biome is faster with cleaner config but does NOT have
Angular-specific rules (RxJS subscribe, Angular template a11y,
control-flow migration). Adopting Biome would require writing those
rules from scratch. Biome was already evaluated and deferred in
[ADR-0035](0035-defer-pact-and-biome.md).

### D) Install `sonar-pitest-plugin@0.5` anyway

**Rejected.** 11-year-old plugin, no SonarCloud support (Sonar's
plugin API moved substantially since 2014), no security patches.
Would pin an abandoned JAR with no upgrade path.

### E) Fork an unmaintained Pitest community plugin

**Rejected.** Would turn the project into an accidental maintainer
of a niche Sonar plugin. Out of scope for a portfolio backend demo.

### F) Write a Pitest → SARIF converter ourselves

**Rejected for now.** Pitest output maps awkwardly to SARIF
(mutations are per-line-per-mutator, not per-issue). Marginal value
over reading the Pitest HTML directly. Reconsider if a community
SARIF converter emerges and gets traction.

### G) Drop Pitest entirely

**Rejected.** Mutation score IS valuable signal — a developer
opening `target/pit-reports/index.html` after `mvn verify -Preport`
gets high-signal feedback on where tests need strengthening. Keeping
the report cost is a few extra minutes on the `report` profile (off
by default on fast builds).

### H) StyleLint for SCSS

**Out of scope** for this ADR. SCSS lint would be a separate tool
with its own rules and SARIF story; tracked as a later follow-up.

## Consequences

### Positive

- **86 potential RxJS subscribe leaks are now tracked systematically**;
  each migration to `takeUntilDestroyed(destroyRef)` clears one
  warning. Progress is visible on SonarCloud.
- ESLint findings are co-located with Java findings on SonarCloud's
  Issues tab — reviewers don't need a second UI.
- Real-time IDE feedback: VSCode + ESLint extension shows errors as
  the dev types, vs SonarCloud's 2-min post-push feedback.
- `angular-eslint` tightens NEW code (`prefer-standalone`,
  `prefer-control-flow`) so Angular 21's zoneless + signals patterns
  don't regress to NgModule / `*ngIf`.
- Mutation score lives in ONE place (Maven Site) consulted by
  developers who want "are my tests asserting what they should"
  data. SonarCloud stays clean — only tools with current, actively
  maintained Sonar integration feed the gate.
- No abandoned-plugin maintenance burden on the Pitest side.
- The `docs` Compose profile remains the single entry point for the
  full-rich HTML reports (Surefire, JaCoCo detail, Pitest, Javadoc).

### Negative

- 5 new dev dependencies on the UI (eslint + 4 plugins) and a new CI
  job. Renovate `digest` auto-bumps cover upgrades; peer-dep
  surprises possible on major bumps (saw `@eslint/js@10` conflict at
  install, fixed by pinning to `@eslint/js@9`).
- CI adds ~30 s for `lint:eslint` on every MR — acceptable given the
  MR budget but not free.
- Warning-count creep: 47 today can drift to 80 if rules aren't
  tightened as tech debt is cleaned. Mitigated by the TASKS.md
  "B1+F1 ESLint cleanup" scheduled session.
- Mutation coverage doesn't appear on SonarCloud PR decoration (and
  never would — free tier has no PR decoration per ADR-0041).
- Developers need to know about Maven Site to find the Pitest data.
  Partially mitigated by the quality-reports-map table.

## Operational notes

- **ESLint flat config only** — no `.eslintrc.json`.
- **Running locally**: `npm run lint` (check), `npm run lint:fix`
  (auto-fix Array<T>→T[], import order, etc).
- **SARIF generation**: `npm run lint:sarif` emits
  `eslint-report.sarif`. The `|| true` at the end ensures the file
  is written even if there are errors — we want Sonar to see them,
  not the CI to bail early.
- **CI path filter**: `lint:eslint` runs on every MR + main (no path
  filter). TypeScript/HTML is the bulk of the UI, most MRs touch it.
- **Pitest local run**: `mvn verify -Preport`. Open
  `target/pit-reports/index.html` directly or via the `docs`
  Compose profile at `http://localhost:8083/pit-reports/`.

## Revisit criteria

- **ESLint revisit triggers:**
  - Biome reaches feature parity with `angular-eslint` for RxJS +
    Angular-template rules → revisit ADR-0035 + Routing A together.
  - SonarCloud adds native Angular rules covering the same gap →
    evaluate dropping ESLint in favour of Sonar's analyzer.
  - ESLint 10 lands + ecosystem follows → major-version upgrade MR,
    check for peer-dep breakage similar to `@eslint/js@10` at
    install.
- **Pitest revisit triggers:**
  - A maintained SonarCloud plugin for Pitest emerges with active
    releases in the last 12 months → evaluate wiring via
    `sonar.externalIssuesReportPaths` or a native plugin hook.
  - Project moves to SonarCloud Developer Edition (paid) AND
    Developer Edition adds native Pitest integration → drop the
    Maven-Site-only stance.
  - A SARIF-based community converter appears and its output is
    actionable on SonarCloud's Issues tab.

## References

- `eslint.config.mjs` — flat config + rule rationale (UI repo).
- UI `.gitlab-ci.yml` — `lint:eslint` job, `sonarcloud` needs block.
- UI `config/sonar-project.properties` —
  `sonar.sarifReportPaths=eslint-report.sarif`.
- svc `pom.xml` — `sonar.sarifReportPaths` aggregated list (Trivy +
  Semgrep + Spectral + ESLint via UI scan).
- svc `pom.xml` — `pitest-maven-plugin` config under the `report`
  profile.
- `docs/reference/quality-reports-map.md` — canonical per-tool map;
  this ADR is the "why" behind its rows.
- UI MR `!66` (stable-v1.0.8 era) — install + initial 0-error state.
- ADR-0009 — UI quality pipeline history.
- ADR-0035 — Biome deferral (revisit trigger).
- ADR-0037 — Spectral SARIF pattern (same mechanism, different
  source tool — the pattern template for Routing C).
- ADR-0041 — no PR analysis on free tier (context for why ESLint
  findings on MR feel comparable to Sonar's post-merge view).
