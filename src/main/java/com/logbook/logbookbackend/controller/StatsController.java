package com.logbook.logbookbackend.controller;

import com.logbook.logbookbackend.dto.StatsDtos;
import com.logbook.logbookbackend.entity.User;
import com.logbook.logbookbackend.repository.UserRepository;
import com.logbook.logbookbackend.service.EntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final EntryService entryService;
    private final UserRepository userRepository;

    @GetMapping("/heatmap")
    public ResponseEntity<StatsDtos.HeatmapResponse> getHeatmap(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        Long userId = (Long) auth.getPrincipal();
        LocalDate today = LocalDate.now(resolveZone(userId));
        LocalDate endDate   = (end   != null) ? end   : today;
        LocalDate startDate = (start != null) ? start : endDate.minusDays(364);
        return ResponseEntity.ok(entryService.getHeatmap(userId, startDate, endDate));
    }

    @GetMapping
    public ResponseEntity<StatsDtos.StatsResponse> getStats(Authentication auth) {
        return ResponseEntity.ok(entryService.getStats((Long) auth.getPrincipal()));
    }

    private ZoneId resolveZone(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getTimezone() == null || user.getTimezone().isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(user.getTimezone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
