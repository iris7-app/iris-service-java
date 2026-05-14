# TASKS — iris-service-java

Open work only. Per `~/.claude/CLAUDE.md` rules : java-only items here ;
done items removed (use `git tag -l` for history).

---

## 🤔 To investigate

- **compat-matrix-weekly schedule produces 0-job pipeline** (DISABLED 2026-05-14).
  Schedule ID `4248722` (cron `0 4 * * 0`, var `RUN_COMPAT=true`). Triggered via
  `play` API or natural cron firing, the resulting pipeline shows `status=failed`
  + `started_at=null` + `/jobs = []` despite the rule pattern matching for
  identical setup on python's `mutmut-auth-monthly` (which works correctly).
  Things ruled out :
    - YAML anchor `<<:` vs `extends: .compat-job` (both produce 0 jobs).
    - Rule order / inline rule placement (mirrored python's working pattern).
    - `restrict_user_defined_variables=false` toggle (no change).
    - `ci_pipeline_variables_minimum_override_role` set to `developer` (no change).
    - Fresh schedule recreate under `owner` role (no change).
    - Passing `RUN_COMPAT=true` in the `play` body in addition to schedule (no change).
  Likely : GitLab quirk specific to java's workflow:rules or schedule context
  (issue [gitlab-org/gitlab#573307](https://gitlab.com/gitlab-org/gitlab/-/issues/573307)
  describes an adjacent symptom — empty scheduled pipelines without error message).
  Schedule re-enable after root cause found ; in the meantime use the manual
  "Run pipeline" button with `RUN_COMPAT=true` to fire the matrix.

---

## 🚫 Blocked upstream

- (none currently)
