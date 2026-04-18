# =============================================================================
# Terraform — GCP infrastructure for mirador (ephemeral demo cluster)
#
# IaC posture after ADR-0013 + ADR-0021 + ADR-0022 (ephemeral cluster):
#
#   MANAGED BY TERRAFORM NOW:
#     - GKE Autopilot cluster `mirador-prod` (on the project's default VPC).
#     - Workload Identity pool is implicit via enable_autopilot.
#
#   DELIBERATELY DROPPED:
#     - Cloud SQL instance / Memorystore Redis — see ADR-0013 + ADR-0021.
#       Archived in docs/archive/terraform-deferred/.
#     - Custom VPC + subnet + NAT + Cloud Router — not needed for the demo;
#       the default VPC has public nodes and NAT egress out of the box.
#       This also halves `terraform apply` time and state size.
#
#   OUT OF TERRAFORM (intentional):
#     - GSM secrets (5 entries) — created via `gcloud secrets create`.
#       They outlive the cluster so demo data password / JWT secret / API
#       key are not rotated on every boot.
#     - external-secrets-operator GCP service account + IAM bindings — same
#       rationale. `bin/demo-up.sh` re-annotates the K8s SA on each fresh
#       cluster.
#
# Ephemeral demo cluster pattern:
#     terraform apply   # create cluster (~5 min)
#     bin/demo-up.sh    # install Argo CD + ESO + deploy app (~3 min)
#     ...run the demo...
#     terraform destroy # delete cluster, stop paying (~5 min)
# =============================================================================

terraform {
  required_version = ">= 1.8"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# =============================================================================
# GKE Autopilot cluster — regional, public nodes, STABLE release channel.
#
# The default network/subnetwork carry public IP ranges and default NAT,
# which is enough for a demo cluster (nodes reach Docker Hub, Maven Central,
# Let's Encrypt, etc. via GCE's default egress).
# =============================================================================
resource "google_container_cluster" "autopilot" {
  name     = var.cluster_name
  location = var.region

  # Autopilot: Google manages nodes, scaling, and upgrades automatically.
  # No node pool configuration needed — resource requests in Deployment
  # manifests drive the node provisioning.
  enable_autopilot = true

  # Use the project's default VPC. Keeping this minimal halves apply time
  # and avoids 6 additional Terraform resources (VPC, subnet, NAT, router,
  # global address, service networking connection). The demo doesn't need
  # private nodes — the ingress-nginx Ingress Controller is what's exposed
  # on a public IP anyway.
  network    = "default"
  subnetwork = "default"

  # Workload Identity: pods authenticate to GCP APIs using Kubernetes
  # service accounts mapped to GCP service accounts — no JSON key files.
  # ESO uses this to pull from Google Secret Manager (ADR-0016).
  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  release_channel {
    # STABLE: ~3 months after the REGULAR channel. Fewer surprises during
    # demos; if a new K8s feature is needed, switch to REGULAR temporarily.
    channel = "STABLE"
  }

  # Ephemeral cluster — terraform destroy must succeed without
  # deletion protection getting in the way.
  deletion_protection = false

  # Autopilot sets sensible defaults for ip_allocation_policy,
  # networking_mode=VPC_NATIVE, gateway_api, and most other fields.
}
