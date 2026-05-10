package org.iris.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link OrderStatus} enum — sanity checks that the
 * 4 expected values exist and serialize to their stable string forms
 * (matches the V8 migration's CHECK constraint values).
 */
class OrderStatusTest {

    @Test
    void allFourStatusesExist() {
        assertThat(OrderStatus.values()).containsExactly(
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED,
                OrderStatus.SHIPPED,
                OrderStatus.CANCELLED
        );
    }

    @Test
    void nameStableAcrossEnumOrder() {
        // The DB CHECK constraint stores the enum NAME (per @Enumerated(STRING)).
        // Reordering the enum declaration must not change these string values.
        assertThat(OrderStatus.PENDING.name()).isEqualTo("PENDING");
        assertThat(OrderStatus.CONFIRMED.name()).isEqualTo("CONFIRMED");
        assertThat(OrderStatus.SHIPPED.name()).isEqualTo("SHIPPED");
        assertThat(OrderStatus.CANCELLED.name()).isEqualTo("CANCELLED");
    }
}
