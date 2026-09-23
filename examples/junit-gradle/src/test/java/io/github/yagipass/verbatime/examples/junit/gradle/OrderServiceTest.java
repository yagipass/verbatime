package io.github.yagipass.verbatime.examples.junit.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.examples.workload.InventoryRepository;
import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.github.yagipass.verbatime.examples.workload.Receipt;

final class OrderServiceTest {

    private OrderService orders;

    @BeforeEach
    void setUp() {
        orders = new OrderService();
    }

    @Test
    void placesOrder() {
        final Receipt receipt = orders.placeOrder("widget", 3);
        assertEquals("widget", receipt.sku());
        assertEquals(3, receipt.qty());
        assertTrue(receipt.cents() > 0);
        assertTrue(receipt.txId().startsWith("tx-"));
    }

    @Test
    void rejectsOutOfStock() {
        assertThrows(OutOfStockException.class, () -> orders.placeOrder("widget", InventoryRepository.STOCK + 1));
    }
}
