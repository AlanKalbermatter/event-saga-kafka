package com.example.paymentservice.repository;

import com.example.paymentservice.entity.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, String> {
    boolean existsByOrderId(String orderId);
}
