package com.example.customerservice.observability;

import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Starts the Pyroscope continuous profiling agent at application startup.
 *
 * Pushes JFR CPU + allocation profiles to Pyroscope every 10s.
 * Profiling is skipped when pyroscope.server-address is blank (e.g. in tests).
 *
 * Profiles are visible at http://localhost:4040 under the "customer-service" application.
 */
@Configuration
public class PyroscopeConfig {

    private static final Logger log = LoggerFactory.getLogger(PyroscopeConfig.class);

    @Value("${pyroscope.server-address:http://localhost:4040}")
    private String serverAddress;

    @Value("${pyroscope.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void start() {
        if (!enabled || serverAddress.isBlank()) {
            log.info("Pyroscope profiling disabled (pyroscope.enabled={}, address='{}')", enabled, serverAddress);
            return;
        }
        log.info("Starting Pyroscope profiling agent → {}", serverAddress);
        PyroscopeAgent.start(
                new Config.Builder()
                        .setApplicationName("customer-service")
                        .setProfilingEvent(EventType.ITIMER)       // CPU time (wall clock)
                        .setProfilingAlloc("512k")                  // allocation profiling every 512kB
                        .setFormat(Format.JFR)
                        .setServerAddress(serverAddress)
                        .setLabels(java.util.Map.of(
                                "region", "local",
                                "env",    "dev"
                        ))
                        .build()
        );
        log.info("Pyroscope agent started — profiles at {}", serverAddress);
    }
}
