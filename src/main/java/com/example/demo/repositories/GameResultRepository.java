package com.example.demo.repositories;

import com.example.demo.entities.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {
    Optional<GameResult> findByUserIdAndFolderIdAndGameType(Long userId, Long folderId, String gameType);
}