package com.mirador.observability.quality.providers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DependenciesSectionProvider} reading the real
 * {@code META-INF/build-reports/pom.xml} present on the test classpath
 * after `mvn test-compile`. No fixture: the real pom is what production
 * sees.
 *
 * <p>Tests focus on Step 1 (pom.xml parsing — properties + dependencies)
 * which is purely local. The Maven Central freshness check (Step 2)
 * runs but its result depends on network availability — pinned only
 * to the "outdatedCount field exists" contract, not specific values.
 *
 * <p>Steps 3 + 4 (dependency-tree.txt, dependency-analysis.txt) are
 * optional and may produce null on a fresh checkout — handled by the
 * {@code if (treeResult != null)} guards in the provider.
 */
class DependenciesSectionProviderTest {

    private final DependenciesSectionProvider provider = new DependenciesSectionProvider();

    @Test
    void parse_returnsAvailableTrueWhenPomOnClasspath() {
        // Pinned: if this fails, the Maven step that copies pom.xml to
        // META-INF/build-reports/ is no longer running. The /actuator/
        // quality dependencies section would silently regress to false.
        Map<String, Object> result = provider.parse();

        assertThat(result).containsEntry("available", true);
    }

    @Test
    void parse_extractsAtLeastOneDependency() {
        // Pinned to a sanity floor: the real pom has 50+ dependencies.
        // A regression in the parser (e.g. wrong XPath after a Spring
        // Boot 5 pom format change) would cause this to drop to 0 first.
        Map<String, Object> result = provider.parse();

        assertThat((Integer) result.get("total")).isGreaterThan(10);
    }

    @Test
    void parse_eachDependencyHasFourRequiredFields() {
        // Schema check — the dashboard table reads {groupId, artifactId,
        // version, scope} per row. Missing field → empty cell.
        Map<String, Object> result = provider.parse();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deps = (List<Map<String, Object>>) result.get("dependencies");
        assertThat(deps).isNotEmpty();
        for (Map<String, Object> d : deps) {
            assertThat(d).containsKeys("groupId", "artifactId", "version", "scope");
            assertThat((String) d.get("groupId")).isNotEmpty();
            assertThat((String) d.get("artifactId")).isNotEmpty();
        }
    }

    @Test
    void parse_includesSpringBootStarter() {
        // Sanity floor — the project IS a Spring Boot app, so
        // spring-boot-starter or one of its derivatives MUST appear.
        // If this fails, the parser is broken (the dependency truly is in
        // pom.xml).
        Map<String, Object> result = provider.parse();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deps = (List<Map<String, Object>>) result.get("dependencies");
        assertThat(deps).anyMatch(d -> ((String) d.get("artifactId")).startsWith("spring-boot"));
    }

    @Test
    void parse_includesOutdatedCountField() {
        // The Maven Central freshness check produces a count regardless
        // of whether the network call succeeds (returns 0 on timeout/error).
        // Pinned just so the field is present — value depends on environment.
        Map<String, Object> result = provider.parse();

        assertThat(result).containsKey("outdatedCount");
        assertThat((Long) result.get("outdatedCount")).isNotNegative();
    }

    @Test
    void parse_resolvesPropertyReferencesInDependencyVersions() {
        // pom.xml uses ${spring.boot.version} style references.
        // After parse, the resolved version should NOT contain `${`
        // (would mean the property substitution didn't fire).
        Map<String, Object> result = provider.parse();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deps = (List<Map<String, Object>>) result.get("dependencies");
        for (Map<String, Object> d : deps) {
            String v = (String) d.get("version");
            // "(managed)" is the explicit default for deps without an inline
            // version (BOM-managed). That's fine — what's NOT fine is a
            // raw `${...}` placeholder (means resolution failed).
            assertThat(v).doesNotStartWith("${");
        }
    }
}
