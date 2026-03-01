package com.logbook.logbookbackend.repository;

import com.logbook.logbookbackend.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    Optional<Milestone> findByIdAndGoalId(Long id, Long goalId);
    int countByGoalId(Long goalId);
    int countByGoalIdAndIsCompleted(Long goalId, Boolean isCompleted);
}