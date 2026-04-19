# ADR-0032 — Community standards files + hierarchical ADR index

- **Status**: Accepted
- **Date**: 2026-04-19
- **Related**: [ADR-0029](0029-jenkinsfile-parity-and-declarative-linter.md) (CI philosophy)

## Context

Two small but adjacent concerns landed at the same time and deserve a
single ADR because they share the same motivation (discoverability):

### 1. Missing community-standard files

GitHub shows a "Community Standards" checklist on every public repo.
Until now, Mirador's checklist was mostly red: no `SECURITY.md`, no
`CONTRIBUTING.md`, no issue templates, no PR template. Each missing
file:

- Withholds information a drive-by visitor would reasonably expect
  (how to report a CVE, how to contribute, what format to use).
- Suppresses the "community health" badge that signals project
  maturity at a glance.
- Forces a contributor to infer the workflow from commit history.

### 2. ADR index doesn't scale flat

The ADR count went past 30 during Phase 2-4 of the industrial pass.
A single flat table is still technically usable but stops answering
"where is the cost-related decision?" without a full scroll. Readers
end up using `grep` on the filenames.

The Michael Nygard format doesn't prescribe a specific index layout —
so there's nothing to violate by adding a hierarchical nav on top of
the flat list, as long as each ADR file stays untouched.

## Decision

### Community files (standard-compliant set)

Add at the repo root:

- `SECURITY.md` — reporting policy, response timeline, scope, existing
  controls, hall of fame placeholder. Tracks the GitHub/GitLab
  expected format.
- `CONTRIBUTING.md` — where to contribute (GitLab canonical, GitHub
  read-only mirror), workflow (`bin/dev/mirador-doctor` → branch →
  `bin/ship/ship.sh`), Conventional Commits rule, code conventions per
  repo, ADR discipline, review policy, what gets declined.

Add per-platform templates:

- `.gitlab/issue_templates/bug.md` + `feature.md`
- `.gitlab/merge_request_templates/default.md` with the "check ADR,
  check i18n, check CI vars, check FR readme pair" checklist
- `.github/ISSUE_TEMPLATE/bug.md` + `feature.md` (YAML frontmatter
  GitHub expects)
- `.github/PULL_REQUEST_TEMPLATE.md` — a short redirect reminding
  contributors that PRs against the mirror are not reviewed (per
  `docs/ops/ci-philosophy.md` Rule 2)

Same files exist in **both** repos (`mirador-service` and
`mirador-ui`). Paths that differ (Maven vs npm) are adapted; paths
that are shared (ship.sh, mirador-doctor, ADR rules) stay identical.

### Hierarchical ADR index

Rewrite `docs/adr/README.md` to:

1. Keep the standard Michael Nygard format, file naming, and flat
   index (compatibility + "grep still works").
2. Add a **Status snapshot** counting Accepted / Superseded /
   Deprecated.
3. Add a **Hierarchical index** grouping by theme:
   - 🧭 Meta
   - 🏗️ Architecture & code patterns
   - 📨 Messaging & data
   - 🔁 CI / release
   - ☸️ Kubernetes & deployment
   - 💰 Cost & cluster lifecycle
   - 🔐 Secrets & authentication
   - 📡 Observability & telemetry
   - 🛡️ Resilience
4. Add a **Cross-theme references** section flagging the 5 most-linked
   ADRs (ADR-0022, ADR-0026, ADR-0017, ADR-0021, ADR-0029) as the
   recommended reading seed.
5. Add a **Maintenance rule** at the bottom: any new ADR MUST update
   both the flat table AND the relevant hierarchical section in the
   same MR. Reviewer is entitled to block on the mismatch.

Grouping is nav only — no ADR file is moved, renamed, or modified.
An ADR that straddles two themes appears under its **primary** one
to avoid duplication; cross-references inside the ADR remain
authoritative.

## Consequences

### Positive

- **GitHub Community Standards checklist passes** — visible signal to
  first-time visitors that the project is maintained.
- **Security reports have a documented path** — CVE disclosure doesn't
  require finding a contact by trial-and-error.
- **Contributor onboarding shortens** — the "where do I PR?" question
  is answered on page 1 of the repo.
- **ADR navigation scales** — a reader looking for "cost decisions"
  jumps to the 💰 section; "auth decisions" goes to 🔐.
- **ADR template stays standard** — nothing in the Nygard format is
  changed. The grouping is nav metadata.

### Negative

- **Two places to update** per new ADR (flat table + theme section).
  Mitigated: the maintenance rule is explicit + MR template has a
  checkbox for it.
- **Community files require maintenance** — SECURITY.md response
  timelines and scope, CONTRIBUTING.md workflow commands. Expected
  drift cost: ~15 minutes per quarter.
- **GitHub PR template is an active redirect**, not a contribution
  path. Users unfamiliar with the mirror convention may be mildly
  surprised. Mitigated: the PR template is explicit about why.

### Neutral

- The existing ADRs don't move. A future contributor running
  `find docs/adr -name "*.md"` gets the same flat list.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| **Flat index only, no grouping** | Doesn't scale past ~20 ADRs — readers end up grepping filenames. |
| **Split ADRs into theme subdirectories** (`docs/adr/cost/`, `docs/adr/ci/`) | Breaks flat numbering + invalidates every existing cross-link. Non-trivial migration with no recurring win. |
| **Tag-based index** (YAML frontmatter + a generator) | Requires a generator job + breaks grep. Overkill for a 30-item list. |
| **Skip `SECURITY.md` — it's a solo project** | Public repo on a public org. Security reports happen whether there's a policy or not; without a policy they arrive as public tickets. |
| **Skip `CONTRIBUTING.md` — nobody contributes to a portfolio** | Wrong prediction. The project is read by recruiters, curious devs, and future Claude sessions — every one of them is a potential contributor to some scope. |
| **Separate ADR for each of the community files** | One ADR per file = 5 ADRs for a shared motivation. Violates ADR-0021's editorial rule (don't add docs without a reason shared across them). |

## Revisit this when

- ADR count passes 80 — grouping may need sub-sections (e.g.
  💰 → "cluster lifecycle" vs "SaaS opt-out"). For now 30 ADRs split
  across 9 themes is ~3 per theme — readable.
- GitHub introduces machine-readable community standards (e.g.
  `community.yaml` format) — switch to that if it becomes de-facto.
- A contributor reports the `CONTRIBUTING.md` workflow is outdated —
  update the doc + this ADR's revisit note.
