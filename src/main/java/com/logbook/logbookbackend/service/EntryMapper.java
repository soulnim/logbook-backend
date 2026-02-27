package com.logbook.logbookbackend.service;

import com.logbook.logbookbackend.dto.EntryDtos;
import com.logbook.logbookbackend.entity.Entry;
import com.logbook.logbookbackend.entity.Tag;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class EntryMapper {

    public EntryDtos.EntryResponse toResponse(Entry entry) {
        return EntryDtos.EntryResponse.builder()
                .id(entry.getId())
                .title(entry.getTitle())
                .content(entry.getContent())
                .entryType(entry.getEntryType())
                .entryDate(entry.getEntryDate() != null ? entry.getEntryDate().toString() : null)
                .startTime(entry.getStartTime() != null ? entry.getStartTime().toString() : null)
                .endTime(entry.getEndTime() != null ? entry.getEndTime().toString() : null)
                .isCompleted(entry.getIsCompleted())
                .mood(entry.getMood())
                .tags(entry.getTags().stream()
                        .map(this::toTagResponse)
                        .collect(Collectors.toSet()))
                .sourceMeta(entry.getSourceMeta())
                .createdAt(entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null)
                .updatedAt(entry.getUpdatedAt() != null ? entry.getUpdatedAt().toString() : null)
                .build();
    }

    public EntryDtos.TagResponse toTagResponse(Tag tag) {
        return EntryDtos.TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .build();
    }
}