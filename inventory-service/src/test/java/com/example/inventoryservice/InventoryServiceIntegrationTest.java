package com.example.inventoryservice;

import com.example.events.*;
import com.example.inventoryservice.entity.Inventory;
import com.example.inventoryservice.repository.InventoryRepository;
import com.example.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
    topics = {"test.order.created", "test.inventory.reserved", "test.inventory.rejected", "test.payment.failed"})
@ActiveProfiles("test")
@DirtiesContext
public class InventoryServiceIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    @Transactional
    public void setUp() {
        inventoryRepository.deleteAll();

        // Seed test inventory
        inventoryService.seedInventory("SKU001", 100);
        inventoryService.seedInventory("SKU002", 50);
        inventoryService.seedInventory("SKU003", 10);
    }

    @Test
    @Transactional
    public void testSuccessfulInventoryReservation() {
        // Given
        String orderId = "ORDER-001";
        OrderCreated orderCreated = createOrderCreatedEvent(orderId);

        // When
        inventoryService.processOrderCreated(orderId, orderCreated);

        // Then
        Optional<Inventory> sku001 = inventoryRepository.findBySku("SKU001");
        assertTrue(sku001.isPresent());
        assertEquals(95, sku001.get().getAvailableQty()); // 100 - 5
        assertEquals(5, sku001.get().getReservedQty());

        Optional<Inventory> sku002 = inventoryRepository.findBySku("SKU002");
        assertTrue(sku002.isPresent());
        assertEquals(47, sku002.get().getAvailableQty()); // 50 - 3
        assertEquals(3, sku002.get().getReservedQty());
    }

    @Test
    @Transactional
    public void testInventoryRejectionDueToInsufficientStock() {
        // Given - Create an order that exceeds available stock
        String orderId = "ORDER-002";
        OrderCreated orderCreated = OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId("USER-123")
                .setTotal(150.0)
                .setItems(Arrays.asList(
                        OrderItem.newBuilder()
                                .setSku("SKU003")
                                .setQty(15) // Only 10 available
                                .setPrice(10.0)
                                .build()
                ))
                .setCreatedAt(Instant.now().toString())
                .setVersion(1)
                .build();

        // When
        inventoryService.processOrderCreated(orderId, orderCreated);

        // Then - Inventory should remain unchanged
        Optional<Inventory> sku003 = inventoryRepository.findBySku("SKU003");
        assertTrue(sku003.isPresent());
        assertEquals(10, sku003.get().getAvailableQty()); // Unchanged
        assertEquals(0, sku003.get().getReservedQty()); // No reservation made
    }

    @Test
    @Transactional
    public void testInventoryRejectionForNonExistentSku() {
        // Given
        String orderId = "ORDER-003";
        OrderCreated orderCreated = OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId("USER-123")
                .setTotal(50.0)
                .setItems(Arrays.asList(
                        OrderItem.newBuilder()
                                .setSku("NONEXISTENT-SKU")
                                .setQty(1)
                                .setPrice(50.0)
                                .build()
                ))
                .setCreatedAt(Instant.now().toString())
                .setVersion(1)
                .build();

        // When
        inventoryService.processOrderCreated(orderId, orderCreated);

        // Then - Should handle gracefully without affecting existing inventory
        assertEquals(3, inventoryRepository.count()); // Original 3 SKUs remain
    }

    @Test
    @Transactional
    public void testSeedInventoryForExistingSku() {
        // Given
        String sku = "SKU001";
        int initialQty = inventoryRepository.findBySku(sku).get().getAvailableQty();

        // When
        inventoryService.seedInventory(sku, 25);

        // Then
        Optional<Inventory> updated = inventoryRepository.findBySku(sku);
        assertTrue(updated.isPresent());
        assertEquals(initialQty + 25, updated.get().getAvailableQty());
    }

    @Test
    @Transactional
    public void testSeedInventoryForNewSku() {
        // Given
        String newSku = "SKU004";
        assertFalse(inventoryRepository.findBySku(newSku).isPresent());

        // When
        inventoryService.seedInventory(newSku, 75);

        // Then
        Optional<Inventory> newInventory = inventoryRepository.findBySku(newSku);
        assertTrue(newInventory.isPresent());
        assertEquals(75, newInventory.get().getAvailableQty());
        assertEquals(0, newInventory.get().getReservedQty());
    }

    private OrderCreated createOrderCreatedEvent(String orderId) {
        return OrderCreated.newBuilder()
                .setOrderId(orderId)
                .setUserId("USER-123")
                .setTotal(80.0)
                .setItems(Arrays.asList(
                        OrderItem.newBuilder()
                                .setSku("SKU001")
                                .setQty(5)
                                .setPrice(10.0)
                                .build(),
                        OrderItem.newBuilder()
                                .setSku("SKU002")
                                .setQty(3)
                                .setPrice(10.0)
                                .build()
                ))
                .setCreatedAt(Instant.now().toString())
                .setVersion(1)
                .build();
    }
}
