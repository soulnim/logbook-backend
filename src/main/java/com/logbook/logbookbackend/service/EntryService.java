package com.logbook.logbookbackend.service;

import com.logbook.logbookbackend.dto.EntryDtos;
import com.logbook.logbookbackend.dto.StatsDtos;
import com.logbook.logbookbackend.entity.Entry;
import com.logbook.logbookbackend.entity.EntryType;
import com.logbook.logbookbackend.entity.Tag;
import com.logbook.logbookbackend.entity.User;
import com.logbook.logbookbackend.repository.EntryRepository;
import com.logbook.logbookbackend.repository.TagRepository;
import com.logbook.logbookbackend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EntryService {

    private final EntryRepository entryRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final EntryMapper mapper;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public EntryDtos.EntryResponse createEntry(Long userId, EntryDtos.CreateRequest request) {
        User user = getUser(userId);
        Set<Tag> tags = resolveTags(userId, user, request.getTags());

        Entry entry = Entry.builder()
                .user(user)
                .title(request.getTitle())
                .content(request.getContent())
                .entryType(request.getEntryType())
                .entryDate(request.getEntryDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isCompleted(request.getIsCompleted() != null ? request.getIsCompleted() : false)
                .mood(request.getMood())
                .tags(tags)
                .build();

        entry = entryRepository.save(entry);
        log.info("Created entry id={} userId={}", entry.getId(), userId);
        return mapper.toResponse(entry);
    }

    @Transactional(readOnly = true)
    public EntryDtos.EntryResponse getEntry(Long userId, Long entryId) {
        return mapper.toResponse(findEntry(userId, entryId));
    }

    public EntryDtos.EntryResponse updateEntry(Long userId, Long entryId, EntryDtos.UpdateRequest request) {
        Entry entry = findEntry(userId, entryId);

        if (request.getTitle() != null)
            entry.setTitle(request.getTitle());
        if (request.getContent() != null)
            entry.setContent(request.getContent());
        if (request.getEntryDate() != null)
            entry.setEntryDate(request.getEntryDate());
        if (request.getStartTime() != null)
            entry.setStartTime(request.getStartTime());
        if (request.getEndTime() != null)
            entry.setEndTime(request.getEndTime());
        if (request.getIsCompleted() != null)
            entry.setIsCompleted(request.getIsCompleted());
        if (request.getMood() != null)
            entry.setMood(request.getMood());
        if (request.getTags() != null) {
            User user = getUser(userId);
            entry.setTags(resolveTags(userId, user, request.getTags()));
        }

        return mapper.toResponse(entryRepository.save(entry));
    }

    public void deleteEntry(Long userId, Long entryId) {
        Entry entry = findEntry(userId, entryId);
        entryRepository.delete(entry);
        log.info("Deleted entry id={} userId={}", entryId, userId);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EntryDtos.EntryResponse> getAllEntries(Long userId) {
        return entryRepository.findAllByUserIdOrderByDateDesc(userId)
                .stream().map(mapper::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EntryDtos.EntryResponse> getEntriesForDate(Long userId, LocalDate date) {
        return entryRepository.findByUserIdAndEntryDateOrderByCreatedAtAsc(userId, date)
                .stream().map(mapper::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EntryDtos.EntryResponse> getEntriesInRange(Long userId, LocalDate start, LocalDate end,
                                                           EntryType type) {
        List<Entry> entries = (type != null)
                ? entryRepository.findByUserIdAndDateRangeAndType(userId, start, end, type)
                : entryRepository.findByUserIdAndDateRange(userId, start, end);
        return entries.stream().map(mapper::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EntryDtos.EntryResponse> search(Long userId, String keyword) {
        return entryRepository.searchByKeyword(userId, keyword)
                .stream().map(mapper::toResponse).collect(Collectors.toList());
    }

    // ── Heatmap ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StatsDtos.HeatmapResponse getHeatmap(Long userId, LocalDate startDate, LocalDate endDate) {
        List<Object[]> raw = entryRepository.countEntriesPerDay(userId, startDate, endDate);

        long maxCount = raw.stream().mapToLong(r -> (Long) r[1]).max().orElse(1);

        List<StatsDtos.HeatmapEntry> data = raw.stream()
                .map(r -> {
                    long count = (Long) r[1];
                    return StatsDtos.HeatmapEntry.builder()
                            .date(r[0].toString())
                            .count((int) count)
                            .level(computeLevel(count, maxCount))
                            .build();
                })
                .collect(Collectors.toList());

        int totalEntries = data.stream().mapToInt(StatsDtos.HeatmapEntry::getCount).sum();
        int activeDays = data.size();

        List<LocalDate> activeDates = entryRepository.findAllActiveDates(userId);
        int[] streaks = computeStreaks(activeDates, endDate);

        return StatsDtos.HeatmapResponse.builder()
                .data(data)
                .totalEntries(totalEntries)
                .activeDays(activeDays)
                .currentStreak(streaks[0])
                .longestStreak(streaks[1])
                .build();
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StatsDtos.StatsResponse getStats(Long userId) {
        Map<String, Long> byType = new HashMap<>();
        entryRepository.countByType(userId).forEach(r -> byType.put(r[0].toString(), (Long) r[1]));

        List<EntryDtos.EntryResponse> recent = entryRepository
                .findRecentByUserId(userId, PageRequest.of(0, 5))
                .stream().map(mapper::toResponse).collect(Collectors.toList());

        List<LocalDate> activeDates = entryRepository.findAllActiveDates(userId);
        int[] streaks = computeStreaks(activeDates, LocalDate.now());

        return StatsDtos.StatsResponse.builder()
                .totalEntries(entryRepository.countByUserId(userId))
                .activeDays(activeDates.size())
                .currentStreak(streaks[0])
                .longestStreak(streaks[1])
                .byType(byType)
                .recentEntries(recent)
                .build();
    }

    // ── Tags ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EntryDtos.TagResponse> getAllTags(Long userId) {
        return tagRepository.findByUserIdOrderByNameAsc(userId)
                .stream().map(mapper::toTagResponse).collect(Collectors.toList());
    }

    public EntryDtos.TagResponse createTag(Long userId, EntryDtos.TagCreateRequest request) {
        if (tagRepository.existsByNameIgnoreCaseAndUserId(request.getName(), userId)) {
            throw new IllegalArgumentException("Tag already exists: " + request.getName());
        }
        User user = getUser(userId);
        Tag tag = Tag.builder()
                .user(user)
                .name(request.getName().toLowerCase())
                .color(request.getColor() != null ? request.getColor() : "#6B7280")
                .build();
        return mapper.toTagResponse(tagRepository.save(tag));
    }

    public void deleteTag(Long userId, Long tagId) {
        tagRepository.findByIdAndUserId(tagId, userId)
                .ifPresentOrElse(
                        tagRepository::delete,
                        () -> {
                            throw new EntityNotFoundException("Tag not found: " + tagId);
                        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Entry findEntry(Long userId, Long entryId) {
        return entryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Entry not found: " + entryId));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private Set<Tag> resolveTags(Long userId, User user, Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty())
            return new HashSet<>();
        Set<Tag> tags = new HashSet<>();
        for (String name : tagNames) {
            String normalized = name.toLowerCase().trim();
            Tag tag = tagRepository.findByNameIgnoreCaseAndUserId(normalized, userId)
                    .orElseGet(() -> tagRepository.save(
                            Tag.builder().user(user).name(normalized).build()));
            tags.add(tag);
        }
        return tags;
    }

    /**
     * Maps a count to GitHub-style intensity level 0–4.
     */
    private int computeLevel(long count, long maxCount) {
        if (count == 0)
            return 0;
        if (maxCount <= 1)
            return 4;
        double ratio = (double) count / maxCount;
        if (ratio <= 0.25)
            return 1;
        if (ratio <= 0.50)
            return 2;
        if (ratio <= 0.75)
            return 3;
        return 4;
    }

    /**
     * Returns [currentStreak, longestStreak] from a DESC-sorted list of unique
     * active dates.
     */
    private int[] computeStreaks(List<LocalDate> datesDesc, LocalDate referenceDate) {
        if (datesDesc.isEmpty())
            return new int[] { 0, 0 };

        // 1. Current streak: starts from the latest entry if it's today or yesterday
        // (relative to ref)
        int current = 0;
        LocalDate firstDate = datesDesc.get(0);

        // If the latest entry is too old (before yesterday), current streak is 0
        if (!firstDate.isBefore(referenceDate.minusDays(1))) {
            LocalDate cursor = firstDate;
            for (LocalDate d : datesDesc) {
                if (d.equals(cursor)) {
                    current++;
                    cursor = d.minusDays(1);
                } else {
                    break;
                }
            }
        }

        // 2. Longest streak: scan ASC
        List<LocalDate> asc = new ArrayList<>(datesDesc);
        Collections.reverse(asc);
        int longest = 0;
        int currentRun = 0;
        LocalDate expected = null;

        for (LocalDate d : asc) {
            if (expected == null || d.equals(expected)) {
                currentRun++;
            } else {
                currentRun = 1;
            }
            expected = d.plusDays(1);
            if (currentRun > longest) {
                longest = currentRun;
            }
        }

        return new int[] { current, longest };
    }
}

// test