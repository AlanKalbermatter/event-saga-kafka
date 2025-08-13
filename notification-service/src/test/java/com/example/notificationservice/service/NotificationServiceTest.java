package com.example.notificationservice.service;

import com.example.notificationservice.entity.ProcessedEvent;
import com.example.notificationservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        reset(processedEventRepository);
    }

    @Test
    void sendOrderCompletedNotification_ShouldProcessNewEvent() {
        // Given
        String orderId = "order-123";
        String completedAt = "2024-01-01T10:00:00Z";
        String eventId = "event-123";

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

        // When
        notificationService.sendOrderCompletedNotification(orderId, completedAt, eventId);

        // Then
        verify(processedEventRepository).existsByEventId(eventId);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void sendOrderCompletedNotification_ShouldSkipAlreadyProcessedEvent() {
        // Given
        String orderId = "order-123";
        String completedAt = "2024-01-01T10:00:00Z";
        String eventId = "event-123";

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        // When
        notificationService.sendOrderCompletedNotification(orderId, completedAt, eventId);

        // Then
        verify(processedEventRepository).existsByEventId(eventId);
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void sendOrderCancelledNotification_ShouldProcessNewEvent() {
        // Given
        String orderId = "order-456";
        String reason = "Payment failed";
        String cancelledAt = "2024-01-01T11:00:00Z";
        String eventId = "event-456";

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(false);

        // When
        notificationService.sendOrderCancelledNotification(orderId, reason, cancelledAt, eventId);

        // Then
        verify(processedEventRepository).existsByEventId(eventId);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void sendOrderCancelledNotification_ShouldSkipAlreadyProcessedEvent() {
        // Given
        String orderId = "order-456";
        String reason = "Payment failed";
        String cancelledAt = "2024-01-01T11:00:00Z";
        String eventId = "event-456";

        when(processedEventRepository.existsByEventId(eventId)).thenReturn(true);

        // When
        notificationService.sendOrderCancelledNotification(orderId, reason, cancelledAt, eventId);

        // Then
        verify(processedEventRepository).existsByEventId(eventId);
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }
}
