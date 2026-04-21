# TASKS — pending work backlog

Source of truth across Claude sessions. Read this first. Update when
adding/starting/finishing a task. Delete when empty (per CLAUDE.md).

## ✅ Closed this session

### 2026-04-21 (later — checkpoint stable-v1.0.6)

- New global rule "Reference pipelines, MRs and config files as
  clickable URLs" added to ~/.claude/CLAUDE.md and mirrored to both
  project CLAUDE.md (svc !112, UI !63).
- bearerAuth `oas3-schema` "unevaluated properties: name" error
  cleared by removing the invalid `.name(securitySchemeName)` setter
  on the HTTP SecurityScheme — `name` is only valid for `apiKey`
  schemes (svc !112).
- `openapi-lint` job `allow_failure: true` shield removed (svc !112).
- compodoc CVE batch (5 → 0 vulns) via npm `overrides` forcing
  `@compodoc/compodoc → @angular-devkit/{core,schematics}@21.2.7`.
  Top-level `"//-overrides"` JSON-comment key documents the rationale
  + revisit trigger (UI !64).
- `.github/workflows/scorecard.yml` `permissions: read-all` narrowed
  to `contents: read` — closes Sonar `githubactions:S8234` and the
  `new_security_rating = 3` driver (svc !113).
- Workflow `.gitlab-ci.yml` `changes:` allowlist widened with
  `bin/**`, `.github/**`, `.spectral.yaml`, README.fr.md, CLAUDE.md
  — without these, MRs touching only those paths produced no
  pipeline at all and were BLOCKED by the branch-protection rule
  (svc !113).
- 4 stable `allow_failure: true` shields dropped (sonar-analysis,
  code-quality, trivy:scan, dockle, release-please) — each ran 7/7
  green on main between pipelines #557→#565. Remaining shields:
  svc 30 → 25 (svc !113).
- `sonar-analysis` rules scoped to MAIN ONLY — SonarCloud free tier
  has no PR/MR analysis ("Project not found" 404 on every MR
  pipeline). The previous shield was hiding 4 consecutive failures
  on MRs 109/110/112/113. Per-MR quality feedback continues via
  `code-quality` (svc !113).
- 2 new `bin/dev/stability-check.sh` sections: `section_adr_proposed`
  (flags ADRs stuck in "Proposed" >30d via git blame) +
  `section_helm_lint` (no-op until `deploy/helm/**` exists). 25 → 27
  sections (svc !113).
- UI features grouping audit — `src/app/features/` is ALREADY
  organised into 4 categories (`core-ux`, `customer`, `infra-ops`,
  `obs`) with 15 features distributed. The TASKS.md item was stale;
  no work needed.

### 2026-04-21 (earlier — checkpoint stable-v1.0.5)

- ServiceMonitor for Mirador in local-prom overlay (svc !108, 1b9acbd)
- gke-prom/ overlay: kube-prom-stack on GKE Autopilot (svc !109,
  dc79814 + b87800a + 8a5292f) — 7d retention, 10Gi PVC on standard-rwo,
  1.5Gi mem, 6 ServiceMonitors, ADR-0039 GKE section
- ADR-0037 Path B: `OpenApiCustomizer openApiSchemaSanitizer()` (svc
  !110, bf69492 + c50e12a + 95452fc) — strips MissingNode/NullNode
  defaults, drops empty-string defaults on non-string types, normalises
  parameter examples to schema type. 13 unit tests. Spectral errors
  24→0 on `oas3-valid-{schema,media}-example`. Both rules re-enabled
  in `.spectral.yaml`.
- bearerAuth `oas3-schema` "unevaluated properties: name" error fixed
  (root cause: `.name(securitySchemeName)` setter is only valid on
  `apiKey` schemes, not `http`). `openapi-lint` job
  `allow_failure: true` shield removed — pipeline now goes red on any
  Spectral error.

### 2026-04-20

- UI 15 feature smoke specs (UI !58, commit 088ec90 → squash on main)
- compat-* profiles surefire discovery (svc !98, commit 029dead — root
  cause: plugin-level compileSourceRoots leaked to testCompile)
- smoke-test CI infra (svc !98, commit ebbdb96 — swap k6 image for
  maven + docker CLI, spin compose + spring-boot:run, target
  app@localhost:8080)
- compose-profiles cleanup (svc, commit d80db1f — split full into
  full/admin/docs, tag observability stack, README+FR updated)
- Auth0 JWT validation ITest (svc, commit 60a5969 — 4 scenarios:
  happy path + expired + wrong issuer + wrong audience, uses
  in-process HttpServer + RSA keypair, no WireMock / Testcontainers)

## 🟡 Improvements

### SonarCloud Quality Gate — remaining drivers (after stable-v1.0.6)

stable-v1.0.6 closed `new_security_rating = 3` (scorecard.yml perms
narrow). Remaining drivers all need REAL test work, not config
tweaks — keep one MR per driver:

- **svc `new_coverage = 47.3%`** < 80% — new code added this period
  without matching tests. Hotspots: `CustomerController` (classify
  helper + enrich path), and the authz paths added in the Auth0 JWT
  validation ITest session.
- **svc `new_security_hotspots_reviewed = 0%`** — hotspots on new code
  need the Sonar "Review" click (mark as "safe" with justification).
  Manual UI step, no code change.
- **UI `new_coverage = 0%`** + **UI `new_security_hotspots_reviewed = 0%`**
  — same shape.

### Reduce `allow_failure: true` shields

