package com.example.demo.repositories;

import com.example.demo.entities.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    List<Vocabulary> findByFolderId(Long folderId);
}