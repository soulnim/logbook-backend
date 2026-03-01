package com.logbook.logbookbackend.service;

import com.logbook.logbookbackend.entity.*;
import com.logbook.logbookbackend.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Manages automatic GOAL entries in the calendar when milestones or goals are
 * marked complete / incomplete.
 *
 * Behaviour:
 *  - Completing a milestone  → CREATE a calendar entry for that milestone.
 *  - Unchecking a milestone  → DELETE the calendar entry for that milestone.
 *  - Marking a goal COMPLETED (explicit status change) → CREATE a goal-level entry.
 *  - Reactivating a goal (COMPLETED → ACTIVE/ARCHIVED)  → DELETE the goal-level entry.
 *
 * Note: goal-level entries are ONLY created via an explicit status change to COMPLETED,
 * never automatically when milestones happen to reach 100%.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalEntryService {

    private final EntryRepository entryRepository;

    /**
     * Called whenever a milestone's completion state changes.
     * Creates a GOAL calendar entry on completion; DELETES it on uncheck.
     */
    @Transactional
    public void trackMilestoneCompletion(User user, Goal goal, Milestone milestone, boolean isCompleted) {
        if (isCompleted) {
            // Check if an entry already exists (e.g. re-completing after a previous uncheck/re-check)
            Entry existing = entryRepository
                    .findGoalEntryByMilestone(user.getId(), goal.getId(), milestone.getId())
                    .orElse(null);

            // Convert now() to the user's local date using their saved timezone.
            // Falls back to UTC if no timezone has been set yet.
            ZoneId zone = resolveZone(user);
            LocalDate completionDate = LocalDate.now(zone);

            if (existing != null) {
                // Refresh the date in case it was re-completed on a different day
                existing.setEntryDate(completionDate);
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
                        .entryDate(completionDate)
                        .isCompleted(true)
                        .goalReferenceId(goal.getId())
                        .milestoneReferenceId(milestone.getId())
                        .build();

                entryRepository.save(goalEntry);
                log.info("Created GOAL entry for milestone id={} on goal id={}", milestone.getId(), goal.getId());
            }
        } else {
            // Milestone unchecked — DELETE the calendar entry so it disappears from the calendar
            entryRepository
                    .findGoalEntryByMilestone(user.getId(), goal.getId(), milestone.getId())
                    .ifPresent(entry -> {
                        entryRepository.delete(entry);
                        log.info("Deleted GOAL entry id={} (milestone id={} unchecked)", entry.getId(), milestone.getId());
                    });
        }
    }

    /**
     * Called only when a goal's status is explicitly changed to/from COMPLETED.
     * Creates a goal-level GOAL entry on completion; DELETES it on reactivation.
     *
     * This is intentionally NOT called automatically when milestones reach 100% —
     * the user must explicitly mark the goal as completed.
     */
    @Transactional
    public void trackGoalCompletion(User user, Goal goal, boolean isCompleted) {
        if (isCompleted) {
            Entry existing = entryRepository
                    .findGoalEntryByGoal(user.getId(), goal.getId())
                    .orElse(null);

            if (existing != null) {
                // Goal was re-completed after being reactivated
                ZoneId zone = resolveZone(user);
                existing.setEntryDate(LocalDate.now(zone));
                existing.setIsCompleted(true);
                existing.setUpdatedAt(OffsetDateTime.now());
                entryRepository.save(existing);
                log.info("Updated GOAL entry id={} for goal id={}", existing.getId(), goal.getId());
            } else {
                ZoneId zone = resolveZone(user);
                Entry goalEntry = Entry.builder()
                        .user(user)
                        .title("🎯 Goal Completed: " + goal.getTitle())
                        .content("Goal marked as completed.")
                        .entryType(EntryType.GOAL)
                        .entryDate(LocalDate.now(zone))
                        .isCompleted(true)
                        .goalReferenceId(goal.getId())
                        .build();

                entryRepository.save(goalEntry);
                log.info("Created GOAL completion entry for goal id={}", goal.getId());
            }
        } else {
            // Goal reactivated — DELETE the goal-level completion entry
            entryRepository
                    .findGoalEntryByGoal(user.getId(), goal.getId())
                    .ifPresent(entry -> {
                        entryRepository.delete(entry);
                        log.info("Deleted GOAL entry id={} (goal id={} reactivated)", entry.getId(), goal.getId());
                    });
        }
    }

    /**
     * Returns the user's configured ZoneId, falling back to UTC if none is set
     * or if the stored value is somehow invalid.
     */
    private ZoneId resolveZone(User user) {
        if (user.getTimezone() == null || user.getTimezone().isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(user.getTimezone());
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' for user id={}, falling back to UTC", user.getTimezone(), user.getId());
            return ZoneId.of("UTC");
        }
    }
}