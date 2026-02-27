package com.logbook.logbookbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logbook.logbookbackend.config.JwtUtil;
import com.logbook.logbookbackend.dto.GitHubDtos;
import com.logbook.logbookbackend.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
     * Encodes the userId into the OAuth state parameter (signed JWT) so we can
     * retrieve it in the callback, which arrives as a plain browser redirect with no
     * Authorization header.
     */
    @GetMapping("/api/github/oauth/authorize")
    public ResponseEntity<Map<String, String>> authorize(@AuthenticationPrincipal Long userId) {
        // Use a short-lived JWT as the state value so we can verify + recover userId
        // in the callback without needing a server-side session store.
        String state = jwtUtil.generateStateToken(userId);
        String url = githubService.buildAuthorizationUrl(state);
        return ResponseEntity.ok(Map.of("url", url, "state", state));
    }

    /**
     * Step 2: GitHub redirects back here after user authorises.
     * This endpoint is permitAll — no JWT in the request. We recover the userId
     * from the signed state token that we put into the OAuth redirect in step 1.
     */
    @GetMapping("/api/github/oauth/callback")
    public void callback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            jakarta.servlet.http.HttpServletResponse response) throws IOException {

        if (error != null) {
            response.sendRedirect(frontendUrl + "/settings?github=error&reason=" + error);
            return;
        }

        // Validate and extract userId from the signed state token
        log.debug("GitHub OAuth callback: state length={}, starts with={}", 
            state != null ? state.length() : 0,
            state != null && state.length() > 10 ? state.substring(0, 10) : state);
            
        if (state == null || !jwtUtil.isValidStateToken(state)) {
            log.warn("GitHub OAuth callback: invalid or missing state token (state={})", 
                state != null ? state.substring(0, Math.min(20, state.length())) + "..." : "null");
            response.sendRedirect(frontendUrl + "/settings?github=error&reason=invalid_state");
            return;
        }

        Long userId;
        try {
            userId = jwtUtil.extractUserIdFromState(state);
        } catch (Exception e) {
            log.error("GitHub OAuth callback: failed to extract userId from state", e);
            response.sendRedirect(frontendUrl + "/settings?github=error&reason=invalid_state");
            return;
        }

        if (userId == null) {
            log.error("GitHub OAuth callback: extractUserIdFromState returned null");
            response.sendRedirect(frontendUrl + "/settings?github=error&reason=invalid_state");
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
            return ResponseEntity.status(401).build();
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