package org.iris.chaos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the {@link ChaosExperiment} enum.
 *
 * <p>No Spring context, no Kubernetes client — just verifies the
 * slug/kind/duration mapping contract that {@link ChaosController}
 * depends on for URL routing and that {@link ChaosService} uses for
 * spec building.
 */
class ChaosExperimentTest {

    @ParameterizedTest
    @CsvSource({
            "pod-kill,       PodChaos,     30s",
            "network-delay,  NetworkChaos, 1m",
            "cpu-stress,     StressChaos,  2m"
    })
    void fromSlug_resolvesKnownExperiments(String slug, String expectedKind, String expectedDuration) {
        ChaosExperiment exp = ChaosExperiment.fromSlug(slug);

        assertThat(exp.slug()).isEqualTo(slug);
        assertThat(exp.kind()).isEqualTo(expectedKind);
        assertThat(exp.duration()).isEqualTo(expectedDuration);
    }

    @Test
    void fromSlug_unknownSlugThrowsActionableException() {
        assertThatThrownBy(() -> ChaosExperiment.fromSlug("reboot-cluster"))
                .isInstanceOf(IllegalArgumentException.class)
                // The error message lists the valid slugs so a caller can fix
                // the typo without reading the enum source. ChaosController
                // surfaces this message back to the HTTP client verbatim.
                .hasMessageContaining("reboot-cluster")
                .hasMessageContaining("pod-kill")
                .hasMessageContaining("network-delay")
                .hasMessageContaining("cpu-stress");
    }

    @Test
    void allKindsMapToDistinctChaosMeshCRDs() {
        // Guards against accidental copy-paste that leaves two enum
        // constants pointing at the same CRD — which would make one of
        // the buttons a no-op from the user's perspective.
        long distinctKinds = java.util.Arrays.stream(ChaosExperiment.values())
                .map(ChaosExperiment::kind)
                .distinct()
                .count();

        assertThat(distinctKinds).isEqualTo(ChaosExperiment.values().length);
    }
}
