package org.iris.chaos;

/**
 * Catalogue of supported Chaos Mesh experiments.
 *
 * <p>Each enum constant carries three attributes that the {@link ChaosService}
 * uses to build the Kubernetes custom resource:
 * <ul>
 *   <li>{@code slug} — URL-friendly identifier, e.g. {@code pod-kill}. Used
 *       as the {@code {experiment}} path variable in {@code POST /chaos/{experiment}}.</li>
 *   <li>{@code kind} — the Chaos Mesh CRD kind ({@code PodChaos},
 *       {@code NetworkChaos}, {@code StressChaos}). Maps 1:1 to the
 *       CRs in {@code deploy/kubernetes/base/chaos/experiments.yaml}.</li>
 *   <li>{@code duration} — Go-style duration string (30s / 1m / 2m). Chaos
 *       Mesh auto-deletes the CR after this elapses.</li>
 * </ul>
 *
 * <p>Adding a new experiment = one enum constant + one spec branch in
 * {@link ChaosService#buildSpec}. The controller picks it up automatically.
 */
public enum ChaosExperiment {

    /**
     * Kill a random iris backend pod with SIGKILL. Exposes Spring Boot
     * restart time on the Grafana golden-signals dashboard.
     */
    POD_KILL("pod-kill", "PodChaos", "30s"),

    /**
     * Inject 200 ms latency on all packets from iris backend pods toward
     * the postgresql pod in the {@code infra} namespace, for 1 minute.
     * Visible as a p99 spike on the DB latency panel plus downstream HTTP
     * response-time impact.
     */
    NETWORK_DELAY("network-delay", "NetworkChaos", "1m"),

    /**
     * Saturate 70 % of one vCPU on a random iris backend pod for 2
     * minutes. Should cause Resilience4j circuit breakers to trip on the
     * heaviest endpoints ({@code /bio}).
     */
    CPU_STRESS("cpu-stress", "StressChaos", "2m");

    private final String slug;
    private final String kind;
    private final String duration;

    ChaosExperiment(String slug, String kind, String duration) {
        this.slug = slug;
        this.kind = kind;
        this.duration = duration;
    }

    /**
     * Resolves a URL slug to its enum constant.
     *
     * @throws IllegalArgumentException when the slug doesn't match any
     *                                  known experiment — the controller
     *                                  maps that to HTTP 400.
     */
    public static ChaosExperiment fromSlug(String slug) {
        for (ChaosExperiment e : values()) {
            if (e.slug.equals(slug)) {
                return e;
            }
        }
        throw new IllegalArgumentException(
                "Unknown chaos experiment: '" + slug + "'. Valid: pod-kill, network-delay, cpu-stress.");
    }

    public String slug() {
        return slug;
    }

    public String kind() {
        return kind;
    }

    public String duration() {
        return duration;
    }
}
