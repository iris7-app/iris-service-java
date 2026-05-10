package org.iris.product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProductDto#from(Product)} mapper.
 *
 * <p>Trivial 1-to-1 field copy — but a regression here would silently
 * leak entity nulls or wrong fields, so explicit coverage matters.
 */
class ProductDtoTest {

    @Test
    void from_copiesAllFields() {
        Instant now = Instant.parse("2026-04-26T12:00:00Z");
        Product p = new Product(
                42L,
                "TestProduct",
                "A test description",
                new BigDecimal("19.99"),
                50,
                now,
                now
        );

        ProductDto dto = ProductDto.from(p);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.name()).isEqualTo("TestProduct");
        assertThat(dto.description()).isEqualTo("A test description");
        assertThat(dto.unitPrice()).isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(dto.stockQuantity()).isEqualTo(50);
        assertThat(dto.createdAt()).isEqualTo(now);
        assertThat(dto.updatedAt()).isEqualTo(now);
    }

    @Test
    void from_propagatesNullDescription() {
        Product p = new Product(
                1L,
                "Minimal",
                null,
                BigDecimal.ZERO,
                0,
                Instant.EPOCH,
                Instant.EPOCH
        );

        ProductDto dto = ProductDto.from(p);

        assertThat(dto.description()).isNull();
    }
}
