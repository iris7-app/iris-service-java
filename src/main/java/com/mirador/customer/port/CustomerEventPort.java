package com.mirador.customer.port;

/**
 * Domain port — describes WHAT the customer domain needs to publish after
 * a customer is created, without specifying HOW.
 *
 * <p>The default implementation is
 * {@code com.mirador.messaging.KafkaCustomerEventPublisher} which wraps
 * a {@code KafkaTemplate} call in a Resilience4j {@code @Retry}
 * decorator. A test-only in-memory fake can implement this interface
 * without Spring, Kafka, or any runtime dependency — that's the whole
 * point of extracting the port.
 *
 * <h2>Deliberate design choices</h2>
 *
 * <ul>
 *   <li><b>Domain primitives only.</b> The method takes {@code (Long,
 *       String, String)} instead of a {@code CustomerCreatedEvent}
 *       record. Even though the record itself is pure (no framework
 *       annotations), it currently lives in {@code com.mirador.messaging}
 *       — referencing it from a domain port would pull a messaging
 *       package into the domain API. Passing primitives keeps the port
 *       framework-free AND messaging-free.</li>
 *   <li><b>Topic name is NOT a parameter.</b> The original non-ported
 *       {@code CustomerEventPublisher.publish(topic, key, event)} leaked
 *       Kafka topic management into {@code CustomerService}. Topic names
 *       are an adapter concern — the Kafka impl reads
 *       {@code app.kafka.topics.customer-created} itself. Swapping Kafka
 *       for RabbitMQ or an in-memory event bus requires zero change to
 *       the domain.</li>
 *   <li><b>Void return, fire-and-forget semantics.</b> Matches the
 *       Pattern 1 — fire-and-forget — already documented in
 *       {@code CustomerService.create()}. The adapter's retry +
 *       fallback handle transient failures internally; the domain
 *       caller does not branch on success/failure.</li>
 * </ul>
 *
 * <p>This is the first port extracted under the hexagonal-lite pattern
 * adopted in ADR-0044. Adding a second port follows the three-question
 * filter in that ADR.
 */
public interface CustomerEventPort {

    /**
     * Publishes the "customer created" event to whatever messaging
     * backend is wired. Fire-and-forget — exceptions inside the
     * adapter do not propagate (the adapter is responsible for retry
     * and fallback behaviour).
     *
     * @param id    the newly-assigned customer primary key (never null)
     * @param name  the customer display name (never null)
     * @param email the customer email (never null)
     */
    void publishCreated(Long id, String name, String email);
}
