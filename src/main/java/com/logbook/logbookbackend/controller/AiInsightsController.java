package com.logbook.logbookbackend.controller;

import com.logbook.logbookbackend.dto.AiInsightsDtos;
import com.logbook.logbookbackend.service.AiInsightsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiInsightsController {

    private final AiInsightsService aiInsightsService;

    @PostMapping("/insights")
    public ResponseEntity<AiInsightsDtos.InsightResponse> getInsight(
            Authentication auth,
            @Valid @RequestBody AiInsightsDtos.InsightRequest request) {

        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(aiInsightsService.generateInsight(userId, request));
    }
}