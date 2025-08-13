package com.example.inventoryservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String sku;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer availableQty;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer reservedQty = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Default constructor for JPA
    public Inventory() {}

    public Inventory(String sku, Integer availableQty) {
        this.sku = sku;
        this.availableQty = availableQty;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(Integer availableQty) {
        this.availableQty = availableQty;
    }

    public Integer getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(Integer reservedQty) {
        this.reservedQty = reservedQty;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean canReserve(int quantity) {
        return availableQty >= quantity;
    }

    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalArgumentException("Insufficient stock to reserve " + quantity + " items. Available: " + availableQty);
        }
        this.availableQty -= quantity;
        this.reservedQty += quantity;
    }

    public void releaseReservation(int quantity) {
        if (reservedQty < quantity) {
            throw new IllegalArgumentException("Cannot release " + quantity + " items. Reserved: " + reservedQty);
        }
        this.reservedQty -= quantity;
        this.availableQty += quantity;
    }
}
