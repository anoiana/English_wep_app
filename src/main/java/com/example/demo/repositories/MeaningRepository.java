// file: src/main/java/com/example/demo/repositories/MeaningRepository.java
package com.example.demo.repositories;

import com.example.demo.entities.Meaning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MeaningRepository extends JpaRepository<Meaning, Long> {

    // Phương thức để lấy danh sách ID của các Meaning dựa trên danh sách Vocabulary ID
    @Query("SELECT m.id FROM Meaning m WHERE m.vocabulary.id IN :vocabularyIds")
    List<Long> findMeaningIdsByVocabularyIds(@Param("vocabularyIds") List<Long> vocabularyIds);
}