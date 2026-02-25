package com.logbook.logbookbackend.dto;

import com.logbook.logbookbackend.entity.EntryType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

// ── Request DTOs ──────────────────────────────────────────────────────────────

public class EntryDtos {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be under 255 characters")
        private String title;

        private String content;

        @NotNull(message = "Entry type is required")
        private EntryType entryType;

        @NotNull(message = "Entry date is required")
        private LocalDate entryDate;

        // For EVENT type
        private LocalTime startTime;
        private LocalTime endTime;

        // For ACTION type
        private Boolean isCompleted;

        @Min(value = 1) @Max(value = 5)
        private Short mood;

        private Set<String> tags; // tag names - will be created if not exist
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        @Size(max = 255)
        private String title;

        private String content;

        private LocalDate entryDate;
        private LocalTime startTime;
        private LocalTime endTime;
        private Boolean isCompleted;

        @Min(value = 1) @Max(value = 5)
        private Short mood;

        private Set<String> tags;
    }

    // ── Response DTOs ─────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EntryResponse {
        private Long id;
        private String title;
        private String content;
        private EntryType entryType;
        private String entryDate;   // ISO date string
        private String startTime;
        private String endTime;
        private Boolean isCompleted;
        private Short mood;
        private Set<TagResponse> tags;
        private String createdAt;
        private String updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TagResponse {
        private Long id;
        private String name;
        private String color;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TagCreateRequest {
        @NotBlank
        @Size(max = 50)
        private String name;

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color")
        private String color;
    }
}