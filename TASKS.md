# Mirador Service — Persistent Task Backlog

<!-- 
  This file is the source of truth for pending work across sessions.
  Claude must READ this file at the start of every session.
  Claude must UPDATE this file whenever a task is added, started, or completed.
  Format: - [ ] pending   - [~] in progress   - [x] done (keep last 10 done for context)
-->

## In Progress

- [~] Maven site enrichment — `<reporting>` section added (JaCoCo, SpotBugs, PMD, Checkstyle, Javadoc, JXR, Surefire). Site generation pending; need to verify output and add `mvn site` to CI pipeline.

## Pending

- [ ] Add `mvn site` step to GitLab CI pipeline (was requested: "fait la modif dans la CI") — generate site on every `verify` run and publish as job artifact
- [ ] Verify Maven site renders correctly: JaCoCo coverage, SpotBugs, PMD, Checkstyle, Javadoc, Surefire reports all visible
- [ ] Add `site:stage` to CI to publish the site HTML as a browsable GitLab Pages artifact

## Recently Completed

- [x] CVE upgrades: Tomcat 11.0.21, springdoc 3.0.3 / swagger-ui 5.32.2, protobuf 4.34.1
- [x] OWASP report embedded in JAR + `report` profile for on-demand full scan
- [x] Quality report tabbed UI (Overview / Tests / Static Analysis / Security / Mutation / Build)
- [x] Health aggregation fixed: Keycloak + Ollama → UNKNOWN when not running (no longer breaks overall UP)
- [x] IdempotencyFilter bug fix: now caches 2xx (not just 200), stores status+body pair
- [x] JwtTokenProvider: `catch (JwtException | IllegalArgumentException e)` — replaced broad `catch (Exception e)`
- [x] Zero `any` types across all Angular components (typed interfaces for all external API shapes)
- [x] CustomerStatsSchedulerTest + QualityReportEndpoint constructor injection
- [x] TodoServiceTest (4 cases), BioServiceTest (3 cases), IdempotencyFilterTest 201-case
- [x] CLAUDE.md created in both repos with full workflow rules
- [x] Angular build: 0 NG8113 warnings, 0 errors
