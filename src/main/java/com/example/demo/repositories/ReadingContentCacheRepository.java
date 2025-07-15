package com.example.demo.repositories;

import com.example.demo.entities.ReadingContentCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReadingContentCacheRepository extends JpaRepository<ReadingContentCache, Long> {
    Optional<ReadingContentCache> findByFolderIdAndLevelAndTopic(Long folderId, int level, String topic);
}