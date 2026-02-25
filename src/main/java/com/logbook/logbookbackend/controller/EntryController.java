package com.logbook.logbookbackend.controller;

import com.logbook.logbookbackend.dto.EntryDtos;
import com.logbook.logbookbackend.entity.EntryType;
import com.logbook.logbookbackend.service.EntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/entries")
@RequiredArgsConstructor
public class EntryController {

    private final EntryService entryService;

    @PostMapping
    public ResponseEntity<EntryDtos.EntryResponse> createEntry(
            Authentication auth,
            @Valid @RequestBody EntryDtos.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(entryService.createEntry(userId(auth), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntryDtos.EntryResponse> getEntry(
            Authentication auth, @PathVariable Long id
    ) {
        return ResponseEntity.ok(entryService.getEntry(userId(auth), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntryDtos.EntryResponse> updateEntry(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody EntryDtos.UpdateRequest request
    ) {
        return ResponseEntity.ok(entryService.updateEntry(userId(auth), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(Authentication auth, @PathVariable Long id) {
        entryService.deleteEntry(userId(auth), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<EntryDtos.EntryResponse>> getByDate(
            Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(entryService.getEntriesForDate(userId(auth), date));
    }

    @GetMapping("/range")
    public ResponseEntity<List<EntryDtos.EntryResponse>> getByRange(
            Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) EntryType type
    ) {
        return ResponseEntity.ok(entryService.getEntriesInRange(userId(auth), start, end, type));
    }

    @GetMapping("/search")
    public ResponseEntity<List<EntryDtos.EntryResponse>> search(
            Authentication auth, @RequestParam String q
    ) {
        return ResponseEntity.ok(entryService.search(userId(auth), q));
    }

    private Long userId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}