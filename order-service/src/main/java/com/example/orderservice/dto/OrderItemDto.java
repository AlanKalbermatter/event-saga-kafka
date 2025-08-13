package com.example.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class OrderItemDto {

    @NotBlank(message = "SKU is required")
    @JsonProperty("sku")
    private String sku;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @JsonProperty("qty")
    private Integer qty;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @JsonProperty("price")
    private BigDecimal price;

    // Constructors
    public OrderItemDto() {}

    public OrderItemDto(String sku, Integer qty, BigDecimal price) {
        this.sku = sku;
        this.qty = qty;
        this.price = price;
    }

    // Getters and Setters
    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "OrderItemDto{" +
                "sku='" + sku + '\'' +
                ", qty=" + qty +
                ", price=" + price +
                '}';
    }
}
