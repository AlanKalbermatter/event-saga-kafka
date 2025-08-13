package com.example.orderviews.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

public class OrderStatus {

    public enum Status {
        CREATED,
        PAYMENT_AUTHORIZED,
        PAYMENT_FAILED,
        INVENTORY_RESERVED,
        INVENTORY_REJECTED,
        SHIPPING_SCHEDULED,
        COMPLETED,
        CANCELLED
    }

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("total")
    private Double total;

    @JsonProperty("lastUpdated")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastUpdated;

    @JsonProperty("paymentId")
    private String paymentId;

    @JsonProperty("failureReason")
    private String failureReason;

    // Constructors
    public OrderStatus() {}

    public OrderStatus(String orderId, Status status, String userId, Double total, Instant lastUpdated) {
        this.orderId = orderId;
        this.status = status;
        this.userId = userId;
        this.total = total;
        this.lastUpdated = lastUpdated;
    }

    // Builder pattern for easier creation
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private OrderStatus orderStatus = new OrderStatus();

        public Builder orderId(String orderId) {
            orderStatus.orderId = orderId;
            return this;
        }

        public Builder status(Status status) {
            orderStatus.status = status;
            return this;
        }

        public Builder userId(String userId) {
            orderStatus.userId = userId;
            return this;
        }

        public Builder total(Double total) {
            orderStatus.total = total;
            return this;
        }

        public Builder lastUpdated(Instant lastUpdated) {
            orderStatus.lastUpdated = lastUpdated;
            return this;
        }

        public Builder paymentId(String paymentId) {
            orderStatus.paymentId = paymentId;
            return this;
        }

        public Builder failureReason(String failureReason) {
            orderStatus.failureReason = failureReason;
            return this;
        }

        public OrderStatus build() {
            return orderStatus;
        }
    }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderStatus that = (OrderStatus) o;
        return Objects.equals(orderId, that.orderId) &&
               status == that.status &&
               Objects.equals(userId, that.userId) &&
               Objects.equals(total, that.total) &&
               Objects.equals(lastUpdated, that.lastUpdated) &&
               Objects.equals(paymentId, that.paymentId) &&
               Objects.equals(failureReason, that.failureReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, status, userId, total, lastUpdated, paymentId, failureReason);
    }

    @Override
    public String toString() {
        return "OrderStatus{" +
               "orderId='" + orderId + '\'' +
               ", status=" + status +
               ", userId='" + userId + '\'' +
               ", total=" + total +
               ", lastUpdated=" + lastUpdated +
               ", paymentId='" + paymentId + '\'' +
               ", failureReason='" + failureReason + '\'' +
               '}';
    }
}
