# Environments & runtime flows

Mirador runs against two environments. The **Angular UI is the same bundle
in both** — only the `EnvService`'s computed URLs change.

- **Local** — everything on the developer's laptop via Docker Compose.
- **Prod tunnel** — the GKE Autopilot cluster reached through
  `kubectl port-forward` on the laptop. No public surface
  ([ADR-0025](../adr/0025-ui-local-only-no-public-prod-ingress.md)).

The backend (Spring Boot) owns its domain + self-admin only —
it does not proxy or query third-party tools
([ADR-0026](../adr/0026-spring-boot-scope-limit-no-third-party-tool-awareness.md)).

## Local (docker-compose)

```
┌─────────────────────────────────────────────────────────────────┐
│                        Developer laptop                         │
│                                                                 │
│   ┌──────────────┐                                              │
│   │ Angular UI   │  :4200 (ng serve)                            │
│   │  EnvService  │  env.* → localhost:<compose ports>           │
│   └──────┬───────┘                                              │
│          │                                                      │
│          │  HTTP (same-origin via proxy.conf.json or CORS)      │
│          ▼                                                      │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │             docker-compose network (bridge)                │ │
│  │                                                            │ │
│  │  ┌──────────┐  :8080 ◀── /api/*  /actuator/*               │ │
│  │  │ app      │  (Spring Boot 4)                             │ │
│  │  │ mirador  │──┐                                           │ │
│  │  └──────────┘  │ JDBC          ┌──────────────┐            │ │
│  │                ├─────────────▶ │ db            │ :5432     │ │
│  │                │               │ PostgreSQL    │           │ │
│  │                │ Kafka         └──────┬───────┘            │ │
│  │                ├─▶ ┌─────────┐        │                    │ │
│  │                │   │ kafka   │ :9092  │                    │ │
│  │                │   └─────────┘        │                    │ │
│  │                │ Lettuce               │                    │ │
│  │                ├─▶ ┌─────────┐        │                    │ │
│  │                │   │ redis   │ :6379  │                    │ │
│  │                │   └─────────┘        │                    │ │
│  │                │ HTTP                  │                    │ │
│  │                └─▶ ┌─────────┐        │                    │ │
│  │                    │ ollama  │ :11434 │                    │ │
│  │                    └─────────┘        │                    │ │
│  │                    ┌─────────┐        │                    │ │
│  │                    │ keycloak│ :9090  ◀── OIDC from UI     │ │
│  │                    └─────────┘                             │ │
│  │                                                            │ │
│  │  ┌── Admin / tooling (browser calls directly) ──────────┐  │ │
│  │  │  cloudbeaver  :8978 ──PG proto──▶ db:5432            │  │ │
│  │  │  pgweb-local  :8081 ──PG proto──▶ db:5432            │  │ │
│  │  │  kafka-ui     :9080 ────────────▶ kafka:29092         │  │ │
│  │  │  redisinsight :5540 ────────────▶ redis:6379          │  │ │
│  │  └──────────────────────────────────────────────────────┘  │ │
│  │                                                            │ │
│  │  ┌── Observability (browser calls directly) ────────────┐  │ │
│  │  │  lgtm :3000 (Grafana)                                │  │ │
│  │  │       :3100 (Loki) ◀── CORS proxy ◀── UI             │  │ │
│  │  │       :3200 (Tempo) ◀── Grafana datasource proxy     │  │ │
│  │  │       :9009 (Mimir)                                  │  │ │
│  │  │       :4317/4318 (OTLP) ◀── OTel push from mirador   │  │ │
│  │  │  pyroscope :4040 ◀── UI iframe / links               │  │ │
│  │  └──────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

Ports are bound on `127.0.0.1` only — no LAN exposure even with a loose
host firewall.

## Prod tunnel (GKE)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           Developer laptop                                │
│                                                                           │
│   ┌──────────────┐                                                        │
│   │ Angular UI   │  :4200 (ng serve — same code as Local)                │
│   │  EnvService  │  'Prod tunnel' → localhost:1<port>                    │
│   └──────┬───────┘                                                        │
│          │                                                                │
│          │  HTTP (localhost:1xxxx)                                        │
│          ▼                                                                │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │              kubectl port-forward (bin/pf-prod.sh)                   │ │
│   │                                                                      │ │
│   │   18080 ─┐    kubelet forwards TCP to the pod IP                     │ │
│   │   13000 ─┤    (authenticated via ~/.kube/config — IAM/WIF gated)     │ │
│   │   13100 ─┤                                                           │ │
│   │   13200 ─┤                                                           │ │
│   │   19009 ─┤                                                           │ │
│   │   14040 ─┤                                                           │ │
│   │   19091 ─┤                                                           │ │
│   │   14242 ─┤                                                           │ │
│   │   18081 ─┤                                                           │ │
│   │   12333 ─┤                                                           │ │
│   │   15432 ─┤   ◀── pgweb-prod (:8082) + CloudBeaver desktop            │ │
│   │   16379 ─┤                                                           │ │
│   │   19092 ─┘                                                           │ │
│   └──────┬──────────────────────────────────────────────────────────────┘ │
│          │ TLS tunnel through the GKE API server                          │
│          ▼                                                                │
│   Partial compose (local tooling that needs the tunnels):                 │
│   ┌─────────────────────────────────────────────────────────────────────┐ │
│   │  cloudbeaver :8978 ──PG proto──▶ localhost:15432 (via tunnel)       │ │
│   │  pgweb-prod  :8082 ──PG proto──▶ host.docker.internal:15432          │ │
│   │  kafka-ui    :9080   (stays pointed at compose kafka or tunnelled)   │ │
│   │  redisinsight:5540   (idem)                                          │ │
│   └─────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────┬────────────────────────────────┘
                                           │
                                           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                           GKE Autopilot cluster                           │
│                        (no public surface — ADR-0025)                     │
│                                                                           │
│  namespace: app                         namespace: infra                   │
│  ┌──────────────────┐                   ┌─────────────────────┐            │
│  │ mirador Deployment│  JDBC            │ postgresql:5432     │            │
│  │   :8080          │  ──────────────▶ │ StatefulSet         │            │
│  │   HPA 1-5        │                   └─────────────────────┘            │
│  │   OTLP push      │  Kafka            ┌─────────────────────┐            │
│  │                  │  ──────────────▶ │ kafka:9092          │            │
│  │  Reads secrets   │  Lettuce          ┌─────────────────────┐            │
│  │  from mirador-   │  ──────────────▶ │ redis:6379          │            │
│  │  secrets         │                   ┌─────────────────────┐            │
│  │  (ESO → GSM)     │                   │ keycloak:8080       │            │
│  └────────┬─────────┘                   ┌─────────────────────┐            │
│           │                             │ lgtm pod            │            │
│           │  OTLP :4317/4318            │   :3000 Grafana     │            │
│           └───────────────────────────▶ │   :3100 Loki        │            │
│                                         │   :3200 Tempo       │            │
│                                         │   :9009 Mimir       │            │
│                                         │   (dashboards code) │            │
│                                         └─────────────────────┘            │
│                                         ┌─────────────────────┐            │
│                                         │ unleash + unleash-db│            │
│                                         └─────────────────────┘            │
│                                         ┌─────────────────────┐            │
│                                         │ pyroscope:4040      │            │
│                                         └─────────────────────┘            │
│                                                                           │
│  namespace: argocd     namespace: chaos-mesh     namespace: external-secrets│
│  ┌─────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐   │
│  │ argocd-server   │   │ chaos-dashboard:2333│   │ ESO operator         │   │
│  │ reconciles main │   │ PodChaos/Network/   │   │ SecretStore GSM      │   │
│  └─────────────────┘   │ StressChaos CRs     │   │ ExternalSecret CRs   │   │
│                        └─────────────────────┘   └─────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
```

