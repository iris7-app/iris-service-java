package com.mirador.features;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exposes the current feature-flag state to the UI.
 *
 * <p>Reads from Unleash's <em>client API</em> ({@code /api/client/features})
 * with a client-side token (read-only). Response is cached for 30 s in-memory
 * — Unleash itself recommends 15-30 s polling from client SDKs, so we match
 * that and save a round-trip per UI render.
 *
 * <p>Exposed flags today:
 * <ul>
 *   <li>{@code mirador.ui.ops-mode} — show operator panels (Chaos, Database,
 *   Pipelines) or hide them for the customer view.</li>
 *   <li>{@code mirador.bio.enabled} — kill-switch for the LLM-backed /bio
 *   endpoint. When off, the UI hides the bio button; the backend also
 *   checks this flag (via the same endpoint) and returns 503 to any
 *   direct call.</li>
 * </ul>
 *
 * <p>Why not the Unleash Java SDK? The SDK wants to run a background
 * polling thread, keep its own cache, report metrics back to Unleash, and
 * pull a {@code unleash-client-java} jar of ~1 MB. For two flags evaluated
 * once per UI render, a single RestClient call is simpler and testable.
 * See ADR-0024 for the trade-off.
 */
@Tag(name = "Feature flags", description = "Current state of UI-visible Unleash flags. Cached 30 s.")
@RestController
@RequestMapping("/features")
@PreAuthorize("isAuthenticated()")
public class FeatureFlagController {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final RestClient client;
    private final String unleashUrl;
    private final String unleashToken;
    private final AtomicReference<CachedFlags> cache = new AtomicReference<>(new CachedFlags(Map.of(), Instant.EPOCH));

    public FeatureFlagController(
            @Value("${app.features.unleash-url:http://unleash.infra.svc.cluster.local:4242/api}") String unleashUrl,
            @Value("${app.features.unleash-token:default:development:unleash-insecure-api-token}") String unleashToken) {
        this.client = RestClient.builder().build();
        this.unleashUrl = unleashUrl;
        this.unleashToken = unleashToken;
    }

    @Operation(summary = "Fetch current flag state (cached 30 s)")
    @GetMapping
    public Map<String, Boolean> currentFlags() {
        CachedFlags cached = cache.get();
        if (Instant.now().isBefore(cached.expiresAt())) {
            return cached.flags();
        }
        Map<String, Boolean> fresh = fetchFromUnleash();
        cache.set(new CachedFlags(fresh, Instant.now().plus(CACHE_TTL)));
        return fresh;
    }

    private Map<String, Boolean> fetchFromUnleash() {
        try {
            JsonNode response = client.get()
                    .uri(unleashUrl + "/client/features")
                    .header(HttpHeaders.AUTHORIZATION, unleashToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.has("features")) {
                return Collections.emptyMap();
            }
            Map<String, Boolean> out = new HashMap<>();
            response.get("features").forEach(f -> {
                // Unleash returns `enabled` at feature-level; we ignore
                // strategies for this read-only UI check. If a flag needs
                // context-based evaluation (per-user rollout), move to the
                // Unleash SDK server-side and expose a POST /evaluate here.
                String name = f.get("name").asText();
                boolean enabled = f.has("enabled") && f.get("enabled").asBoolean(false);
                out.put(name, enabled);
            });
            return out;
        } catch (RestClientResponseException e) {
            // Unleash unreachable — return the last known good cache, even
            // if stale. Better a 30-s-stale flag than a broken UI page.
            return cache.get().flags();
        } catch (Exception e) {
            return cache.get().flags();
        }
    }

    private record CachedFlags(Map<String, Boolean> flags, Instant expiresAt) {}
}
