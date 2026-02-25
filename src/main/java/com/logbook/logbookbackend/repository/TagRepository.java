package com.logbook.logbookbackend.repository;

import com.logbook.logbookbackend.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByNameIgnoreCaseAndUserId(String name, Long userId);
    List<Tag> findByUserIdOrderByNameAsc(Long userId);
    boolean existsByNameIgnoreCaseAndUserId(String name, Long userId);
    Optional<Tag> findByIdAndUserId(Long id, Long userId);
}