package com.logbook.logbookbackend.service;

import com.logbook.logbookbackend.dto.GoalDtos;
import com.logbook.logbookbackend.entity.*;
import com.logbook.logbookbackend.repository.GoalRepository;
import com.logbook.logbookbackend.repository.MilestoneRepository;
import com.logbook.logbookbackend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GoalService {

    private final GoalRepository      goalRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository      userRepository;
    private final GoalEntryService    goalEntryService;

    // ── Goals CRUD ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GoalDtos.GoalResponse> getAllGoals(Long userId) {
        return goalRepository.findAllByUserIdWithMilestones(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GoalDtos.GoalResponse> getGoalsByStatus(Long userId, GoalStatus status) {
        return goalRepository.findByUserIdAndStatusWithMilestones(userId, status)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GoalDtos.GoalResponse getGoal(Long userId, Long goalId) {
        Goal goal = findGoal(userId, goalId);
        return toResponse(goal);
    }

    public GoalDtos.GoalResponse createGoal(Long userId, GoalDtos.CreateGoalRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Goal goal = Goal.builder()
                .user(user)
                .title(req.getTitle())
                .description(req.getDescription())
                .type(req.getType())
                .color(req.getColor() != null ? req.getColor() : "#818cf8")
                .targetDate(req.getTargetDate())
                .build();

        return toResponse(goalRepository.save(goal));
    }

    public GoalDtos.GoalResponse updateGoal(Long userId, Long goalId, GoalDtos.UpdateGoalRequest req) {
        Goal goal = findGoal(userId, goalId);

        if (req.getTitle()       != null) goal.setTitle(req.getTitle());
        if (req.getDescription() != null) goal.setDescription(req.getDescription());
        if (req.getType()        != null) goal.setType(req.getType());
        if (req.getColor()       != null) goal.setColor(req.getColor());
        if (req.getTargetDate()  != null) goal.setTargetDate(req.getTargetDate());

        return toResponse(goalRepository.save(goal));
    }

    public GoalDtos.GoalResponse updateGoalStatus(Long userId, Long goalId, GoalDtos.UpdateGoalStatusRequest req) {
        Goal goal = findGoal(userId, goalId);

        boolean wasCompleted = goal.getStatus() == GoalStatus.COMPLETED;
        boolean willBeCompleted = req.getStatus() == GoalStatus.COMPLETED;

        goal.setStatus(req.getStatus());
        goal = goalRepository.save(goal);

        // Track goal-level completion in the calendar
        if (wasCompleted != willBeCompleted) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            goalEntryService.trackGoalCompletion(user, goal, willBeCompleted);
        }

        return toResponse(goal);
    }

    public void deleteGoal(Long userId, Long goalId) {
        Goal goal = findGoal(userId, goalId);
        goalRepository.delete(goal);
        // NOTE: GOAL calendar entries are intentionally NOT deleted here —
        // they serve as a permanent historical record of accomplishments.
    }

    // ── Milestones ────────────────────────────────────────────────────────────

    public GoalDtos.GoalResponse addMilestone(Long userId, Long goalId, GoalDtos.CreateMilestoneRequest req) {
        Goal goal = findGoal(userId, goalId);

        int order = req.getDisplayOrder() != null
                ? req.getDisplayOrder()
                : goal.getMilestones().size();

        Milestone milestone = Milestone.builder()
                .goal(goal)
                .title(req.getTitle())
                .displayOrder(order)
                .build();

        goal.getMilestones().add(milestone);
        return toResponse(goalRepository.save(goal));
    }

    public GoalDtos.GoalResponse updateMilestone(Long userId, Long goalId, Long milestoneId,
                                                 GoalDtos.UpdateMilestoneRequest req) {
        Goal goal = findGoal(userId, goalId);

        Milestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
                .orElseThrow(() -> new EntityNotFoundException("Milestone not found"));

        if (req.getTitle()        != null) milestone.setTitle(req.getTitle());
        if (req.getDisplayOrder() != null) milestone.setDisplayOrder(req.getDisplayOrder());

        boolean wasCompleted = Boolean.TRUE.equals(milestone.getIsCompleted());
        boolean willBeCompleted = wasCompleted;

        if (req.getIsCompleted() != null) {
            willBeCompleted = req.getIsCompleted();
            milestone.setIsCompleted(req.getIsCompleted());
            milestone.setCompletedAt(req.getIsCompleted() ? OffsetDateTime.now() : null);
        }

        milestoneRepository.save(milestone);

        // Track milestone completion change in calendar
        if (wasCompleted != willBeCompleted) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            goalEntryService.trackMilestoneCompletion(user, goal, milestone, willBeCompleted);
        }

        // Re-fetch the goal to get the latest milestone list
        goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));

        // NOTE: goal-level completion entry is only created when the user explicitly
        // marks the goal COMPLETED via updateGoalStatus(). Reaching 100% milestones
        // does not auto-create it.

        return toResponse(goal);
    }

    public GoalDtos.GoalResponse deleteMilestone(Long userId, Long goalId, Long milestoneId) {
        Goal goal = findGoal(userId, goalId);

        Milestone milestone = milestoneRepository.findByIdAndGoalId(milestoneId, goalId)
                .orElseThrow(() -> new EntityNotFoundException("Milestone not found"));

        goal.getMilestones().remove(milestone);
        return toResponse(goalRepository.save(goal));
        // NOTE: any GOAL calendar entry for this milestone is intentionally kept
        // as a historical record.
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GoalDtos.GoalSummary getSummary(Long userId) {
        long active    = goalRepository.countByUserIdAndStatus(userId, GoalStatus.ACTIVE);
        long completed = goalRepository.countByUserIdAndStatus(userId, GoalStatus.COMPLETED);

        // Count overdue: active goals with targetDate in the past
        long overdue = goalRepository.findByUserIdAndStatusWithMilestones(userId, GoalStatus.ACTIVE)
                .stream()
                .filter(g -> g.getTargetDate() != null && g.getTargetDate().isBefore(LocalDate.now()))
                .count();

        return new GoalDtos.GoalSummary(active, completed, overdue);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Goal findGoal(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Goal not found"));
    }

    private GoalDtos.GoalResponse toResponse(Goal goal) {
        List<Milestone> milestones = goal.getMilestones();
        int total     = milestones.size();
        int completed = (int) milestones.stream().filter(m -> Boolean.TRUE.equals(m.getIsCompleted())).count();
        int progress  = total > 0 ? (int) Math.round((double) completed / total * 100) : 0;

        boolean overdue = goal.getTargetDate() != null
                && goal.getTargetDate().isBefore(LocalDate.now())
                && goal.getStatus() == GoalStatus.ACTIVE;

        long daysUntil = goal.getTargetDate() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate())
                : Long.MAX_VALUE;

        List<GoalDtos.MilestoneResponse> milestoneResponses = milestones.stream()
                .map(m -> GoalDtos.MilestoneResponse.builder()
                        .id(m.getId())
                        .title(m.getTitle())
                        .isCompleted(m.getIsCompleted())
                        .completedAt(m.getCompletedAt() != null ? m.getCompletedAt().toString() : null)
                        .displayOrder(m.getDisplayOrder())
                        .createdAt(m.getCreatedAt() != null ? m.getCreatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return GoalDtos.GoalResponse.builder()
                .id(goal.getId())
                .title(goal.getTitle())
                .description(goal.getDescription())
                .type(goal.getType())
                .status(goal.getStatus())
                .color(goal.getColor())
                .targetDate(goal.getTargetDate() != null ? goal.getTargetDate().toString() : null)
                .milestones(milestoneResponses)
                .totalMilestones(total)
                .completedMilestones(completed)
                .progressPercent(progress)
                .overdue(overdue)
                .daysUntilDeadline(daysUntil)
                .createdAt(goal.getCreatedAt() != null ? goal.getCreatedAt().toString() : null)
                .updatedAt(goal.getUpdatedAt() != null ? goal.getUpdatedAt().toString() : null)
                .build();
    }
}