Running history:
- 2026-04-20: removed 4 (svc owasp-dependency-check, svc cosign:sign,
  UI bundle-size-check, UI typedoc).
- 2026-04-21 stable-v1.0.5: removed 1 (svc openapi-lint via Path B).
- 2026-04-21 stable-v1.0.6: removed 5 (svc sonar-analysis,
  code-quality, trivy:scan, dockle, release-please) +
  scoped sonar-analysis to main only (free-tier limitation).

Counts: svc 30 → 25, UI 14 → 14. Next sessions: continue 5/session.
Priority candidates with KNOWN flakiness (manual investigation
needed before flipping): `test:k8s-apply` (kubectl wait migration
TODO), `grype:scan` (arm64 panic — deferred to upstream fix),
`terraform-plan` (state bucket dependency).

#### Spectral warnings cleanup (was: openapi-lint shield flip — DONE 2026-04-21)

The `openapi-lint` job is now `allow_failure: false` (default) — any
`--fail-severity error` finding goes red. 6 warnings remain that don't
trip the gate but are real API-contract polishing:

- `operation-description` missing on `/customers/{id}.get`,
  `/scheduled/jobs.get`, `/customers/summary.get` — add
  `@Operation(description=...)` on the controllers.
- `operation-tag-defined` on `/scheduled/jobs.get.tags[0]` —
  either add the tag to `GroupedOpenApi.tags()` or drop the
  controller-level `@Tag`.
- 2× `no-script-tags-in-markdown` on `/demo/security/xss-*` —
  these are literal XSS demo endpoints; either `@Hidden` them in
  prod OpenAPI groups or escape the `<script>` in the Javadoc.

If we want to gate on warnings too: drop `--fail-severity error` for
`--fail-severity warn` on the spectral CLI invocation. Decide as a
separate session — for now warnings are visible in the report
artifact + Sonar issues UI.

### Follow-ups from ADR-0039 (kube-prometheus-stack overlay)

The `local-prom/` overlay shipped 2026-04-21. The `gke-prom/` overlay
shipped 2026-04-21 too (this MR). The Mirador ServiceMonitor shipped in
both. Remaining follow-ups:

- **Smoke-test `gke-prom/` on the ephemeral cluster** — kustomize+dry-run
  validation passes locally, but the storage class, PVC reclaim, and
  Autopilot privileged-DaemonSet behaviours haven't been exercised on
  a real GKE cluster yet. Schedule alongside the next
  `bin/cluster/demo-up.sh` cycle. Then verify:
  - PVC for `prometheus-prometheus-stack-kube-prom-prometheus-db-...-0`
    binds on `standard-rwo` and is mounted at `/prometheus`.
  - node-exporter pods schedule on every Autopilot worker node (PSS
    privileged label honored).
  - kube-prom Prometheus successfully scrapes Mirador's
    `/actuator/prometheus` cross-namespace.
  - Grafana datasource "Prometheus (kube-prom-stack)" returns data on
    a `up{job="kubelet"}` query.
  - After `demo-down.sh`, the orphaned PVC is detected and cleaned by
    `bin/budget/gcp-cost-audit.sh`.
- **`test:k8s-apply-prom` CI job** — copy of the existing `test:k8s-apply`
  but applies `local-prom/`. Adds ~3 min to the pipeline; only runs on
  `deploy/kubernetes/overlays/local-prom/**` changes (path filter).
  Catches Prometheus Operator manifest drift in CI before it hits a
  developer's machine. Also extend with a `gke-prom` matrix entry now
  that the overlay exists.
- **kubelet CA injection on GKE** — gke-prom currently keeps
  `insecureSkipVerify: true` on the 3 kubelet ServiceMonitor endpoints
  because GKE kubelet certs aren't signed by the SA-token-visible root.
  Fix path: mount the kubelet CA from a Secret + add `caFile:` to each
  endpoint via a JSON6902 patch on the rendered file. Documented in
  ADR-0039 "Future work" + the gke-prom values-kube-prom-stack.yaml
  comment block under `kubelet:`.

## 🟢 Nice-to-have

### Extend `bin/dev/stability-check.sh` — 4 ideas remain

stable-v1.0.6 added 2 sections (`section_adr_proposed`,
`section_helm_lint`). Backlog of remaining ideas:

- Lighthouse score regression vs baseline beyond the existing simple
  delta (e.g. fail-on-degradation thresholds for perf/a11y/bp/seo).
- Mermaid diagram syntax check (escape pitfalls re-occur in
  long-form architecture docs).
- Trivy CVE delta beyond the existing simple delta — currently flags
  count change; could flag NEW CVE IDs vs last report so a 0→0 with
  one CVE replaced by another is detected.
- TODO/FIXME age scanner already exists (`section_stale_todos`); a
  `git blame --porcelain` extension to attribute by author would be
  the next step.

### Move root-level files to `config/` (UI + svc) — defer

Audit 2026-04-21: only ~2 candidates per repo are realistically
movable without breaking tool conventions (e.g. `.spectral.yaml` →
`config/` would need a CI invocation update; `run.sh` → `bin/`).
The CLAUDE.md "≤ 15 files" rule was not met today (24 files in
each), but the bulk of the count is dotfiles that tools require at
root (`.gitignore`, `.gitattributes`, `.dockerignore`,
`.gitleaks.toml`, `.mise.toml`, `.editorconfig`, `.prettierrc`,
`.release-please-manifest.json`, etc.). Defer until a session has
explicit appetite to update CI invocations + tool docs.
