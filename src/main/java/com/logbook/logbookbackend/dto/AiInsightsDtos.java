package com.logbook.logbookbackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

public class AiInsightsDtos {

    public enum InsightType {
        WEEKLY_SUMMARY,   // What did I do this week?
        LEARNING_PATTERNS, // What am I learning?
        PRODUCTIVITY_CHECK, // How productive have I been?
        COMMIT_DIGEST,    // Summarise my GitHub commits
        MOTIVATE_ME       // Encouraging message based on progress
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class InsightRequest {
        @NotNull
        private InsightType insightType;

        @Size(max = 120, message = "Focus note must be under 120 characters")
        private String focusNote; // optional — user's short personalisation hint
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InsightResponse {
        private InsightType insightType;
        private String insight;       // the AI-generated text
        private int entryCount;       // how many entries were analysed
        private String dateRange;     // e.g. "Feb 21 – Feb 28"
        private boolean hasData;      // false if user has no entries to analyse
    }
}