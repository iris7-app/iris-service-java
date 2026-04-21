# ADR-0041 — CI hygiene: honest green discipline

- Status: Accepted
- Date: 2026-04-21
- Deciders: @benoit.besson
- Related: ADR-0022 (ephemeral demo cluster — no TF state bucket), ADR-0037
  (Spectral rules disabled), CLAUDE.md "Pipelines stay green"
- Supersedes: N/A (merges the two preceding ADRs 0041 "scope-out not shield"
  and 0043 "tag only after post-merge main green" into a single doctrine)

## Context

Two different ways silently break the invariant "a green pipeline means
main is actually green":

1. `allow_failure: true` shields on jobs that structurally cannot pass in
   the current environment — the red square is hidden, the real failure
   is invisible.
2. Tagging `stable-vX.Y.Z` right after the MR merges, while the
   post-merge main pipeline is still running, with a "I'll move the tag
   if it goes red" recovery plan — the recovery gets forgotten and the
   tag ends up labelling a red commit as "stable".

Both anti-patterns produce **green-looking** states that mask real
failures. The unifying rule: **CI state must not lie — every green
signal must be a real green.**

### Incidents (April 2026 session)

**Scope-out incidents:**

- **Shielded sonar-analysis on MR pipelines.** SonarCloud's free tier
  does not support pull-request / merge-request analysis. The job
  failed 4-out-of-4 on MRs 109/110/112/113 with "Project not found —
  please check sonar.projectKey". `allow_failure: true` hid every
  failure. The "7/7 main success" audit was correct but irrelevant:
  every MR also ran the job, and every MR run was silently red.
- **Shielded terraform-plan without `TF_STATE_BUCKET`.** Per ADR-0022
  the project deliberately does not provision a permanent GCS bucket.
  `terraform init` against an empty backend fails 100 %; the shield hid
  the red square on every pipeline view.
- **Post-hoc 0-jobs pipeline.** Once `terraform-plan` was scoped-out
  via `when: never`, `terraform-apply` still had `needs: terraform-plan`
  without `optional: true`. GitLab refused to create the pipeline at
  all:
  ```
  'terraform-apply' job needs 'terraform-plan' job, but
  'terraform-plan' does not exist in the pipeline. […] use
  needs:optional.
  ```

**Tag-on-green incidents:**

- **stable-v1.0.7 tagged on a soon-to-be-red commit.** Right after
  `!119` merged, the tag was pushed. Main pipeline #570 then failed
  (sonar-analysis MR regression above). Tag had to be deleted and
  re-pushed after the fix — consumers who fetched in that window saw
  a different commit than later fetchers.
- **stable-v1.0.8 near-miss.** `!123` merged cleanly (MR pipeline
  green). Main pipeline #592 then failed with the 0-jobs error above.
  Had the tag already landed on that SHA, it would have pointed at a
  commit whose main pipeline validated nothing.

## Decision

Two rules, same underlying principle — pipelines must be verifiably
honest about their state.

### Rule 1 — Scope-out, don't shield

**When a CI job cannot succeed in the current environment — not
because of the code change, but because of an environmental constraint
— scope it out via `rules:` rather than shield it with
`allow_failure: true`. When a dependent job references it via
`needs:`, mark the reference `optional: true`.**

Concretely:

- `sonar-analysis` rule narrowed to
  `$CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH` — MR triggers removed.
- `terraform-plan` rule gated on `$TF_STATE_BUCKET` being non-empty;
  otherwise `when: never`.
- `terraform-apply` has `needs: terraform-plan` with `optional: true`
  + a mirror `$TF_STATE_BUCKET` rule on its `when: manual` gate.

### Rule 2 — Tag only after post-merge main green

**A `stable-vX.Y.Z` tag must reference a SHA on `main` whose
corresponding post-merge pipeline finished `success`. The MR
pipeline succeeding is not enough — main runs the full default-branch
ruleset (deploy stages, scheduled-only jobs) which can fail even when
the MR passed.**

Operationally:

1. MR auto-merge armed.
2. When the MR merges, GitLab launches a fresh pipeline on `main`
   with the squash commit SHA.
3. A watcher checks `(mr.state == merged) AND (main.sha matches
   squash SHA) AND (main.status == success)`.
4. Only then: tag, push to both remotes.

## Alternatives considered

### A) Keep `allow_failure: true` shields indefinitely

**Rejected.** Produces permanent red squares in every pipeline view,
silently tolerates real regressions (the 4 MR failures were invisible
until the shield was lifted), trains readers to ignore red as
"normal".

### B) Flip to `allow_failure: false` and fix the underlying issue

**Rejected for the env-incompatible cases.** The underlying issue is
not a code bug — it's an environmental constraint (no paid SonarCloud
tier, no state bucket). Flipping `false` without scoping out breaks
every pipeline.

### C) Tag immediately after MR merge, recover if main goes red

**Rejected.** The recovery requires human attention at exactly the
wrong moment (during the next task), the "tag on red" window is
visible to anyone who `git fetch --tags` in the gap, and the
delete+retag churns the reflog which makes post-mortems harder.

### D) Tag on MR pipeline green (before merge)

**Rejected.** The MR pipeline does NOT run the full main-branch
ruleset. A change that passes the MR pipeline can still break main
(demonstrated by `!123` above: MR pipeline green, main pipeline
0-jobs fail).

### E) Scope-out via `rules:` + tag-on-post-merge-main-green (accepted)

Each rule is compact and mechanical. Scope-out keeps pipeline views
honest — skipped jobs render differently from failed jobs.
Tag-on-post-merge-main-green waits an extra 5-10 min after each merge
but guarantees the tag points at a commit whose full pipeline
verifiably passed.

