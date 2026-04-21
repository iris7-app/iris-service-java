# TASKS — pending work backlog

Source of truth across Claude sessions. Read this first. Update when
adding/starting/finishing a task. Delete when empty (per CLAUDE.md).

## ✅ Closed this session

### 2026-04-21 (latest — Auth0 login fully working + CHAOS-1 wired)

- **Auth0 login round-trip working end-to-end** with Google social
  connection on the existing tenant (`dev-ksxj46zlkhk2gcvo`) — see
  commits `bc12524` (svc AUTH0_* env vars), `3d28e11`/`7a77eef`/
  `79530e7` (UI interceptor Auth0-aware + multi-role isAdmin), and
  ADR-0047 for the consent-screen trade-off decision.
- **CHAOS-1 shipped**: `com.mirador.chaos` feature slice with Fabric8
  backend + RBAC + 3 experiments (PodChaos/NetworkChaos/StressChaos)
  + UI buttons + 14 unit tests across 3 test classes (svc `2c97aa8`,
  `5a12d47`, `b657253`, `161b17a`; UI `a596a90`).
- **B hexagonal-lite shipped**: `CustomerEventPort` extracted with
  Kafka adapter `KafkaCustomerEventPublisher` + ArchUnit rule for
  port-layer purity (svc `c6eb86c`). ADR-0044 documents the pattern.
- **ADR consolidation**: 41-45 merged/renumbered into 41-44 (CI
  hygiene, quality reports routing, pin GH Actions, hexagonal
  considered) + ADR-0047 Auth0 consent. Count: 43 Accepted, 2
  Superseded.
- **Chaos Mesh smoke test on kind** (P5 A): kind v0.31 +
  Chaos Mesh v2.7.2 official install. Applied
  `deploy/kubernetes/base/chaos/experiments.yaml` → all 3 CRs
  created successfully (PodChaos, NetworkChaos, StressChaos).
  Validates that the YAML schema + the programmatic equivalent in
  `ChaosService.java` (same CRD structure) work against a real
  cluster. Cluster torn down after test.

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
- 2026-04-21 stable-v1.0.8: 0 shield removals (audit confirmed
  remaining 7 unconditional shields all protect legit flakes or
  manual-trigger jobs) + scoped `terraform-plan` to require
  `TF_STATE_BUCKET` set (was failing 5/5 main + 5/5 MR with "bucket
  doesn't exist" — same anti-pattern as sonar's free-tier MR fail
  before scope-out).

Counts: svc 30 → 25, UI 14 → 14.

**No more easy wins** — the remaining 7 svc shields are residual
hard cases. Each blocked by a specific issue:
- `test:k8s-apply` + `test:k8s-apply-prom` — kind-on-CI SIGPIPE flake
  (TODO: migrate to `kubectl wait --for=condition=Ready`, dated
  2026-05-21).
- `grype:scan` — Go runtime panic on arm64 macbook-local runner
  (anchore/grype:v0.87.0-debug is amd64-only). Possible fixes
  (require testing): bump to a newer multi-arch debug variant if
  available, OR scope to schedule-only on a SaaS amd64 runner, OR
  remove the job until grype ships arm64 binary. Not safe to flip
  without one of these.
- `.compat-job` template — manual-only, "compat failures don't
  block MR merge" is documented intent.
- `semgrep` — manual-only, "static analysis is informational" is
  documented intent.
- `native-image-build` — manual-only, 30-min Kaniko AOT build never
  fired in observed window.

#### ~~Spectral warnings cleanup~~ — DONE 2026-04-21

Re-ran Spectral against live `/v3/api-docs` with
`--fail-severity warn` against `.spectral.yaml`: zero warnings, zero
errors. The 6 warnings previously listed (`operation-description`
missing, `operation-tag-defined`, 2× `no-script-tags-in-markdown`)
were all resolved by incremental commits — the ClassLevel `@Tag`
on `ScheduledJobController`, the fence-code-block + HTML-entity
escape in `SecurityDemoController` XSS demos, and full `description`
coverage on every `@Operation`. The `openapi-lint` job stays on
`--fail-severity error` default — bumping to warn is a future
opt-in if we ever want zero-warning as the MR contract.

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
- ~~**`test:k8s-apply-prom` CI job**~~ — DONE 2026-04-21 stable-v1.0.8
  (svc !121). Path-filtered on `local-prom/**`, `gke-prom/**`,
  `base/**`, `scripts/ci-k8s-test.sh`. EXTRA_PODS waits for the 4
  kube-prom-stack pods (Prometheus StatefulSet, node-exporter
  DaemonSet, ksm + operator Deployments). Same `allow_failure: true`
  shield window as parent test:k8s-apply (2026-05-21).
- ~~**kubelet CA injection on GKE**~~ — DECIDED 2026-04-21: not
  pursued. The fix path (mount kubelet CA from a Secret + JSON6902
  `caFile:` patch on the rendered file) was investigated and rejected
  because GKE Autopilot signs the kubelet serving cert with a
  separate, non-SA-token-visible root — there is no stable Secret
  reference to mount at kustomize time. The trade-off is documented
  inline at `deploy/kubernetes/overlays/gke-prom/values-kube-prom-stack.yaml`
  lines 219-236: `insecureSkipVerify: true` stays, residual MITM
  surface = cluster L3 isolation (already enforced by GKE network
  policy). Re-evaluate if GKE ever ships kubelet certs signed by the
  SA-token-visible root.

## 🟢 Nice-to-have

### ~~Extend `bin/dev/stability-check.sh`~~ — DONE 2026-04-21

stable-v1.0.6 added 2 sections (`section_adr_proposed`,
`section_helm_lint`). stable-v1.0.8 added `section_mermaid_lint`
(detects Mermaid blocks missing the diagram-type opener) +
extended `section_lighthouse` with absolute thresholds for
a11y/bp/seo (perf already had one). Trivy CVE delta is per-ID
(`comm -23` between current and baseline ID sets) — was already
done before this session, the TASKS description was stale. The
TODO-age-by-author idea is low-value on a single-author project
and was dropped.

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
