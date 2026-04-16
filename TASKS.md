# Mirador Service — Persistent Task Backlog

<!--
  Source of truth for pending work across Claude sessions.
  Read at session start; update immediately on task changes.
  Format: - [ ] pending   - [~] in progress   - [x] done (keep last 10 done for context)
  When all tasks are done, delete this file and commit the deletion.
-->

## Pending — Cluster bring-up

- [~] **deploy:gke first run** — pipeline on `main` builds image + deploys
      to GKE Autopilot. Once Cloud SQL is provisioned (below), cert-manager
      emits a Let's Encrypt cert and HTTPS goes live at
      `https://mirador1.duckdns.org`.

- [ ] **Cloud SQL instance** — provision via `gcloud` (faster iteration
      than fixing the current terraform-plan "bucket doesn't exist" error).
      Commands are documented in
      `deploy/kubernetes/overlays/gke/cloud-sql-proxy.yaml` (setup section).
      Then:
      1. `gcloud sql instances describe mirador-db --format='value(connectionName)'`
      2. Set GitLab CI var `CLOUD_SQL_INSTANCE=$GCP_PROJECT:$GKE_REGION:mirador-db`
      3. Re-trigger deploy:gke — the GKE Kustomize overlay embeds the
         sidecar patch + ConfigMap override automatically.
      4. After verification, **pause the instance** to minimise cost.

- [ ] **Terraform apply** — after Cloud SQL is manual-provisioned and the
      terraform-plan bucket issue is resolved, run the full terraform-apply
      to bring VPC + GKE Autopilot + Memorystore Redis + IAM SAs into code.

- [ ] **Managed Kafka on GCP** (~$35/day) — deferred. Requires
      uncommenting ~70 lines in `deploy/terraform/gcp/kafka.tf` + SASL
      config in backend ConfigMap. See ADR-0005 for the cost trade-off.

## Pending — Version upgrades (deferred majors, separate MRs each)

- [ ] **Spring AI** `1.0.0-M6` → `1.1.4` GA (or `2.0.0-M4`). Closes 5
      known CVEs. Requires artifact rename
      (`spring-ai-ollama-spring-boot-starter` → `spring-ai-starter-model-ollama`)
      and API-surface validation.
- [ ] **ShedLock** 6 → 7 (major; distributed-lock library).
- [ ] **Testcontainers** 1.21 → 2.0 (major; breaking changes).
- [ ] **testcontainers-keycloak** 3 → 4 (major).
- [ ] **Checkstyle** 10.26.1 → 13.4.0 (3 majors; Checkstyle-config review).

## Pending — Industry-standard upgrades

- [ ] **Argo CD GitOps** — replace push-based CI deploy with pull-based
      reconciliation. Argo `Application` points at
      `deploy/kubernetes/overlays/gke`. Rollback = `git revert`.

- [ ] **External Secrets Operator + Google Secret Manager** — replace the
      `kubectl create secret` step in `.kubectl-apply` with ExternalSecret
      CRDs. Auto-rotation, no secrets in CI variable storage.

- [ ] **k6 smoke test post-deploy** — 30 s k6 load against
      `/actuator/health` + `/customers`, p95 < 500 ms, rollback on failure.

- [ ] **distroless java25 image** — switch once Google publishes it (track
      https://github.com/GoogleContainerTools/distroless). Drops ~90 CVEs
      vs `eclipse-temurin:25-jre`.

- [ ] **mise (or asdf) + `.tool-versions`** — pin Java 25 + Maven + kubectl
      + terraform per repo.

- [ ] **OpenAPI lint (Spectral)** — enforce operationId uniqueness and
      break-detection vs previous main tag's `/api-docs.yaml`.

- [ ] **Argo Rollouts / Flagger** — progressive traffic split for canary
      deploys. Requires Istio or Linkerd; deferred until Argo CD lands.

## Pending — Documentation

- [ ] **Tech glossary verification pass** — `docs/technologies.md` has
      entries tagged "verify" by the generating agent: WireMock usage
      (not in pom.xml?), SSE/SockJS wiring, Cilium/Dataplane V2 explicit
      status, Memorystore vs scaffolding, Artifact Registry explicit
      provisioning, JSONB + SLSA claims.

- [ ] **ADRs for decisions currently tacit** — container runtime choice,
      OTel OTLP push vs scrape, `@Transactional(readOnly)` strategy.

## Recently Completed

- [x] Kustomize base + overlays (`local`/`gke`/`eks`/`aks`) with postgres
      mini-base, TLS patch, Cloud SQL sidecar patch.
- [x] 8 ADRs under `docs/adr/` (Kustomize, Cloud SQL, local runner,
      in-cluster Kafka, version hoisting, WIF, feature-sliced packages).
- [x] Renovate + gitleaks + commitlint + release-please tooling.
- [x] K8s hardening — NetworkPolicies default-deny, PodDisruptionBudget,
      topologySpreadConstraints, PodSecurity admission, restricted
      securityContext + readOnlyRootFilesystem.
- [x] Docker image supply-chain — syft SBOM, Grype CVE scan, Dockle,
      hadolint, cosign keyless via GitLab OIDC + Sigstore.
- [x] 1100-line technology glossary (`docs/technologies.md`).
- [x] Bumped 12 safe minor/patch versions (resilience4j, pyroscope, jjwt,
      maven plugins, spotbugs, pmd, asm, OTel appender, sonar-maven-plugin).
