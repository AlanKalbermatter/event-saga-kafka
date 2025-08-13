package com.example.paymentservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_orders")
public class ProcessedOrder {

    @Id
    private String orderId;

    @Column(nullable = false)
    private String status; // PROCESSING, AUTHORIZED, FAILED

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @Column
    private Integer retryAttempts = 0;

    protected ProcessedOrder() {}

    public ProcessedOrder(String orderId, String status) {
        this.orderId = orderId;
        this.status = status;
        this.processedAt = LocalDateTime.now();
    }

    // Getters and setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public Integer getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(Integer retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public void incrementRetryAttempts() {
        this.retryAttempts = (this.retryAttempts == null ? 0 : this.retryAttempts) + 1;
    }
}
