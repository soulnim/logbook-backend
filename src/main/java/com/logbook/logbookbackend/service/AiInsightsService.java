package com.logbook.logbookbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logbook.logbookbackend.dto.AiInsightsDtos;
import com.logbook.logbookbackend.entity.Entry;
import com.logbook.logbookbackend.entity.EntryType;
import com.logbook.logbookbackend.repository.EntryRepository;
import com.logbook.logbookbackend.repository.GoalRepository;
import com.logbook.logbookbackend.entity.Goal;
import com.logbook.logbookbackend.entity.GoalStatus;
import com.logbook.logbookbackend.entity.Milestone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AiInsightsService {

    private final EntryRepository entryRepository;
    private final GoalRepository   goalRepository;
    private final ObjectMapper      objectMapper;

    @Value("${app.groq.api-key:not-configured}")
    private String groqApiKey;

    private static final String GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL      = "llama-3.1-8b-instant";
    private static final int    MAX_TOKENS = 300;

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter ENTRY_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── Public API ────────────────────────────────────────────────────────────

    public AiInsightsDtos.InsightResponse generateInsight(Long userId, AiInsightsDtos.InsightRequest request) {
        if ("not-configured".equals(groqApiKey)) {
            return AiInsightsDtos.InsightResponse.builder()
                    .insightType(request.getInsightType())
                    .hasData(false)
                    .insight("AI insights are not configured yet. Add your GROQ_API_KEY to get started.")
                    .entryCount(0)
                    .dateRange("")
                    .build();
        }

        // Determine date range based on insight type
        LocalDate end   = LocalDate.now();
        LocalDate start = switch (request.getInsightType()) {
            case WEEKLY_SUMMARY, COMMIT_DIGEST, MOTIVATE_ME -> end.minusDays(6);
            case LEARNING_PATTERNS, PRODUCTIVITY_CHECK       -> end.minusDays(29);
            case GOALS_CHECK                                  -> end.minusDays(0); // uses goals, not entries
        };

        // GOALS_CHECK: different flow — reads goals, not entries
        if (request.getInsightType() == AiInsightsDtos.InsightType.GOALS_CHECK) {
            return generateGoalsInsight(userId, request.getFocusNote());
        }

        // Fetch relevant entries
        List<Entry> entries = fetchEntries(userId, request.getInsightType(), start, end);

        if (entries.isEmpty()) {
            return AiInsightsDtos.InsightResponse.builder()
                    .insightType(request.getInsightType())
                    .hasData(false)
                    .insight("No entries found for this period. Start logging to get insights!")
                    .entryCount(0)
                    .dateRange(formatRange(start, end))
                    .build();
        }

        // Build and send prompt
        String systemPrompt = buildSystemPrompt(request.getInsightType());
        String userPrompt   = buildUserPrompt(request.getInsightType(), entries, request.getFocusNote(), start, end);

        String insight = callGroq(systemPrompt, userPrompt);

        return AiInsightsDtos.InsightResponse.builder()
                .insightType(request.getInsightType())
                .hasData(true)
                .insight(insight)
                .entryCount(entries.size())
                .dateRange(formatRange(start, end))
                .build();
    }

    // ── Entry fetching ────────────────────────────────────────────────────────

    private List<Entry> fetchEntries(Long userId, AiInsightsDtos.InsightType type,
                                     LocalDate start, LocalDate end) {
        return switch (type) {
            case LEARNING_PATTERNS ->
                    entryRepository.findByUserIdAndDateRangeAndType(userId, start, end, EntryType.SKILL);
            case PRODUCTIVITY_CHECK ->
                    entryRepository.findByUserIdAndDateRangeAndType(userId, start, end, EntryType.ACTION);
            case COMMIT_DIGEST ->
                    entryRepository.findByUserIdAndDateRangeAndType(userId, start, end, EntryType.COMMIT);
            default ->
                    entryRepository.findByUserIdAndDateRange(userId, start, end);
            case GOALS_CHECK -> List.of(); // not used
        };
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    private String buildSystemPrompt(AiInsightsDtos.InsightType type) {
        String base = """
                You are a personal journaling assistant for Logbook, a developer journal app.
                Your ONLY job is to analyse the user's journal entries and give a concise, warm, specific insight.
                Rules:
                - Max 120 words. Be direct, no fluff.
                - Never ask follow-up questions.
                - Never discuss anything outside their entries.
                - Use plain text only, no markdown headers or bullet lists.
                - Refer to the user as "you".
                - Be encouraging but honest.
                """;

        return base + switch (type) {
            case WEEKLY_SUMMARY     -> "Summarise what the user did, built, and learned this week in 2-3 short paragraphs.";
            case LEARNING_PATTERNS  -> "Identify what topics or skills the user is focusing on learning. Point out any patterns or growth areas.";
            case PRODUCTIVITY_CHECK -> "Analyse the user's ACTION entries. Comment on their completion rate, what they're working on, and give one actionable suggestion.";
            case COMMIT_DIGEST      -> "Summarise the user's GitHub commit activity in plain English — what they were building and any patterns you notice.";
            case MOTIVATE_ME        -> "Give a short, genuine motivational message based on what the user has actually accomplished. Be specific, not generic.";
            case GOALS_CHECK        -> ""; // handled separately
        };
    }

    private String buildUserPrompt(AiInsightsDtos.InsightType type, List<Entry> entries,
                                   String focusNote, LocalDate start, LocalDate end) {
        StringBuilder sb = new StringBuilder();

        sb.append("Here are my journal entries from ")
                .append(formatRange(start, end))
                .append(":\n\n");

        for (Entry e : entries) {
            sb.append("[").append(e.getEntryDate().format(ENTRY_FMT)).append("] ");
            sb.append(e.getEntryType().name()).append(": ");
            sb.append("\"").append(e.getTitle()).append("\"");

            if (e.getContent() != null && !e.getContent().isBlank()) {
                // Truncate long content to keep prompt size reasonable
                String content = e.getContent().length() > 200
                        ? e.getContent().substring(0, 200) + "..."
                        : e.getContent();
                sb.append(" — ").append(content.replace("\n", " "));
            }

            // For COMMIT entries, include commit count from title
            if (e.getEntryType() == EntryType.COMMIT && e.getSourceMeta() != null) {
                try {
                    JsonNode meta = objectMapper.readTree(e.getSourceMeta());
                    JsonNode commits = meta.get("commits");
                    if (commits != null && commits.isArray()) {
                        sb.append(" [").append(commits.size()).append(" commit(s) to ")
                                .append(meta.path("repoName").asText()).append("]");
                    }
                } catch (Exception ignored) {}
            }

            // Include tags if present
            if (!e.getTags().isEmpty()) {
                String tags = e.getTags().stream()
                        .map(t -> "#" + t.getName())
                        .collect(Collectors.joining(" "));
                sb.append(" ").append(tags);
            }

            // Include mood
            if (e.getMood() != null) {
                sb.append(" [mood: ").append(e.getMood()).append("/5]");
            }

            // Include completion for ACTION type
            if (e.getEntryType() == EntryType.ACTION) {
                sb.append(Boolean.TRUE.equals(e.getIsCompleted()) ? " ✓" : " ○");
            }

            sb.append("\n");
        }

        // Add productivity stats for that type
        if (type == AiInsightsDtos.InsightType.PRODUCTIVITY_CHECK) {
            long completed = entries.stream().filter(e -> Boolean.TRUE.equals(e.getIsCompleted())).count();
            sb.append("\nCompletion rate: ").append(completed).append("/").append(entries.size()).append(" tasks done.\n");
        }

        // Append user's optional focus note
        if (focusNote != null && !focusNote.isBlank()) {
            sb.append("\nPlease focus on: ").append(focusNote.trim());
        }

        sb.append("\n\nPlease provide your insight now.");
        return sb.toString();
    }

    // ── Goals insight ─────────────────────────────────────────────────────────

    private AiInsightsDtos.InsightResponse generateGoalsInsight(Long userId, String focusNote) {
        List<Goal> activeGoals = goalRepository.findByUserIdAndStatusWithMilestones(userId, GoalStatus.ACTIVE);
        List<Goal> allGoals    = goalRepository.findAllByUserIdWithMilestones(userId);
        long completedCount    = allGoals.stream().filter(g -> g.getStatus() == GoalStatus.COMPLETED).count();

        if (activeGoals.isEmpty() && completedCount == 0) {
            return AiInsightsDtos.InsightResponse.builder()
                    .insightType(AiInsightsDtos.InsightType.GOALS_CHECK)
                    .hasData(false)
                    .insight("You have no goals set yet. Head to the Goals page to create your first one!")
                    .entryCount(0)
                    .dateRange("")
                    .build();
        }

        String systemPrompt = """
                You are a personal journaling assistant for Logbook.
                Your ONLY job is to help the user reflect on their goal progress.
                Rules:
                - Max 150 words. Be direct, warm, and specific.
                - Never ask follow-up questions.
                - Use plain text only, no markdown.
                - Refer to the user as "you".
                - Be honest — acknowledge overdue goals, celebrate progress.
                Analyse the user's current goals and milestone completion. Give a brief honest assessment
                of their progress and one concrete suggestion for what to focus on next.
                """;

        StringBuilder sb = new StringBuilder();
        sb.append("Here are my current goals:\n\n");

        for (Goal g : activeGoals) {
            long done  = g.getMilestones().stream().filter(m -> Boolean.TRUE.equals(m.getIsCompleted())).count();
            long total = g.getMilestones().size();
            boolean overdue = g.getTargetDate() != null && g.getTargetDate().isBefore(java.time.LocalDate.now());

            sb.append("GOAL: \"").append(g.getTitle()).append("\"");
            sb.append(" [").append(g.getType().name()).append("]");
            if (overdue)       sb.append(" ⚠ OVERDUE");
            if (g.getTargetDate() != null) sb.append(" (due ").append(g.getTargetDate()).append(")");
            sb.append("\n");
            sb.append("Progress: ").append(done).append("/").append(total).append(" milestones done");
            if (total > 0) {
                sb.append(" (").append(Math.round((double) done / total * 100)).append("%)");
            }
            sb.append("\n");

            // List milestones
            for (Milestone m : g.getMilestones()) {
                sb.append("  ").append(Boolean.TRUE.equals(m.getIsCompleted()) ? "✓" : "○");
                sb.append(" ").append(m.getTitle()).append("\n");
            }
            sb.append("\n");
        }

        if (completedCount > 0) {
            sb.append("Previously completed goals: ").append(completedCount).append("\n");
        }

        if (focusNote != null && !focusNote.isBlank()) {
            sb.append("\nPlease focus on: ").append(focusNote.trim());
        }

        sb.append("\nHow are my goals going?");

        String insight = callGroq(systemPrompt, sb.toString());
        int totalMilestones = activeGoals.stream().mapToInt(g -> g.getMilestones().size()).sum();

        return AiInsightsDtos.InsightResponse.builder()
                .insightType(AiInsightsDtos.InsightType.GOALS_CHECK)
                .hasData(true)
                .insight(insight)
                .entryCount(activeGoals.size())
                .dateRange(activeGoals.size() + " active goal" + (activeGoals.size() == 1 ? "" : "s"))
                .build();
    }

    // ── Groq API call ─────────────────────────────────────────────────────────

    private String callGroq(String systemPrompt, String userPrompt) {
        try {
            RestTemplate rest = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(groqApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("max_tokens", MAX_TOKENS);
            body.put("temperature", 0.7);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user",   "content", userPrompt)
            ));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = rest.exchange(GROQ_URL, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            log.error("Groq API call failed", e);
            return "Sorry, I couldn't generate an insight right now. Please try again in a moment.";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatRange(LocalDate start, LocalDate end) {
        return start.format(DISPLAY_FMT) + " – " + end.format(DISPLAY_FMT);
    }
}