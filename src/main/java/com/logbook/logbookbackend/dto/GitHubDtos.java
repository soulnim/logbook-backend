package com.logbook.logbookbackend.dto;

import lombok.*;

import java.util.List;

public class GitHubDtos {

    // ── GitHub API responses (mapped from GitHub's JSON) ──────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class GitHubRepo {
        private String full_name;   // "soulnim/logbook-backend"
        private String name;        // "logbook-backend"
        private boolean _private;
        private String description;
        private String html_url;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class GitHubUser {
        private String id;
        private String login;        // GitHub username
        private String avatar_url;
        private String name;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class GitHubTokenResponse {
        private String access_token;
        private String token_type;
        private String scope;
        private String error;
        private String error_description;
    }

    // ── Webhook payload (GitHub push event) ───────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PushPayload {
        private String ref;          // "refs/heads/main"
        private String after;        // latest commit SHA
        private List<CommitPayload> commits;
        private RepositoryPayload repository;
        private PusherPayload pusher;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CommitPayload {
        private String id;           // SHA
        private String message;
        private String timestamp;    // ISO 8601
        private String url;
        private CommitAuthor author;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CommitAuthor {
        private String name;
        private String email;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RepositoryPayload {
        private Long id;
        private String full_name;
        private String name;
        private String html_url;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PusherPayload {
        private String name;
    }

    // ── API responses to frontend ──────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GitHubStatusResponse {
        private boolean connected;
        private String githubUsername;
        private boolean syncEnabled;
        private String syncFrom;      // ISO date or null
        private List<WatchedRepoResponse> watchedRepos;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WatchedRepoResponse {
        private Long id;
        private String repoFullName;
        private String repoName;
        private boolean isActive;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RepoListItem {
        private String fullName;
        private String name;
        private boolean isPrivate;
        private String description;
        private boolean alreadyWatched;
    }

    // ── Requests from frontend ────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ConnectPreferenceRequest {
        private boolean syncOldCommits;  // true = sync past 90 days, false = start fresh
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class WatchRepoRequest {
        private String repoFullName;
        private String repoName;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SyncToggleRequest {
        private boolean enabled;
    }
}