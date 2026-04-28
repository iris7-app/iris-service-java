package org.iris.chaos;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fabric8 Kubernetes client wiring for the chaos feature.
 *
 * <p>The builder is lazy: it does NOT open a connection at startup. Bean
 * creation succeeds even off-cluster without a kubeconfig. The first
 * actual API call (i.e. the first {@code POST /chaos/...}) attempts to
 * connect — and if no auth context is available, fails with a
 * {@code KubernetesClientException} that {@link ChaosController} maps to
 * {@code 503 Service Unavailable}.
 *
 * <p>Inside the cluster, the client auto-discovers credentials from
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/} (injected by
 * the kubelet when {@code serviceAccountName: iris-backend} is set
 * in the Deployment spec). See
 * {@code deploy/kubernetes/base/backend/deployment.yaml} + {@code rbac.yaml}.
 *
 * <p>Outside the cluster (local dev on laptop), the client reads
 * {@code ~/.kube/config}. If that file is absent or empty, API calls
 * fail with a clear error rather than the bean throwing at boot.
 */
@Configuration
public class ChaosConfig {

    /**
     * A singleton {@link KubernetesClient} shared by {@link ChaosService}.
     * Fabric8's client is thread-safe and long-lived — one bean per app
     * is the recommended pattern.
     */
    @Bean
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
