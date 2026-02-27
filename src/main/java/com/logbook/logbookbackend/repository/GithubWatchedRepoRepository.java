package com.logbook.logbookbackend.repository;

import com.logbook.logbookbackend.entity.GithubWatchedRepo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubWatchedRepoRepository extends JpaRepository<GithubWatchedRepo, Long> {

    List<GithubWatchedRepo> findByUserIdOrderByRepoNameAsc(Long userId);

    List<GithubWatchedRepo> findByUserIdAndIsActiveTrue(Long userId);

    Optional<GithubWatchedRepo> findByUserIdAndRepoFullName(Long userId, String repoFullName);

    boolean existsByUserIdAndRepoFullName(Long userId, String repoFullName);
}