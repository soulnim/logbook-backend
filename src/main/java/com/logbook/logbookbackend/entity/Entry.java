package com.logbook.logbookbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "is_completed", nullable = false)
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "mood")
    private Short mood;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "entry_tags",
            joinColumns = @JoinColumn(name = "entry_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    /**
     * For COMMIT entries: stores GitHub metadata as JSON.
     * Structure: { repoFullName, repoName, branch, commits: [{sha, message, url, timestamp, authorName}] }
     * Content field remains the user's editable personal notes.
     */
    @Column(name = "source_meta", columnDefinition = "TEXT")
    private String sourceMeta;

    /**
     * For GOAL entries: references the goal that triggered this entry.
     * Preserved even if the goal is deleted (no FK constraint).
     */
    @Column(name = "goal_reference_id")
    private Long goalReferenceId;

    /**
     * For GOAL entries: references the specific milestone that was completed.
     * NULL means the entire goal was completed (not a specific milestone).
     */
    @Column(name = "milestone_reference_id")
    private Long milestoneReferenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }
}