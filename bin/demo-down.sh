#!/usr/bin/env bash
# =============================================================================
# demo-down.sh — tear down the ephemeral mirador demo cluster.
#
# Runs `terraform destroy` on the GKE Autopilot cluster. After this, GCP
# billing drops to ~€0/month — only the GCS state bucket (cents) and the
# Artifact Registry images (cents) keep existing. GSM secrets stay intact
# (outside Terraform scope) so `demo-up.sh` can bring everything back
# without re-rotating credentials.
#
# Prerequisite: gcloud auth application-default login.
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TF_DIR="$REPO_ROOT/deploy/terraform/gcp"
PROJECT_ID="${TF_VAR_project_id:-project-8d6ea68c-33ac-412b-8aa}"
REGION="${TF_VAR_region:-europe-west1}"
CLUSTER_NAME="${TF_VAR_cluster_name:-mirador-prod}"
TF_STATE_BUCKET="${TF_STATE_BUCKET:-${PROJECT_ID}-tf-state}"

echo "▶️  demo-down starting (project=$PROJECT_ID region=$REGION cluster=$CLUSTER_NAME)"

cd "$TF_DIR"
terraform init \
  -backend-config="bucket=$TF_STATE_BUCKET" \
  -backend-config="prefix=mirador/gcp" \
  -input=false -reconfigure >/dev/null

TF_VAR_project_id="$PROJECT_ID" \
TF_VAR_region="$REGION" \
TF_VAR_cluster_name="$CLUSTER_NAME" \
TF_VAR_app_host="${TF_VAR_app_host:-mirador1.duckdns.org}" \
  terraform destroy -input=false -auto-approve

cat <<EOF

✅  demo-down complete
---
Surviving resources (intentional, ~€0/month):
  - GCS bucket $TF_STATE_BUCKET (Terraform state, cents/month)
  - Artifact Registry images (cents/month)
  - GSM secrets: mirador-{db-password,jwt-secret,api-key,gitlab-api-token,keycloak-admin-password}
  - GCP SA external-secrets-operator@ (no cost)

Bring everything back with: bin/demo-up.sh
EOF
