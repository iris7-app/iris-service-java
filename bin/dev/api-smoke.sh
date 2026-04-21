#!/usr/bin/env bash
# =============================================================================
# bin/dev/api-smoke.sh — thin wrapper around `hurl` for docs/api/smoke.hurl.
#
# Fills in the ergonomic variables (`base`, `run_id`) so a reviewer can just
# run `bin/dev/api-smoke.sh` and get a pass/fail result without knowing Hurl's
# CLI flags. Passes any extra args through to `hurl`, so `bin/dev/api-smoke.sh
# --verbose` works.
#
# Requires: hurl ≥ 4.0 on PATH. Install: `brew install hurl` (macOS) or see
# https://hurl.dev/docs/installation.html for Linux / Windows.
# The backend must be running — start with `./run.sh all` or `./mvnw spring-boot:run`.
#
# Env overrides:
#   API_BASE        — override the `--variable base=...` (default: localhost:8080)
#   API_SMOKE_ARGS  — extra args appended before the .hurl path
#                     (e.g. API_SMOKE_ARGS="--report-html target/hurl-report")
# =============================================================================
set -euo pipefail

BASE="${API_BASE:-http://localhost:8080}"
RUN_ID="$(date +%s%N)"

# Repo root — script lives in bin/dev/, so go up two levels.
# `cd -P` resolves symlinks so this works when the repo is checked out via
# a symlinked worktree (git worktree / IDE alias).
SCRIPT_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -P "$SCRIPT_DIR/../.." && pwd)"
HURL_FILE="$REPO_ROOT/docs/api/smoke.hurl"

if ! command -v hurl >/dev/null 2>&1; then
  echo "hurl is not installed. Install with 'brew install hurl' (macOS)"
  echo "or see https://hurl.dev/docs/installation.html (Linux/Windows)."
  exit 2
fi

if [ ! -f "$HURL_FILE" ]; then
  echo "Expected Hurl file not found: $HURL_FILE"
  exit 2
fi

echo "▶ Running API smoke test against $BASE (run_id=$RUN_ID)"
# shellcheck disable=SC2086  # API_SMOKE_ARGS is intentionally word-split
exec hurl --test \
  --variable "base=$BASE" \
  --variable "run_id=$RUN_ID" \
  ${API_SMOKE_ARGS:-} \
  "$@" \
  "$HURL_FILE"
