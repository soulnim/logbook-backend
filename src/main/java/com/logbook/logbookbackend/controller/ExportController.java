package com.logbook.logbookbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.logbook.logbookbackend.dto.EntryDtos;
import com.logbook.logbookbackend.dto.GoalDtos;
import com.logbook.logbookbackend.service.EntryService;
import com.logbook.logbookbackend.service.GoalService;
import com.logbook.logbookbackend.entity.GoalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final EntryService entryService;
    private final GoalService  goalService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── JSON export ───────────────────────────────────────────────────────────

    @GetMapping("/json")
    public ResponseEntity<byte[]> exportJson(Authentication auth) throws Exception {
        Long userId = (Long) auth.getPrincipal();

        List<EntryDtos.EntryResponse> entries = entryService.getAllEntries(userId);
        List<GoalDtos.GoalResponse>   goals   = goalService.getAllGoals(userId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportedAt",    java.time.OffsetDateTime.now().toString());
        payload.put("totalEntries",  entries.size());
        payload.put("totalGoals",    goals.size());
        payload.put("entries",       entries);
        payload.put("goals",         goals);

        ObjectMapper pretty = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT);

        byte[] bytes    = pretty.writeValueAsBytes(payload);
        String filename = "logbook-export-" + LocalDate.now().format(FILE_DATE) + ".json";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(bytes);
    }

    // ── CSV export (entries only) ─────────────────────────────────────────────

    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv(Authentication auth) throws Exception {
        Long userId = (Long) auth.getPrincipal();
        List<EntryDtos.EntryResponse> entries = entryService.getAllEntries(userId);

        StringBuilder csv = new StringBuilder();

        // Header row
        csv.append("id,date,type,title,content,tags,mood,is_completed,start_time,end_time,created_at\n");

        for (EntryDtos.EntryResponse e : entries) {
            csv.append(e.getId()).append(',');
            csv.append(e.getEntryDate()).append(',');
            csv.append(e.getEntryType()).append(',');
            csv.append(csvEscape(e.getTitle())).append(',');
            csv.append(csvEscape(e.getContent())).append(',');

            // Tags as semicolon-separated list inside the cell
            String tags = e.getTags() == null ? "" :
                    e.getTags().stream()
                            .map(EntryDtos.TagResponse::getName)
                            .reduce((a, b) -> a + ";" + b)
                            .orElse("");
            csv.append(csvEscape(tags)).append(',');

            csv.append(e.getMood()        != null ? e.getMood()        : "").append(',');
            csv.append(e.getIsCompleted() != null ? e.getIsCompleted() : "").append(',');
            csv.append(e.getStartTime()   != null ? e.getStartTime()   : "").append(',');
            csv.append(e.getEndTime()     != null ? e.getEndTime()     : "").append(',');
            csv.append(e.getCreatedAt()   != null ? e.getCreatedAt()   : "").append('\n');
        }

        byte[] bytes    = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = "logbook-entries-" + LocalDate.now().format(FILE_DATE) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(bytes);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Wrap value in quotes and escape internal quotes for CSV. */
    private String csvEscape(String value) {
        if (value == null || value.isEmpty()) return "";
        // If contains comma, newline or quote — wrap in double quotes
        if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}