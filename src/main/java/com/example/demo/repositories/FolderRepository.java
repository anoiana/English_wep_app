package com.example.demo.repositories;

import com.example.demo.dto.FolderResponseDTO;
import com.example.demo.entities.Folder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// KHÔNG CẦN List NỮA

public interface FolderRepository extends JpaRepository<Folder, Long> {

    // Đếm số folder của một user
    long countByUserId(Long userId);

    // Query mới: Tìm kiếm và phân trang
    @Query("SELECT new com.example.demo.dto.FolderResponseDTO(f.id, f.name, f.user.id, size(f.vocabularies)) " +
            "FROM Folder f " +
            "WHERE f.user.id = :userId AND LOWER(f.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<FolderResponseDTO> findWithSearchAndPagination(
            @Param("userId") Long userId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);
}