package com.logbook.logbookbackend.repository;

import com.logbook.logbookbackend.entity.Entry;
import com.logbook.logbookbackend.entity.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EntryRepository extends JpaRepository<Entry, Long> {

    // Scope single-entry lookup to user
    Optional<Entry> findByIdAndUserId(Long id, Long userId);

    // All entries for a specific date (user-scoped)
    List<Entry> findByUserIdAndEntryDateOrderByCreatedAtAsc(Long userId, LocalDate date);

    // Calendar month fetch (user-scoped)
    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.tags " +
            "WHERE e.user.id = :userId " +
            "AND e.entryDate BETWEEN :startDate AND :endDate " +
            "ORDER BY e.entryDate ASC, e.createdAt ASC")
    List<Entry> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // With type filter
    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.tags " +
            "WHERE e.user.id = :userId " +
            "AND e.entryDate BETWEEN :startDate AND :endDate " +
            "AND e.entryType = :type " +
            "ORDER BY e.entryDate ASC")
    List<Entry> findByUserIdAndDateRangeAndType(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("type") EntryType type
    );

    // Heatmap: count per day (user-scoped)
    @Query("SELECT e.entryDate, COUNT(e) FROM Entry e " +
            "WHERE e.user.id = :userId " +
            "AND e.entryDate BETWEEN :startDate AND :endDate " +
            "GROUP BY e.entryDate " +
            "ORDER BY e.entryDate ASC")
    List<Object[]> countEntriesPerDay(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Keyword search (user-scoped)
    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.tags " +
            "WHERE e.user.id = :userId " +
            "AND (LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "  OR LOWER(e.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY e.entryDate DESC")
    List<Entry> searchByKeyword(
            @Param("userId") Long userId,
            @Param("keyword") String keyword
    );

    // Count by type (user-scoped)
    @Query("SELECT e.entryType, COUNT(e) FROM Entry e " +
            "WHERE e.user.id = :userId GROUP BY e.entryType")
    List<Object[]> countByType(@Param("userId") Long userId);

    // Total count (user-scoped)
    long countByUserId(Long userId);

    // Recent entries (user-scoped)
    @Query("SELECT e FROM Entry e LEFT JOIN FETCH e.tags " +
            "WHERE e.user.id = :userId " +
            "ORDER BY e.entryDate DESC, e.createdAt DESC")
    List<Entry> findRecentByUserId(
            @Param("userId") Long userId,
            org.springframework.data.domain.Pageable pageable
    );

    // Streak: distinct active dates (user-scoped)
    @Query("SELECT DISTINCT e.entryDate FROM Entry e " +
            "WHERE e.user.id = :userId " +
            "ORDER BY e.entryDate DESC")
    List<LocalDate> findAllActiveDates(@Param("userId") Long userId);

    // ── GOAL entry lookups ────────────────────────────────────────────────────

    /**
     * Find an existing GOAL entry for a specific milestone completion.
     */
    @Query("SELECT e FROM Entry e " +
            "WHERE e.user.id = :userId " +
            "AND e.goalReferenceId = :goalId " +
            "AND e.milestoneReferenceId = :milestoneId " +
            "AND e.entryType = com.logbook.logbookbackend.entity.EntryType.GOAL")
    Optional<Entry> findGoalEntryByMilestone(
            @Param("userId") Long userId,
            @Param("goalId") Long goalId,
            @Param("milestoneId") Long milestoneId
    );

    /**
     * Find an existing GOAL entry for an entire goal completion (no specific milestone).
     */
    @Query("SELECT e FROM Entry e " +
            "WHERE e.user.id = :userId " +
            "AND e.goalReferenceId = :goalId " +
            "AND e.milestoneReferenceId IS NULL " +
            "AND e.entryType = com.logbook.logbookbackend.entity.EntryType.GOAL")
    Optional<Entry> findGoalEntryByGoal(
            @Param("userId") Long userId,
            @Param("goalId") Long goalId
    );
}