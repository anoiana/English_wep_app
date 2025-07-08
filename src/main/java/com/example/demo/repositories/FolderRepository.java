package com.example.demo.repositories;

import com.example.demo.dto.FolderResponseDTO;
import com.example.demo.entities.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByUserId(Long userId);

    // SỬA LẠI ĐƯỜNG DẪN ĐẾN DTO, SỬ DỤNG DẤU '$'
    @Query("SELECT new com.example.demo.dto.FolderResponseDTO(f.id, f.name, f.user.id, size(f.vocabularies)) FROM Folder f WHERE f.user.id = :userId")
    List<FolderResponseDTO> findFoldersWithVocabularyCountByUserId(@Param("userId") Long userId);
}