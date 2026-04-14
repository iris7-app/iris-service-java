#!/usr/bin/env bash
# =============================================================================
# register-runner.sh — register the local GitLab Runner against a GitLab instance
#
# Prerequisites:
#   1. Runner container must be running:
#      ./run.sh runner
#
#   2. Get a runner token from the target GitLab instance:
#      Project → Settings → CI/CD → Runners → New project runner
#      ✓ "Run untagged jobs"  ✓ "Lock to current project"
#      Click "Create runner" → copy the token (glrt-xxxxxxxxxxxx)
#
# Usage:
#   ./scripts/register-runner.sh local <TOKEN>   — register against local GitLab (localhost:9081)
#   ./scripts/register-runner.sh cloud <TOKEN>   — register against gitlab.com
# =============================================================================

set -euo pipefail

BLUE='\033[0;34m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${BLUE}▶  $*${NC}"; }
ok()   { echo -e "${GREEN}✓  $*${NC}"; }
die()  { echo -e "${RED}✗  $*${NC}" >&2; exit 1; }

TARGET="${1:-}"
TOKEN="${2:-}"

case "$TARGET" in
  local)
    GITLAB_URL="http://host.docker.internal:9081"
    INSTANCE_LABEL="local GitLab (localhost:9081)"
    ;;
  cloud)
    GITLAB_URL="https://gitlab.com"
    INSTANCE_LABEL="gitlab.com"
    ;;
  *)
    die "Usage: $0 <local|cloud> <RUNNER_TOKEN>\n  local — register against local GitLab (localhost:9081)\n  cloud — register against gitlab.com"
    ;;
esac

[[ -z "$TOKEN" ]] && die "Usage: $0 $TARGET <RUNNER_TOKEN>\n  Get it from: $INSTANCE_LABEL → Project → Settings → CI/CD → Runners → New project runner"

# Check the runner container is up
docker ps --filter "name=gitlab-runner" --filter "status=running" --format "{{.Names}}" \
  | grep -q "gitlab-runner" \
  || die "Runner container is not running. Start it first:\n  ./run.sh runner"

log "Registering runner against ${INSTANCE_LABEL}..."

docker exec -it gitlab-runner gitlab-runner register \
  --non-interactive \
  --url "$GITLAB_URL" \
  --token "$TOKEN" \
  --executor "docker" \
  --docker-image "maven:3.9.14-eclipse-temurin-25-noble" \
  --description "local-runner-$(hostname)" \
  --docker-volumes "/var/run/docker.sock:/var/run/docker.sock" \
  --docker-volumes "/root/.m2:/root/.m2:rw" \
  --docker-pull-policy "if-not-present" \
  --run-untagged="true" \
  --locked="false"

# ── Post-registration: tune concurrency ──────────────────────────────────────
# GitLab Runner defaults to 1 concurrent job. Bump to match your CPU cores.
CORES=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
CONCURRENCY=$(( CORES > 2 ? CORES - 1 : 1 ))   # leave 1 core for the OS

docker exec gitlab-runner sed -i \
  "s/^concurrent = .*/concurrent = ${CONCURRENCY}/" \
  /etc/gitlab-runner/config.toml

ok "Runner registered against ${INSTANCE_LABEL} — ${CONCURRENCY} concurrent jobs (${CORES} cores detected)"

# ── Show config summary ───────────────────────────────────────────────────────
echo ""
echo -e "  ${BLUE}Runner config:${NC}"
docker exec gitlab-runner cat /etc/gitlab-runner/config.toml \
  | grep -E "concurrent|name|url|executor|image" | sed 's/^/  /'

echo ""
echo -e "  ${GREEN}Next push will trigger jobs on this machine.${NC}"
echo -e "  Monitor:  ${BLUE}docker logs -f gitlab-runner${NC}"
echo ""
echo -e "  To unregister:  ${YELLOW}docker exec gitlab-runner gitlab-runner unregister --all-runners${NC}"
