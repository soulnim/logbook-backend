package com.logbook.logbookbackend.controller;

import com.logbook.logbookbackend.dto.GoalDtos;
import com.logbook.logbookbackend.entity.GoalStatus;
import com.logbook.logbookbackend.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    // ── Goals ─────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<GoalDtos.GoalResponse>> getGoals(
            Authentication auth,
            @RequestParam(required = false) GoalStatus status) {

        Long userId = (Long) auth.getPrincipal();
        List<GoalDtos.GoalResponse> goals = status != null
                ? goalService.getGoalsByStatus(userId, status)
                : goalService.getAllGoals(userId);
        return ResponseEntity.ok(goals);
    }

    @GetMapping("/summary")
    public ResponseEntity<GoalDtos.GoalSummary> getSummary(Authentication auth) {
        return ResponseEntity.ok(goalService.getSummary((Long) auth.getPrincipal()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoalDtos.GoalResponse> getGoal(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(goalService.getGoal((Long) auth.getPrincipal(), id));
    }

    @PostMapping
    public ResponseEntity<GoalDtos.GoalResponse> createGoal(
            Authentication auth,
            @Valid @RequestBody GoalDtos.CreateGoalRequest req) {
        return ResponseEntity.ok(goalService.createGoal((Long) auth.getPrincipal(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalDtos.GoalResponse> updateGoal(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody GoalDtos.UpdateGoalRequest req) {
        return ResponseEntity.ok(goalService.updateGoal((Long) auth.getPrincipal(), id, req));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<GoalDtos.GoalResponse> updateStatus(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody GoalDtos.UpdateGoalStatusRequest req) {
        return ResponseEntity.ok(goalService.updateGoalStatus((Long) auth.getPrincipal(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(Authentication auth, @PathVariable Long id) {
        goalService.deleteGoal((Long) auth.getPrincipal(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Milestones ────────────────────────────────────────────────────────────

    @PostMapping("/{goalId}/milestones")
    public ResponseEntity<GoalDtos.GoalResponse> addMilestone(
            Authentication auth,
            @PathVariable Long goalId,
            @Valid @RequestBody GoalDtos.CreateMilestoneRequest req) {
        return ResponseEntity.ok(goalService.addMilestone((Long) auth.getPrincipal(), goalId, req));
    }

    @PatchMapping("/{goalId}/milestones/{milestoneId}")
    public ResponseEntity<GoalDtos.GoalResponse> updateMilestone(
            Authentication auth,
            @PathVariable Long goalId,
            @PathVariable Long milestoneId,
            @Valid @RequestBody GoalDtos.UpdateMilestoneRequest req) {
        return ResponseEntity.ok(goalService.updateMilestone((Long) auth.getPrincipal(), goalId, milestoneId, req));
    }

    @DeleteMapping("/{goalId}/milestones/{milestoneId}")
    public ResponseEntity<GoalDtos.GoalResponse> deleteMilestone(
            Authentication auth,
            @PathVariable Long goalId,
            @PathVariable Long milestoneId) {
        return ResponseEntity.ok(goalService.deleteMilestone((Long) auth.getPrincipal(), goalId, milestoneId));
    }
}