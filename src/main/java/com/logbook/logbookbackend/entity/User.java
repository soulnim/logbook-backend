package com.logbook.logbookbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_id", nullable = false, unique = true)
    private String googleId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "last_login", nullable = false)
    @Builder.Default
    private OffsetDateTime lastLogin = OffsetDateTime.now();

    // ── GitHub Integration ────────────────────────────────────────────────────

    @Column(name = "github_id")
    private String githubId;

    @Column(name = "github_username")
    private String githubUsername;

    @Column(name = "github_access_token", columnDefinition = "TEXT")
    private String githubAccessToken;

    @Column(name = "github_sync_enabled", nullable = false)
    @Builder.Default
    private Boolean githubSyncEnabled = false;

    /** If set, only commits on/after this date are synced (for "start fresh" preference). */
    @Column(name = "github_sync_from")
    private OffsetDateTime githubSyncFrom;

    /**
     * IANA timezone identifier (e.g. "Asia/Kuala_Lumpur", "America/New_York").
     * Set once on first login from the browser's Intl API; can be changed in Settings.
     * NULL → backend falls back to UTC when computing local dates.
     */
    @Column(name = "timezone", length = 64)
    private String timezone;
}