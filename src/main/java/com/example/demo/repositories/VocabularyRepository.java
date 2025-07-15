package com.example.demo.repositories;

import com.example.demo.entities.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List; // Vẫn giữ lại nếu cần ở đâu đó

public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    // Giữ lại hàm cũ nếu cần
    List<Vocabulary> findByFolderId(Long folderId);

    // Đếm số từ vựng trong một folder
    long countByFolderId(Long folderId);

    // Query mới: Tìm kiếm và phân trang cho từ vựng
    // Spring Data JPA sẽ tự tạo query từ tên phương thức
    Page<Vocabulary> findByFolderIdAndWordContainingIgnoreCase(Long folderId, String word, Pageable pageable);
}