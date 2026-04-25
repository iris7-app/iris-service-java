# ADR-0060 : SB3 compat target = prod-grade, not informational

**Status** : Accepted
**Date** : 2026-04-25
**Decided by** : User directive ("je veux une compat SB3 prod") during the
2026-04-24/25 night-long compat-matrix wave session.

## Context

The compatibility matrix (`matrix-results/COMPATIBILITY_MATRIX.md`) tracks
5 cells :

- SB4 + Java 25 (default, prod target)
- SB4 + Java 21 (compat)
- SB4 + Java 17 (compat)
- SB3 + Java 21 (compat)
- SB3 + Java 17 (compat)

Earlier framing treated the 4 compat cells as **informational** : they could
be 🔴 with `allow_failure: true` shields, the tag/release process didn't
gate on them, the matrix doc tracked them for awareness only.

This framing was implicit in :
- ADR-0049 (`allow_failure: true` policy as short-term bridge)
- The compat matrix's day-1 framing : "informational matrix, doesn't gate main"
- The triaging decision to defer SB3 structural debt as "multi-hour wave 6"

During the 2026-04-24 evening session, when the SB3 cells were re-evaluated
post-wave-5, the question came up : should the SB3 overlay's
`CustomerController` be **degraded** (drop the inline enrich/bio/todo
methods, match main's 6-param constructor) to fit within a quick fix budget ?

**User directive** : "je veux une compat SB3 prod" → SB3 is a PROD target,
not a degraded informational target. Compat-SB3 cells must reach feature
parity with main's SB4 build, including all controllers, all endpoints, all
behaviour.

## Decision

**SB3 compat = prod-grade target with full feature parity.** All 5 matrix
cells (SB4×J17/J21/J25 + SB3×J17/J21) carry equivalent functional weight.

Implications :

1. **No degradation acceptable** : SB3 overlay cannot drop endpoints
   (enrich, bio, todo, etc.) just because they don't fit in main's
   refactored 6-param constructor. The SB3 overlay must mirror main's
   architectural split (separate controllers per concern).

2. **SB3 wave 6 path** : align SB3 overlay structure to main's. Concretely :
   - Update SB3 overlay `CustomerController` to 6-param constructor
   - REMOVE the enrich/bio/todo inline methods from SB3 overlay
   - Either rely on main's `CustomerEnrichmentController` /
     `CustomerDiagnosticsController` (if they compile in SB3 mode without
     SB4-only dependencies) OR create SB3 overlays for those controllers too
   - Update or add SB3 test overlays as needed
   - Acceptance criterion : `mvn -Dsb3 verify` runs all the same tests as
     `mvn verify` (default), with same pass rate (allowing only for
     feature gaps that don't exist in SB3 itself, e.g. SB4-only DSL)

3. **No `allow_failure: true` on compat cells as a permanent state** —
   the existing shield carries a dated TODO; once the structural fixes
   land, the shield must be removed.

4. **Effort is in scope** : The earlier "multi-hour, deferred" framing was
   based on the false assumption that SB3 was informational. With the
   prod-grade requirement, multi-hour effort is justified because the
   work delivers actual value (a second supported deployment lane, a
   real upgrade-path safety net for SB3-still-running customers).

## Consequences

**Pros** :
- Clear acceptance criteria for compat-SB3-* cells (must run same
  tests, same coverage as default).
- Prevents the "compat is informational" excuse from being used to
  defer real fixes indefinitely.
- Reduces tag-on-red risk : compat shields can be removed once parity
  is achieved.
- Makes the project genuinely cross-version (advertised SB3+SB4
  support is real, not theoretical).

**Cons** :
- Multi-hour-to-multi-day work to bring SB3 overlay to parity with the
  current main split (CustomerController + CustomerEnrichmentController
  + CustomerDiagnosticsController + maybe more).
- Ongoing maintenance cost : every refactor of main's controller layout
  requires a corresponding SB3 overlay update. Worth budgeting in the
  feature-development cadence.
- `mvn -Dsb3 verify` becomes a real signal — if it breaks, can't
  ignore. New PRs touching `customer/` may need to ship SB3 overlay
  updates too.

## Backlog implications

The following items, previously classified "informational defer", are now
**prod-grade work** :

- Wave 6 : `CustomerControllerTest` constructor mismatch — fix by
  aligning SB3 overlay to main's structure (not by adding test overlay).
- Wave 7+ : any further compat-SB3-* failures uncovered by the new test
  alignment.
- SB3 documentation refresh : `README.md` should explicitly state SB3
  is a supported deployment target (not "experimental compat").

## Cross-references

- `~/.claude/CLAUDE.md` "Surgical fixes, not allow_failure bypasses" —
  this ADR strengthens that rule for compat-SB3 specifically.
- ADR-0049 (allow_failure policy) — compat-SB3 shields now violate the
  spirit of this policy.
- `matrix-results/COMPATIBILITY_MATRIX.md` — the "informational" framing
  in older sections of this doc should be updated to reflect this ADR.
- TASKS.md "🔴 Compat matrix structural debt" — items must be re-classified
  from "deferred" to "active backlog".
