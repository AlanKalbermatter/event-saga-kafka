package com.example.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CreateOrderRequest {

    @NotBlank(message = "User ID is required")
    @JsonProperty("userId")
    private String userId;

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    @JsonProperty("items")
    private List<OrderItemDto> items;

    // Constructors
    public CreateOrderRequest() {}

    public CreateOrderRequest(String userId, List<OrderItemDto> items) {
        this.userId = userId;
        this.items = items;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "CreateOrderRequest{" +
                "userId='" + userId + '\'' +
                ", items=" + items +
                '}';
    }
}
