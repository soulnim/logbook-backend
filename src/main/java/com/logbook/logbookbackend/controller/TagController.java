package com.logbook.logbookbackend.controller;

import com.logbook.logbookbackend.dto.EntryDtos;
import com.logbook.logbookbackend.service.EntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final EntryService entryService;

    @GetMapping
    public ResponseEntity<List<EntryDtos.TagResponse>> getAllTags(Authentication auth) {
        return ResponseEntity.ok(entryService.getAllTags((Long) auth.getPrincipal()));
    }

    @PostMapping
    public ResponseEntity<EntryDtos.TagResponse> createTag(
            Authentication auth,
            @Valid @RequestBody EntryDtos.TagCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(entryService.createTag((Long) auth.getPrincipal(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(Authentication auth, @PathVariable Long id) {
        entryService.deleteTag((Long) auth.getPrincipal(), id);
        return ResponseEntity.noContent().build();
    }
}