# ADR-0033 — Playwright E2E in kind-in-CI

- **Status**: Accepted
- **Date**: 2026-04-19
- **Related**: [ADR-0028](0028-kind-cluster-in-ci-for-manifest-validation.md)
  (kind-in-CI manifest validation), iris-ui ROADMAP Tier-1 #2

## Context

The test pyramid today has two layers:

| Layer | Framework | Where it runs | What it catches |
|---|---|---|---|
| Unit | Vitest (UI) + JUnit 5 (service) | CI + local | Component / class behaviour in isolation |
| Integration | `@SpringBootTest` + Testcontainers | CI | Controller → service → DB → Kafka wiring |

What's missing is an **end-to-end** layer: a browser actually
clicking buttons in the Angular UI, hitting the Spring Boot backend,
and asserting the resulting UI state. The classes of bug only
end-to-end catches include:

- CORS preflight misconfig
- JWT propagation through `authInterceptor`
- Route `loadComponent` 404s
- A Flyway migration that breaks a `select` read path
- Kafka request-reply timeout bubbling up to the user-visible toast
- `@if (auth.isAuthenticated())` missing a branch

None of the existing layers exercise these, because all of them mock
out at least one side of the interaction. A visible fail is the
cheapest way to catch them.

The ROADMAP lists Playwright E2E as Tier-1 item #2 (validated in this
session). This ADR formalises the decision and the placement.

## Decision

**Ship a Playwright E2E suite in the UI repo, run it inside the CI's
kind cluster (ADR-0028) alongside the manifest validation.**

### Scope (first iteration)

Three specs, not more:

1. **login.spec.ts** — `admin/admin` credentials → customers page
   reachable.
2. **customer-crud.spec.ts** — add random customer → list refreshes
   → delete round-trips.
3. **health.spec.ts** — dashboard health probes all green within 15 s
   of load.

### Placement in CI

- **Stage**: new `e2e` stage on the UI repo, runs after `build`.
- **Trigger rule**: MR pipeline + main branch + weekly schedule
  (detects slow upstream drift).
- **Allow failure**: `true` initially — Playwright E2E is notoriously
  flaky until tuned, and the unit + integration layers already
  catch regressions. We promote to `allow_failure: false` once the
  suite has run green 10 times consecutively.
- **Runtime**: ~90 seconds for 3 specs with `retries: 2`.

### Target backend

Here's the awkward part, and why this ADR exists: the kind cluster
from ADR-0028 **skips the iris backend pod** (the image is
amd64-only, the runner is arm64). The E2E has to hit a running
backend somehow.

Two candidate models:

| Option | Pro | Con |
|---|---|---|
| (A) `docker-compose up` the backend in the CI job | Fast (~30 s), matches local dev loop | Duplicates some of what kind-in-CI validates — two topologies for one test |
| (B) Multi-arch backend image + schedule on kind | Exact prod topology | Multi-arch build = +7 min per push, undoes ADR-0022's build thrift |

**Chosen: (A)**. The E2E asserts the **user-visible contract**, not
the deployment topology. kind-in-CI already validates the topology.
A separate compose-up for the backend keeps the E2E fast and honest
about what it's testing.

### Runtime mechanics

The `e2e:kind` job:

1. `docker compose -f docker-compose.yml up -d postgres redis kafka`
   (the backend's dependencies).
2. `./run.sh app &` or `mvn spring-boot:run` in the background.
3. `npm ci` in the UI repo, `npx playwright install chromium`.
4. `npx ng build` + `npx http-server dist/iris-ui` on port 4200.
5. `npx playwright test` against `http://localhost:4200`.
6. Teardown (`docker compose down`).

Screenshots + videos on failure are uploaded as CI artifacts.

### UI-side plumbing

- New devDep: `@playwright/test` (pinned minor via Renovate).
- `playwright.config.ts` at UI repo root:
  - `baseURL: http://localhost:4200`
  - `retries: 2` on CI, `0` locally
  - Projects for Chromium only (Firefox / WebKit deferred until
    needed).
  - Reporter: `list` + `html` on CI artifacts.
- `npm run e2e:local` script — spawns the full stack via `./run.sh`
  and runs the suite against it. Zero-cost dev loop.
- `.github/workflows/*` untouched — GitHub Actions stays in the
  narrow-CI scope (CodeQL + Scorecard per ADR-0029).

### Testing philosophy

- **Golden path only**. E2E is for the happy scenarios that a demo
  would showcase. Edge cases and error paths stay in integration
  tests.
- **Hermetic**. Each spec brings up its own state (seeded customers
  via API) + tears down at the end. No cross-spec leakage.
- **Flake-sensitive**. One red E2E spec in CI = a GitLab comment +
  a Slack ping, not an automatic re-run. Investigating flakes is a
  scheduled task (Tier-2 ROADMAP once we hit >2 flakes/week).

## Consequences

### Positive

- **Catches the "glue" bugs** unit + integration don't. The list above
  (CORS, JWT propagation, route 404, …) stops being a demo surprise.
- **Validates the UI actually works** on every push — not just that
  the tests we wrote pass.
- **Screenshots / videos on failure** give a direct post-mortem
  artifact for any CI red, no local repro needed.
- **Low maintenance cost at 3 specs**. Scales up slowly by design.

### Negative

- **~90 seconds added to the CI path** — acceptable, and parallel
  with the existing `k8s` stage (no critical-path blocking).
- **Playwright flakiness risk** — `retries: 2` + `allow_failure: true`
  initially, promoted to hard failure only after 10 green runs.
- **Docker-compose up in CI is separate from kind-in-CI** — two
  topologies. Documented in this ADR so future reader knows the
  rationale is "test the user contract", not "test the prod shape".

### Neutral

- **Browser-only coverage**. Mobile / tablet / different browsers are
  not tested. Portfolio-scale decision; revisit if real users arrive.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| **Multi-arch backend image so kind runs it natively** | +7 min per build (buildx cross-compile), no material gain since E2E asserts user contract, not deployment topology. |
| **Cypress instead of Playwright** | Playwright runs headless out-of-the-box in Alpine, Cypress needs Xvfb + bigger image. Both are credible; Playwright is slightly simpler for the kind-in-CI use case. |
| **Skip E2E — rely on integration + manual demo** | Loses the "validate the UI actually works" signal on every push. Manual demo is a once-per-release check. |
| **Chromatic visual regression on top of Playwright (ROADMAP Tier-2 #8)** | Valuable but one layer at a time. Chromatic needs the Playwright suite in place first. |
| **Test against GKE production** | Ephemeral (ADR-0022), unavailable most of the time. Even if up, running E2E against prod burns cost. |

## Revisit this when

- **Suite grows past 10 specs** — start splitting by concern
  (auth, customers, chaos). Maintenance pattern documented then.
- **Flakes exceed 2/week** — investigate (timing, selector
  brittleness, test isolation) before adding more specs.
- **Real users + real traffic arrive** — promote to `allow_failure:
  false` + add mobile + add performance budget (Lighthouse).
- **Multi-arch backend build becomes trivial** (GitHub Actions
  buildx, or native arm64 amd64 emulation speedup) — revisit the
  kind-vs-compose topology choice.
