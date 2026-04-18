package com.mirador.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Backend-for-Frontend (BFF) proxy for observability data the UI would
 * otherwise have to fetch from Loki and Tempo directly.
 *
 * <h3>Why a proxy, not a direct browser call?</h3>
 * Three reasons, in priority order:
 * <ol>
 *   <li><b>Single auth surface</b> — the UI already sends its JWT to
 *   {@code /api}; this controller is just another authenticated endpoint.
 *   Loki/Tempo themselves are unauthenticated inside the cluster, and
 *   exposing them to the public ingress would require wiring up a second
 *   OAuth2 proxy. Avoided.</li>
 *   <li><b>No CORS headaches</b> — the UI talks to a single origin
 *   ({@code customer-ui.mirador1.duckdns.org}). No preflight for each
 *   observability tool.</li>
 *   <li><b>Topology hiding</b> — the browser never learns about LGTM's
 *   internal DNS or ports. We can swap Loki for a Grafana Cloud endpoint
 *   tomorrow without touching the UI.</li>
 * </ol>
 *
 * <h3>What this controller is NOT</h3>
 * Not a full-featured proxy — it forwards a specific subset of GET paths
 * the UI actually needs, with the query string passed through as-is:
 * <ul>
 *   <li>{@code GET /obs/loki/query_range?query=...&start=...&end=...} →
 *   {@code Loki /loki/api/v1/query_range}</li>
 *   <li>{@code GET /obs/tempo/traces/{id}} →
 *   {@code Tempo /api/traces/{id}}</li>
 * </ul>
 *
 * <p>Writes, admin APIs, Grafana dashboards, and Argo CD / Chaos Mesh
 * dashboards are deliberately excluded. See ADR-0024 for the scope.
 */
@Tag(name = "BFF — Observability", description = "Proxy to Loki + Tempo for the UI. Read-only, auth-gated.")
@RestController
@RequestMapping("/obs")
@PreAuthorize("isAuthenticated()")
public class BffObservabilityController {

    private final RestClient client;
    private final String lokiUrl;
    private final String tempoUrl;

    public BffObservabilityController(
            @Value("${app.observability.loki-url:http://lgtm.infra.svc.cluster.local:3100}") String lokiUrl,
            @Value("${app.observability.tempo-url:http://lgtm.infra.svc.cluster.local:3200}") String tempoUrl) {
        // Default RestClient — no connection pooling tuned, no timeouts beyond
        // JDK defaults. LGTM is in-cluster and fast; if it ever takes >10s to
        // respond to a query, the UI's loading spinner is the least of our
        // problems.
        this.client = RestClient.builder().build();
        this.lokiUrl = lokiUrl;
        this.tempoUrl = tempoUrl;
    }

    @Operation(summary = "Proxy a LogQL range query to Loki")
    @GetMapping(path = "/loki/query_range", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> lokiQueryRange(
            @RequestParam String query,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) Integer limit) {
        return forward(client
                .get()
                .uri(b -> b
                        .scheme("http")
                        .host(extractHost(lokiUrl))
                        .port(extractPort(lokiUrl, 3100))
                        .path("/loki/api/v1/query_range")
                        .queryParam("query", query)
                        .queryParamIfPresent("start", java.util.Optional.ofNullable(start))
                        .queryParamIfPresent("end", java.util.Optional.ofNullable(end))
                        .queryParamIfPresent("limit", java.util.Optional.ofNullable(limit))
                        .build()));
    }

    @Operation(summary = "Proxy a trace-by-id lookup to Tempo")
    @GetMapping(path = "/tempo/traces/{traceId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> tempoTraceById(@org.springframework.web.bind.annotation.PathVariable String traceId) {
        return forward(client
                .get()
                .uri(b -> b
                        .scheme("http")
                        .host(extractHost(tempoUrl))
                        .port(extractPort(tempoUrl, 3200))
                        .path("/api/traces/{id}")
                        .build(Map.of("id", traceId))));
    }

    private ResponseEntity<String> forward(RestClient.RequestHeadersSpec<?> spec) {
        try {
            String body = spec.retrieve().body(String.class);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (RestClientResponseException e) {
            // Bubble up the upstream's status code so the UI can distinguish
            // 404 (trace not found) from 502 (LGTM down). Don't wrap in a
            // generic 500 — that would hide real issues.
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"error\":\"upstream unreachable\"}");
        }
    }

    private static String extractHost(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return "localhost";
        }
    }

    private static int extractPort(String url, int fallback) {
        try {
            int p = java.net.URI.create(url).getPort();
            return p == -1 ? fallback : p;
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
