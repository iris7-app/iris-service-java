package com.mirador.observability;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the Maven site HTML from target/site/ at /maven-site/ during local development.
 *
 * Run `mvn site` first to generate the reports, then access them at:
 *   http://localhost:8080/maven-site/index.html
 *
 * In CI, the maven-site job generates and publishes the site as a pipeline artifact.
 * If embedded static resources exist at classpath:/static/maven-site/, Spring Boot
 * will serve them automatically (the classpath takes precedence over this handler).
 *
 * Note: file: URIs in addResourceLocations() are resolved relative to the JVM working
 * directory (user.dir), which for `mvnw spring-boot:run` is the project root. The absolute
 * path is built explicitly to avoid ambiguity when the app is started from another directory.
 */
@Configuration
public class MavenSiteConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Absolute path to target/site/ — avoids relative-path issues when the app is
        // started from outside the project root (e.g., from a shell script or IDE).
        String targetSite = "file:" + System.getProperty("user.dir") + "/target/site/";

        registry.addResourceHandler("/maven-site/**")
                .addResourceLocations(
                        "classpath:/static/maven-site/",  // embedded in JAR (production)
                        targetSite                         // local dev: mvn site output
                );
    }
}
