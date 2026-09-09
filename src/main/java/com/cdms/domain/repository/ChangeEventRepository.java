package com.cdms.domain.repository;

import com.cdms.domain.entity.ChangeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for {@link ChangeEvent} entities.
 *
 * <p><strong>Key responsibility:</strong> fast deduplication via {@link #existsByEventId(String)}.
 *
 * <p>Exactly-once guarantee flow:
 * <pre>
 *   1. existsByEventId() → quick pre-check (avoids unnecessary transaction overhead)
 *   2. INSERT change_event → DB UNIQUE(event_id) is the final safety net
 *   3. DataIntegrityViolationException on conflict → mapped to DUPLICATE result
 * </pre>
 */
@Repository
public interface ChangeEventRepository extends JpaRepository<ChangeEvent, Long> {

    /**
     * Fast existence check for deduplication.
     *
     * <p>This is Layer 1 of the exactly-once guarantee.
     * Returns {@code true} if the event has already been processed.
     * <p><strong>Note:</strong> This alone is NOT sufficient under concurrency —
     * the DB UNIQUE constraint is the final guarantee.
     *
     * @param eventId the unique event identifier
     * @return true if event has already been recorded
     */
    boolean existsByEventId(String eventId);

    /**
     * Find a specific change event by its ID.
     * Used for audit lookups and status queries.
     *
     * @param eventId the unique event identifier
     * @return the change event if found
     */
    Optional<ChangeEvent> findByEventId(String eventId);

    /**
     * Count records for a given event ID.
     * Used in concurrency tests to assert exactly-once (expected: 1).
     *
     * @param eventId the unique event identifier
     * @return number of records with this event ID (should always be 0 or 1)
     */
    long countByEventId(String eventId);

    /**
     * Count successfully processed events within a time range.
     * Used for monitoring and reporting.
     */
    @Query("SELECT COUNT(e) FROM ChangeEvent e WHERE e.status = 'PROCESSED' " +
            "AND e.createdAt BETWEEN :from AND :to")
    long countProcessedBetween(@Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    /**
     * Count duplicate events within a time range.
     * Used for monitoring — high duplicate rate may indicate CDC misconfiguration.
     */
    @Query("SELECT COUNT(e) FROM ChangeEvent e WHERE e.status = 'DUPLICATE' " +
            "AND e.createdAt BETWEEN :from AND :to")
    long countDuplicatesBetween(@Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
}
