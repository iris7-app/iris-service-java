package org.iris.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TodoService — mocks JsonPlaceholderClient to avoid hitting the real API.
 *
 * Note: these tests call the service methods directly (not via Spring AOP proxy),
 * so @CircuitBreaker and @Retry annotations are NOT active. The resilience wiring
 * is verified in integration tests; these tests cover the business logic and fallback.
 */
class TodoServiceTest {

    private JsonPlaceholderClient client;
    private TodoService service;

    @BeforeEach
    void setUp() {
        client = mock(JsonPlaceholderClient.class);
        service = new TodoService(client);
    }

    @Test
    void getTodos_delegatesToClient() {
        var todo = new TodoItem(1L, 42L, "Buy milk", false);
        when(client.getTodos(42L)).thenReturn(List.of(todo));

        List<TodoItem> result = service.getTodos(42L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Buy milk");
    }

    @Test
    void getTodos_clientReturnsEmpty_returnsEmptyList() {
        when(client.getTodos(1L)).thenReturn(List.of());

        assertThat(service.getTodos(1L)).isEmpty();
    }

    @Test
    void fallbackTodos_returnsEmptyList() {
        // Fallback must return an empty list for graceful degradation
        // (caller returns 200 with empty data rather than propagating the error)
        List<TodoItem> result = service.fallbackTodos(99L, new RuntimeException("timeout"));

        assertThat(result).isEmpty();
    }

    @Test
    void fallbackTodos_doesNotThrowForAnyException() {
        // Fallback must be safe regardless of exception type
        assertThat(service.fallbackTodos(1L, new OutOfMemoryError("heap"))).isEmpty();
        assertThat(service.fallbackTodos(1L, new IllegalStateException("closed"))).isEmpty();
    }
}
