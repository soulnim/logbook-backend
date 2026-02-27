package com.logbook.logbookbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "github_watched_repos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubWatchedRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "repo_full_name", nullable = false, length = 255)
    private String repoFullName;   // e.g. "soulnim/logbook-backend"

    @Column(name = "repo_name", nullable = false, length = 255)
    private String repoName;       // e.g. "logbook-backend"

    @Column(name = "webhook_id")
    private Long webhookId;        // GitHub webhook ID (used to delete it later)

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}