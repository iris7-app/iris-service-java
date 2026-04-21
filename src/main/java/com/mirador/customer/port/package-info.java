/**
 * Domain ports for the customer feature slice.
 *
 * <p>Ports are interfaces the customer domain owns that describe WHAT
 * external capability is needed without specifying HOW. Implementations
 * live in adapter packages (e.g. {@code com.mirador.messaging.KafkaCustomerEventPublisher}
 * implements {@link com.mirador.customer.port.CustomerEventPort} using Apache
 * Kafka).
 *
 * <h2>Port-layer invariant</h2>
 *
 * Classes in this package MUST NOT depend on any framework type — no
 * Spring, no JPA, no Jackson, no messaging client libraries. This is
 * enforced at build time by {@code ArchitectureTest.
 * domain_ports_must_not_depend_on_framework_packages}.
 *
 * <h2>Why a {@code port/} sub-package, not just "interfaces wherever"</h2>
 *
 * Per {@link com.mirador.customer} (see ADR-0044 superseding ADR-0008),
 * the project uses feature-slicing as the top-level package layout.
 * Within a feature, framework coupling is legal by default — the
 * {@code port/} sub-package is the one exception: it marks the boundary
 * where the domain exposes a plug-in point. Keeping ports in their own
 * package makes the "framework-free" rule mechanically enforceable with
 * ArchUnit and visually obvious to reviewers.
 *
 * <h2>Scope (as of 2026-04-21)</h2>
 *
 * Only {@link CustomerEventPort} lives here — the sample port for the
 * hexagonal-lite pattern described in ADR-0044. New ports land here on
 * the same three-question filter documented in the ADR:
 *
 * <ol>
 *   <li>Does the dependency have a plausible second implementation?</li>
 *   <li>Would a framework-free unit test genuinely add value?</li>
 *   <li>Is the impl leaking framework concerns into domain code?</li>
 * </ol>
 *
 * All three YES → port candidate. Otherwise stay on the ADR-0008 default
 * (direct framework usage in the feature package).
 */
package com.mirador.customer.port;
