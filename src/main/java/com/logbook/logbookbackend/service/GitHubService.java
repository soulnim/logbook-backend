package com.logbook.logbookbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logbook.logbookbackend.dto.GitHubDtos;
import com.logbook.logbookbackend.entity.Entry;
import com.logbook.logbookbackend.entity.EntryType;
import com.logbook.logbookbackend.entity.GithubWatchedRepo;
import com.logbook.logbookbackend.entity.User;
import com.logbook.logbookbackend.repository.EntryRepository;
import com.logbook.logbookbackend.repository.GithubWatchedRepoRepository;
import com.logbook.logbookbackend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GitHubService {

    private final UserRepository userRepository;
    private final EntryRepository entryRepository;
    private final GithubWatchedRepoRepository watchedRepoRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.github.client-id}")
    private String githubClientId;

    @Value("${app.github.client-secret}")
    private String githubClientSecret;

    @Value("${app.github.webhook-secret}")
    private String webhookSecret;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    private static final String GITHUB_API = "https://api.github.com";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";

    // ── OAuth flow ────────────────────────────────────────────────────────────

    /** Build the GitHub OAuth authorization URL to redirect the user to. */
    public String buildAuthorizationUrl(String state) {
        try {
            return "https://github.com/login/oauth/authorize" +
                    "?client_id=" + githubClientId +
                    "&scope=repo,admin:repo_hook" +
                    "&state=" + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build authorization URL", e);
        }
    }

    /** Exchange the OAuth code for an access token and save to user. */
    public void handleCallback(Long userId, String code) {
        // Exchange code for token
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "client_id", githubClientId,
                "client_secret", githubClientSecret,
                "code", code
        );

        ResponseEntity<GitHubDtos.GitHubTokenResponse> tokenResp = rest.exchange(
                GITHUB_TOKEN_URL,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                GitHubDtos.GitHubTokenResponse.class
        );

        GitHubDtos.GitHubTokenResponse tokenData = tokenResp.getBody();
        if (tokenData == null || tokenData.getAccess_token() == null) {
            throw new RuntimeException("GitHub OAuth failed: no access token received");
        }

        // Fetch GitHub user info
        GitHubDtos.GitHubUser ghUser = fetchGitHubUser(tokenData.getAccess_token());

        // Update user record
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setGithubId(String.valueOf(ghUser.getId()));
        user.setGithubUsername(ghUser.getLogin());
        user.setGithubAccessToken(tokenData.getAccess_token());
        user.setGithubSyncEnabled(true);
        userRepository.save(user);

        log.info("GitHub connected for userId={} githubUsername={}", userId, ghUser.getLogin());
    }

    /** Disconnect GitHub from user account and remove all webhooks. */
    public void disconnect(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Delete all webhooks
        List<GithubWatchedRepo> repos = watchedRepoRepository.findByUserIdAndIsActiveTrue(userId);
        for (GithubWatchedRepo repo : repos) {
            if (repo.getWebhookId() != null && user.getGithubAccessToken() != null) {
                deleteWebhook(user.getGithubAccessToken(), repo.getRepoFullName(), repo.getWebhookId());
            }
        }
        watchedRepoRepository.deleteAll(repos);

        user.setGithubId(null);
        user.setGithubUsername(null);
        user.setGithubAccessToken(null);
        user.setGithubSyncEnabled(false);
        user.setGithubSyncFrom(null);
        userRepository.save(user);

        log.info("GitHub disconnected for userId={}", userId);
    }

    // ── Preferences ───────────────────────────────────────────────────────────

    public void applyConnectPreference(Long userId, boolean syncOldCommits) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (syncOldCommits) {
            // Sync commits from the last 90 days
            user.setGithubSyncFrom(OffsetDateTime.now().minusDays(90));
        } else {
            // Start fresh — only new commits from now
            user.setGithubSyncFrom(OffsetDateTime.now());
        }
        userRepository.save(user);

        // If user chose to sync old commits, trigger a background import for watched repos
        if (syncOldCommits && user.getGithubAccessToken() != null) {
            List<GithubWatchedRepo> repos = watchedRepoRepository.findByUserIdAndIsActiveTrue(userId);
            for (GithubWatchedRepo repo : repos) {
                importPastCommits(user, repo.getRepoFullName(), repo.getRepoName());
            }
        }
    }

    public void toggleSync(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setGithubSyncEnabled(enabled);
        userRepository.save(user);
        log.info("GitHub sync {} for userId={}", enabled ? "enabled" : "paused", userId);
    }

    // ── Repo management ───────────────────────────────────────────────────────

    /** Fetch the user's GitHub repos to show in the picker. */
    public List<GitHubDtos.RepoListItem> listUserRepos(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.getGithubAccessToken() == null) {
            throw new RuntimeException("GitHub not connected");
        }

        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = githubHeaders(user.getGithubAccessToken());

        ResponseEntity<GitHubDtos.GitHubRepo[]> resp = rest.exchange(
                GITHUB_API + "/user/repos?sort=updated&per_page=50",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                GitHubDtos.GitHubRepo[].class
        );

        List<GithubWatchedRepo> alreadyWatched = watchedRepoRepository.findByUserIdOrderByRepoNameAsc(userId);
        Set<String> watchedNames = alreadyWatched.stream()
                .map(GithubWatchedRepo::getRepoFullName)
                .collect(Collectors.toSet());

        return Arrays.stream(Objects.requireNonNull(resp.getBody()))
                .map(r -> GitHubDtos.RepoListItem.builder()
                        .fullName(r.getFull_name())
                        .name(r.getName())
                        .isPrivate(r.isPrivateRepo())
                        .description(r.getDescription())
                        .alreadyWatched(watchedNames.contains(r.getFull_name()))
                        .build())
                .collect(Collectors.toList());
    }

    /** Add a repo to the watch list and register a GitHub webhook on it. */
    public GitHubDtos.WatchedRepoResponse watchRepo(Long userId, String repoFullName, String repoName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (watchedRepoRepository.existsByUserIdAndRepoFullName(userId, repoFullName)) {
            // Re-activate if it was previously removed and register a fresh webhook
            GithubWatchedRepo existing = watchedRepoRepository
                    .findByUserIdAndRepoFullName(userId, repoFullName).get();
            existing.setIsActive(true);
            // Re-register webhook — the old one was deleted during unwatchRepo()
            Long newWebhookId = registerWebhook(user.getGithubAccessToken(), repoFullName);
            existing.setWebhookId(newWebhookId);
            watchedRepoRepository.save(existing);
            return toWatchedRepoResponse(existing);
        }

        Long webhookId = registerWebhook(user.getGithubAccessToken(), repoFullName);

        GithubWatchedRepo repo = GithubWatchedRepo.builder()
                .user(user)
                .repoFullName(repoFullName)
                .repoName(repoName)
                .webhookId(webhookId)
                .isActive(true)
                .build();

        repo = watchedRepoRepository.save(repo);
        log.info("Now watching repo={} for userId={}", repoFullName, userId);

        // Import past commits if user set a sync-from date in the past
        if (user.getGithubSyncFrom() != null &&
                user.getGithubSyncFrom().isBefore(OffsetDateTime.now().minusMinutes(5))) {
            importPastCommits(user, repoFullName, repoName);
        }

        return toWatchedRepoResponse(repo);
    }

    /** Remove a repo from the watch list and delete the GitHub webhook. */
    public void unwatchRepo(Long userId, Long repoId) {
        GithubWatchedRepo repo = watchedRepoRepository.findById(repoId)
                .orElseThrow(() -> new EntityNotFoundException("Watched repo not found"));

        if (!repo.getUser().getId().equals(userId)) {
            throw new SecurityException("Access denied");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (repo.getWebhookId() != null && user.getGithubAccessToken() != null) {
            deleteWebhook(user.getGithubAccessToken(), repo.getRepoFullName(), repo.getWebhookId());
        }

        watchedRepoRepository.delete(repo);
        log.info("Stopped watching repo={} for userId={}", repo.getRepoFullName(), userId);
    }

    // ── Webhook handler ───────────────────────────────────────────────────────

    /**
     * Verify the GitHub webhook HMAC signature.
     * GitHub sends: X-Hub-Signature-256: sha256=<hmac>
     */
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) return false;
        String expected = signatureHeader.substring(7);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = bytesToHex(hash);
            return computed.equalsIgnoreCase(expected);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Webhook signature verification failed", e);
            return false;
        }
    }

    /**
     * Process a GitHub push event webhook.
     * Finds or creates a COMMIT entry for (user, date, repo) and appends the commits.
     */
    public void processPushEvent(GitHubDtos.PushPayload payload) {
        if (payload.getCommits() == null || payload.getCommits().isEmpty()) return;

        String repoFullName = payload.getRepository().getFull_name();
        String repoName = payload.getRepository().getName();
        String branch = payload.getRef().replace("refs/heads/", "");

        // Find all users watching this repo — use targeted query, not findAll()
        List<GithubWatchedRepo> watchers = watchedRepoRepository
                .findByRepoFullNameAndIsActiveTrue(repoFullName);

        for (GithubWatchedRepo watcher : watchers) {
            User user = watcher.getUser();
            if (!Boolean.TRUE.equals(user.getGithubSyncEnabled())) continue;

            // Filter out commits before the sync-from date
            List<GitHubDtos.CommitPayload> commits = payload.getCommits().stream()
                    .filter(c -> {
                        if (user.getGithubSyncFrom() == null) return true;
                        OffsetDateTime commitTime = OffsetDateTime.parse(c.getTimestamp());
                        return commitTime.isAfter(user.getGithubSyncFrom());
                    })
                    .collect(Collectors.toList());

            if (commits.isEmpty()) continue;

            // Use the date of the first commit in the push
            LocalDate entryDate = OffsetDateTime.parse(commits.get(0).getTimestamp()).toLocalDate();

            upsertCommitEntry(user, repoFullName, repoName, branch, entryDate, commits);
        }
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public GitHubDtos.GitHubStatusResponse getStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<GitHubDtos.WatchedRepoResponse> repos = watchedRepoRepository
                .findByUserIdOrderByRepoNameAsc(userId).stream()
                .map(this::toWatchedRepoResponse)
                .collect(Collectors.toList());

        return GitHubDtos.GitHubStatusResponse.builder()
                .connected(user.getGithubId() != null)
                .githubUsername(user.getGithubUsername())
                .syncEnabled(Boolean.TRUE.equals(user.getGithubSyncEnabled()))
                .syncFrom(user.getGithubSyncFrom() != null ? user.getGithubSyncFrom().toString() : null)
                .watchedRepos(repos)
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Find existing COMMIT entry for (user, date, repo) or create a new one.
     * Merges the new commits into the existing sourceMeta.
     */
    private void upsertCommitEntry(User user, String repoFullName, String repoName,
                                   String branch, LocalDate entryDate,
                                   List<GitHubDtos.CommitPayload> newCommits) {
        // Look for an existing COMMIT entry for this user, date, and repo
        Optional<Entry> existing = entryRepository
                .findByUserIdAndEntryDateOrderByCreatedAtAsc(user.getId(), entryDate)
                .stream()
                .filter(e -> e.getEntryType() == EntryType.COMMIT)
                .filter(e -> {
                    try {
                        if (e.getSourceMeta() == null) return false;
                        Map<String, Object> meta = objectMapper.readValue(e.getSourceMeta(), new TypeReference<>() {});
                        return repoFullName.equals(meta.get("repoFullName"));
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .findFirst();

        if (existing.isPresent()) {
            mergeCommitsIntoEntry(existing.get(), newCommits);
        } else {
            createCommitEntry(user, repoFullName, repoName, branch, entryDate, newCommits);
        }
    }

    private void createCommitEntry(User user, String repoFullName, String repoName,
                                   String branch, LocalDate entryDate,
                                   List<GitHubDtos.CommitPayload> commits) {
        String sourceMeta = buildSourceMeta(repoFullName, repoName, branch, commits);
        int count = commits.size();
        String title = count == 1
                ? "1 commit to " + repoName
                : count + " commits to " + repoName;

        Entry entry = Entry.builder()
                .user(user)
                .title(title)
                .content(null)  // User fills this in
                .entryType(EntryType.COMMIT)
                .entryDate(entryDate)
                .isCompleted(false)
                .sourceMeta(sourceMeta)
                .build();

        entryRepository.save(entry);
        log.info("Created COMMIT entry for userId={} repo={} date={}", user.getId(), repoFullName, entryDate);
    }

    @SuppressWarnings("unchecked")
    private void mergeCommitsIntoEntry(Entry entry, List<GitHubDtos.CommitPayload> newCommits) {
        try {
            Map<String, Object> meta = objectMapper.readValue(entry.getSourceMeta(), new TypeReference<>() {});
            List<Map<String, Object>> existingCommits = (List<Map<String, Object>>) meta.get("commits");

            Set<String> existingShas = existingCommits.stream()
                    .map(c -> (String) c.get("sha"))
                    .collect(Collectors.toSet());

            for (GitHubDtos.CommitPayload c : newCommits) {
                if (!existingShas.contains(c.getId())) {
                    Map<String, Object> commitMap = new LinkedHashMap<>();
                    commitMap.put("sha", c.getId());
                    commitMap.put("message", c.getMessage());
                    commitMap.put("url", c.getUrl());
                    commitMap.put("timestamp", c.getTimestamp());
                    commitMap.put("authorName", c.getAuthor() != null ? c.getAuthor().getName() : "");
                    existingCommits.add(commitMap);
                }
            }

            meta.put("commits", existingCommits);

            // Update the title with new count
            String repoName = (String) meta.get("repoName");
            int count = existingCommits.size();
            entry.setTitle(count == 1 ? "1 commit to " + repoName : count + " commits to " + repoName);
            entry.setSourceMeta(objectMapper.writeValueAsString(meta));
            entryRepository.save(entry);

        } catch (JsonProcessingException e) {
            log.error("Failed to merge commits into entry id={}", entry.getId(), e);
        }
    }

    private String buildSourceMeta(String repoFullName, String repoName, String branch,
                                   List<GitHubDtos.CommitPayload> commits) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("repoFullName", repoFullName);
            meta.put("repoName", repoName);
            meta.put("branch", branch);

            List<Map<String, Object>> commitList = commits.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sha", c.getId());
                m.put("message", c.getMessage());
                m.put("url", c.getUrl());
                m.put("timestamp", c.getTimestamp());
                m.put("authorName", c.getAuthor() != null ? c.getAuthor().getName() : "");
                return m;
            }).collect(Collectors.toList());

            meta.put("commits", commitList);
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build source meta", e);
        }
    }

    private Long registerWebhook(String token, String repoFullName) {
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = githubHeaders(token);

        String webhookUrl = backendUrl + "/api/webhooks/github";

        Map<String, Object> config = Map.of(
                "url", webhookUrl,
                "content_type", "json",
                "secret", webhookSecret,
                "insecure_ssl", "0"
        );

        Map<String, Object> body = Map.of(
                "name", "web",
                "active", true,
                "events", List.of("push"),
                "config", config
        );

        try {
            ResponseEntity<Map> resp = rest.exchange(
                    GITHUB_API + "/repos/" + repoFullName + "/hooks",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<?, ?> respBody = resp.getBody();
            if (respBody != null && respBody.get("id") != null) {
                return Long.parseLong(respBody.get("id").toString());
            }
        } catch (Exception e) {
            log.error("Failed to register webhook for repo={}", repoFullName, e);
        }
        return null;
    }

    private void deleteWebhook(String token, String repoFullName, Long webhookId) {
        try {
            RestTemplate rest = new RestTemplate();
            rest.exchange(
                    GITHUB_API + "/repos/" + repoFullName + "/hooks/" + webhookId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(githubHeaders(token)),
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to delete webhook id={} for repo={}", webhookId, repoFullName);
        }
    }

    private void importPastCommits(User user, String repoFullName, String repoName) {
        if (user.getGithubAccessToken() == null || user.getGithubSyncFrom() == null) return;

        try {
            RestTemplate rest = new RestTemplate();
            HttpHeaders headers = githubHeaders(user.getGithubAccessToken());

            String since = user.getGithubSyncFrom().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String url = GITHUB_API + "/repos/" + repoFullName + "/commits?since=" + since + "&per_page=100";

            ResponseEntity<List> resp = rest.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);

            if (resp.getBody() == null) return;

            // Group by date and create entries
            Map<LocalDate, List<GitHubDtos.CommitPayload>> byDate = new LinkedHashMap<>();

            for (Object commitObj : resp.getBody()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> commitData = (Map<String, Object>) commitObj;
                @SuppressWarnings("unchecked")
                Map<String, Object> commitInfo = (Map<String, Object>) commitData.get("commit");
                @SuppressWarnings("unchecked")
                Map<String, Object> authorInfo = (Map<String, Object>) commitInfo.get("author");

                String sha = (String) commitData.get("sha");
                String message = (String) commitInfo.get("message");
                String timestamp = (String) ((Map<?, ?>) commitInfo.get("author")).get("date");
                String htmlUrl = (String) commitData.get("html_url");
                String authorName = (String) authorInfo.get("name");

                LocalDate date = OffsetDateTime.parse(timestamp).toLocalDate();

                GitHubDtos.CommitPayload cp = new GitHubDtos.CommitPayload();
                cp.setId(sha);
                cp.setMessage(message);
                cp.setTimestamp(timestamp);
                cp.setUrl(htmlUrl);
                GitHubDtos.CommitAuthor author = new GitHubDtos.CommitAuthor();
                author.setName(authorName);
                cp.setAuthor(author);

                byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(cp);
            }

            // Create/merge entries per day
            for (Map.Entry<LocalDate, List<GitHubDtos.CommitPayload>> dayEntry : byDate.entrySet()) {
                upsertCommitEntry(user, repoFullName, repoName, "(imported)",
                        dayEntry.getKey(), dayEntry.getValue());
            }

            log.info("Imported past commits for userId={} repo={} days={}", user.getId(), repoFullName, byDate.size());
        } catch (Exception e) {
            log.error("Failed to import past commits for repo={}", repoFullName, e);
        }
    }

    private GitHubDtos.GitHubUser fetchGitHubUser(String token) {
        RestTemplate rest = new RestTemplate();
        ResponseEntity<GitHubDtos.GitHubUser> resp = rest.exchange(
                GITHUB_API + "/user",
                HttpMethod.GET,
                new HttpEntity<>(githubHeaders(token)),
                GitHubDtos.GitHubUser.class
        );
        return Objects.requireNonNull(resp.getBody());
    }

    private HttpHeaders githubHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private GitHubDtos.WatchedRepoResponse toWatchedRepoResponse(GithubWatchedRepo repo) {
        return GitHubDtos.WatchedRepoResponse.builder()
                .id(repo.getId())
                .repoFullName(repo.getRepoFullName())
                .repoName(repo.getRepoName())
                .isActive(repo.getIsActive())
                .build();
    }
}