package com.logbook.logbookbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logbook.logbookbackend.config.JwtUtil;
import com.logbook.logbookbackend.dto.GitHubDtos;
import com.logbook.logbookbackend.service.GitHubService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GitHubController {

    private final GitHubService githubService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ── OAuth flow ────────────────────────────────────────────────────────────

    /**
     * Step 1: User clicks "Connect GitHub" in settings.
     * Returns the GitHub OAuth URL for the frontend to redirect to.
     */
    @GetMapping("/api/github/oauth/authorize")
    public ResponseEntity<Map<String, String>> authorize(@AuthenticationPrincipal Long userId) {
        String state = UUID.randomUUID().toString();
        String url = githubService.buildAuthorizationUrl(state);
        return ResponseEntity.ok(Map.of("url", url, "state", state));
    }

    /**
     * Step 2: GitHub redirects back here after user authorises.
     * Exchanges code for token, saves to user, redirects to frontend settings page.
     */
    @GetMapping("/api/github/oauth/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @AuthenticationPrincipal Long userId,
            jakarta.servlet.http.HttpServletResponse response) throws IOException {

        if (error != null) {
            response.sendRedirect(frontendUrl + "/settings?github=error&reason=" + error);
            return;
        }

        try {
            githubService.handleCallback(userId, code);
            response.sendRedirect(frontendUrl + "/settings?github=connected");
        } catch (Exception e) {
            log.error("GitHub OAuth callback failed for userId={}", userId, e);
            response.sendRedirect(frontendUrl + "/settings?github=error&reason=callback_failed");
        }
    }

    /**
     * Disconnect GitHub from the user's account.
     */
    @DeleteMapping("/api/github/connection")
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Long userId) {
        githubService.disconnect(userId);
        return ResponseEntity.noContent().build();
    }

    // ── Status & settings ─────────────────────────────────────────────────────

    @GetMapping("/api/github/status")
    public ResponseEntity<GitHubDtos.GitHubStatusResponse> getStatus(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(githubService.getStatus(userId));
    }

    @PostMapping("/api/github/preferences")
    public ResponseEntity<Void> applyPreferences(
            @AuthenticationPrincipal Long userId,
            @RequestBody GitHubDtos.ConnectPreferenceRequest request) {
        githubService.applyConnectPreference(userId, request.isSyncOldCommits());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/api/github/sync")
    public ResponseEntity<Void> toggleSync(
            @AuthenticationPrincipal Long userId,
            @RequestBody GitHubDtos.SyncToggleRequest request) {
        githubService.toggleSync(userId, request.isEnabled());
        return ResponseEntity.ok().build();
    }

    // ── Repo management ───────────────────────────────────────────────────────

    @GetMapping("/api/github/repos")
    public ResponseEntity<List<GitHubDtos.RepoListItem>> listRepos(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(githubService.listUserRepos(userId));
    }

    @PostMapping("/api/github/repos/watch")
    public ResponseEntity<GitHubDtos.WatchedRepoResponse> watchRepo(
            @AuthenticationPrincipal Long userId,
            @RequestBody GitHubDtos.WatchRepoRequest request) {
        return ResponseEntity.ok(
                githubService.watchRepo(userId, request.getRepoFullName(), request.getRepoName())
        );
    }

    @DeleteMapping("/api/github/repos/{repoId}")
    public ResponseEntity<Void> unwatchRepo(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long repoId) {
        githubService.unwatchRepo(userId, repoId);
        return ResponseEntity.noContent().build();
    }

    // ── Webhook receiver ──────────────────────────────────────────────────────

    /**
     * GitHub sends push events here.
     * This endpoint is PUBLIC (no JWT) but verified by HMAC signature.
     */
    @PostMapping("/api/webhooks/github")
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestBody String rawPayload) {

        // Verify signature
        if (!githubService.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Only handle push events
        if (!"push".equals(event)) {
            return ResponseEntity.ok().build();
        }

        try {
            GitHubDtos.PushPayload payload = objectMapper.readValue(rawPayload, GitHubDtos.PushPayload.class);
            githubService.processPushEvent(payload);
        } catch (Exception e) {
            log.error("Failed to process GitHub push webhook", e);
            // Return 200 anyway so GitHub doesn't disable the webhook
        }

        return ResponseEntity.ok().build();
    }
}