## Consequences

### Positive

- Every pipeline view shows only jobs that can actually succeed on the
  current branch / env. Red squares are real regressions.
- `stable-vX.Y.Z` tags are guaranteed to point at a commit whose main
  pipeline was green. Consumers trust the tag without cross-checking.
- No delete+retag churn — tags are immutable once pushed.
- When the environment changes (paid tier enabled, bucket provisioned,
  token set), scope-out gates re-activate automatically — no code
  change needed.
- The SHA-aligned watcher (Rule 2) catches the "0-jobs pipeline"
  failure shape that simpler "wait for any green main" would miss.

### Negative

- Requires per-job investigation before flipping a shield. The audit
  must include MR runs, not just main runs — missing this led to the
  sonar-analysis regression on #570.
- `needs: optional: true` is easy to forget — if job A is scoped-out
  and job B has `needs: A` without `optional`, the pipeline fails to
  create at all (caught post-merge on `terraform-apply`, recovered by
  Rule 2).
- +5-10 min between MR merge and tag push. Acceptable since tags are
  for stability, not release velocity.
- Requires a careful watcher script (merge SHA match, not just
  "latest main pipeline green") — see the operational pattern below.

## Operational checklist when scoping out a job

1. Verify the underlying reason is **environmental**, not a bug.
2. Pick the gate condition (`$TOKEN == null`,
   `$CI_COMMIT_BRANCH != main`, etc.).
3. Add `when: never` at the top of the `rules:` block for the negative
   case, keep the positive cases below.
4. **Grep for `needs:` references** to the job. For each one, add
   `optional: true`:
   ```bash
   grep -nE "needs:.*<job-name>|job:\s*<job-name>" .gitlab-ci.yml
   ```
5. Replace the `allow_failure: true` line + its dated TODO with a
   `# Shield removed <date> — <one-line rationale>` block pointing at
   this ADR.
6. Run `glab api ci/lint` to validate the YAML before pushing.
7. Open the MR and — per Rule 2 — wait for the post-merge main
   pipeline GREEN on the merge SHA before tagging.

## Operational pattern — SHA-aligned watcher (Rule 2)

```bash
last_mr=""; last_main=""; mr_merged_sha=""
while true; do
  mr_json=$(glab mr view --repo "$PROJECT" "$MR_IID" --output json 2>/dev/null)
  mr=$(echo "$mr_json" | jq -r '"\(.state)|\(.head_pipeline.status // "none")"')
  squash_sha=$(echo "$mr_json" | jq -r '.squash_commit_sha // .merge_commit_sha // ""')
  main_top=$(glab api "projects/$PROJECT_ENC/pipelines?ref=main&per_page=1" \
    | jq -r '"\(.[0].iid // "?")|\(.[0].status // "none")|\(.[0].sha // "")"')
  main_iid="${main_top%%|*}"; rest="${main_top#*|}"
  main_status="${rest%%|*}"; main_sha="${rest#*|}"

  if [[ "$mr" == merged* && -n "$squash_sha" && -z "$mr_merged_sha" ]]; then
    mr_merged_sha="$squash_sha"
    echo "$(date '+%H:%M:%S') MR merged as ${squash_sha:0:8}; waiting for main on this SHA"
  fi

  # Green only when the main pipeline on the MERGE SHA succeeded.
  if [[ -n "$mr_merged_sha" && "$main_sha" == "$mr_merged_sha"* \
        && "$main_status" == "success" ]]; then
    echo "BOTH merged + post-merge main green — exit"; exit 0
  fi

  case "$mr" in *failed*) exit 1 ;; esac
  if [[ -n "$mr_merged_sha" && "$main_sha" == "$mr_merged_sha"* \
        && "$main_status" == "failed" ]]; then
    exit 1
  fi
  sleep 30
done
```

Key distinction: `$main_sha == $mr_merged_sha` check, NOT "any main
pipeline green". Without it, the watcher exits on an unrelated older
green pipeline.

## Revisit criteria

- SonarCloud upgrades its free tier to support PR analysis → re-add
  the MR rule on `sonar-analysis` (Rule 1).
- GCS state bucket is provisioned + `TF_STATE_BUCKET` is set in CI
  variables → `terraform-plan` + `terraform-apply` rules activate
  automatically; no YAML change.
- GitLab ships a "merge-when-pipeline-succeeds AND tag on success"
  atomic primitive → drop the external SHA-aligned watcher (Rule 2).
- Post-merge main pipelines routinely finish in under 2 min → the
  wait cost becomes trivial; nothing changes in the rule.

## References

- `.gitlab-ci.yml` — `sonar-analysis` rules block (Rule 1),
  `terraform-plan` + `terraform-apply` rules + `needs:optional`
  wiring (Rule 1), main-branch workflow allowlist (Rule 2).
- Pipelines that revealed the pattern:
  [#570](https://gitlab.com/mirador1/mirador-service/-/pipelines/2467312321)
  (sonar fail masked by shield);
  [#592](https://gitlab.com/mirador1/mirador-service/-/pipelines/2467721661)
  (0-job creation fail, `needs` not `optional`).
- `~/.claude/CLAUDE.md` → "Pipelines stay green" (enforcing rule for
  this ADR) + "Tag every green stability checkpoint, never tag on
  red" (Rule 2 in CLAUDE form).
- Project `CLAUDE.md` files — mirror both rules at repo level.
- ADR-0022 — `TF_STATE_BUCKET` unprovisioned by design (Rule 1 gate).
- ADR-0037 — Spectral rules disabled (same scope-out spirit applied
  to a different tool).
