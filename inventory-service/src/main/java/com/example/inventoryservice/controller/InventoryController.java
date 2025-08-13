package com.example.inventoryservice.controller;

import com.example.inventoryservice.entity.Inventory;
import com.example.inventoryservice.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seedInventory(@Valid @RequestBody SeedInventoryRequest request) {
        logger.info("Seeding inventory for SKU: {} with quantity: {}", request.sku, request.quantity);

        inventoryService.seedInventory(request.sku, request.quantity);

        return ResponseEntity.ok("Successfully seeded inventory for SKU: " + request.sku);
    }

    @GetMapping
    public ResponseEntity<List<Inventory>> getAllInventory() {
        List<Inventory> inventory = inventoryService.getAllInventory();
        return ResponseEntity.ok(inventory);
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Inventory> getInventoryBySku(@PathVariable String sku) {
        Optional<Inventory> inventory = inventoryService.getInventoryBySku(sku);

        if (inventory.isPresent()) {
            return ResponseEntity.ok(inventory.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    public static class SeedInventoryRequest {
        @NotBlank(message = "SKU is required")
        public String sku;

        @Min(value = 1, message = "Quantity must be at least 1")
        public int quantity;

        // Default constructor for JSON deserialization
        public SeedInventoryRequest() {}

        public SeedInventoryRequest(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }
}
