# TASKS — iris-service-java

Open work only. Per `~/.claude/CLAUDE.md` rules : Java-only items
here ; done items removed (use `git tag -l` for history).

---

## 📊 RPO measurement (paused)

RTO measured 2026-04-28 : **7 seconds** for postgres pod-kill on
GKE Autopilot ([shared/docs/runbooks/rto-rpo-measurement.md](https://gitlab.com/iris-7/iris-service-shared/-/blob/main/docs/runbooks/rto-rpo-measurement.md)).
Beats 30 s SLA target.

Remaining : **RPO measurement** with steady-state write traffic
during chaos window (k6 at 50 req/s POSTing /customers, count
post-recovery `SELECT id FROM customer WHERE id IN (...)` holes).
Cluster #2 was torn down to save costs — restart via
`bin/cluster/demo/up.sh` when ready, deploy iris-service-java alongside
postgres for write traffic.

## 🎯 e-commerce coverage — last item

JaCoCo bundle 94.97%, order/product packages 100%, Spring Boot IT
landed via stable-v1.2.16/17, smoke.hurl already covers the order/
product/lines flow (section 9bis), bin/dev/sections/code.sh already
loops over order + product slices. Remaining :

- ☐ PIT mutations score ≥ 75 % on `org.iris.{order,product}.*`
  (slow — `mvn test-compile org.pitest:pitest-maven:mutationCoverage`
  takes ~5-10 min on the e-commerce slice ; defer to a scheduled
  batch unless the time is budgeted)

## 🎨 SLO dashboard screenshots

5 panels to capture (SLO overview / breakdown by endpoint / latency
heatmap / Apdex / chaos demo) once cluster restart + Grafana up.
Procedure documented around 2026-04-28 08:55.
