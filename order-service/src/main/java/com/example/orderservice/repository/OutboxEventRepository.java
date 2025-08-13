package com.example.orderservice.repository;

import com.example.orderservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.processedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnprocessedEvents();

    @Query("SELECT o FROM OutboxEvent o WHERE o.processedAt IS NULL ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findUnprocessedEventsWithLimit(@Param("limit") int limit);

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, String aggregateId);

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.processedAt IS NULL")
    long countUnprocessedEvents();

    @Query("SELECT o FROM OutboxEvent o WHERE o.processedAt IS NOT NULL AND o.processedAt < :before")
    List<OutboxEvent> findProcessedEventsBefore(@Param("before") Instant before);
}
