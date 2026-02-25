package com.logbook.logbookbackend.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

public class StatsDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HeatmapEntry {
        private String date;   // "YYYY-MM-DD"
        private int count;
        private int level;     // 0-4 intensity level (like GitHub)
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HeatmapResponse {
        private List<HeatmapEntry> data;
        private int totalEntries;
        private int activeDays;
        private int currentStreak;
        private int longestStreak;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatsResponse {
        private long totalEntries;
        private int activeDays;
        private int currentStreak;
        private int longestStreak;
        private Map<String, Long> byType;  // { NOTE: 12, SKILL: 5, ... }
        private List<EntryDtos.EntryResponse> recentEntries;
    }
}