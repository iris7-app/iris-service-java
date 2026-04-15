# Mirador Service — Persistent Task Backlog

<!--
  This file is the source of truth for pending work across sessions.
  Claude must READ this file at the start of every session.
  Claude must UPDATE this file whenever a task is added, started, or completed.
  Format: - [ ] pending   - [~] in progress   - [x] done (keep last 10 done for context)
-->

## Pending — Reports & Documentation pipeline

- [ ] **Scheduled CI report job** — dedicated GitLab scheduled pipeline (daily, not on every push)
      that runs `mvn site` + TypeDoc + Javadoc and pushes the generated files to GitLab using a
      dedicated project access token (Reporter role, API + write_repository scopes, 90-day expiry).
      Job: `generate-reports` stage, pushes to a `reports/` branch, artifacts browsable in GitLab.
- [ ] **Static Maven site server** — add an `nginx` container to `docker-compose.yml` that serves
      `target/site/` at port 8083. The backend generates the site; nginx serves it independently.
      Add a `maven-site` entry in the port reference table in README.
- [ ] **TypeDoc** — generate TypeScript API docs from JSDoc comments in Angular services.
      Add `typedoc` to `package.json`, run `typedoc --entryPointStrategy expand src/app` in the
      scheduled CI job, publish output alongside the Maven site.
- [ ] **Javadoc enrichment** — Javadoc is already in `<reporting>`, but add `@apiNote` / `@implNote`
      tags to non-obvious public methods so the generated site is useful, not just structural.

## Pending — Maven site integration in Angular UI

- [x] Serve the Maven site from Spring Boot: `MavenSiteConfig` serves `target/site/` at
      `/maven-site/` (dev: `file:target/site/`, prod: `classpath:/static/maven-site/`)
- [x] Angular quality page: "Maven Site" tab with iframe; Runtime tab with active profiles,
      uptime, JAR layers
- [ ] Alternative: add a dedicated Angular route `/quality/site` as a full-page iframe
      (better UX than the embedded tab for large reports)

## Pending — Maven site enrichments proposed but not implemented

These were proposed at 2026-04-14T20:56 in response to "d'autres idées pour épaissir Maven site":

### Sécurité
- [ ] **Trivy** — scan de l'image Docker (CVE dans les couches OS + dépendances Java).
      `trivy image <image>` → JSON → parser et afficher dans /actuator/quality
- [ ] **License compliance** — `maven-license-plugin` pour lister les licences des dépendances
      et alerter sur GPL/AGPL incompatibles avec un projet commercial

### Métriques de code avancées
- [ ] **Complexité cyclomatique** — les données sont dans `jacoco.csv` (colonne COMPLEXITY) ;
      exposer le top-10 des classes les plus complexes dans la page quality
- [ ] **Tests les plus lents** — parser les Surefire XML (`time` par test case) et afficher
      le top-10 des tests les plus lents dans l'onglet Tests
- [ ] **Classes sans tests** — croiser la liste des classes (JaCoCo) avec les suites de test
      pour identifier les classes avec 0% de couverture intentionnelle vs oubliées

### Dépendances enrichies
- [ ] **Fraîcheur des dépendances** — appel à `search.maven.org` pour vérifier si une version
      plus récente existe pour chaque dépendance directe ; afficher un badge "outdated"
- [ ] **Arbre de dépendances** — `mvn dependency:tree -DoutputType=json` parsé et affiché
      comme un arbre interactif dans la page quality
- [ ] **Conflits de version** — `mvn dependency:analyze` (dépendances déclarées non utilisées
      et utilisées non déclarées) ; exposer dans /actuator/quality

### Build & Infra
- [ ] **Temps de startup** — extraire depuis les logs Spring Boot (`Started MiradorApplication
      in X.XXX seconds`) et afficher dans le dashboard comme métrique de performance
- [ ] **Pipeline history** — appel à l'API GitLab (`GET /projects/:id/pipelines`) pour
      afficher les 10 derniers pipelines avec statut et durée dans la page quality
- [ ] **Branches actives** — `git branch -r` avec date du dernier commit, affiché dans
      la page about ou quality

## Pending — autres demandes non traitées

- [ ] **Kafka ACLs** — "quand je clique sur ACLS sur la vue Kafka UI il affiche No Authorizer
      is configured on the broker" → documenter pourquoi (KRaft sans authorizer en dev) et/ou
      activer l'authorizer dans la config Kafka du docker-compose
- [ ] **Pyroscope** — "je ne vois que 3 profiles type liés à l'application, un pour la CPU
      et 2 pour la mémoire" → vérifier si les profils wall-clock et goroutine sont configurés

## Recently Completed

- [x] Runtime section in /actuator/quality: active profiles, uptime, JAR layers (BOOT-INF/layers.idx)
- [x] MavenSiteConfig: serves target/site/ at /maven-site/; SecurityConfig permits /maven-site/**
- [x] README architecture diagram simplified (Mermaid dev + Kubernetes ASCII)
- [x] Maven site: <reporting> section generates Surefire, JaCoCo, SpotBugs, Javadoc;
      `maven-site` job added to GitLab CI; artifact published as pipeline artifact
- [x] CVE upgrades: Tomcat 11.0.21, springdoc 3.0.3 / swagger-ui 5.32.2, protobuf 4.34.1
- [x] OWASP dependency check embedded in JAR + `report` CI profile
- [x] Quality page tabbed: Overview / Tests / Static Analysis / Security (OWASP) / Mutation / Build
- [x] Health: Keycloak + Ollama → UNKNOWN when not running (no longer breaks UP)
- [x] IdempotencyFilter: cache 2xx (not just 200), store (status, body) pair
- [x] CustomerStatsSchedulerTest, QualityReportEndpoint constructor injection