## Per-page call flows

| UI page | Local flow | Prod-tunnel flow |
|---|---|---|
| **Login** | `POST :8080/auth/login` + Keycloak OIDC on `:9090` | `POST :18080/auth/login` + Keycloak on `:19091` |
| **Dashboard** — health probes | `GET :8080/actuator/health` | `GET :18080/actuator/health` |
| **Dashboard** — tool tiles | `<a href="localhost:<env.*>">` | idem, 1xxxx ports |
| **Customers CRUD** | `/api/customers/*` on `:8080` | `/api/customers/*` on `:18080` |
| **Database → SQL Explorer** | `GET :8081/api/query` (pgweb-local → db:5432) | `GET :8082/api/query` (pgweb-prod → tunnel :15432) |
| **Database → VACUUM** | `POST :8080/actuator/maintenance` | `POST :18080/actuator/maintenance` |
| **Database → CloudBeaver link** | `<a href=":8978">` (compose container) | user opens desktop CloudBeaver manually |
| **Observability — Grafana** | `<iframe src=":3000/…">` | `<iframe src=":13000/…">` |
| **Observability — Tempo (TraceQL)** | `GET :3000/api/datasources/proxy/uid/tempo` | `GET :13000/api/datasources/proxy/uid/tempo` |
| **Observability — Loki (LogQL)** | `GET :3100/loki/api/v1/query_range` (CORS proxy) | `GET :13100/loki/…` |
| **Pipelines** | `GET :3333/gitlab/*` (docker-api.mjs local proxy → gitlab.com) | idem, proxy runs on laptop |
| **Chaos dashboard** | — (not in compose) | `<iframe src=":12333">` |
| **Feature flags** | — (not in compose) | `<a href=":14242">` (future: `unleash-proxy`) |
| **Activity / Audit** | `/audit` on `:8080` | `/audit` on `:18080` |
| **Quality** | `/actuator/quality` on `:8080` + `sonar:9000` + `maven-site:8084` + `compodoc:8085` | `/actuator/quality` on `:18080` only; no sonar / maven site / compodoc in prod |

