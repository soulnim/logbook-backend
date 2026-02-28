package com.logbook.logbookbackend.service;

import com.logbook.logbookbackend.entity.*;
import com.logbook.logbookbackend.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Manages automatic GOAL entries in the calendar when milestones or goals are
 * marked complete / incomplete. These entries are read-only from the user's
 * perspective and are never deleted when the source goal is deleted — they act
 * as a permanent historical record of accomplishments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalEntryService {

    private final EntryRepository entryRepository;

    /**
     * Called whenever a milestone's completion state changes.
     * Creates a GOAL calendar entry on completion; marks it incomplete on uncheck.
     */
    @Transactional
    public void trackMilestoneCompletion(User user, Goal goal, Milestone milestone, boolean isCompleted) {
        if (isCompleted) {
            Entry existing = entryRepository
                    .findGoalEntryByMilestone(user.getId(), goal.getId(), milestone.getId())
                    .orElse(null);

            if (existing != null) {
                // Move the entry to today (re-completion after unchecking)
                existing.setEntryDate(LocalDate.now());
                existing.setIsCompleted(true);
                existing.setUpdatedAt(OffsetDateTime.now());
                entryRepository.save(existing);
                log.info("Updated GOAL entry id={} for milestone id={}", existing.getId(), milestone.getId());
            } else {
                Entry goalEntry = Entry.builder()
                        .user(user)
                        .title("✓ " + milestone.getTitle())
                        .content("Milestone completed for goal: " + goal.getTitle())
                        .entryType(EntryType.GOAL)
                        .entryDate(LocalDate.now())
                        .isCompleted(true)
                        .goalReferenceId(goal.getId())
                        .milestoneReferenceId(milestone.getId())
                        .build();

                entryRepository.save(goalEntry);
                log.info("Created GOAL entry for milestone id={} on goal id={}", milestone.getId(), goal.getId());
            }
        } else {
            // Milestone unchecked — mark the calendar entry as incomplete
            entryRepository
                    .findGoalEntryByMilestone(user.getId(), goal.getId(), milestone.getId())
                    .ifPresent(entry -> {
                        entry.setIsCompleted(false);
                        entry.setUpdatedAt(OffsetDateTime.now());
                        entryRepository.save(entry);
                        log.info("Marked GOAL entry id={} incomplete (milestone unchecked)", entry.getId());
                    });
        }
    }

    /**
     * Called whenever a goal's overall completion state changes.
     * Creates a GOAL calendar entry when the entire goal is completed;
     * marks it incomplete when the goal is re-opened.
     */
    @Transactional
    public void trackGoalCompletion(User user, Goal goal, boolean isCompleted) {
        if (isCompleted) {
            Entry existing = entryRepository
                    .findGoalEntryByGoal(user.getId(), goal.getId())
                    .orElse(null);

            if (existing != null) {
                existing.setEntryDate(LocalDate.now());
                existing.setIsCompleted(true);
                existing.setUpdatedAt(OffsetDateTime.now());
                entryRepository.save(existing);
                log.info("Updated GOAL entry id={} for goal id={}", existing.getId(), goal.getId());
            } else {
                Entry goalEntry = Entry.builder()
                        .user(user)
                        .title("🎯 Goal Completed: " + goal.getTitle())
                        .content("All milestones completed!")
                        .entryType(EntryType.GOAL)
                        .entryDate(LocalDate.now())
                        .isCompleted(true)
                        .goalReferenceId(goal.getId())
                        .build();

                entryRepository.save(goalEntry);
                log.info("Created GOAL completion entry for goal id={}", goal.getId());
            }
        } else {
            // Goal re-opened — mark the overall completion entry as incomplete
            entryRepository
                    .findGoalEntryByGoal(user.getId(), goal.getId())
                    .ifPresent(entry -> {
                        entry.setIsCompleted(false);
                        entry.setUpdatedAt(OffsetDateTime.now());
                        entryRepository.save(entry);
                        log.info("Marked GOAL entry id={} incomplete (goal re-opened)", entry.getId());
                    });
        }
    }
}