# OVH Cloud — Mirador deployment module

> **Status: CANONICAL / Stage 1** — applied via CI on demand (when:manual gate
> until first credentials wired). Joins GCP at the canonical-tier delivery
> target list per [ADR-0053](../../../docs/adr/0053-ovh-canonical-target.md).

---

## Why OVH ?

Three answers, in order of weight :

1. **HDS certification** (Hébergeur de Données de Santé — French health-data
   hosting certification). OVH is HDS-certified at GRA9 + SBG5 regions ;
   Scaleway is NOT. For health-data scenarios this is the **only**
   French-jurisdiction option.
2. **French sovereignty** — same axis as Scaleway (French-owned, EU
   jurisdiction, no CLOUD Act exposure), but the HDS layer is the real
   differentiator. This is what made [ADR-0036](../../../docs/adr/0036-multi-cloud-terraform-posture.md)'s
   "OVH ≈ Scaleway" equivalence collapse for canonical use cases.
3. **Mature Managed Kubernetes** — `ovh_cloud_project_kube` is GA since
   2020, runs upstream Kubernetes (no custom distro), free control plane
   (vs €72/month for AWS EKS).

See [ADR-0053](../../../docs/adr/0053-ovh-canonical-target.md) for the full decision context.

---

## Cost breakdown

| Resource | Monthly cost |
|---|---|
| Managed K8s control plane | **€0** (free) |
| 1× B2-7 node (2 vCPU / 7 GB RAM) | **€25.20** |
| vRack private network | **€0** |
| Private subnet | **€0** |
| **Total at min scale** | **~€25/month** |
| Auto-scale to 2 nodes (max) | **~€50/month** |

⚠️ **The €10/month project cap from [ADR-0022](../../../docs/adr/0022-ephemeral-demo-cluster.md) is BLOWN by this module when running.** ADR-0053 accepts this overhead because OVH is the *canonical 2nd target*, not a side experiment. Apply only when you actively need it ; `bin/cluster/ovh-down.sh` to stop paying.

---

## Prerequisites (one-time setup)

### 1. OVH Public Cloud project

- Go to https://www.ovh.com/manager/#/cloud/createProject (account needed)
- Pick a billing method (credit card; no free tier on Public Cloud)
- The project ID appears in the URL after creation : `https://www.ovh.com/manager/#/cloud/project/<HERE>` — that 32-char hex is your `ovh_project_id`.

### 2. vRack on the project

- In the manager : *Cloud → your project → Network → Private network → Order*
- It's free, takes ~5 minutes to activate.
- Without this, `terraform apply` fails at the `data.ovh_cloud_project_vrack` lookup.

### 3. API credentials

- Go to https://eu.api.ovh.com/createToken/
- **Application name** : `mirador-terraform`
- **Application description** : "Terraform-managed Mirador K8s cluster"
- **Validity** : `0` (= unlimited; rotate manually every 6 months — set a calendar reminder)
- **Rights** : narrow scope, paste these 4 lines :
  ```
  GET /cloud/project/*
  POST /cloud/project/*
  PUT /cloud/project/*
  DELETE /cloud/project/*
  ```
- Click **Create keys**. The page shows you :
  - `Application Key` → save as `OVH_APPLICATION_KEY`
  - `Application Secret` → save as `OVH_APPLICATION_SECRET` (shown ONCE — copy now)
  - `Consumer Key` → save as `OVH_CONSUMER_KEY`

### 4. Wire into your local `.env`

Copy `terraform.tfvars.example` → `terraform.tfvars` (gitignored), fill in the four values. OR set them as `TF_VAR_*` environment variables (preferred — secrets never touch disk).

---

## Apply (Terraform default)

```bash
cd deploy/terraform/ovh
terraform init                  # ~30s (downloads ovh provider)
terraform plan -out=plan.out    # ~10s, shows what will be created
terraform apply plan.out        # ~5-7 min (cluster provision)

# Get the kubeconfig
terraform output -raw kubeconfig > ~/.kube/ovh-mirador.yaml
chmod 600 ~/.kube/ovh-mirador.yaml
export KUBECONFIG=~/.kube/ovh-mirador.yaml
kubectl get nodes               # should show the 1 B2-7 node Ready
```

## Apply (OpenTofu opt-in, per [ADR-0053 § Tooling](../../../docs/adr/0053-ovh-canonical-target.md#tooling--terraform-by-default-opentofu-opt-in))

```bash
export TF_BIN=tofu              # picked up by bin/cluster/ovh-up.sh wrappers
tofu init && tofu apply         # same HCL, same providers, MPL-2.0 binary
```

The CI runs both in parallel on every MR (per ADR-0053 § Tooling) so dual-compat breakage is caught on day 1.

---

## Destroy (stop paying)

```bash
cd deploy/terraform/ovh
terraform destroy               # ~5 min (cluster + nodes + network)
```

OR via the helper :

```bash
bin/cluster/ovh-down.sh         # wraps the above, confirms cost stopped
```

---

## Authentication note

OVH does NOT support **Workload Identity Federation** (the OIDC-based credential exchange GCP/AWS use). Instead, three long-lived API credentials are required (see step 3 above).

**Operational implications** :

- Credentials are long-lived → rotate manually every 6 months. Set a reminder.
- CI variables (`OVH_APPLICATION_KEY`, `OVH_APPLICATION_SECRET`, `OVH_CONSUMER_KEY`) MUST be marked **Protected + Masked** in GitLab so they don't leak to job logs.
- OVH IAM scopes the consumer_key to specific API paths — keep it narrow (`/cloud/project/*` only). Broader scopes (`/me`, `/domain`) leak unrelated access if the secret is exfiltrated.

