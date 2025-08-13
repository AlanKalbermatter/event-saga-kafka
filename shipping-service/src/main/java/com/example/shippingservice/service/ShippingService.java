package com.example.shippingservice.service;

import com.example.events.InventoryReserved;
import com.example.events.PaymentAuthorized;
import com.example.events.ShippingScheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private static final List<String> CARRIERS = List.of(
        "FedEx", "UPS", "DHL", "USPS", "Amazon Logistics"
    );

    private final Random random = new Random();

    public ShippingScheduled scheduleShipping(PaymentAuthorized payment, InventoryReserved inventory) {
        log.info("Scheduling shipping for order: {}", payment.getOrderId());

        // Generate unique shipment ID
        String shipmentId = generateShipmentId();

        // Select carrier based on simple logic (could be more sophisticated)
        String carrier = selectCarrier(payment.getAmount(), inventory.getItems().size());

        // Calculate ETA (2-5 business days from now)
        String eta = calculateEta(carrier);

        // Create shipping scheduled event
        return ShippingScheduled.newBuilder()
            .setOrderId(payment.getOrderId())
            .setShipmentId(shipmentId)
            .setCarrier(carrier)
            .setEta(eta)
            .setScheduledAt(Instant.now().toString())
            .build();
    }

    private String generateShipmentId() {
        return "SH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String selectCarrier(double amount, int itemCount) {
        // Simple carrier selection logic based on order value and item count
        if (amount > 500) {
            return "FedEx"; // Premium service for high-value orders
        } else if (itemCount > 10) {
            return "UPS"; // Good for bulk orders
        } else if (amount < 50) {
            return "USPS"; // Economical for small orders
        } else {
            // Random selection for other cases
            return CARRIERS.get(random.nextInt(CARRIERS.size()));
        }
    }

    private String calculateEta(String carrier) {
        // Calculate ETA based on carrier (simplified logic)
        int daysToAdd = switch (carrier) {
            case "Amazon Logistics" -> 1 + random.nextInt(2); // 1-2 days
            case "FedEx" -> 2 + random.nextInt(2); // 2-3 days
            case "UPS" -> 2 + random.nextInt(3); // 2-4 days
            case "DHL" -> 3 + random.nextInt(2); // 3-4 days
            case "USPS" -> 3 + random.nextInt(3); // 3-5 days
            default -> 3 + random.nextInt(3); // 3-5 days default
        };

        return Instant.now().plus(daysToAdd, ChronoUnit.DAYS).toString();
    }
}
