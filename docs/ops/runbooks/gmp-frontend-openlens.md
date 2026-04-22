# Runbook: OpenLens metrics on GKE Autopilot via GMP frontend

**Last verified**: 2026-04-22 · **Applies to**: `mirador-prod` GKE Autopilot cluster

## Symptom

OpenLens connected to the GKE Autopilot cluster shows **empty Metrics tabs**
on Pods / Workloads, even though `kubectl top pods` returns data. The same
OpenLens showing the local `kind` cluster works fine.

## Root cause

GKE Autopilot locks down the `kube-system` namespace. The standard
`prometheus-community/kube-prometheus-stack` chart tries to create a
`kubelet` Service there so Prometheus can scrape cAdvisor (per-pod CPU /
memory). Autopilot rejects the Service creation with:

```
services "kube-prometheus-stack-kube-etcd" is forbidden: GKE Warden authz
[denied by managed-namespaces-limitation]: the namespace "kube-system" is
managed and the request's verb "patch" is denied
```

Without cAdvisor scrape, OpenLens's "Metrics" tab queries return empty
series → panel shows nothing.

## Context — why Autopilot cluster at all

Per ADR-0022 the demo cluster is ephemeral + cost-capped at €10/mo. GKE
Autopilot is ~40 % cheaper than GKE Standard for the same workload at this
scale, so we accept the "can't patch kube-system" limit. Going to Standard
just to restore full `kube-prometheus-stack` support is not justified at
the current scale.

## Fix — GMP frontend query proxy (option B in the evaluated set)

GKE Autopilot **already enables** Google Managed Prometheus (GMP) by
default — `managedPrometheusConfig.enabled=True` on the cluster. cAdvisor
+ kubelet are part of the `SYSTEM_COMPONENTS` Google collects. The catch:
the data lands in Cloud Monitoring, not a local Prometheus port. To bridge
it to any Prometheus-speaking client (OpenLens, Grafana, k9s) we deploy
the GMP `frontend` component — a Prometheus-compatible query proxy
fronting Cloud Monitoring.

```text
OpenLens → svc/gmp-frontend:9090 → Cloud Monitoring API (PromQL-compat)
                                   ↑
                                   GMP auto-collected cAdvisor + kubelet
```

### One-shot install

```bash
bin/cluster/demo/install-gmp-frontend.sh
```

The script handles:

1. **GCP service account** `gmp-frontend-viewer@<project>.iam.gserviceaccount.com`
   with `roles/monitoring.viewer` + `roles/stackdriver.resourceMetadata.viewer`.
2. **Workload Identity binding** K8s SA `monitoring/gmp-frontend` → GCP SA.
3. **Deployment + Service** in `monitoring` ns. Service labelled
   `app.kubernetes.io/name=prometheus` so OpenLens auto-detects.

### Configure OpenLens

1. Close the cluster tab in OpenLens + reopen it. Auto-detect re-scans
   services on connection and finds `monitoring/gmp-frontend` via the
   `app.kubernetes.io/name=prometheus` label.
2. If auto-detect fails: Cluster Settings → Metrics → set Prometheus
   Service to `monitoring/gmp-frontend:9090`, Prometheus type
   `prometheus-operator`.

### Verify

```bash
kubectl port-forward -n monitoring svc/gmp-frontend 9091:9090
curl -s 'http://localhost:9091/api/v1/query?query=container_cpu_usage_seconds_total' \
  | jq '.data.result | length'
```

Expected: > 200 series (cAdvisor emits one per container per node).
If 0: check the pod logs `kubectl logs -n monitoring deploy/gmp-frontend`
for a Workload Identity error (most common: the K8s SA annotation
`iam.gke.io/gcp-service-account` missing or pointing at a nonexistent SA).

## Alternatives evaluated + why rejected

| Option | Effort | Result | Verdict |
|---|---|---|---|
| **A. Status quo (no metrics)** | 0 | OpenLens pod metrics empty | ❌ user request |
| **B. GMP frontend** (this runbook) | ~30 min | Full cAdvisor + kubelet in OpenLens | ✅ chosen |
| **C. GCP Console → Metrics Explorer** | 0 | Full observability via GCP web UI | ✅ pragmatic alt |
| **D. Grafana of lgtm** | 0 (already) | Partial — app OTLP, no cluster metrics | ✅ kept in parallel |
| **E. Switch to GKE Standard** | high + cost | kube-prometheus-stack works natively | ❌ rebuild + €€€ |
| **F. Lens Desktop (paid $6/mo)** | 0 + $$ | Native Prometheus + GMP support | ❌ paid |

We ship **B** + **C** + **D** together (Grafana for app-level + cluster
metrics via OTLP; GMP frontend for OpenLens cAdvisor; GCP Console as
power-user escape hatch).

## Cost

+ ~€0.01/h (1 frontend pod, 100m CPU / 128Mi). Included in the already-
provisioned Autopilot budget — no extra cluster resources.

## Teardown

Happens automatically via `bin/cluster/demo/down.sh` (the whole cluster
is deleted). To remove only the frontend without destroying the cluster:

```bash
kubectl delete ns monitoring
gcloud iam service-accounts delete gmp-frontend-viewer@<project>.iam.gserviceaccount.com
```

## Troubleshooting

- **`ImagePullBackOff: frontend:vX.Y.Z-gke.0: not found`** — the Google
  registry rotates tags; check the latest via
  `curl -s https://raw.githubusercontent.com/GoogleCloudPlatform/prometheus-engine/main/examples/frontend.yaml | grep image:`
  then update the `install-gmp-frontend.sh` image tag.
- **`Workload Identity error: could not impersonate service account`** —
  verify the K8s SA annotation + GCP IAM binding match EXACTLY (namespace
  `monitoring`, SA name `gmp-frontend`). Recreate the binding with
  `--member="serviceAccount:${PROJECT_ID}.svc.id.goog[monitoring/gmp-frontend]"`.
- **OpenLens still empty after restart** — inspect with DevTools (⌘⌥I).
  Look for `Failed to fetch metrics` in the console; if it shows CORS
  errors, OpenLens is trying a different endpoint than gmp-frontend —
  double-check the Prometheus Service address in Cluster Settings.
