package org.iris.chaos;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

/**
 * REST endpoints for on-demand Chaos Mesh experiments.
 *
 * <p>The whole endpoint family is {@code ROLE_ADMIN}-only — triggering a
 * pod kill or CPU stress is destructive, no read-only role should have
 * this capability. See {@link org.iris.auth.SecurityConfig}
 * {@code /chaos/**} rule.
 */
@RestController
@RequestMapping("/chaos")
@Tag(name = "Chaos", description = "On-demand Chaos Mesh experiments (admin-only)")
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    private final ChaosService chaosService;

    public ChaosController(ChaosService chaosService) {
        this.chaosService = chaosService;
    }

    /**
     * Returns the catalogue of available experiments. Useful for UIs that
     * build the button list dynamically instead of hard-coding it. Still
     * ADMIN-only: the shape of the catalogue reveals attack surface and
     * there's no anonymous use-case for it.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List available chaos experiments")
    public ResponseEntity<Map<String, Object>> catalog() {
        return ResponseEntity.ok(Map.of(
                "experiments", Arrays.stream(ChaosExperiment.values())
                        .map(e -> Map.of(
                                "slug", e.slug(),
                                "kind", e.kind(),
                                "duration", e.duration()))
                        .toList()));
    }

    /**
     * Triggers the named experiment. The CR is created with a unique
     * timestamped name; Chaos Mesh auto-deletes it after the declared
     * duration elapses.
     *
     * @param experiment URL slug — one of {@code pod-kill},
     *                   {@code network-delay}, {@code cpu-stress}
     * @return 200 + CR name on success,
     *         400 if the slug is unknown,
     *         503 if Chaos Mesh CRDs aren't installed on the cluster,
     *         500 for any other Kubernetes API failure (RBAC denied,
     *         conflict, etc.)
     */
    @PostMapping("/{experiment}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger a chaos experiment")
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable String experiment) {
        try {
            ChaosExperiment exp = ChaosExperiment.fromSlug(experiment);
            String crName = chaosService.trigger(exp);
            return ResponseEntity.ok(Map.of(
                    "experiment", exp.slug(),
                    "kind", exp.kind(),
                    "customResourceName", crName,
                    "duration", exp.duration(),
                    "status", "triggered"));
        } catch (IllegalArgumentException e) {
            // Unknown slug — echo the valid list back to help the caller.
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()));
        } catch (IllegalStateException e) {
            // Chaos Mesh CRDs not registered — actionable message already
            // embedded in the exception (see ChaosService).
            log.warn("chaos_trigger_unavailable experiment={} reason={}",
                    experiment, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", e.getMessage()));
        } catch (KubernetesClientException e) {
            // RBAC, conflict, generic API failure — log and surface 500.
            log.error("chaos_trigger_failed experiment={} code={} message={}",
                    experiment, e.getCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Kubernetes API error: " + e.getMessage(),
                    "code", e.getCode()));
        }
    }
}