## Invariants

1. **Zero `/obs/*` or `/features` endpoint on Spring Boot** — ADR-0026.
2. **No public Ingress on the cluster** — ADR-0025.
3. **Spring Boot owns**: app domain + actuator + functional deps (PG /
   Kafka / Redis / Keycloak / Ollama) via native protocol + outbound
   OTLP. Nothing else.
4. **Each admin / observability tool is reached directly** from the
   browser or via its own native proxy (Grafana datasource proxy) —
   never through Spring Boot.
5. **pgweb runs in compose only**. Two containers: `pgweb-local` for
   compose mode, `pgweb-prod` (profile `prod-tunnel`) for cluster mode
   via the port-forward tunnel.
6. **Grafana / LGTM runs in both environments** — in compose for dev,
   in the cluster for prod. Symmetric.
7. **Unleash / Argo CD / Chaos Mesh are cluster-only**. No compose
   equivalents; their compose-time alternatives (simple environment
   variables for flags, `kubectl apply` for GitOps, nothing for Chaos
   Mesh) are good enough for dev.

## Related ADRs

- [ADR-0022](../adr/0022-ephemeral-demo-cluster.md) — ephemeral cluster
  lifecycle + cost model.
- [ADR-0023](../adr/0023-stay-on-autopilot.md) — GKE Autopilot vs
  Standard.
- [ADR-0024](../adr/0024-bff-observability-proxy-and-unleash-without-sdk.md)
  — *partially superseded by ADR-0026*. The BFF endpoints it introduced
  (`/obs/*`, `/features`) were removed.
- [ADR-0025](../adr/0025-ui-local-only-no-public-prod-ingress.md) — no
  public surface; UI local-only; port-forward tunnels.
- [ADR-0026](../adr/0026-spring-boot-scope-limit-no-third-party-tool-awareness.md)
  — Spring Boot scope = domain + self-admin only.
