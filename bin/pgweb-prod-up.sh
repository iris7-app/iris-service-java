#!/usr/bin/env bash
# =============================================================================
# bin/pgweb-prod-up.sh — start the "prod tunnel" pgweb container (port 8082).
#
# Per ADR-0026 the Angular UI's Database page calls pgweb directly. In "Prod
# tunnel" mode that pgweb still runs on the laptop (compose profile
# `prod-tunnel`), but points at the CLUSTER Postgres through the port-forward
# tunnel that bin/pf-prod.sh opens on localhost:15432.
#
# Because the cluster DB password is stored in Google Secret Manager (not
# in .env for dev reasons), this script fetches it from the live cluster
# Secret before starting the container.
#
# Pre-requisites:
#   1. bin/demo-up.sh (or bin/demo-up-fast.sh) — GKE cluster up
#   2. bin/pf-prod.sh --daemon           — tunnels open (we need 15432 here)
#
# Usage:
#   bin/pgweb-prod-up.sh            # start pgweb-prod on localhost:8082
#   bin/pgweb-prod-up.sh --down      # stop the container
# =============================================================================

set -eu

if [ "${1:-}" = "--down" ]; then
  docker compose --profile prod-tunnel stop pgweb-prod 2>/dev/null || true
  docker compose --profile prod-tunnel rm -f pgweb-prod 2>/dev/null || true
  echo "🛑  pgweb-prod stopped."
  exit 0
fi

# Sanity 1: a cluster is reachable.
if ! kubectl cluster-info >/dev/null 2>&1; then
  echo "❌  kubectl cannot reach a cluster. Run bin/demo-up.sh first." >&2
  exit 1
fi

# Sanity 2: the Postgres tunnel is open on the host.
# nc -G 1 is the macOS flag for a 1-second connect timeout; the GNU/Linux
# equivalent is -w 1. Try both; either accepts the probe.
if ! { nc -z -G 1 localhost 15432 2>/dev/null || nc -z -w 1 localhost 15432 2>/dev/null; }; then
  echo "❌  localhost:15432 is not open. Run bin/pf-prod.sh --daemon first." >&2
  exit 1
fi

# Fetch the live DB password from the cluster Secret (synced there by ESO
# from GSM — ADR-0016). Env var name matches what docker-compose.yml expects.
PGWEB_DB_PASSWORD=$(kubectl get secret mirador-secrets -n app \
    -o jsonpath='{.data.DB_PASSWORD}' 2>/dev/null | base64 -d)

if [ -z "$PGWEB_DB_PASSWORD" ]; then
  echo "❌  Could not read DB_PASSWORD from Secret mirador-secrets in namespace app." >&2
  echo "    ESO may not have synced yet, or the Secret name changed." >&2
  exit 1
fi

export PGWEB_DB_PASSWORD

echo "🔑  Using DB password from cluster Secret (ESO)."
echo "🐘  Starting pgweb-prod on http://localhost:8082 → cluster Postgres via tunnel…"

docker compose --profile prod-tunnel up -d pgweb-prod

# Quick readiness probe so the user knows pgweb has actually opened 8082.
for _ in $(seq 1 15); do
  if nc -z localhost 8082 2>/dev/null; then
    echo "✅  pgweb-prod ready at http://localhost:8082"
    echo "    The Angular UI's EnvService picks this up automatically on"
    echo "    'Prod tunnel' environment."
    exit 0
  fi
  sleep 1
done

echo "⚠️   pgweb-prod started but port 8082 is not responding after 15s."
echo "    Check logs: docker compose logs pgweb-prod"
exit 1
