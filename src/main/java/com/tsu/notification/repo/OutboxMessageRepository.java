package com.tsu.notification.repo;

import com.tsu.notification.entities.OutboxMessageTb;
import com.tsu.enums.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessageTb, UUID> {

    /**
     * Find pending events ready for processing with pessimistic lock
     * CRITICAL: Uses SKIP LOCKED to prevent concurrent processing
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        SELECT * FROM outbox_message
        WHERE status = :status
        OR (status = 'FAILED' AND attempt_count < max_attempts AND next_attempt_at <= :time)
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxMessageTb> findOutboxMessageForUpdate(
        @Param("status") OutboxStatus status,
        @Param("time") LocalDateTime nextAttemptTime,
        @Param("limit") int limit
    );

    /**
     * Find events by aggregate
     */
    List<OutboxMessageTb> findByMessageTypeAndMessageId(String aggregateType, UUID aggregateId);

    /**
     * Find events by status
     */
    List<OutboxMessageTb> findByStatus(OutboxStatus status, Pageable pageable);

    /**
     * Count pending events
     */
    long countByStatus(OutboxStatus status);

    /**
     * Delete old processed events (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM OutboxMessageTb o WHERE o.status = 'PROCESSED' AND o.processedAt < :threshold")
    int deleteProcessedMessagesBefore(@Param("threshold") Instant threshold);

    /**
     * Find stuck events (processing for too long)
     */
    @Query("""
        SELECT o FROM OutboxMessageTb o
        WHERE o.processingStartedAt IS NOT NULL
        AND o.processedAt IS NULL
        AND o.processingStartedAt < :threshold
        """)
    Stream<OutboxMessageTb> findStuckMessages(@Param("threshold") Instant threshold);

    /**
     * Reset stuck events back to pending
     */
    @Modifying
    @Query("""
        UPDATE OutboxMessageTb o
        SET o.status = 'PENDING',
            o.processingStartedAt = NULL
        WHERE o.id IN :ids
        """)
    int resetStuckMessages(@Param("ids") List<UUID> ids);
}
