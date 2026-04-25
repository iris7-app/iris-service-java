# TASKS — mirador-service (Java/Spring Boot)

Source of truth for SVC-only pending work across Claude sessions.
Read at session start. Update on every task change. Commit immediately.

**Per `~/.claude/CLAUDE.md` rule "One TASKS.md per project"** :
this file contains ONLY tasks that touch the svc repo. UI tasks
live in `mirador-ui/TASKS.md` ; Python tasks in
`mirador-service-python/TASKS.md`.

**Last refresh** : 2026-04-25 14:48 — split per-repo + B-1b cancelled per
new file-length floor 1 000 LOC.

---

## ✅ Recently shipped (svc-only tags, last 10)

| Tag | Theme |
|---|---|
| [stable-v1.0.59](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.59) | **Compat matrix officially closed** (doc-only — wave 7+8 already covered everything end-to-end ; ADR-0061 Entry 2 PARTIAL → FIXED) |
| [stable-v1.0.58](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.58) | **CORS W3C trace context headers** (`traceparent` / `tracestate` / `baggage` — caught by UI e2e:kind golden specs after the SPA fallback fix exposed the latent issue) |
| [stable-v1.0.57](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.57) | **SB3 wave 8 — Spring Kafka 3.3.4 BOM pin + KafkaConfig V2 serializer overlay** (4 KafkaConfigTest errors closed, ADR-0061 Entry 4 fixed) |
| [stable-v1.0.56](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.56) | **SB3 wave 7 — Jackson V2 isolation** (`RecentCustomerBuffer` overlay + ADR-0061 catalog) |
| [stable-v1.0.51](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.51) | **J17 API overlays + IT tag-gating** (`AggregationService` J17 overlay + `page.get(size-1)` + `@Tag("integration")` + failsafe excludes) |
| [stable-v1.0.50](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.50) | switch case `_` fix + `COMPATIBILITY_MATRIX.md` doc |
| [stable-v1.0.49](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.49) | **4 surgical compat fixes** (Maven java17 profile order + 29× catch `_` + ArchTest kafka method-level + ArchTest demo exclusion) |
| [stable-v1.0.47](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.47) | **D2 Ollama 20→95 % branches** + trivy `--timeout 5m→15m` fix |
| [stable-v1.0.46](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.46) | **Phase C Checkstyle `failOnViolation=true`** (121 → 0) + RateLimit branch tests (57→86 %) |
| [stable-v1.0.45](https://gitlab.com/mirador1/mirador-service/-/releases/stable-v1.0.45) | CLAUDE rule mirror + ADR-0057 polyrepo + ADR-0045/0046 stubs + regen-adr-index hardening |

**Major waves shipped 2026-04-22** : Phase B-2/B-4 CI modularisation
(svc 2 619 → 173 LOC) ; Phase Q (backend ↔ build-tool decoupling, ADR-0052) ;
release-please removed (ADR-0055) ; Alertmanager flipped ON with null-receiver
(ADR-0048 amended).

---

## 🟡 Pending — concrete work, no blockers

*(empty as of 2026-04-25 14:48 — Phase C ✅, Phase B-1b ✅ cancelled
per 1 000 LOC floor, compat matrix ✅ closed, SB3 waves 7-8 ✅ done)*

---

## 👤 Actions user (1-click each, manual)

- **GitHub mirror push (svc)** — `git push github main` from
  `mirador-service`. Auto-shipped 2026-04-25 14:34 (svc 221 commits
  → 0 commits behind after manual run). Re-run as needed when CI
  ships new main commits ; consider `bin/launchd/` cron if recurring.
- **SonarCloud security_hotspots_reviewed = 0 %** — manual UI step on
  https://sonarcloud.io/project/security_hotspots?id=<svc-project>.
  Mark hotspots as "safe" with justification. Cannot be automated.

---

## ✅ Compat matrix structural debt — DONE 2026-04-25 13:30

All 5 matrix cells green locally after 8 waves of SB3/SB4 × Java17/21/25
compat fixes. Verified `mvn clean verify` against each combination on
2026-04-25 — every cell exits 0. ADR-0061 Entry 2 status flipped 🔧
PARTIAL → ✅ FIXED in commit [`64d017e`](https://gitlab.com/mirador1/mirador-service/-/commit/64d017e), tag
[stable-v1.0.59](https://gitlab.com/mirador1/mirador-service/-/tags/stable-v1.0.59).

| Cell | Command | Status |
|---|---|---|
| SB4 + J25 | `mvn verify` | ✅ BUILD SUCCESS |
| SB4 + J21 | `mvn verify -Dcompat` | ✅ BUILD SUCCESS |
| SB4 + J17 | `mvn verify -Dcompat -Djava17` | ✅ BUILD SUCCESS |
| SB3 + J21 | `mvn verify -Dsb3` | ✅ BUILD SUCCESS |
| SB3 + J17 | `mvn verify -Dsb3 -Djava17` | ✅ BUILD SUCCESS |

The Jackson V2/V3 conflict (the one that previously blocked SB3 cells)
was already resolved by waves 7-8 — the previous TASKS.md status was
stale, written before the wave 7-8 mechanisms had been verified
end-to-end. The two surgical mechanisms :

- **`RecentCustomerBuffer` overlay** (wave 7 — svc 1.0.56) : main + test
  overlay swap `tools.jackson.*` (V3) → `com.fasterxml.jackson.*` (V2)
  since SB 3.4.x ships Jackson V2 only. See ADR-0061 Entry 2.
- **`KafkaConfig` overlay + Spring Kafka 3.3.4 BOM pin** (wave 8 — svc
  1.0.57) : pin Spring Kafka to last 3.x release on SB 3.4.x line +
  overlay `JacksonJsonSerializer` (V3-aware) → `JsonSerializer` (V2).
  SK 3.3.4's `JsonKafkaHeaderMapper` has no V3 references, so the V3
  init chain disappears entirely. See ADR-0061 Entry 4.

Verification command (local, ~25 min for the full matrix) :

```bash
for args in "" "-Dcompat" "-Dcompat -Djava17" "-Dsb3" "-Dsb3 -Djava17"; do
  echo "=== mvn verify $args ===" && mvn clean verify $args -q 2>&1 | tail -3
done
```

---

## 🟢 Nice-to-have (slow-day backlog — svc only)

- **OVH staging cluster** — multi-region peering, NAT Gateway for HDS
  audit. Out of scope for portfolio demo. Re-open only if a real
  staging environment is needed.
- **Phase B-1b extraction CANCELLED** 2026-04-25 — `QualityReportEndpoint.java`
  is 469 LOC, well below the new 1 000 LOC file-length floor.
  Stays as-is per the "< 1 000 LOC → DO NOT split" rule.

---

## 🧭 Ideas pour plus tard (svc-specific)

### Release automation — DONE 2026-04-23 ✅

Replaced `release-please` (GitHub-API-only, 401 on GitLab PAT) with
2 local shell scripts in `bin/ship/` (`changelog.sh` + `gitlab-release.sh`).
ADR-0055 documents the trade-offs vs semantic-release. Revisit triggers
explicit (team > 2 contributors, tag cadence < 1 / day, cross-repo
coordination needed, or shell version > 300 LOC).
