package com.mirador.chaos;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Creates Chaos Mesh custom resources (PodChaos / NetworkChaos / StressChaos)
 * in the {@code app} namespace via the Fabric8 Kubernetes client.
 *
 * <p>The CRs are "fire and forget" — Chaos Mesh deletes them automatically
 * after their {@code duration} elapses. The UI button therefore creates
 * a fresh CR on each click with a unique timestamped name; there's no
 * stop/restart logic to maintain.
 *
 * <p>Spec templates mirror {@code deploy/kubernetes/base/chaos/experiments.yaml}
 * — that YAML stays as the declarative option (apply via {@code kubectl apply
 * -k ...base/chaos/}) and this service is its programmatic counterpart.
 */
@Service
public class ChaosService {

    private static final Logger log = LoggerFactory.getLogger(ChaosService.class);

    /** Chaos Mesh CRD group/version. All three kinds we use (PodChaos,
     *  NetworkChaos, StressChaos) share this. */
    private static final String API_VERSION = "chaos-mesh.org/v1alpha1";
    /** Namespace holding the mirador backend pods — experiments target these. */
    private static final String APP_NAMESPACE = "app";
    /** Namespace holding the postgresql pod — network delay targets it. */
    private static final String INFRA_NAMESPACE = "infra";

    // Repeated string literals centralised to keep Sonar java:S1192 clean.
    private static final String KEY_NAMESPACES      = "namespaces";
    private static final String KEY_LABEL_SELECTORS = "labelSelectors";
    private static final String KEY_MODE            = "mode";
    private static final String KEY_SELECTOR        = "selector";
    private static final String KEY_DURATION        = "duration";

    private final KubernetesClient client;

    public ChaosService(KubernetesClient client) {
        this.client = client;
    }

    /**
     * Creates a new CR for the requested experiment.
     *
     * @return the CR name that was created — the UI echoes it back to the
     *         user so they can {@code kubectl get <kind> <name>} to inspect.
     * @throws IllegalStateException when the Chaos Mesh CRDs aren't
     *         installed (404 from the API) — the controller maps this to
     *         {@code 503 Service Unavailable}.
     * @throws KubernetesClientException for any other API failure (RBAC
     *         denied, conflict, etc.) — propagates to the controller and
     *         surfaces as {@code 500}.
     */
    public String trigger(ChaosExperiment experiment) {
        String name = "mirador-" + experiment.slug() + "-" + Instant.now().getEpochSecond();

        GenericKubernetesResource cr = new GenericKubernetesResourceBuilder()
                .withApiVersion(API_VERSION)
                .withKind(experiment.kind())
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(APP_NAMESPACE)
                    .withLabels(Map.of(
                            "app.mirador.demo/chaos", experiment.slug(),
                            "app.mirador.demo/triggered-from", "ui"))
                .endMetadata()
                .build();
        cr.setAdditionalProperty("spec", buildSpec(experiment));

        try {
            // client.resource() derives the right REST endpoint from the
            // resource's apiVersion + kind via the server's CRD discovery.
            // No need to hardcode the plural (podchaos vs podchaoses vs
            // podchaoss — the plural rules for kinds ending in 'Chaos'
            // are unusual, discovery handles it safely).
            client.resource(cr).create();
            log.info("chaos_experiment_triggered name={} kind={} duration={}",
                    name, experiment.kind(), experiment.duration());
            return name;
        } catch (KubernetesClientException e) {
            // 404 when the CRD isn't registered (fast-mode cluster without
            // Chaos Mesh operator). Translate to a clear actionable error.
            if (e.getCode() == 404) {
                throw new IllegalStateException(
                        "Chaos Mesh CRDs not installed. Run `bin/cluster/demo-up.sh` (full mode) "
                                + "or install Chaos Mesh manually — see README badge and "
                                + "deploy/kubernetes/base/chaos/kustomization.yaml.", e);
            }
            throw e;
        }
    }

    /**
     * Builds the {@code spec} section of the CR. Each branch mirrors the
     * corresponding YAML block in
     * {@code deploy/kubernetes/base/chaos/experiments.yaml} so the
     * declarative and programmatic versions stay in sync.
     */
    @SuppressWarnings("java:S1452") // Map<String, Object> is unavoidable for JSON-ish spec building
    private Map<String, Object> buildSpec(ChaosExperiment exp) {
        Map<String, Object> backendSelector = Map.of(
                KEY_NAMESPACES, List.of(APP_NAMESPACE),
                KEY_LABEL_SELECTORS, Map.of(
                        "app.kubernetes.io/name", "mirador",
                        "app.kubernetes.io/component", "backend"));

        return switch (exp) {
            case POD_KILL -> Map.of(
                    "action", "pod-kill",
                    KEY_MODE, "one",          // exactly one random pod
                    KEY_DURATION, exp.duration(),
                    KEY_SELECTOR, backendSelector);

            case NETWORK_DELAY -> Map.of(
                    "action", "delay",
                    KEY_MODE, "all",          // all mirador pods get the latency
                    KEY_DURATION, exp.duration(),
                    KEY_SELECTOR, backendSelector,
                    "delay", Map.of(
                            "latency", "200ms",
                            "correlation", "50",
                            "jitter", "50ms"),
                    "direction", "to",         // egress toward target
                    "target", Map.of(
                            KEY_MODE, "all",
                            KEY_SELECTOR, Map.of(
                                    KEY_NAMESPACES, List.of(INFRA_NAMESPACE),
                                    KEY_LABEL_SELECTORS, Map.of(
                                            "app.kubernetes.io/name", "postgresql"))));

            case CPU_STRESS -> Map.of(
                    KEY_MODE, "one",
                    KEY_DURATION, exp.duration(),
                    KEY_SELECTOR, backendSelector,
                    "stressors", Map.of(
                            "cpu", Map.of(
                                    "workers", 1,
                                    "load", 70)));   // 70 % of one vCPU
        };
    }
}
