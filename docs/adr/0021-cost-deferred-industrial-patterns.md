# ADR-0021: Cost-deferred industrial patterns

- **Status**: Accepted
- **Date**: 2026-04-18

## Context

Building Mirador as a showcase of "industry-standard" platform practices
runs into a tension: many industrial patterns cost money in their
canonical managed form. A portfolio demo shouldn't burn a meaningful
monthly bill just to show a stack it could run.

## Decision

**We defer the significant-cost patterns as long as the skipped
capability is a secondary feature — one that isn't part of what the
demo exercises.** Everything that IS exercised ships in its
"industrial" form even when that means a few extra pieces of plumbing
(Argo CD, ESO + GSM, cert-manager, SBOM + cosign, etc.).

Whenever the deferral involves a non-trivial monthly bill, we keep the
reactivation path alive: the manifests / Terraform blocks exist but are
archived or commented, with a runbook explaining the trigger that
justifies paying.

## What's deferred and why

| Pattern | "Industrial" default | Why deferred here | Cost if re-enabled |
|---|---|---|---|
| **Cloud SQL** (PITR, auto-backups, Query Insights, IAM auth) | ✓ on production GKE | Demo never exercises PITR/backup scenarios; Postgres in-cluster does the job (ADR-0013). Reactivation runbook in `docs/archive/gke-cloud-sql/`. | ~€10/mo active, ~€2/mo paused |
| **Memorystore Redis** (HA, managed patching) | ✓ production caches | Demo cache is ephemeral; in-cluster Redis single-pod is enough. | ~€25/mo basic tier |
| **Managed Kafka on GCP** | ✓ production event bus | Demo exercises one topic + one reply-topic. `deploy/terraform/gcp/kafka.tf` has the Terraform kept commented (ADR-0005). | ~€35/day (quoted) |
| **Multi-replica + HA** across all deployments | ✓ production SLO | Demo has no traffic + no paying users; restart during demo is tolerable (ADR-0014). | ~2× compute on every pod |
| **Dedicated external static IP** | ✓ for stable ingress DNS | Ingress uses the auto-assigned IP; duckdns handles the DNS refresh. | ~€3/mo per IP |
| **Cloud Armor WAF** | ✓ public endpoints | No sensitive user data in the demo; rate-limit + Spring Security is enough. | ~€5–10/mo + per-request |
| **Istio / Linkerd** (mTLS service mesh) | ✓ zero-trust prod | Intra-cluster traffic in a single namespace pair doesn't justify the operator's CPU/memory footprint yet. Opens the door to Argo Rollouts canaries when added. | ~€5–15/mo compute + ops complexity |
| **Managed Grafana Cloud / Datadog / NewRelic APM** | ✓ production observability | OTel SDK + structured logs are in place; the LGTM stack can be deployed in-cluster when needed. OTLP export is currently disabled (no collector reachable). | €€€ depending on retention + cardinality |
| **Argo Rollouts / Flagger progressive delivery** | ✓ canary/blue-green | Demo has no user traffic to canary against; depends on Istio anyway. | Included in Istio cost above |
| **Velero backup to GCS** | ✓ DR posture | Demo state is reproducible from git; PV backups add storage cost with no testable restore scenario. | ~€0.02/GB/mo storage + retention |
| **Falco / runtime security** | ✓ detect shells-in-pods etc. | PodSecurity admission + NetworkPolicies catch the common misconfigs. Falco itself is free; its SIEM destination is the expensive part. | €€ on the log-shipping side |
| **OpenCost / Kubecost**  | ✓ cost observability | Demo cluster is small enough that `gcloud billing` suffices. OpenCost is free; Kubecost's managed tier is paid. | €0 for OpenCost; Kubecost paid tier |
| **Multi-region cluster** | ✓ DR | Single-region europe-west1 is enough for a demo. Multi-region doubles compute + egress + Cloud SQL replica pricing. | 2× compute + egress fees |

## What we do NOT defer (even when it costs something small)

| Pattern | Cost here | Why we still ship it |
|---|---|---|
| **External Secrets Operator + Google Secret Manager** | €0 (5 secrets, free tier) — ~€0.06/secret beyond 6 | Secret-rotation is a security posture, not a feature. GSM's free tier covers the current demo, with a clear cost curve if it grows. |
| **cert-manager + Let's Encrypt** | €0 | TLS is a user-facing feature (`https://mirador1.duckdns.org`). Industrial minimum. |
| **Argo CD (core subset)** | ~400m CPU + 900Mi RAM on existing nodes | GitOps is the feature being demonstrated. |
| **Let's Encrypt cert via cert-manager** for Argo CD UI too | €0 | Avoids the "why does this cert look self-signed" question. |
| **Workload Identity Federation** everywhere | €0 | Security posture; JSON-key approach would be strictly worse. |
| **Full supply chain (syft + Grype + Trivy + cosign)** | Build-time CI cost only | Industrial minimum for a publicly-pushed image. |

## Consequences

Positive:
- Monthly GCP bill stays in the **€0–3 range** for the demo cluster
  (Autopilot CPU/RAM + artifact registry + a few GSM secrets over
  quota if it ever grows).
- Every deferred pattern is explicitly tracked, with the cost delta
  and the trigger that would justify enabling it. No hidden holes.
- The shipped subset is **coherent** — we skip features, not quality.

Negative:
- A reader might mistake "deferred" for "doesn't know about this
  pattern". This ADR + the TASKS.md "Pending — Industry-standard
  upgrades" section exist to prevent that.
- Some trade-offs (e.g. "Istio or not") are actually non-trivial
  architectural choices that would deserve their own ADR if/when the
  demo needs them. Until then, their absence is the default.

## Trigger matrix

Re-enable an item from the deferred list when one of these becomes
true:

- Real users start hitting the demo → HA + Cloud Armor + Istio.
- The demo needs to survive a region-scoped GCP outage → multi-region.
- Compliance asks for audit logs on secret access or retention
  guarantees → the items with explicit compliance triggers.
- A new demo scenario needs a secondary feature (e.g. showing
  point-in-time recovery live) → the scenario's supporting service
  (e.g. Cloud SQL).

## References

- ADR-0005 — managed Kafka deferred
- ADR-0013 — Cloud SQL deferred (supersedes ADR-0003)
- ADR-0014 — single-replica policy
- ADR-0015 — Argo CD (shipped, included in "not deferred")
- ADR-0016 — External Secrets Operator (shipped)
- `docs/archive/gke-cloud-sql/` — Cloud SQL reactivation runbook
