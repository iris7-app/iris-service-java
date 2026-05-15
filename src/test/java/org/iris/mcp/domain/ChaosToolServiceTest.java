package org.iris.mcp.domain;

import org.iris.chaos.ChaosExperiment;
import org.iris.chaos.ChaosService;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChaosToolService} — covers the structured-error
 * shape returned in the three failure paths (unknown slug, Chaos Mesh
 * unavailable, generic Kubernetes API error).
 */
class ChaosToolServiceTest {

    private ChaosService chaosService;
    private ChaosToolService tool;

    @BeforeEach
    void setUp() {
        chaosService = mock(ChaosService.class);
        tool = new ChaosToolService(chaosService);
    }

    @Test
    void successPathReturnsTriggeredStatus() {
        when(chaosService.trigger(ChaosExperiment.POD_KILL))
                .thenReturn("iris-pod-kill-1730000000000");

        Map<String, Object> result = tool.triggerChaosExperiment("pod-kill");
        assertThat(result).containsEntry("status", "triggered");
        assertThat(result).containsEntry("experiment", "pod-kill");
        assertThat(result).containsEntry("kind", "PodChaos");
        assertThat(result).containsEntry("customResourceName", "iris-pod-kill-1730000000000");
    }

    @Test
    void unknownSlugReturnsErrorWithList() {
        Map<String, Object> result = tool.triggerChaosExperiment("not-a-thing");
        assertThat(result).containsEntry("status", "error");
        assertThat(result).containsEntry("error_code", "unknown_scenario");
        assertThat(result.get("message")).asString()
                .contains("Unknown chaos experiment");
    }

    @Test
    void chaosMeshUnavailableMaps404() {
        when(chaosService.trigger(ChaosExperiment.POD_KILL))
                .thenThrow(new IllegalStateException("Chaos Mesh CRDs not installed"));

        Map<String, Object> result = tool.triggerChaosExperiment("pod-kill");
        assertThat(result).containsEntry("status", "error");
        assertThat(result).containsEntry("error_code", "chaos_mesh_unavailable");
    }

    @Test
    void kubernetesApiErrorCarriesHttpCode() {
        KubernetesClientException ex = new KubernetesClientException("Forbidden", 403, null);
        when(chaosService.trigger(ChaosExperiment.NETWORK_DELAY)).thenThrow(ex);

        Map<String, Object> result = tool.triggerChaosExperiment("network-delay");
        assertThat(result).containsEntry("status", "error");
        assertThat(result).containsEntry("error_code", "kubernetes_api_error_403");
    }
}
