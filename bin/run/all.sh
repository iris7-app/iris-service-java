#!/usr/bin/env bash
# bin/run/all.sh — implements `./run.sh all`.
# Extracted 2026-04-22 from run.sh under Phase B-7-8 (1 sub-script per case).
# Invoked by the run.sh dispatcher; sources _preamble.sh for shared helpers.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
source "$REPO_ROOT/bin/run/_preamble.sh"

    ensure_docker
    echo "Starting everything..."
    # Start infra + every optional service. After the 2026-04 profile
    # taxonomy refactor (see docker-compose.yml header), the profiles
    # split is:
    #   full  → heavy extras (ollama, keycloak)
    #   admin → nice-to-have browsers (cloudbeaver, kafka-ui, redis*, sonarqube)
    # `run.sh all` keeps the original "kitchen sink" semantics by
    # activating both. `docs` (maven-site, compodoc) is opt-in only —
    # `run.sh site` brings maven-site up on demand.
    # redis-commander dropped 2026-04-25 from `all` invocation : the project is
    # unmaintained (last release 2021-07), and the `0.8.1` tag was yanked from
    # Docker Hub making the all-up fail mid-stream. redisinsight covers the
    # same Redis-browser UX. Users who specifically want redis-commander can
    # still run it manually : `docker compose --profile admin up -d redis-commander`
    # (and accept that the pin needs maintenance — see TASKS.md).
    docker compose --profile full --profile admin up -d \
      db kafka redis ollama keycloak cloudbeaver kafka-ui redisinsight
    # Start observability stack (lgtm, cors-proxy, docker-proxy) — all
    # now live under the `observability` profile.
    docker compose -f deploy/compose/observability.yml --profile observability up -d
    # Wait for DB to be healthy before starting the app
    echo -n "Waiting for PostgreSQL"
    until docker inspect -f '{{.State.Health.Status}}' postgres-demo 2>/dev/null | grep -q healthy; do
      echo -n "."
      sleep 2
    done
    echo " ready!"
    $MVNW spring-boot:run
