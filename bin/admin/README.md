# `bin/admin/` — one-shot admin / housekeeping scripts

Scripts that change project-level state outside the code itself
(GitLab/GitHub repo settings, IAM bindings, GCP quotas, etc.).
Each script is **idempotent** + has a `--dry-run` mode where
relevant.

| Script | What it does |
|---|---|
| `gitlab-housekeeping.sh` | Disable Wiki + Secure UI on both GitLab projects, Issues + Wiki on both GitHub mirrors. `--status` shows current state, `--dry-run` previews. |

## Conventions for new scripts here

1. **`--help` / `-h`** prints usage in 30 lines max.
2. **`--dry-run`** mode if the script touches external state.
3. **Idempotent** — safe to re-run without checking prior runs.
4. **Read-only mode** (`--status` / `--check`) if applicable.
5. **Document WHY**: the script's docstring explains the policy, not
   just the commands.
6. **Cross-reference** related ADRs / runbooks at the bottom.

Add scripts here if they:

- Touch repo settings (GitLab / GitHub API).
- Touch GCP project / IAM at the project level.
- Are run a few times per year, NOT per-session.

If the script runs every day → it belongs under `bin/dev/` (developer
loop) or `bin/run/` (per-task dispatcher) instead.
