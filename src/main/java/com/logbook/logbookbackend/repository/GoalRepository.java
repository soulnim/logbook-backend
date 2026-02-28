package com.logbook.logbookbackend.repository;

import com.logbook.logbookbackend.entity.Goal;
import com.logbook.logbookbackend.entity.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    @Query("SELECT g FROM Goal g LEFT JOIN FETCH g.milestones WHERE g.user.id = :userId ORDER BY g.createdAt DESC")
    List<Goal> findAllByUserIdWithMilestones(@Param("userId") Long userId);

    @Query("SELECT g FROM Goal g LEFT JOIN FETCH g.milestones WHERE g.user.id = :userId AND g.status = :status ORDER BY g.createdAt DESC")
    List<Goal> findByUserIdAndStatusWithMilestones(@Param("userId") Long userId, @Param("status") GoalStatus status);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, GoalStatus status);
}