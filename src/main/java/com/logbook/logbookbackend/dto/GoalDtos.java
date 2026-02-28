package com.logbook.logbookbackend.dto;

import com.logbook.logbookbackend.entity.GoalStatus;
import com.logbook.logbookbackend.entity.GoalType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class GoalDtos {

    // ── Requests ──────────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateGoalRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 255)
        private String title;

        private String description;

        @NotNull(message = "Type is required")
        private GoalType type;

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color")
        private String color;

        private LocalDate targetDate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class UpdateGoalRequest {
        @Size(max = 255)
        private String title;

        private String description;
        private GoalType type;

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
        private String color;

        private LocalDate targetDate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class UpdateGoalStatusRequest {
        @NotNull
        private GoalStatus status;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreateMilestoneRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 255)
        private String title;

        private Integer displayOrder;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class UpdateMilestoneRequest {
        @Size(max = 255)
        private String title;

        private Boolean isCompleted;
        private Integer displayOrder;
    }

    // ── Responses ─────────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MilestoneResponse {
        private Long id;
        private String title;
        private Boolean isCompleted;
        private String completedAt;
        private Integer displayOrder;
        private String createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GoalResponse {
        private Long id;
        private String title;
        private String description;
        private GoalType type;
        private GoalStatus status;
        private String color;
        private String targetDate;
        private List<MilestoneResponse> milestones;
        private int totalMilestones;
        private int completedMilestones;
        private int progressPercent;      // 0-100
        private boolean overdue;          // targetDate is in the past and status = ACTIVE
        private long daysUntilDeadline;   // negative = overdue
        private String createdAt;
        private String updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GoalSummary {
        private long activeCount;
        private long completedCount;
        private long overdueCount;
    }
}