This is a known regression vs GCP's WIF and is documented in [ADR-0053 § Related](../../../docs/adr/0053-ovh-canonical-target.md#references).

---

## Troubleshooting

### `Error: Unable to fetch vRack`

The Public Cloud project doesn't have a vRack ordered yet. Go to step 2 of Prerequisites.

### `Error: cannot find Kubernetes version 1.31`

OVH dropped support for that version (typically n-2 minors). Bump `var.k8s_version` to a supported one ; check https://docs.ovh.com/gb/en/kubernetes/release-notes/.

### `Error: project_id must be exactly 32 hex characters`

You probably copied the project NAME (e.g. `My Mirador Project`) instead of the project ID (the hex string from the manager URL).

### `Error: Forbidden / 403 on POST /cloud/project/<id>/kube`

The consumer key was generated with too-narrow scopes (e.g. GET-only). Re-generate at https://eu.api.ovh.com/createToken/ with the 4 verbs from step 3.

### Cluster apply succeeds but `kubectl get nodes` shows 0 nodes

Node provisioning takes 3-5 minutes after the control plane is ready. Re-run `kubectl get nodes -w` and wait. If still 0 after 10 minutes, check the OVH manager → your cluster → Nodes for provisioning errors (typically out-of-quota on B2-7 instances in the chosen region).

---

## Stage 2 — what's now wired (vs originally deferred)

**Updated 2026-04-23.** The original stage-1 deferred 4 items ; this section now reflects the actual stage-2 status.

### ✅ OVH Object Storage backend — READY-TO-MIGRATE

The `backend.tf` carries both the default `local` block (still active) AND a fully-templated `s3` block (commented out). To migrate :

```bash
# 1. Bootstrap the Object Container + S3 credentials (idempotent ; ~30s)
bin/cluster/ovh/init-backend.sh
# Outputs: container created (versioned) + AWS_* creds written to .env.local

# 2. Edit deploy/terraform/ovh/backend.tf :
#    - Comment the `backend "local"` block
#    - Uncomment the `backend "s3"` block (template is fully populated)

# 3. Source credentials + migrate state
set -a ; source .env.local ; set +a
cd deploy/terraform/ovh
terraform init -migrate-state
# Terraform copies local terraform.tfstate → s3://mirador-tfstate/ovh/...
```

**Cost** : Object Storage container is free up to 100 GB. Even with 1000 versions of the state, you're under the free tier (state file ~100 KB).

### ✅ Adding ingress (Public Cloud LoadBalancer) — VIA K8S OVERLAY

OVH's Public Cloud LoadBalancer is **provisioned by Kubernetes**, NOT by Terraform. The `ovh_cloud_project_loadbalancer` in OVH's TF provider is a *data source only* (read-only). The right path :

1. Edit `deploy/kubernetes/overlays/ovh-prom/kustomization.yaml`
2. Uncomment the `lgtm-loadbalancer-ovh-patch.yaml` patch entry
3. `kubectl apply --server-side -k deploy/kubernetes/overlays/ovh-prom`

The K8s overlay's `service.beta.kubernetes.io/ovh-loadbalancer-type: classic` annotation triggers OVH's cloud-controller to provision a Classic-tier LB (~€20/month). Without the annotation, OVH defaults to Premium (~€100/month — silent budget killer).

**To get the LB's public IP after apply** :
```bash
kubectl get svc -n observability lgtm \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

### ⏸ NAT Gateway (HDS-compliant nodes) — NOT POSSIBLE on Standard tier

OVH's "no public IP on workload nodes" requires the **Premium tier** Managed Kubernetes (different SKU, ~3× the cost). Standard-tier nodes always get a public IP. For an HDS-eligible production deploy, switching to Premium is documented as a follow-up but out of scope for this stage.

The vRack private network IS in place for node-to-node + node-to-control-plane traffic — so internal traffic doesn't traverse the public Internet (cost AND security benefit). Only outbound traffic from nodes to external services uses the public IP. For HDS audit, this nuance needs spelling out.

### ⏸ Multi-region peering — DEFERRED

For a `mirador-staging` cluster in SBG5 alongside `mirador-prod` in GRA9. Worth doing once we have a real staging workflow ; not justified by the demo's single-cluster setup today.

---

## Related

- [ADR-0053](../../../docs/adr/0053-ovh-canonical-target.md) — promotes OVH to canonical
- [ADR-0036](../../../docs/adr/0036-multi-cloud-terraform-posture.md) — multi-cloud Terraform posture (amended by 0053)
- [`deploy/terraform/gcp/`](../gcp/) — GCP canonical module (default deploy)
- [`deploy/terraform/scaleway/`](../scaleway/) — Scaleway reference (EU-sovereign without HDS)
- [`bin/cluster/ovh/up.sh`](../../../bin/cluster/ovh/up.sh) — lifecycle wrapper
- [`bin/cluster/ovh/down.sh`](../../../bin/cluster/ovh/down.sh) — lifecycle wrapper
- [`bin/cluster/ovh/init-backend.sh`](../../../bin/cluster/ovh/init-backend.sh) — Object Storage backend bootstrap (stage-2)
- [`bin/budget/ovh-cost-audit.sh`](../../../bin/budget/ovh-cost-audit.sh) — monthly spend monitor
- OVH HDS reference : https://www.ovhcloud.com/fr/enterprise/certification-conformity/hds/
