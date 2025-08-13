package com.example.inventoryservice.service;

import com.example.events.*;
import com.example.inventoryservice.entity.Inventory;
import com.example.inventoryservice.producer.InventoryRejectedProducer;
import com.example.inventoryservice.producer.InventoryReservedProducer;
import com.example.inventoryservice.producer.PaymentFailedProducer;
import com.example.inventoryservice.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryReservedProducer inventoryReservedProducer;
    private final InventoryRejectedProducer inventoryRejectedProducer;
    private final PaymentFailedProducer paymentFailedProducer;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryReservedProducer inventoryReservedProducer,
            InventoryRejectedProducer inventoryRejectedProducer,
            PaymentFailedProducer paymentFailedProducer) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryReservedProducer = inventoryReservedProducer;
        this.inventoryRejectedProducer = inventoryRejectedProducer;
        this.paymentFailedProducer = paymentFailedProducer;
    }

    @Transactional
    public void processOrderCreated(String orderId, OrderCreated orderCreated) {
        logger.info("Processing inventory reservation for order: {}", orderId);

        try {
            // Extract SKUs from the order created event
            List<String> skus = orderCreated.getItems().stream()
                    .map(item -> item.getSku().toString())
                    .toList();

            // Lock inventory items for update to prevent race conditions
            List<Inventory> inventoryItems = inventoryRepository.findBySkusForUpdate(skus);

            // Check if all items are available
            List<ReservedItem> reservedItems = new ArrayList<>();
            String rejectionReason = null;

            for (var orderItem : orderCreated.getItems()) {
                String sku = orderItem.getSku().toString();
                int requestedQty = orderItem.getQty();

                Optional<Inventory> inventoryOpt = inventoryItems.stream()
                        .filter(inv -> inv.getSku().equals(sku))
                        .findFirst();

                if (inventoryOpt.isEmpty()) {
                    rejectionReason = "SKU not found: " + sku;
                    break;
                }

                Inventory inventory = inventoryOpt.get();
                if (!inventory.canReserve(requestedQty)) {
                    rejectionReason = String.format("Insufficient stock for SKU %s. Requested: %d, Available: %d",
                            sku, requestedQty, inventory.getAvailableQty());
                    break;
                }
            }

            if (rejectionReason != null) {
                // Inventory reservation failed - emit compensation and rejection events
                handleInventoryRejection(orderId, rejectionReason, orderCreated);
            } else {
                // Reserve all items
                for (var orderItem : orderCreated.getItems()) {
                    String sku = orderItem.getSku().toString();
                    int requestedQty = orderItem.getQty();

                    Inventory inventory = inventoryItems.stream()
                            .filter(inv -> inv.getSku().equals(sku))
                            .findFirst()
                            .orElseThrow();

                    inventory.reserve(requestedQty);
                    inventoryRepository.save(inventory);

                    reservedItems.add(ReservedItem.newBuilder()
                            .setSku(sku)
                            .setQty(requestedQty)
                            .build());

                    logger.info("Reserved {} units of SKU {} for order {}", requestedQty, sku, orderId);
                }

                // Publish inventory reserved event
                InventoryReserved inventoryReserved = InventoryReserved.newBuilder()
                        .setOrderId(orderId)
                        .setItems(reservedItems)
                        .setWarehouseId("MAIN_WAREHOUSE") // Could be dynamic based on business logic
                        .setReservedAt(Instant.now().toString())
                        .setVersion(1)
                        .build();

                inventoryReservedProducer.publishInventoryReserved(orderId, inventoryReserved);
                logger.info("Successfully reserved inventory for order: {}", orderId);
            }

        } catch (Exception e) {
            logger.error("Unexpected error processing inventory for order: {}", orderId, e);
            throw e;
        }
    }

    private void handleInventoryRejection(String orderId, String reason, OrderCreated orderCreated) {
        logger.warn("Inventory reservation failed for order: {}. Reason: {}", orderId, reason);

        // Emit inventory rejected event
        InventoryRejected inventoryRejected = InventoryRejected.newBuilder()
                .setOrderId(orderId)
                .setReason(reason)
                .setRejectedAt(Instant.now().toString())
                .setVersion(1)
                .build();

        inventoryRejectedProducer.publishInventoryRejected(orderId, inventoryRejected);

        // Emit compensation event to trigger order cancellation
        // Since we're processing order.created events, we emit payment.failed to prevent payment processing
        PaymentFailed paymentFailed = PaymentFailed.newBuilder()
                .setOrderId(orderId)
                .setPaymentId("PENDING-" + orderId) // Payment hasn't been created yet
                .setReason("Inventory reservation failed: " + reason)
                .setFailedAt(Instant.now().toString())
                .setVersion(1)
                .build();

        paymentFailedProducer.publishPaymentFailed(orderId, paymentFailed);
        logger.info("Published compensation events for failed inventory reservation on order: {}", orderId);
    }

    @Transactional
    public void seedInventory(String sku, int quantity) {
        Optional<Inventory> existing = inventoryRepository.findBySku(sku);
        if (existing.isPresent()) {
            Inventory inventory = existing.get();
            inventory.setAvailableQty(inventory.getAvailableQty() + quantity);
            inventoryRepository.save(inventory);
            logger.info("Added {} units to existing SKU: {}. New available quantity: {}",
                    quantity, sku, inventory.getAvailableQty());
        } else {
            Inventory newInventory = new Inventory(sku, quantity);
            inventoryRepository.save(newInventory);
            logger.info("Created new inventory item for SKU: {} with {} units", sku, quantity);
        }
    }

    @Transactional(readOnly = true)
    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Inventory> getInventoryBySku(String sku) {
        return inventoryRepository.findBySku(sku);
    }
}
