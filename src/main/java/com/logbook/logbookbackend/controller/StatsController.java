package com.logbook.logbookbackend.controller;

import com.logbook.logbookbackend.dto.StatsDtos;
import com.logbook.logbookbackend.service.EntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final EntryService entryService;

    @GetMapping("/heatmap")
    public ResponseEntity<StatsDtos.HeatmapResponse> getHeatmap(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        LocalDate endDate   = (end   != null) ? end   : LocalDate.now();
        LocalDate startDate = (start != null) ? start : endDate.minusDays(364);
        return ResponseEntity.ok(entryService.getHeatmap((Long) auth.getPrincipal(), startDate, endDate));
    }

    @GetMapping
    public ResponseEntity<StatsDtos.StatsResponse> getStats(Authentication auth) {
        return ResponseEntity.ok(entryService.getStats((Long) auth.getPrincipal()));
    }
}