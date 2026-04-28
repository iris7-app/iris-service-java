package org.iris.messaging;

import org.iris.customer.port.CustomerEventPort;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka implementation of {@link CustomerEventPort} — the first
 * hexagonal-lite adapter shipped under ADR-0044.
 *
 * <p>Renamed from {@code CustomerEventPublisher} on 2026-04-21. The old
 * class exposed a generic {@code publish(topic, key, event)} signature
 * that leaked Kafka concepts into {@code CustomerService}. This class
 * owns those concepts:
 *
 * <ul>
 *   <li><b>Topic binding.</b> {@code @Value("${app.kafka.topics.customer-created}")}
 *       now lives here — the domain no longer knows the topic name.</li>
 *   <li><b>Retry decorator.</b> Resilience4j {@code @Retry} annotation
 *       + {@code publishCreatedFallback} stay on the concrete class
 *       because Spring AOP proxies intercept the interface call and
 *       forward to the annotated implementation.</li>
 *   <li><b>Sync-to-Kafka conversion.</b> {@code KafkaTemplate.send}
 *       returns a {@code CompletableFuture}; we block with
 *       {@code .get(TIMEOUT, SECONDS)} so broker failures surface as
 *       exceptions the retry decorator can see.</li>
 * </ul>
 *
 * <h3>Retry strategy (application.yml: resilience4j.retry.instances.kafkaPublish)</h3>
 * <pre>
 *   attempt 1 — immediate
 *   attempt 2 — 200 ms ± jitter (± 50 %)
 *   attempt 3 — 400 ms ± jitter (exponential × 2)
 *   fallback  — log ERROR and return silently (customer row is already persisted)
 * </pre>
 *
 * <p>Swapping Kafka for RabbitMQ or an in-memory event bus is now a
 * "write another {@link CustomerEventPort} implementation" task — the
 * domain ({@code CustomerService}) does not change.
 */
@Service
public class KafkaCustomerEventPublisher implements CustomerEventPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaCustomerEventPublisher.class);
    /** Ack timeout per attempt — short enough to surface failures quickly for retry. */
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public KafkaCustomerEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.customer-created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Blocks until the broker acknowledges the record (or
     * {@link #SEND_TIMEOUT_SECONDS} elapses). Failures are surfaced as
     * {@link IllegalStateException} so Resilience4j {@code @Retry} sees
     * them and applies the backoff. After all retries are exhausted the
     * {@link #publishCreatedFallback} method is invoked reflectively and
     * logs at ERROR level — no exception escapes to the caller.
     */
    @Retry(name = "kafkaPublish", fallbackMethod = "publishCreatedFallback")
    @Override
    public void publishCreated(Long id, String name, String email) {
        CustomerCreatedEvent event = new CustomerCreatedEvent(id, name, email);
        try {
            kafkaTemplate.send(topic, String.valueOf(id), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted waiting for Kafka ack on topic=" + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            // Unwrap and rethrow so Resilience4j sees a RuntimeException and applies retry.
            throw new IllegalStateException(
                    "Kafka publish failed for topic=" + topic + " key=" + id, e);
        }
    }

    /**
     * Fallback invoked after all retry attempts are exhausted.
     *
     * <p>Logs at ERROR so the failure is visible in monitoring without
     * crashing the caller. The customer row was already persisted — the
     * event loss is acceptable in this demo; a production system would
     * use an Outbox pattern for exactly-once semantics.
     *
     * <p>Signature must mirror {@link #publishCreated} + one extra
     * {@code Throwable} parameter (Resilience4j invokes via reflection).
     */
    @SuppressWarnings("unused") // invoked reflectively by Resilience4j
    void publishCreatedFallback(Long id, String name, String email, Throwable t) {
        log.error("kafka_publish_all_retries_failed topic={} key={} cause={}",
                topic, id, t.getMessage());
    }
}
